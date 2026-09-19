/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.effects.TailCeiling
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
 * - **Active** — the owner wants reverb ([configure] with a FINITE `size >= MIN_ACTIVE_SIZE`):
 *   process normally.
 * - **Draining** — the owner turned it off while the combs still hold a tail: keep processing
 *   with SILENT input under the retained last-active parameters, so the tail mixes out on its
 *   own timeline (live sends are discarded — the owner said off). A closed-form sample-counted
 *   countdown ([Reverb.drainSamplesUntilSilent]) says when the combs are provably inaudible,
 *   and [hasTail] answers true for the whole countdown BY CONSTRUCTION (round 2 measured that a
 *   live scan would beat the countdown by only ~one revolution for real content — see there).
 *   Unlike the delay there is NO self-oscillating regime: comb feedback is structurally < 1
 *   ([Reverb.normalizeSize] bounds size in VoiceFactory, [configure] bounds it again),
 *   so every finite drain terminates — and a NON-finite countdown (an Inf or NaN comb cell from
 *   a hot send: neither ever decays) resets immediately instead: the heal the old gate's
 *   takeover path provided, and the only exit such an orbit would otherwise ever have.
 * - **Off** — countdown done: one [Reverb.reset] (combs, allpasses and LPF stores literally
 *   zero), then a true short-circuit until an owner re-enables. A reverb-carrying owner
 *   arriving MID-drain goes straight to Active with the network kept — the tail deliberately
 *   continues under the new room (send-return semantics, same as the delay).
 *
 * This adoption fixes the GATE shape only; the Freeverb internals keep their own audit round.
 *
 * **The room's SIZE glides** (`docs/plans/knob-glide.md`, the pilot, 2026-09-19): a new size
 * reaches the network over [KnobGlide]'s ~50 ms, one step per block, instead of in one step. It is
 * a COEFFICIENT knob, so the per-block value is written into the [Reverb] before it processes the
 * block ([advanceGlide]) and the shared DSP is untouched (the master shares it and does not
 * glide). It glides on the normalized 0..1 axis the [Reverb] holds; the comb feedback is affine in
 * it, so this is also a linear glide of the feedback.
 *  - **Damping does not glide.** Measured in review round 1: a full-span damping jump already sits
 *    66 to 70 dB below the tail (the comb's one-pole state stays continuous, and the change reaches
 *    the output one comb length later, staggered over the combs), so a glide would buy ~15 dB on an
 *    artifact nobody hears. A size jump sits 44 to 50 dB below it, and the glide takes 12 to 15 dB
 *    off that.
 *  - **The old path, literally.** [configure] writes the configured values exactly as before the
 *    glide existed. While a glide moves, [advanceGlide] overwrites the size before the block
 *    processes; the only reader in between is the drain countdown, which reads the glide.
 *  - **Lifecycle.** Only Active can start a glide. The first configure out of Off SNAPS (the network
 *    is empty in Off, so there is nothing to be continuous with, and a song whose room never
 *    changes stays bit-identical); Draining keeps gliding towards the last Active size, so a drain
 *    is still "Active on silent input", mid-glide too; a configure into Active from Draining
 *    glides from where the room stands; [reset] and [retire] forget the glide (both land in Off,
 *    and leaving Off is where a glide is forgotten). A chain arriving through a swap carries its
 *    own stage, whose first configure snaps like any other.
 *  - **The drain countdown** starts from the LARGER of the size in force and the size the glide is
 *    heading to: the feedback only moves between the two for the rest of the drain, and a
 *    countdown from a feedback that is still rising would end while the tail is audible. For a
 *    FALLING glide that over-holds, accepted for simplicity (a tighter bound needs its own proof):
 *    size 1.0 falling to 0.0, switched off mid-glide, counts down at feedback 0.98 (~21 s from a
 *    full-scale peak) where the room decays like ~0.7 (~1.3 s). What it holds is an idle network
 *    and a rented unit on silent input, never audio.
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
     * INVARIANT: the room params change only through [configure] and, while the size glides,
     * [advanceGlide] in [process]. A direct write bypasses the lifecycle and desyncs [state] from
     * the DSP (tests may write directly to probe the core; production must not).
     */
    var reverb: Reverb? = null
        private set

    /** Activating configures the shelf refused a unit for. Counted per orbit life, like the delay's. */
    override var deniedRents: Int = 0
        private set

    /**
     * A refusal is remembered until [reset]: the owner re-applies its config every block, and
     * without the latch a refused unit is an allocate-and-catch per block (the delay's lesson,
     * review round 1). All units are one size, so a Boolean is the whole latch. While latched the
     * shelf is still consulted — a unit another orbit or playback returned meanwhile is free to
     * take (the delay's round-2 lesson, re-learned here in round 3); only the allocation is skipped.
     */
    private var refused = false

    private var state = State.Off

    /** Remaining drain samples ([Reverb.drainSamplesUntilSilent], captured at the off-transition). */
    private var drainRemaining = 0.0

    /** All-zero input for the draining phase — the owner said off, so live sends are discarded. */
    private val silentInput = StereoBuffer(blockFrames)

    /** The Active-state tail question from a ceiling on the combs' content (see [TailCeiling]); replaces the comb scan. */
    private val activeTail = TailCeiling()

    /** The room's size on the normalized 0..1 axis [Reverb.size] holds (see the class KDoc). */
    private val sizeGlide = KnobGlide(sampleRate = units.sampleRate, blockFrames = blockFrames)

    /**
     * Applies the orbit owner's reverb settings. Called by `KatalystChain.applyParams` on every
     * block the lease is (re)claimed. An off-config does NOT reach the [reverb]: the retained
     * last-active parameters are what the drain runs on.
     *
     * Non-finite params read as OFF (or as unset, for [Reverb.lowpass]), never as the previous
     * owner's room: [Reverb]'s setters drop NaN/Inf writes, so before the lifecycle a non-finite
     * param left the DSP on whatever the previous owner set — the same leak shape the phaser's
     * depth gate had (ledger D2 round 2). Size gets the 0..1 bound HERE too, at the door
     * (conversions in one place): it is the normalized comb-feedback axis directly, and the bound
     * keeps the feedback inside the range the drain countdown is proven for (see
     * [Reverb.normalizeSize], which production size already passes through in VoiceFactory — the
     * door bound is bit-identical there and protects a future direct caller).
     */
    fun configure(
        size: Double,
        lowpass: Double?,
    ) {
        if (size.isFinite() && size >= MIN_ACTIVE_SIZE) {
            val unit = reverb ?: rentUnit() ?: return

            if (state == State.Off) {
                // The network is empty in Off: the room ARRIVES, it does not move (see the class KDoc).
                // The ONE place a glide is forgotten: [reset], [release] and the end of a drain all
                // land in Off, so this covers them without a second mechanism.
                sizeGlide.reset()
            }

            // The old path, literally: while a glide moves, [advanceGlide] overwrites the size
            // before the block processes (see the class KDoc).
            val boundedSize = size.coerceIn(0.0, 1.0)

            unit.size = boundedSize
            unit.lowpass = lowpass?.takeIf { it.isFinite() }
            sizeGlide.retarget(boundedSize)
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
            //
            // Mid-glide the size keeps moving through the drain (see [advanceGlide]), between the
            // one in force and the target, so the larger of the two bounds every revolution to come
            // (an over-hold for a falling glide, see the class KDoc). Read from the glide: an Active
            // configure earlier in this block may have written its target into the unit already.
            val drainSize = if (sizeGlide.isGliding) maxOf(sizeGlide.value, sizeGlide.target) else unit.size

            drainRemaining = unit.drainSamplesUntilSilent(peak = unit.combPeakAbs(), size = drainSize)

            if (drainRemaining <= 0.0 || !drainRemaining.isFinite()) {
                unit.reset()
                activeTail.reset() // the unit holds nothing now
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
     * zeroed size and so hid a still-charged network from cleanup. Off is empty by
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
    override fun hasTail(): Boolean = when (state) {
        State.Off -> false
        State.Draining -> true
        // A ceiling, not a scan: [process] maintains it from the send buffer.
        State.Active -> reverb != null && activeTail.hasTail
    }

    /**
     * Rents the unit on the first activating configure. Refused → stays Off, counted, latched until
     * [reset]. Nothing else in this class allocates.
     */
    private fun rentUnit(): Reverb? {
        val unit = units.rent(allocateOnMiss = !refused)

        if (unit == null) {
            if (!refused) {
                deniedRents++
                refused = true
            }

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
        activeTail.reset()
        refused = false
        deniedRents = 0 // per life, see the delay's release()
    }

    /** Clears the network, the lifecycle AND the DSP params — called from
     *  `KatalystChain.reset` on orbit deactivation. The params go back to factory here
     *  (the delay's review-round-5 rationale): [Reverb]'s setters DROP non-finite writes, so a
     *  NaN param from the next life's first owner would otherwise inherit THIS life's value.
     *  Mirrors [KatalystDelayEffect.reset]. */
    override fun reset() {
        val unit = reverb
        if (unit != null) {
            unit.reset()
            unit.size = 0.0
            unit.lowpass = null
        }
        state = State.Off
        drainRemaining = 0.0
        activeTail.reset()
        refused = false
    }

    /**
     * Moves the size glide by one block and writes it into the network while it MOVES, before the
     * network processes the block, over whatever [configure] wrote. A settled size is not written:
     * it already holds the configured value, which is what keeps a steady room bit-identical.
     */
    private fun advanceGlide(unit: Reverb) {
        if (sizeGlide.isGliding) {
            unit.size = sizeGlide.advance()
        } else {
            sizeGlide.advance()
        }
    }

    /**
     * Retiring hands the unit back DIRTY instead of clearing it ([release], not [reset]): zeroing
     * it here would be a big store on the audio thread, and the shelf zeroes it again on return
     * (`KatalystChain.retire`).
     */
    override fun retire() {
        release()
    }

    override fun process(ctx: KatalystContext) {
        // Active and Draining are only ever entered with a unit in hand; Off needs none.
        val unit = reverb ?: return

        when (state) {
            State.Off -> {}

            State.Active -> {
                // First, so the tail ceiling below reads the feedback this block runs at.
                advanceGlide(unit)

                val send = ctx.reverbSendBuffer
                val frames = ctx.blockFrames
                // Same shape as the delay's: the send peak feeds a ceiling on the combs' content,
                // decaying by the comb feedback once per longest-comb revolution (TailCeiling).
                activeTail.observe(
                    inputPeak = TailCeiling.peakOf(send, frames),
                    frames = frames,
                    windowSamples = unit.tailWindowSamples,
                    feedback = unit.tailFeedback,
                    lapsPerWindow = unit.tailLapsPerWindow,
                )
                unit.process(send, ctx.mixBuffer, frames)
            }

            State.Draining -> {
                // min(): silentInput is sized from the constructor blockFrames; a mismatched ctx
                // must not read past it. The countdown MUST decrement by the SAME clamped count
                // the DSP processed — a countdown outrunning the network would fire the terminal
                // reset while the tail is still audible (the delay's review-round-2 rationale).
                val frames = min(ctx.blockFrames, silentInput.left.size)

                advanceGlide(unit)
                unit.process(silentInput, ctx.mixBuffer, frames)

                drainRemaining -= frames

                if (drainRemaining <= 0.0) {
                    unit.reset()
                    activeTail.reset() // the unit holds nothing now
                    state = State.Off
                }
            }
        }
    }

    companion object {
        /**
         * Below this normalized size the reverb is "off" — the comb feedback floor (0.7, see
         * `Reverb.FEEDBACK_OFFSET`) makes even size 0.0 ring for ~0.7 s, so "no reverb" must be a
         * threshold decision (see [configure]). Authored, that is a `reverb(size = ...)` below 0.1.
         */
        const val MIN_ACTIVE_SIZE = 0.01
    }
}
