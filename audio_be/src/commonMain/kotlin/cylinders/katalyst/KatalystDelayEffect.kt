/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import kotlin.math.min

/**
 * Delay send/return effect for the bus pipeline.
 *
 * Reads from the delay send buffer and mixes the delayed signal into the mix buffer.
 *
 * The effect owns an active/draining/off lifecycle so that turning the delay off never freezes a
 * live tail inside the ring (block-framing ledger D3: the ring's write clock used to stop dead the
 * moment a no-delay voice took the orbit lease, and the stale tail resurrected later, detached
 * from time):
 *
 * - **Active** — the owner wants the delay ([configure] with a time >= [MIN_ACTIVE_DELAY_SECONDS]):
 *   process normally.
 * - **Draining** — the owner turned it off while the ring still holds a tail: keep processing with
 *   SILENT input under the retained last-active parameters, so the already-scheduled echoes
 *   complete on their own timeline (live sends are discarded — the owner said off). A
 *   sample-counted closed-form countdown ([DelayLine.drainSamplesUntilSilent]) says when the tail
 *   is provably inaudible. `|feedback| >= 1.0` self-oscillates and deliberately never auto-drains
 *   (raw engine — the drone IS the authored sound; the escape is a new owner with a tame delay.
 *   A CHARGED self-osc ring keeps the orbit alive indefinitely by design — that also means a
 *   stopped playback with such a ring is never reclaimed, which is PRE-EXISTING behavior, not
 *   introduced by the drain; whether orbits deserve a post-stop tail bound like the master's
 *   `MAX_MASTER_TAIL_HOLD_SECONDS` is an open maintainer question).
 * - **Off** — countdown done: one [DelayLine.reset] (the ring is now literally zero, including the
 *   pre-drain regions a future LONGER delay time could otherwise tap into), then a true
 *   short-circuit until an owner re-enables. That zero-ring guarantee is for THIS path only: a new
 *   delay-carrying owner arriving MID-drain goes straight to Active with the ring kept — the tail
 *   deliberately continues under the new tap (send-delay semantics; same raw behavior as any live
 *   delay-time change, which can always reach older ring content).
 *
 * "Off" is a threshold rather than zero because a zero time would be coerced up to the DSP minimum
 * and ring as a metallic comb instead of being silent (see the MasterChain gate note).
 */
class KatalystDelayEffect(
    /**
     * The DSP core. INVARIANT: `delayTimeSeconds`/`feedback`/`feedbackCap` change only through
     * [configure] — a direct write bypasses the lifecycle and desyncs [state] from the DSP
     * (tests may write directly to probe the core; production must not).
     */
    val delayLine: DelayLine,
    blockFrames: Int,
) : KatalystEffect {

    private enum class State { Off, Active, Draining }

    private var state = State.Off

    /** Remaining drain samples; [Double.POSITIVE_INFINITY] while `|feedback| >= 1` (self-oscillation). */
    private var drainRemaining = 0.0

    /** All-zero input for the draining phase — the owner said off, so live sends are discarded. */
    private val silentInput = StereoBuffer(blockFrames)

    /**
     * Applies the orbit owner's delay settings. Called by `Cylinder.applyBusEffects` on every
     * block the lease is (re)claimed. An off-config (time below [MIN_ACTIVE_DELAY_SECONDS]) does
     * NOT reach the [delayLine]: the retained last-active parameters are what the drain runs on.
     */
    fun configure(timeSeconds: Double, feedback: Double, cap: Double) {
        if (timeSeconds >= MIN_ACTIVE_DELAY_SECONDS) {
            delayLine.delayTimeSeconds = timeSeconds
            delayLine.feedback = feedback
            delayLine.feedbackCap = cap
            state = State.Active
            return
        }

        if (state == State.Active) {
            // One O(delayInt) scan at the transition: the countdown starts from what the TAP can
            // still reach (review round 3 replaced the static worst-case bound; round 4 shrank
            // the scan from the whole ring to the tap window — older content is overwritten
            // before the tap arrives and can never be emitted). An already-silent window
            // (including an EMPTY self-oscillating ring) goes straight to Off.
            drainRemaining = delayLine.drainSamplesUntilSilent(peak = delayLine.tapWindowPeakAbs())

            if (drainRemaining <= 0.0) {
                delayLine.reset()
                state = State.Off
            } else {
                state = State.Draining
            }
        }
        // Off, or already Draining: the countdown keeps running, nothing changes.
    }

    /**
     * True while the ring can still contribute audio — the state-aware replacement for always
     * scanning: Off is empty by construction ([DelayLine.reset] on entry); Active asks the ring.
     * Draining is tailed BY CONSTRUCTION, not by scan (settled in review round 4): it is entered
     * only when the tap window held content above the threshold (an already-silent ring goes
     * straight to Off in [configure] — that is what protects the empty-self-osc engine-leak case
     * round 1 found), and it CONSERVATIVELY reports a tail for the whole countdown — which can
     * outlive the ring's last audible sample by up to one delay period (review round 5: at high
     * fb the countdown can even outlast a full ring revolution, so a whole-ring scan COULD answer
     * false near the end; this arm never cuts audio, it only holds the orbit a bounded moment
     * longer). (A CHARGED self-osc ring pins its orbit by design; that half is pre-existing and
     * open, see the class KDoc.)
     */
    fun hasTail(): Boolean = when (state) {
        State.Off -> false
        State.Draining -> true
        State.Active -> delayLine.hasTail()
    }

    /** Clears the ring, the lifecycle AND the DSP params — called from `Cylinder.resetBusEffects`
     *  on orbit deactivation. The params go back to factory here (review round 5): `DelayLine`'s
     *  setters DROP non-finite writes, so a NaN param from the next life's first owner would
     *  otherwise inherit THIS life's value — e.g. a dead owner's self-oscillating feedback.
     *  Mirrors `Phaser.resetForReuse`. */
    fun reset() {
        delayLine.reset()
        delayLine.delayTimeSeconds = 0.0
        delayLine.feedback = 0.0
        delayLine.feedbackCap = 1.0
        state = State.Off
        drainRemaining = 0.0
    }

    override fun process(ctx: KatalystContext) {
        when (state) {
            State.Off -> {}

            State.Active -> {
                delayLine.process(ctx.delaySendBuffer, ctx.mixBuffer, ctx.blockFrames)
            }

            State.Draining -> {
                // min(): silentInput is sized from the constructor blockFrames; a mismatched ctx
                // must not read past it (on Kotlin/JS an out-of-range read is undefined -> NaN
                // straight into the ring). Production passes one value into both — Cylinder owns
                // the effect AND the context — so the clamp never binds there. The countdown MUST
                // decrement by the SAME clamped count the DSP processed (review round 2): a
                // countdown outrunning the ring would fire the terminal reset at ~-50 dBFS.
                val frames = min(ctx.blockFrames, silentInput.left.size)
                delayLine.process(silentInput, ctx.mixBuffer, frames)

                // Infinity minus a block stays Infinity, so the self-oscillating case needs no branch.
                drainRemaining -= frames

                if (drainRemaining <= 0.0) {
                    delayLine.reset()
                    state = State.Off
                }
            }
        }
    }

    companion object {
        /**
         * Below this, an authored delay time means "off" — the DSP would coerce it up to its own
         * minimum and ring as a metallic comb instead of falling silent.
         */
        const val MIN_ACTIVE_DELAY_SECONDS = 0.01
    }
}
