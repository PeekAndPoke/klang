/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import kotlin.math.min

/**
 * Reverb send/return effect for the bus pipeline.
 *
 * Reads from the reverb send buffer and mixes the wet reverb signal into the mix buffer.
 *
 * Owns the same active/draining/off lifecycle as [KatalystDelayEffect] (block-framing ledger D3,
 * adopted here as the decided follow-up): turning the reverb off must never freeze a live tail
 * inside the comb network. The old short-circuit stopped the comb clocks dead the moment a
 * no-reverb voice took the orbit lease, and the stale tail resurrected when a reverb voice
 * reclaimed it, displaced by the (block-quantised, framing-dependent) freeze length — and the
 * cylinder's param-gated tail check could not see the frozen energy either.
 *
 * - **Active** — the owner wants reverb ([configure] with an explicit finite `roomFade`, at any
 *   value including 0.0, or a FINITE `roomSize >= MIN_ACTIVE_ROOM_SIZE`): process normally.
 * - **Draining** — the owner turned it off while the combs still hold a tail: keep processing
 *   with SILENT input under the retained last-active parameters, so the tail mixes out on its
 *   own timeline (live sends are discarded — the owner said off). A closed-form sample-counted
 *   countdown ([Reverb.drainSamplesUntilSilent]) says when the combs are provably inaudible,
 *   and [hasTail] answers true for the whole countdown BY CONSTRUCTION (round 2 measured that a
 *   live scan would beat the countdown by only ~one revolution for real content — see there).
 *   Unlike the delay there is NO self-oscillating regime: comb feedback is structurally < 1
 *   ([Reverb.normalizeRoomSize] clamps roomSize in VoiceFactory, [configure] bounds roomFade),
 *   so every finite drain terminates — and a NON-finite countdown (an Inf or NaN comb cell from
 *   a hot send: neither ever decays) resets immediately instead: the heal the old gate's
 *   takeover path provided, and the only exit such an orbit would otherwise ever have.
 * - **Off** — countdown done: one [Reverb.reset] (combs, allpasses and LPF stores literally
 *   zero), then a true short-circuit until an owner re-enables. A reverb-carrying owner
 *   arriving MID-drain goes straight to Active with the network kept — the tail deliberately
 *   continues under the new room (send-return semantics, same as the delay).
 *
 * This adoption fixes the GATE shape only; the Freeverb internals keep their own audit round.
 */
class KatalystReverbEffect(
    /** The unit shelf this orbit rents from (resource warehouse, 2d). */
    private val units: ReverbUnits,
    blockFrames: Int,
) : KatalystEffect {

    /**
     * Test seam: an effect around a unit the spec already holds. It behaves exactly like one that
     * rented that unit on its first activating [configure]; a further rent is never needed.
     */
    constructor(reverb: Reverb, blockFrames: Int) : this(
        units = ReverbUnits(reverb.sampleRate),
        blockFrames = blockFrames,
    ) {
        this.reverb = reverb
    }

    private enum class State { Off, Active, Draining }

    /**
     * The DSP core, or `null` until an owner asks for reverb (a fresh orbit holds NO network — a
     * Freeverb unit is ~200 KB, and eight of them per playback were 1.6 MB zero-filled on the audio
     * thread before any note). Rented from [units] on the first activating [configure]; kept across
     * [reset] like the delay's ring, returned only by eviction (2f).
     *
     * INVARIANT: the room params change only through [configure] — a direct write bypasses the
     * lifecycle and desyncs [state] from the DSP (tests may write directly to probe the core;
     * production must not).
     */
    var reverb: Reverb? = null
        private set

    /** Activating configures the shelf refused a unit for. Counted per orbit life, like the delay's. */
    var deniedRents: Int = 0
        private set

    /**
     * A refusal is remembered until [reset]: the owner re-applies its config every block, and
     * without the latch a refused unit is an allocate-and-catch per block (the delay's lesson,
     * review round 1). All units are one size, so a Boolean is the whole latch.
     */
    private var refused = false

    private var state = State.Off

    /** Remaining drain samples ([Reverb.drainSamplesUntilSilent], captured at the off-transition). */
    private var drainRemaining = 0.0

    /** All-zero input for the draining phase — the owner said off, so live sends are discarded. */
    private val silentInput = StereoBuffer(blockFrames)

    /**
     * Applies the orbit owner's reverb settings. Called by `Cylinder.applyBusEffects` on every
     * block the lease is (re)claimed. An off-config does NOT reach the [reverb]: the retained
     * last-active parameters are what the drain runs on.
     *
     * The active test must ask the same question the DSP decays from (`roomFade ?: roomSize`):
     * testing roomSize alone made `room(0.6).roomfade(0.1)` — no `roomsize` — silent, because
     * roomSize defaults to 0.0 and the override was never reached. An explicit roomFade is
     * intent to reverberate at ANY value: 0.0 is the engine's SHORTEST tail (~0.7 s), not "off".
     *
     * Non-finite params read as OFF (or as unset, for the overrides), never as the previous
     * owner's room: [Reverb]'s setters drop NaN/Inf writes, so before the lifecycle a non-finite
     * param left the DSP on whatever the previous owner set — the same leak shape the phaser's
     * depth gate had (ledger D2 round 2). Both room axes get the 0..1 stability bound HERE, at
     * the door (conversions in one place): they are the normalized comb-feedback axis directly,
     * and above 1.0 the network has no steady state at all (see [Reverb.normalizeRoomSize],
     * which production roomSize already passes through in VoiceFactory — the door bound is
     * bit-identical there and protects a future direct caller). One accepted letter-gap: an
     * Active-by-roomFade config with a NON-finite roomSize leaves that field on its previous
     * value — provably never read, because the DSP consults roomSize only while roomFade is
     * null, and every path that nulls roomFade also writes a finite roomSize (review round 2
     * traced it).
     */
    fun configure(
        roomSize: Double,
        roomFade: Double?,
        roomLp: Double?,
        roomDim: Double?,
        iResponse: String?,
    ) {
        val fade = roomFade?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)

        if (fade != null || (roomSize.isFinite() && roomSize >= MIN_ACTIVE_ROOM_SIZE)) {
            val unit = reverb ?: rentUnit() ?: return

            unit.roomSize = roomSize.coerceIn(0.0, 1.0)
            unit.roomFade = fade
            unit.roomLp = roomLp?.takeIf { it.isFinite() }
            unit.roomDim = roomDim
            unit.iResponse = iResponse
            state = State.Active
            return
        }

        val unit = reverb
        if (state == State.Active && unit != null) {
            // One comb scan at the transition: the countdown starts from what the combs actually
            // hold, so a barely-charged network drains in proportion to its content. An
            // already-silent network goes straight to Off — and so does a BROKEN one: a
            // non-finite comb cell makes the countdown non-finite (combPeakAbs reports any
            // poisoned cell as +Inf), and the reset is the heal (review rounds 1-2; the old
            // gate's takeover path provided exactly this exit).
            drainRemaining = unit.drainSamplesUntilSilent(peak = unit.combPeakAbs())

            if (drainRemaining <= 0.0 || !drainRemaining.isFinite()) {
                unit.reset()
                state = State.Off
            } else {
                state = State.Draining
            }
        }
        // Off, or already Draining: the countdown keeps running, nothing changes.
    }

    /**
     * True while the combs can still contribute audio — the state-aware replacement for the
     * cylinder's old param-gated scan, which reported "no tail" the moment a no-reverb owner
     * zeroed roomSize and so hid a still-charged network from cleanup. Off is empty by
     * construction ([Reverb.reset] on every entry); Active asks the network; Draining is tailed
     * BY CONSTRUCTION for the whole countdown, mirroring [KatalystDelayEffect.hasTail].
     *
     * Review history, so nobody relitigates it: round 1 flipped the Draining arm to a live
     * network scan, arguing the fb-only countdown ignores damping and over-holds a stopped
     * playback's engine ~20x. Round 2 DISPROVED the number: the comb damping LPF has unity DC
     * gain, so a tail's low-frequency component decays at exactly fb per revolution no matter
     * how dark the room (damping darkens a tail, it does not shorten it) — a scan frees the
     * orbit ~one revolution (~37 ms) earlier for LF-bearing content and ~30% earlier only for
     * purely-HF charges (the LPF's impulse smearing), never the claimed 20 s. The
     * by-construction arm was restored: it matches the settled sibling, keeps the cleanup poll
     * O(1) instead of an O(22k) scan per tail check, and its conservatism is bounded by that
     * same impulse-smear class. Safety is unchanged either way (a held tail never cuts audio),
     * and the hold is content-proportional because the countdown starts from the MEASURED peak
     * — an orbit ringing out under a live owner held the engine just as long before this
     * lifecycle existed.
     */
    fun hasTail(): Boolean = when (state) {
        State.Off -> false
        State.Draining -> true
        State.Active -> reverb?.hasTail() ?: false
    }

    /**
     * Rents the unit on the first activating configure. Refused → stays Off, counted, latched until
     * [reset]. Nothing else in this class allocates.
     */
    private fun rentUnit(): Reverb? {
        if (refused) {
            return null
        }

        val unit = units.rent()

        if (unit == null) {
            deniedRents++
            refused = true

            return null
        }

        reverb = unit

        return unit
    }

    /**
     * The return path (resource warehouse, 2f): hands the network back to the shelf and forgets
     * it. Called when the owning engine is disposed — never while the orbit can still render.
     */
    fun release() {
        reverb?.let { units.giveBack(it) }
        reverb = null
        state = State.Off
        drainRemaining = 0.0
        refused = false
    }

    /** Clears the network, the lifecycle AND the DSP params — called from
     *  `Cylinder.resetBusEffects` on orbit deactivation. The params go back to factory here
     *  (the delay's review-round-5 rationale): [Reverb]'s setters DROP non-finite writes, so a
     *  NaN param from the next life's first owner would otherwise inherit THIS life's value.
     *  Mirrors [KatalystDelayEffect.reset]. */
    fun reset() {
        val unit = reverb
        if (unit != null) {
            unit.reset()
            unit.roomSize = 0.0
            unit.damp = 0.5
            unit.roomFade = null
            unit.roomLp = null
            unit.roomDim = null
            unit.iResponse = null
        }
        state = State.Off
        drainRemaining = 0.0
        refused = false
    }

    override fun process(ctx: KatalystContext) {
        // Active and Draining are only ever entered with a unit in hand; Off needs none.
        val unit = reverb ?: return

        when (state) {
            State.Off -> {}

            State.Active -> {
                unit.process(ctx.reverbSendBuffer, ctx.mixBuffer, ctx.blockFrames)
            }

            State.Draining -> {
                // min(): silentInput is sized from the constructor blockFrames; a mismatched ctx
                // must not read past it. The countdown MUST decrement by the SAME clamped count
                // the DSP processed — a countdown outrunning the network would fire the terminal
                // reset while the tail is still audible (the delay's review-round-2 rationale).
                val frames = min(ctx.blockFrames, silentInput.left.size)
                unit.process(silentInput, ctx.mixBuffer, frames)

                drainRemaining -= frames

                if (drainRemaining <= 0.0) {
                    unit.reset()
                    state = State.Off
                }
            }
        }
    }

    companion object {
        /**
         * Below this, an authored roomSize means "off" — the comb feedback floor (0.7, see
         * `Reverb.FEEDBACK_OFFSET`) makes even roomSize 0.0 ring for ~0.7 s, so "no reverb"
         * must be a threshold decision. An explicit roomFade bypasses this test entirely
         * (see [configure]).
         */
        const val MIN_ACTIVE_ROOM_SIZE = 0.01
    }
}
