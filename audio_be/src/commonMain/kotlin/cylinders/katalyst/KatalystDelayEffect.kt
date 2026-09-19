/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.TailCeiling
import io.peekandpoke.klang.audio_be.warehouse.ResourceWarehouse
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
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
 * from time). The transition table on [State] is AUTHORITATIVE for the edges: which event moves
 * which state where is settled there and nowhere else. The bullets below give the reasoning, and
 * where one of them names an edge it is quoting that table, not competing with it.
 *
 * - **Active** — the owner wants the delay ([configure] with a FINITE time >= [MIN_ACTIVE_DELAY_SECONDS]):
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
     * INVARIANT (unchanged): `time`/`feedback`/`cap` change only through
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
    override var deniedRents: Int = 0
        private set

    /**
     * The smallest ring CLASS the warehouse has failed to allocate for this effect, or 0. Without
     * it a refusal is retried on EVERY block: `KatalystChain.applyParams` re-applies the owner's
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
     * Frames a ring must hold to serve [time], including the interpolation guard. Past the
     * Int range (13.5 h at 44.1 kHz, or a non-finite time) it saturates to `Int.MAX_VALUE`, which
     * no allocator serves — so the request degrades to `null` as the design intends, instead of
     * `toInt()` saturating and the `+ margin` wrapping NEGATIVE and quietly renting the smallest ring.
     */
    private fun framesFor(time: Double): Int {
        val frames = ceil(time * sampleRate)

        if (!(frames < Int.MAX_VALUE - RING_MARGIN_FRAMES)) { // also catches NaN
            return Int.MAX_VALUE
        }

        return frames.toInt() + RING_MARGIN_FRAMES
    }

    /**
     * Ensures a ring that holds [time] is installed, renting or growing as needed. Returns the
     * line to use, or `null` if there is none and the warehouse refused one.
     *
     * Growing rents the next sufficient class, **migrates the old ring's history into it** (2c —
     * `DelayLine.adoptHistory`, so a delay that is ringing at that moment keeps ringing across the
     * seam), then gives the old ring back. Never shrinks: a shorter time keeps the ring it has.
     */
    private fun ensureRing(time: Double): DelayLine? {
        val needed = framesFor(time)
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

    /** All-zero input for the draining phase — the owner said off, so live sends are discarded. */
    private val silentInput = StereoBuffer(blockFrames)

    /**
     * The Active-state tail question answered from a ceiling on the ring's content (see
     * [TailCeiling]), maintained every block from the send buffer; it replaces the O(ring) scan
     * `DelayLine.hasTail` used to run from `Cylinder.tryDeactivate`.
     *
     * It lives on the effect rather than on [Active] because it OUTLIVES that state: nothing
     * clears it on the way into [Draining], so a delay that returns before the drain ends resumes
     * with the ceiling it had. [TailCeiling.observe] runs only in [Active.process], so across a
     * drain the ceiling is FROZEN while the ring moves underneath it.
     *
     * **This paragraph is the one home of what the frozen ceiling is worth.** It is a bound while
     * Draining and at the moment of return, wherever the ring decays, which is every
     * `|feedback| < 1`: there the stale number says "the ring holds no more than this", and that is
     * the safe direction, because this number decides whether the orbit may be torn down. It is NOT
     * a bound in at least the three places below (the list is what review found, not a proof that
     * there are no more), and each can let [hasTail] answer false over real content:
     * - a tap LENGTHENED on the way back reaches cells the ceiling has already decayed past. This
     *   is the exception [TailCeiling] files itself, and it prices the cut it can make: -60 to
     *   -90 dBFS if an orbit deactivates in that instant. It belongs to live delay-time changes
     *   rather than to the drain.
     * - at `|feedback| >= 1` the ring GROWS under the frozen ceiling. While the drain runs that
     *   cannot cut anything (the drain is infinite by design and the frozen value is already above
     *   [TailCeiling.SILENCE]), and neither can the moment of return. AFTERWARDS it can: a
     *   self-oscillating delay takes a charge, the owner leaves, the ring grows to the cap under a
     *   ceiling frozen at a fraction of it, and a new owner returns with a TAME feedback (the
     *   escape this class's KDoc documents) and silent sends. The stale ceiling then decays
     *   geometrically and crosses [TailCeiling.SILENCE] while the ring still holds about
     *   `(ring at return / frozen ceiling) * 1e-5`. Traced through [TailCeiling] at a 0.4 s
     *   delay with a returning feedback near 1: a 0.1 send peak leaves about -86 dBFS behind,
     *   0.005 about -60 dBFS and 0.0002 about -32 dBFS, after several windows of silence. With a
     *   returning feedback near ZERO it is louder and sooner, see the next bullet.
     * - a feedback REDUCED to (near) zero, after a drain or LIVE on an owner handover:
     *   [TailCeiling.observe] recomputes the running window with the feedback in force NOW, so the
     *   ceiling vanishes at the next window close while the ring still holds one repeat written
     *   under the old feedback. Measured on the compiled classes in the 5c-1 review: at 0.7 to
     *   0.0 an echo at about -3 to -6 dBFS is dropped in a sizeable share of phases, the orbit
     *   reset within about 80 blocks. It needs the new owner to send nothing audible and the mix
     *   to be silent for the cylinder's ten blocks while the repeat is in flight; no built-in song
     *   reaches it. A feedback of 0.01 or more stays below -83 dBFS.
     *   All three are PRE-EXISTING and bit-identical before this state machine, so they are
     *   recorded as open (`audio/MEMORY.md`, `docs/tasks/katalyst-dsl.md`) rather than fixed here:
     *   any repair moves the block on which an orbit resets and needs the listening checkpoint.
     *   The reverb does not share them (fixed comb lengths, comb feedback structurally below 1),
     *   so this paragraph must NOT be copied into its conversion.
     *
     * Entering [Off] is the one place the ceiling is forgotten, because there the ring is empty.
     * That line is load-bearing: without it the next life reads the previous life's ceiling and
     * the orbit is held open long past its due. Guarded by "a life that ended in Off starts the
     * next one with an empty ceiling" in `KatalystDelayEffectSpec`.
     */
    private val activeTail = TailCeiling()

    /**
     * The lifecycle as a state machine (`docs/plans/effect-state-machines.md`), one class per
     * state. One instance of each is created with the effect and [state] points at the current
     * one, so a transition is a pointer swap and nothing allocates on the audio thread. `enter`
     * is the only way into a state, and it is what resets that state's own data.
     *
     * | state \ event | `configure`, delay ON | `configure`, delay OFF | `process`, countdown ends | `reset` | `retire` / `release` |
     * |---|---|---|---|---|---|
     * | **Off** | **Active** (a ring is rented if needed). Stays Off in ONE case: there is no ring at all AND the warehouse refuses one. A refused GROW is not that case, it keeps the ring it has and activates on it, with the time clamped to what that ring holds | Off | (never) | Off | Off |
     * | **Active** | Active (the knobs are rewritten) | **Draining**, or **Off** when the tap window is already silent | (never) | Off | Off |
     * | **Draining** | **Active** (the ring and its ceiling carry on under the new tap) | Draining (the countdown keeps running) | **Off** | Off | Off |
     *
     * The ON arm of [configure] is the same from every state, so it stays on the effect and only
     * the OFF arm dispatches ([deactivate]). Per block that is one virtual call for the block
     * ([process]) and, on the OFF arm only, one more for the owner's config: a running delay takes
     * the ON arm and dispatches nothing through [state]. That is the price of the `when`s they
     * replace.
     *
     * `sealed` buys no exhaustive `when` here, because no `when` over the states is left: it is
     * documentation that the set is closed, and the compiler's guarantee that a fourth state
     * cannot appear from outside this file.
     */
    private sealed class State {
        /**
         * One block. Everything the block needs is read into a local first (the ring, the frame
         * count, the countdown); the per-sample loop itself belongs to [DelayLine].
         */
        abstract fun process(ctx: KatalystContext)

        /** This state's answer to [KatalystDelayEffect.hasTail], where the reasoning lives. */
        abstract fun hasTail(): Boolean

        /**
         * The owner's off-config arrived. [line] is the effect's ring, which [configure] has
         * already proven non-null. A state that has nothing to do with it ignores it.
         *
         * Do not read the parameter as a pattern: it is here ONLY so the one state that uses the
         * ring does not repeat a null check the caller has just done. [process] takes the context
         * and reads `delayLine` itself, because there the caller has checked nothing.
         */
        abstract fun deactivate(line: DelayLine)
    }

    /** Nothing in the ring and nothing to do: a true short-circuit until an owner re-enables. */
    private inner class Off : State() {
        /**
         * PRECONDITION, and the contract every copy of this template inherits: the caller has
         * already emptied the unit, because [hasTail] here answers a hardcoded `false` and cannot
         * check. Entering Off with a charged ring is therefore a silent cut, not a wrong number.
         *
         * The four callers and how each of them satisfies it: [Active.deactivate] zeroes the ring
         * when the tap window it measured is already silent (no countdown runs on that arm);
         * [Draining.process] zeroes it when the countdown runs out; [reset] zeroes it and puts the
         * DSP params back to factory; [release] hands the ring to the shelf DIRTY and drops it, so
         * there is nothing left to empty. All that is left here is forgetting the ceiling.
         */
        fun enter() {
            activeTail.reset()
            state = this
        }

        override fun process(ctx: KatalystContext) {}

        override fun hasTail(): Boolean = false

        /** Already off: an owner that says off again says nothing. */
        override fun deactivate(line: DelayLine) {}
    }

    /**
     * The owner wants the delay. It carries no data of its own: the knobs live on the [DelayLine],
     * where the DSP reads them, and the tail ceiling outlives this state (see [activeTail]).
     */
    private inner class Active : State() {
        fun enter() {
            state = this
        }

        override fun process(ctx: KatalystContext) {
            // A ring is implied here; the guard is so no path can throw in render.
            val line = delayLine ?: return
            val send = ctx.delaySendBuffer
            val frames = ctx.blockFrames

            // The tail question is answered from the INPUT: the block's send peak feeds a
            // ceiling on the ring's content that decays by the feedback once per delay period
            // (TailCeiling). O(block) here, O(1) to ask. Nothing is ever O(ring).
            activeTail.observe(
                inputPeak = TailCeiling.peakOf(send, frames),
                frames = frames,
                windowSamples = line.tailWindowSamples,
                feedback = line.feedback,
                lapsPerWindow = line.tailLapsPerWindow,
            )
            line.process(send, ctx.mixBuffer, frames)
        }

        /** A ceiling, not a scan: [process] maintains it from the send buffer. */
        override fun hasTail(): Boolean = delayLine != null && activeTail.hasTail

        override fun deactivate(line: DelayLine) {
            // One O(delayInt) scan at the transition: the countdown starts from what the TAP can
            // still reach (review round 3 replaced the static worst-case bound; round 4 shrank
            // the scan from the whole ring to the tap window: older content is overwritten
            // before the tap arrives and can never be emitted). An already-silent window
            // (including an EMPTY self-oscillating ring) goes straight to Off.
            val remaining = line.drainSamplesUntilSilent(peak = line.tapWindowPeakAbs())

            if (remaining <= 0.0) {
                line.reset()
                off.enter()
            } else {
                draining.enter(remaining)
            }
        }
    }

    /**
     * The owner turned the delay off while the ring still held a tail: keep processing with SILENT
     * input under the retained last-active parameters, so the already-scheduled echoes complete on
     * their own timeline. The countdown is this state's whole data, and [enter] is its only
     * INITIALISER: [process] advances it every block, nothing outside this class touches it at
     * all. That is why a delay that returns mid-drain and leaves again gets a fresh countdown and
     * never the remains of the previous one, and it is what made three defensive writes of the old
     * flag version dead code (the two `drainRemaining = 0.0` in [reset] and [release], and the
     * field write on the arm that went straight to Off).
     */
    private inner class Draining : State() {
        /** Remaining drain samples; [Double.POSITIVE_INFINITY] while `|feedback| >= 1` (self-oscillation). */
        private var remaining = 0.0

        fun enter(remaining: Double) {
            this.remaining = remaining
            state = this
        }

        override fun process(ctx: KatalystContext) {
            // A ring is implied here; the guard is so no path can throw in render.
            val line = delayLine ?: return
            // min(): silentInput is sized from the constructor blockFrames; a mismatched ctx
            // must not read past it (on Kotlin/JS an out-of-range read is undefined -> NaN
            // straight into the ring). Production passes one value into both (Cylinder owns
            // the effect AND the context), so the clamp never binds there. The countdown MUST
            // decrement by the SAME clamped count the DSP processed (review round 2): a
            // countdown outrunning the ring would fire the terminal reset at ~-50 dBFS.
            val frames = min(ctx.blockFrames, silentInput.left.size)
            line.process(silentInput, ctx.mixBuffer, frames)

            // Infinity minus a block stays Infinity, so the self-oscillating case needs no branch.
            val left = remaining - frames
            remaining = left

            if (left <= 0.0) {
                line.reset()
                off.enter()
            }
        }

        /** Tailed BY CONSTRUCTION, never by scan: see [KatalystDelayEffect.hasTail]. */
        override fun hasTail(): Boolean = true

        /**
         * Already draining: the countdown keeps running on the parameters it started with. A
         * second off-config must NOT restart it, or an owner re-applied every block would hold
         * the tail open for ever.
         */
        override fun deactivate(line: DelayLine) {}
    }

    private val off = Off()
    private val active = Active()
    private val draining = Draining()

    /**
     * The current state: one of the three instances above, never a fresh one.
     *
     * This initializer is the one entry into Off that does not run [Off.enter], and the exception
     * is stated here because "enter is the only way in" is the rule the other conversions copy: a
     * freshly built [TailCeiling] is what `reset()` would make of it, and there is no ring yet to
     * empty, so the precondition holds by construction.
     */
    private var state: State = off

    /**
     * Test seam: the current state OBJECT, for `KatalystDelayStateIdentitySpec`, which walks a full
     * transition cycle and asserts that only ever those three instances appear. That is how the
     * "a transition allocates nothing" rule of `docs/plans/effect-state-machines.md` is guarded
     * without an allocation profiler. Production never reads it.
     */
    internal val currentState: Any get() = state

    /**
     * Applies the orbit owner's delay settings. Called by `KatalystChain.applyParams` on every
     * block the lease is (re)claimed. An off-config (a time that is non-finite or below [MIN_ACTIVE_DELAY_SECONDS]) does
     * NOT reach the [delayLine]: the retained last-active parameters are what the drain runs on.
     */
    fun configure(time: Double, feedback: Double, cap: Double) {
        // Non-finite reads as OFF (time) or as the shared default (feedback, cap), never as the previous
        // owner's value: DelayLine's setters DROP non-finite writes, so passing one through would leave
        // whatever the last owner set. VoiceFactory already turns non-finite slots into defaults; this
        // guard is the door's own contract for a direct caller (the reverb door reads a non-finite size
        // as off the same way).
        if (time.isFinite() && time >= MIN_ACTIVE_DELAY_SECONDS) {
            // No ring and none to be had: the orbit stays dry rather than the worklet dying.
            val line = ensureRing(time) ?: return

            // No tail bookkeeping here: the ceiling reads the period and feedback in force on
            // every block, so a change (or a grow, which keeps the content) is simply followed.
            line.time = time
            line.feedback = if (feedback.isFinite()) feedback else DELAY_FEEDBACK
            line.cap = if (cap.isFinite()) cap else DELAY_CAP
            active.enter()
            return
        }

        // Never activated: nothing to drain.
        val line = this.delayLine ?: return

        // Only [Active] has work to do here; the other two say why they do not.
        state.deactivate(line)
    }

    /**
     * True while the ring can still contribute audio, the state-aware replacement for always
     * scanning: Off is empty by construction ([DelayLine.reset] on entry); Active asks the
     * CEILING, not the ring (see [activeTail], which is also where what that ceiling is worth is
     * written down).
     * Draining is tailed BY CONSTRUCTION, not by scan (settled in review round 4): it is entered
     * only when the tap window held content above the threshold (an already-silent ring goes
     * straight to Off from [Active.deactivate], which is what protects the empty-self-osc
     * engine-leak case round 1 found), and it CONSERVATIVELY reports a tail for the whole
     * countdown, which can
     * outlive the ring's last audible sample by up to one delay period (review round 5: at high
     * fb the countdown can even outlast a full ring revolution, so a whole-ring scan COULD answer
     * false near the end; this arm never cuts audio, it only holds the orbit a bounded moment
     * longer). (A CHARGED self-osc ring pins its orbit by design; that half is pre-existing and
     * open, see the class KDoc.)
     */
    override fun hasTail(): Boolean = state.hasTail()

    /**
     * The return path (resource warehouse, 2f): hands the ring back to the shelf and forgets it.
     * Called when the owning engine is disposed — never while the orbit can still render, since
     * the ring now belongs to whoever rents it next. A later [configure] would rent afresh.
     */
    fun release() {
        delayLine?.let { rings.giveBack(it.ring) }
        delayLine = null
        off.enter()
        refusedFrames = 0
        deniedRents = 0 // per life: a shelved cylinder must not carry a previous engine's count
    }

    /** Clears the ring, the lifecycle AND the DSP params, called from `KatalystChain.reset`
     *  on orbit deactivation. The params go back to factory here (review round 5): `DelayLine`'s
     *  setters DROP non-finite writes, so a NaN param from the next life's first owner would
     *  otherwise inherit THIS life's value — e.g. a dead owner's self-oscillating feedback.
     *  Mirrors `Phaser.resetForReuse`. */
    override fun reset() {
        // The ring is KEPT — re-activation is then free. Eviction (2f) is what returns it.
        delayLine?.let {
            it.reset()
            it.time = 0.0
            it.feedback = 0.0
            it.cap = DELAY_CAP
        }
        off.enter()
        refusedFrames = 0
    }

    /**
     * Retiring hands the unit back DIRTY instead of clearing it ([release], not [reset]): zeroing
     * it here would be a big store on the audio thread, and the shelf zeroes it again on return
     * (`KatalystChain.retire`).
     */
    override fun retire() {
        release()
    }

    override fun process(ctx: KatalystContext) = state.process(ctx)

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
