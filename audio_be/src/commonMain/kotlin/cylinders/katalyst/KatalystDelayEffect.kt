/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.TailCountdown
import io.peekandpoke.klang.audio_be.warehouse.ResourceWarehouse
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import kotlin.math.ceil
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
    /** Where the ring comes from. Rented on first activation, never before (resource warehouse, 2b). */
    private val rings: SizedBuffers,
    private val sampleRate: Int,
    blockFrames: Int,
) : KatalystEffect {
    /**
     * Test seam: start with a ring already installed, as every spec written before rings were rented
     * does. Production never calls this — a cylinder's delay has NO ring until a voice asks for one.
     */
    constructor(delayLine: DelayLine, blockFrames: Int) : this(
        rings = SizedBuffers(baseFrames = delayLine.capacityFrames, budgetBytes = Int.MAX_VALUE),
        sampleRate = delayLine.sampleRate,
        blockFrames = blockFrames,
    ) {
        this.delayLine = delayLine
    }

    /**
     * The DSP core, or `null` while this orbit has never asked for a delay. That null is the whole
     * point: a cylinder used to construct a 10-second ring (7.68 MB, 97 % of the cylinder) whether
     * or not the orbit ever used delay, and eight of those zero-filled on the audio thread on first
     * play were the "Der Schmetterling" stutter. Now the ring is rented from the warehouse on the
     * first `configure` that activates, sized to the class that holds the requested time.
     *
     * INVARIANT (unchanged): `delayTimeSeconds`/`feedback`/`feedbackCap` change only through
     * [configure] — a direct write bypasses the lifecycle and desyncs [state] from the DSP
     * (tests may write directly to probe the core; production must not).
     */
    var delayLine: DelayLine? = null
        private set

    /**
     * Rents refused by the warehouse (out of memory). The effect degrades rather than dies: a
     * first-activation refusal leaves it Off; a grow refusal keeps the current ring, and
     * `DelayLine` clamps the time to what that ring holds. Surfaced through diagnostics later.
     */
    var deniedRents: Int = 0
        private set

    /**
     * The smallest ring CLASS the warehouse has failed to allocate for this effect, or 0. Without
     * it a refusal is retried on EVERY block — `Cylinder.applyBusEffects` re-applies the owner's
     * config per block — and "graceful degradation" becomes a 344 Hz allocate-and-catch storm on the
     * audio thread (review round 1, both reviewers). While the needed class is `>= refusedFrames`
     * no ALLOCATION is attempted; the shelf is still consulted, because a ring another orbit
     * returned meanwhile is free to take (review round 2). Keyed on the class, not the raw frame
     * count: two times inside one class are the same allocation, and the smaller one must not slip
     * past the latch into a retry that is certain to fail. Cleared by [reset] (a new owner life)
     * and by a later SUCCESSFUL rent of anything.
     */
    private var refusedFrames: Int = 0

    /**
     * Frames a ring must hold to serve [timeSeconds], including the interpolation guard. Past the
     * Int range (13.5 h at 44.1 kHz, or a non-finite time) it saturates to `Int.MAX_VALUE`, which
     * no allocator serves — so the request degrades to `null` as the design intends, instead of
     * `toInt()` saturating and the `+ margin` wrapping NEGATIVE and quietly renting the smallest ring.
     */
    private fun framesFor(timeSeconds: Double): Int {
        val frames = ceil(timeSeconds * sampleRate)

        if (!(frames < Int.MAX_VALUE - RING_MARGIN_FRAMES)) { // also catches NaN
            return Int.MAX_VALUE
        }

        return frames.toInt() + RING_MARGIN_FRAMES
    }

    /**
     * Ensures a ring that holds [timeSeconds] is installed, renting or growing as needed. Returns the
     * line to use, or `null` if there is none and the warehouse refused one.
     *
     * Growing rents the next sufficient class, **migrates the old ring's history into it** (2c —
     * `DelayLine.adoptHistory`, so a delay that is ringing at that moment keeps ringing across the
     * seam), then gives the old ring back. Never shrinks: a shorter time keeps the ring it has.
     */
    private fun ensureRing(timeSeconds: Double): DelayLine? {
        val needed = framesFor(timeSeconds)
        val current = delayLine

        if (current != null && current.capacityFrames >= needed) {
            return current
        }

        val neededClass = rings.classFrames(needed)
        val latched = refusedFrames != 0 && neededClass >= refusedFrames

        // Latched: the shelf only — never the allocation that already failed at this class.
        val ring = rings.rent(needed, allocateOnMiss = !latched)

        if (ring == null) {
            if (!latched) {
                deniedRents++
                refusedFrames = neededClass
            }

            // Keep what we have (the time will clamp to it), or stay without.
            return current
        }

        refusedFrames = 0

        val line = DelayLine(ring, sampleRate)

        if (current != null) {
            // Adopt FIRST: once given back the ring belongs to the shelf and its next renter.
            line.adoptHistory(current)
            rings.giveBack(current.ring)
        }

        delayLine = line

        return line
    }

    private enum class State { Off, Active, Draining }

    private var state = State.Off

    /** Remaining drain samples; [Double.POSITIVE_INFINITY] while `|feedback| >= 1` (self-oscillation). */
    private var drainRemaining = 0.0

    /** All-zero input for the draining phase — the owner said off, so live sends are discarded. */
    private val silentInput = StereoBuffer(blockFrames)

    /**
     * The Active-state tail question in closed form (see [TailCountdown]): fed every block from
     * the send buffer, it replaces the O(ring) scan `DelayLine.hasTail` used to run from
     * `Cylinder.tryDeactivate`.
     */
    private val activeTail = TailCountdown()

    /**
     * Applies the orbit owner's delay settings. Called by `Cylinder.applyBusEffects` on every
     * block the lease is (re)claimed. An off-config (time below [MIN_ACTIVE_DELAY_SECONDS]) does
     * NOT reach the [delayLine]: the retained last-active parameters are what the drain runs on.
     */
    fun configure(timeSeconds: Double, feedback: Double, cap: Double) {
        if (timeSeconds >= MIN_ACTIVE_DELAY_SECONDS) {
            // No ring and none to be had: the orbit stays dry rather than the worklet dying.
            val line = ensureRing(timeSeconds) ?: return

            // A changed time or feedback changes how fast a silent ring decays: re-measure.
            if (line.delayTimeSeconds != timeSeconds || line.feedback != feedback) {
                activeTail.invalidate()
            }
            line.delayTimeSeconds = timeSeconds
            line.feedback = feedback
            line.feedbackCap = cap
            state = State.Active
            return
        }

        // Never activated: nothing to drain.
        val delayLine = this.delayLine ?: return

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
        // Closed form, not a scan: the countdown [process] feeds from the send buffer.
        State.Active -> delayLine != null && activeTail.hasTail
    }

    /**
     * The return path (resource warehouse, 2f): hands the ring back to the shelf and forgets it.
     * Called when the owning engine is disposed — never while the orbit can still render, since
     * the ring now belongs to whoever rents it next. A later [configure] would rent afresh.
     */
    fun release() {
        delayLine?.let { rings.giveBack(it.ring) }
        delayLine = null
        state = State.Off
        drainRemaining = 0.0
        activeTail.reset()
        refusedFrames = 0
        deniedRents = 0 // per life: a shelved cylinder must not carry a previous engine's count
    }

    /** Clears the ring, the lifecycle AND the DSP params — called from `Cylinder.resetBusEffects`
     *  on orbit deactivation. The params go back to factory here (review round 5): `DelayLine`'s
     *  setters DROP non-finite writes, so a NaN param from the next life's first owner would
     *  otherwise inherit THIS life's value — e.g. a dead owner's self-oscillating feedback.
     *  Mirrors `Phaser.resetForReuse`. */
    fun reset() {
        // The ring is KEPT — re-activation is then free. Eviction (2f) is what returns it.
        delayLine?.let {
            it.reset()
            it.delayTimeSeconds = 0.0
            it.feedback = 0.0
            it.feedbackCap = 1.0
        }
        state = State.Off
        drainRemaining = 0.0
        activeTail.reset()
        refusedFrames = 0
    }

    override fun process(ctx: KatalystContext) {
        // Active and Draining both imply a ring; the guard is so no path can throw in render.
        val delayLine = this.delayLine ?: return

        when (state) {
            State.Off -> {}

            State.Active -> {
                val send = ctx.delaySendBuffer
                val frames = ctx.blockFrames
                // The tail question is answered from the INPUT: audible send → a tail exists; the
                // block it goes silent → one bounded read of what the tap can still reach starts
                // the proof; after that, subtraction. Nothing here is O(ring).
                activeTail.observe(silent = TailCountdown.isSilent(send, frames), frames = frames) {
                    delayLine.drainSamplesUntilSilent(peak = delayLine.tapWindowPeakAbs())
                }
                delayLine.process(send, ctx.mixBuffer, frames)
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
        /** Headroom past the requested time so `DelayLine`'s `bufferSize - 2` interpolation guard never clamps it. */
        /** See [ResourceWarehouse.RING_MARGIN_FRAMES] — class 0 of the shelf includes it. */
        const val RING_MARGIN_FRAMES = ResourceWarehouse.RING_MARGIN_FRAMES

        /**
         * Below this, an authored delay time means "off" — the DSP would coerce it up to its own
         * minimum and ring as a metallic comb instead of falling silent.
         */
        const val MIN_ACTIVE_DELAY_SECONDS = 0.01
    }
}
