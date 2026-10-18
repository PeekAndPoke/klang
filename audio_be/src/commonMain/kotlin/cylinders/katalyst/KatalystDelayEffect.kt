/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.TailCeiling
import io.peekandpoke.klang.audio_be.utils.sameKnob
import io.peekandpoke.klang.audio_be.warehouse.ResourceWarehouse
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * The orbit delay, an insert-style stage (Katalyst step 5b-2, 2026-09-19): it is fed from the orbit
 * mix AT ITS POSITION in the chain, scaled by the orbit owner's ONE `wet`, and it adds its echoes
 * into that same mix, so the dry signal stays and whatever follows in the chain (the reverb in the
 * classic order) hears the echoes. The same effect runs at the output (`master(...)`).
 *
 * **What glides** (`audio/ref/knob-glide.md`), each because its jump was measured audible first
 * (step 5b-2, a band-limited pad and a pluck, energy above 8 kHz against a hard cut of the return):
 *  - `wet`, a LEVEL knob, per sample ([KnobGlide.advanceScaled]) into the feed.
 *  - `feedback`: a jump sat at -28 to -35 dB for 0.3 to 0.7, 0.7 to 0.0 and 0.0 to 0.9 (a hard
 *    cut: -26 to -31 dB), -47 dB for 0.3 to 0.4, on the pad. The glide moves it one step per block
 *    and [DelayLine] ramps each step per sample (a gain on the recirculating audio has no state to
 *    smooth a per-block step, which came back every period at -47 to -55 dB); together they reach
 *    the steady floor (-90 to -93 dB). The old path literally, as for the reverb's size:
 *    [configure] writes the target, and while the glide moves [advanceGlide] overwrites it before
 *    the block processes.
 *  - `time` does NOT glide (a glide bends the echoes' pitch): [DelayLine] crossfades from the old
 *    tap to the new one (its KDoc has the measurement and the rules).
 * The first configure out of [Off] snaps both glides (the ring is empty there, and a song whose
 * delay never changes stays identical to a constant feed), except the wet of a modulated time (see
 * below); [Draining] keeps the feedback gliding towards the last Active value, so a drain is still
 * "Active on silent input"; [Off.enter] forgets both, next to the tail ceiling.
 *
 * **Every switch-off fades the feed with the wet glide** (engine follow-up 42, maintainer Q39,
 * 2026-10-13: "fade, do not cut"; knob expressions step 5b built it for a modulated time only):
 *  - **Going off**, by any path (an owner change to a voice that leaves the delay off, a `katp` time below
 *    [MIN_ACTIVE_DELAY_SECONDS], an unset time, a non-finite one), the off-config takes the wet glide to 0
 *    over its span (50 ms, one block for a modulated wet), and the stage stays [Active] while it falls:
 *    the falling feed goes into the ring and the ceiling watches it, as on any Active block. The block
 *    whose glide LANDS measures the tap window at its end ([Active.process]) and enters [Draining] or
 *    [Off]. So the fade completes on its own: it needs no further off-config, and an orbit whose owner
 *    stopped offering (no configure at all), or the leaving chain of a swap, drains like any other. Only a
 *    wet already at rest at 0 has nothing to fade and goes on at once.
 *  - **The cost, accepted with the decision:** on an owner change the NEW owner's mix feeds the OLD
 *    owner's ring for the fade's span (50 ms, one block for a modulated wet) at a falling level. A
 *    bleed across owners, chosen over the edge a cut wrote into the ring
 *    (`docs/tasks-archive/2026-10/20261016-delay-reverb-owner-change-cut.md`).
 *  - **Coming back** out of [Draining] the wet glides up from 0, where the fade left it; a return while the
 *    fade still runs turns the glide around where it stands. Out of [Off] it snaps, except on a modulated
 *    time ([timeModulated]), whose wet glides up from 0 ([KnobGlide.startFrom]) there too, the first
 *    activation after a build or a reset included (the ring is empty, so the first echoes fade in rather
 *    than arrive; an LFO through the gate otherwise restarts the feed with a step twice per cycle).
 *
 * The effect owns an active/draining/off lifecycle so that turning the delay off never freezes a
 * live tail inside the ring (block-framing ledger D3: the ring's write clock used to stop dead the
 * moment a no-delay voice took over the orbit, and the stale tail resurrected later, detached
 * from time). The transition table on [State] is AUTHORITATIVE for the edges: which event moves
 * which state where is settled there and nowhere else. The bullets below give the reasoning, and
 * where one of them names an edge it is quoting that table, not competing with it.
 *
 * - **Active** — the owner wants the delay ([configure] with a FINITE time >= [MIN_ACTIVE_DELAY_SECONDS]):
 *   process normally.
 * - **Draining** — the owner turned it off while the ring still holds a tail: keep processing with
 *   SILENT input under the retained last-active parameters, so the already-scheduled echoes
 *   complete on their own timeline (the mix is no longer fed in: the owner said off). A
 *   sample-counted closed-form countdown ([DelayLine.drainSamplesUntilSilent]) says when the tail
 *   is provably inaudible. `|feedback| >= 1.0` self-oscillates and deliberately never auto-drains
 *   (raw engine — the drone IS the authored sound; the escape is a new owner with a tame delay.
 *   A CHARGED self-osc ring keeps the orbit alive indefinitely by design while the playback
 *   runs. Once it is STOPPED, the engine holds its tails for `PlaybackEngine.MAX_TAIL_HOLD_SECONDS`
 *   after its notes ended and then releases its whole output, this ring included, exponentially
 *   (`TailRelease`), never with a hard cut (phase 3 step 12 decision (j), maintainer 2026-09-28;
 *   before it such an orbit kept a stopped playback's engine for ever).
 * - **Off** — countdown done: one [DelayLine.reset] (the ring is now literally zero, including the
 *   pre-drain regions a future LONGER delay time could otherwise tap into), then a true
 *   short-circuit until an owner re-enables. That zero-ring guarantee is for THIS path only: a new
 *   delay-carrying owner arriving MID-drain goes straight to Active with the ring kept: the tail
 *   deliberately continues under the new tap, which [DelayLine] crossfades to like any live
 *   delay-time change (a longer tap can always reach older ring content).
 *
 * "Off" is a threshold rather than zero because a zero time would be coerced up to the DSP minimum
 * and ring as a metallic comb instead of being silent.
 */
class KatalystDelayEffect(
    /** Where the ring comes from. Rented on first activation, never before (resource warehouse, 2b). */
    private val rings: SizedBuffers,
    private val sampleRate: Int,
    blockFrames: Int,
    /** True when the wet's knob holds a modulator: [wetGlide] spans one block (knob expressions step 5b, D2). */
    wetModulated: Boolean = false,
    /** True when the feedback's knob holds a modulator: [feedbackGlide] spans one block (step 5b, D2). */
    feedbackModulated: Boolean = false,
    /**
     * True when the time's knob holds a modulator: a return out of [Off] then glides the wet up from 0 instead of
     * snapping it (see the class KDoc). Every switch-off fades, modulated or not.
     */
    private val timeModulated: Boolean = false,
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
        val frames = framesNeeded(time)

        if (!(frames < Int.MAX_VALUE)) { // also catches NaN
            return Int.MAX_VALUE
        }

        return frames.toInt()
    }

    /**
     * [framesFor] before the conversion to `Int`: `ceil(time · sampleRate)` plus the margin, as a double. Exact for
     * every frame count below `Int.MAX_VALUE` (whole numbers far below 2^53), so a compare with it decides what a
     * compare with [framesFor] decides. Inline, so its double never crosses a call (see [ensureRing]).
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun framesNeeded(time: Double): Double = ceil(time * sampleRate) + RING_MARGIN_FRAMES

    /**
     * Ensures a ring that holds [time] is installed, renting or growing as needed. Returns the
     * line to use, or `null` if there is none and the warehouse refused one.
     *
     * Growing rents the next sufficient class, **migrates the old ring's history into it** (2c —
     * `DelayLine.adoptHistory`, so a delay that is ringing at that moment keeps ringing across the
     * seam), then gives the old ring back. Never shrinks: a shorter time keeps the ring it has.
     */
    private fun ensureRing(time: Double): DelayLine? {
        val current = delayLine

        // The steady path, run on every block an owner is committed: compared in doubles ([framesNeeded]), so the
        // `Int` conversion of [framesFor] runs only when a ring is rented or grown. On purpose (engine follow-up 1,
        // 2026-10-11): on Kotlin/JS `toInt()` is a stdlib call, V8 left it out of this stage's budget, and the frame
        // count it was handed (a `ceil`, which V8 cannot rule out as -0) crossed as a heap number every block
        // (`audio/ref/performance.md`). The same decision as `capacityFrames >= framesFor(time)`: a frame count past
        // `Int.MAX_VALUE` or a NaN fails both.
        if (current != null && current.capacityFrames >= framesNeeded(time)) {
            return current
        }

        val needed = framesFor(time)
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

    /** All-zero input for the draining phase: the owner said off, so the mix is no longer fed in. */
    private val silentInput = StereoBuffer(blockFrames)

    /** What the line is fed while Active: the orbit mix at this stage's position, times [wetGlide]. */
    private val feed = StereoBuffer(blockFrames)

    /**
     * The owner's `wet`, a LEVEL knob (see the class KDoc). Taken to 0 before every drain; on a modulated time started
     * from 0 on a return out of Off; forgotten in [Off.enter].
     */
    private val wetGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames, modulated = wetModulated)

    /** The owner's `feedback`, a COEFFICIENT knob (see the class KDoc). Forgotten in [Off.enter]. */
    private val feedbackGlide =
        KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames, modulated = feedbackModulated)

    /** Test seam: the wet in force after the last processed block (the glide's value). */
    internal val wetInForce: Double get() = wetGlide.value

    /**
     * The Active-state tail question answered from a ceiling on the ring's content (see
     * [TailCeiling]), maintained every block from the feed; it replaces the O(ring) scan
     * `DelayLine.hasTail` used to run from `Cylinder.tryDeactivate`.
     *
     * It lives on the effect rather than on [Active] because it OUTLIVES that state: nothing
     * clears it on the way into [Draining], so a delay that returns before the drain ends resumes
     * with the ceiling it had. [TailCeiling.observe] runs only in [Active.process], so across a
     * drain the ceiling is FROZEN while the ring moves underneath it.
     *
     * **This paragraph is the one home of what the frozen ceiling is worth.** It is a bound while
     * Draining and at the moment of return wherever the ring cannot grow, which is every
     * `|feedback| <= 1`, once the drain's own feedback is credited to it ([Draining.resume],
     * [TailCeiling.resume]): there the stale number says "the ring holds no more than this", and
     * that is the safe direction, because this number decides whether the orbit may be torn down.
     * What review found, and where each stands since Katalyst 5c-5:
     * - a tap LENGTHENED, live or on the way back, reaches cells the ceiling no longer covers.
     *   This is the exception [TailCeiling] files itself: at a low feedback those cells never
     *   decayed, so the cut, if an orbit deactivates in that instant, can be the full level of
     *   whatever is still in the ring's span. STILL OPEN.
     * - at `|feedback| > 1` the ring GROWS under the frozen ceiling (to the cap, from a charge the
     *   ceiling froze at a fraction of it), and a new owner returning with a tame feedback used to
     *   resume that stale value: ring and ceiling then decayed together and the ceiling crossed
     *   `SILENCE_FLOOR` with the ring still at -39 to -87 dBFS (0.4 s, charges 0.1 to
     *   0.0002). CLOSED: that return RE-MEASURES ([Draining.resume]); after it, nothing above
     *   -110 dBFS is left when the orbit resets.
     * - a feedback REDUCED to (near) zero, after a drain or LIVE on an owner handover or a glide:
     *   [TailCeiling.observe] recomputed the running window with the feedback in force NOW, so the
     *   ceiling vanished at the next window close while the ring still held the repeat written
     *   under the old feedback, on the orbit path (after its ten silent blocks) and on the
     *   chain-swap ring-out (which retires the leaving chain on the first silent answer). Worst
     *   measured: -9 dBFS dropped at 0.7 to 0.0. CLOSED: the ceiling keeps the largest feedback its
     *   window saw (see [TailCeiling]); after it, nothing above -110 dBFS is dropped.
     * Both repairs only ever RAISE the ceiling, so an orbit can reset later than before, never
     * earlier; for a steady feedback nothing moved. The reverb shares none of the drain half
     * (fixed comb lengths, comb feedback structurally below 1). The feed's fade at a switch-off
     * adds nothing here: it runs in [Active], where the ceiling observes every block.
     *
     * Entering [Off] is the one place the ceiling is forgotten, because there the ring is empty.
     * That line is load-bearing: without it the next life reads the previous life's ceiling and
     * the orbit is held open long past its due. Guarded by "a life that ended in Off starts the
     * next one with an empty ceiling" in `KatalystDelayEffectSpec`.
     */
    private val activeTail = TailCeiling()

    /**
     * The lifecycle as a state machine (`audio/ref/effect-state-machines.md`), one class per
     * state. One instance of each is created with the effect and [state] points at the current
     * one, so a transition is a pointer swap and nothing allocates on the audio thread. `enter`
     * is the only way into a state, and it is what resets that state's own data.
     *
     * | state \ event | `configure`, delay ON | `configure`, delay OFF | `process`, the fade lands | `process`, countdown ends | `reset` | `retire` / `release` |
     * |---|---|---|---|---|---|---|
     * | **Off** | **Active** (a ring is rented if needed; on a modulated time the wet glides up from 0). Stays Off in ONE case: there is no ring at all AND the warehouse refuses one. A refused GROW is not that case, it keeps the ring it has and activates on it, with the time clamped to what that ring holds | Off | (never) | (never) | Off | Off |
     * | **Active** | Active (the knobs are rewritten, an unchanged config writes nothing, see [applied]; mid-fade the wet glide turns around) | Active while the wet glide falls to 0 (the fade); with the wet already at rest at 0, at once what the next column says | **Draining**, or **Off** when the tap window is already silent | (never) | Off | Off |
     * | **Draining** | **Active** (the ring and its ceiling carry on under the new tap; after a self-oscillating drain the ceiling is re-measured first; the wet glides up from 0) | Draining (the countdown keeps running) | (never) | **Off** | Off | Off |
     *
     * The ON arm of [configure] is the same from every state but two lines, so it stays on the
     * effect and only the OFF arm dispatches ([deactivate]); the two lines are reference compares,
     * one that starts a modulated time's wet from 0 on any return to Active (see the class KDoc),
     * and one that hands a return from [Draining] its ceiling check ([Draining.resume], Katalyst 5c-5).
     * Per block that is one virtual call for the block ([process]) and, on the OFF arm only, one
     * more for the owner's config: a running delay takes the ON arm and dispatches nothing through
     * [state]. That is the price of the `when`s they replace.
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

        /** This state's answer to [KatalystDelayEffect.sustainsItself]. */
        abstract fun sustainsItself(): Boolean

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
         * The four callers and how each of them satisfies it: [Active.leave] zeroes the ring
         * when the tap window it measured is already silent (no countdown runs on that arm);
         * [Draining.process] zeroes it when the countdown runs out; [reset] zeroes it and puts the
         * DSP params back to factory; [release] hands the ring to the shelf DIRTY and drops it, so
         * there is nothing left to empty. All that is left here is forgetting the RECORDS of the
         * finished life: the ceiling and the two glides.
         */
        fun enter() {
            activeTail.reset()
            wetGlide.reset()
            feedbackGlide.reset()
            // The memo describes an Active life; this one has ended (see [applied]).
            appliedValid = false
            state = this
        }

        override fun process(ctx: KatalystContext) {}

        override fun hasTail(): Boolean = false

        override fun sustainsItself(): Boolean = false

        /** Already off: an owner that says off again says nothing. */
        override fun deactivate(line: DelayLine) {}
    }

    /**
     * The owner wants the delay, or its feed is fading out after an off-config ([fading]). The knobs
     * live on the [DelayLine], where the DSP reads them, and the tail ceiling outlives this state (see
     * [activeTail]).
     */
    private inner class Active : State() {
        /**
         * True while an off-config's fade runs: the wet glide falls to 0, and the block it lands in
         * goes on into the drain ([process]). Dies with the state; [enter], the ON arm's door, clears it,
         * which is what turns a fade around on a return.
         */
        private var fading = false

        fun enter() {
            fading = false
            state = this
        }

        override fun process(ctx: KatalystContext) {
            // A ring is implied here; the guard is so no path can throw in render.
            val line = delayLine ?: return
            // min(): [feed] is sized from the constructor blockFrames, like the silent input.
            val frames = min(ctx.blockFrames, feed.left.size)

            // First, so the ceiling below reads the feedback this block runs at.
            advanceGlide(line)
            wetGlide.advanceScaled(into = feed, source = ctx.mixBuffer, frames = frames)

            // The tail question is answered from the INPUT: the block's feed peak feeds a
            // ceiling on the ring's content that decays by the feedback once per delay period
            // (TailCeiling). O(block) here, O(1) to ask. Nothing is ever O(ring). One call that takes no double
            // (engine follow-up 1, `DelayLine.observeTail` says why).
            line.observeTail(ceiling = activeTail, input = feed, frames = frames)
            line.process(input = feed, output = ctx.mixBuffer, length = frames)

            // The fade lands in this block: the drain starts from what the ring holds now, with no off-config
            // needed to move it on (an orbit without an owner configures nothing; see the class KDoc).
            if (fading && !wetGlide.isGliding) {
                leave(line)
            }
        }

        /** A ceiling, not a scan: [process] maintains it from the feed. */
        override fun hasTail(): Boolean = delayLine != null && activeTail.hasTail

        /** A charged ring recirculating at |feedback| >= 1 never decays. */
        override fun sustainsItself(): Boolean {
            val line = delayLine ?: return false

            return activeTail.hasTail && abs(line.feedback) >= 1.0
        }

        override fun deactivate(line: DelayLine) {
            // Every switch-off: the feed falls to 0 over the wet glide's span while the stage stays Active, and the
            // block the glide lands in goes on into [leave] ([process]; see the class KDoc). A wet at rest at 0 has
            // nothing to fade, and neither has a glide that snaps (no block has consumed its first value yet).
            if (wetGlide.isGliding || wetGlide.value != 0.0) {
                wetGlide.retarget(0.0)

                if (wetGlide.isGliding) {
                    fading = true

                    return
                }
            }

            leave(line)
        }

        /** The way out of Active once the feed is at 0: [Draining], or [Off] when the tap window is already silent. */
        private fun leave(line: DelayLine) {
            // One O(delayInt) scan at the transition: the countdown starts from what the TAP can
            // still reach (review round 3 replaced the static worst-case bound; round 4 shrank
            // the scan from the whole ring to the tap window: older content is overwritten
            // before the tap arrives and can never be emitted). An already-silent window
            // (including an EMPTY self-oscillating ring) goes straight to Off.
            //
            // Mid-glide the feedback keeps moving through the drain (see [advanceGlide]), between
            // the one in force and the target, so the larger magnitude of the two bounds every
            // period to come (the reverb's rule, `audio/ref/knob-glide.md` pilot log entry 6).
            // Read from the glide: an Active configure earlier in this block may have written its
            // target into the line already (called from [process], the glide has already moved this block).
            val drainFeedback = if (feedbackGlide.isGliding) {
                maxOf(abs(feedbackGlide.value), abs(feedbackGlide.target))
            } else {
                line.feedback
            }

            val remaining = line.drainSamplesUntilSilent(peak = line.tapWindowPeakAbs(), feedback = drainFeedback)

            if (remaining <= 0.0) {
                line.reset()
                off.enter()
            } else {
                draining.enter(remaining = remaining, feedback = drainFeedback)
            }
        }
    }

    /**
     * The owner turned the delay off while the ring still held a tail: keep processing with SILENT
     * input under the retained last-active parameters, so the already-scheduled echoes complete on
     * their own timeline. Its data is the countdown and the drain's feedback bound (for the
     * return, see [resume]), and [enter] is the only INITIALISER of both: [process] advances the
     * countdown every block, nothing outside this class touches either. That is why a delay that returns mid-drain and leaves again gets a fresh countdown and
     * never the remains of the previous one, and it is what made three defensive writes of the old
     * flag version dead code (the two `drainRemaining = 0.0` in [reset] and [release], and the
     * field write on the arm that went straight to Off).
     */
    private inner class Draining : State() {
        /** Remaining drain samples; [Double.POSITIVE_INFINITY] while `|feedback| >= 1` (self-oscillation). */
        private var remaining = 0.0

        /**
         * The feedback bound the countdown was computed with (see [Active.leave]): mid-glide
         * the larger magnitude of the value in force and the target, otherwise the line's own
         * feedback, SIGNED. Its magnitude bounds every feedback this drain runs at; [resume]
         * hands it to [TailCeiling.resume], which takes the magnitude.
         */
        private var feedback = 0.0

        fun enter(remaining: Double, feedback: Double) {
            this.remaining = remaining
            this.feedback = feedback
            state = this
        }

        /**
         * The owner is back before the countdown ended. [TailCeiling.observe] did not run while
         * the ring drained, so the ceiling is the one frozen at the drain's start. Up to
         * |feedback| 1 it is still a bound once the drain's feedback is credited to it
         * ([TailCeiling.resume]); above 1 the ring grew under it, and it re-measures what the tap
         * can still reach: one O(delay) scan, on this edge and only after a self-oscillating
         * drain (Katalyst 5c-5). Raise-only, so a return can move a reset later, never earlier.
         */
        fun resume(line: DelayLine) {
            if (activeTail.resume(feedback)) {
                activeTail.remeasure(line.tapWindowPeakAbs())
            }
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

            advanceGlide(line)
            line.process(input = silentInput, output = ctx.mixBuffer, length = frames)

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

        /** The countdown is infinite exactly when the drain's feedback is >= 1 in magnitude. */
        override fun sustainsItself(): Boolean = remaining == Double.POSITIVE_INFINITY

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
     * "a transition allocates nothing" rule of `audio/ref/effect-state-machines.md` is guarded
     * without an allocation profiler. Production never reads it.
     */
    internal val currentState: Any get() = state

    /** The holder the four-number [configure] door fills, this stage's own instance ([DelayConfig] says why). */
    private val door = DelayConfig()

    /**
     * The four knobs the last ON-arm [configure] applied, in [DelayConfig]'s order (time, feedback, cap, wet), valid
     * while [appliedValid]: an unchanged config returns before it writes anything (engine follow-up 32, 2026-10-16).
     *
     * **Why:** the writer re-applies its config on every block (`KatalystChain.applyParams`), and on V8, in a mixed
     * profile where most orbits take the off arm, this method can be compiled with its calls left out (`ensureRing`
     * and the line's `time`, `feedback` and `cap` setters), so their doubles crossed as heap numbers: up to four per
     * block, measured in pinned mixed processes (`audio/ref/performance.md`). The phaser's house pattern
     * (`KatalystPhaserEffect.applied`), with the two writes item 1 found in the way handled:
     *  - **The feedback glide.** While it moves, [advanceGlide] overwrites the line's feedback before each block and
     *    this method writes the target back after it, so the line holds the target between the two (a reader there,
     *    [Active.sustainsItself], sees it). So a MOVING feedback glide never skips; a settled one holds exactly the
     *    target, which is what the skipped write would store.
     *  - **The latched retry.** A refused grow keeps the ring it has and consults the shelf again on every configure
     *    ([ensureRing]); the memo is valid only when the installed ring serves the time, so that retry still runs
     *    every block.
     * Everything else the ON arm does on an unchanged config in [Active] is a no-op: the line's setters store the
     * same numbers, a `retarget` to the target that stands is free, `active.enter` keeps the state, and the two
     * lines for a return from another state do not apply (the memo is valid only in Active).
     *
     * **When it is valid:** set at the end of the ON arm, cleared at the start of every configure that does not skip
     * and in [Off.enter]. So it is valid only in [Active] and never during a fade: every way out of Active is the OFF
     * arm of this method (which clears it before a fade begins, so the landing in [Active.process] finds it cleared) or
     * [Off.enter]. The OFF arm is never skipped; and a return after a fade, whose config may equal the memo, is not
     * skipped either, because the off-config that began the fade cleared it.
     *
     * Compared NaN-aware with -0.0 told apart ([sameKnob]). It stores the RAW knobs, a deliberate departure from the
     * cached-config rule of `/review-loop` ("store substituted values"): a memo that only skips an unchanged config must
     * match the input exactly, raw is the simplest exact key, and [sameKnob] settles NaN. Every write [configure] makes
     * is a function of these raw knobs, so equal raw knobs mean equal writes. An ARRAY, not double fields, so the
     * stores cannot box (engine follow-up 7). No test can pin the box; this KDoc is the guard.
     */
    private val applied = DoubleArray(4)

    /** True while [applied] describes the config in force; see there. */
    private var appliedValid: Boolean = false

    /**
     * The door for a direct caller (the specs): fills this stage's own [DelayConfig] and configures from it, so the
     * rules below are written once, in the holder overload.
     */
    fun configure(time: Double, feedback: Double, cap: Double, wet: Double) {
        val c = door

        c.time = time
        c.feedback = feedback
        c.cap = cap
        c.wet = wet
        configure(c)
    }

    /**
     * Applies the orbit owner's delay settings. Called by `KatalystChain.applyParams` on every
     * block an owner is committed. An off-config (a time that is non-finite or below [MIN_ACTIVE_DELAY_SECONDS]) does
     * NOT reach the [delayLine]: the retained last-active parameters are what the drain runs on.
     *
     * [DelayConfig.wet] is how much of the orbit mix feeds the line. Raw: no clamp, a negative wet feeds the
     * line inverted, above 1 hotter than the mix. The stage reads [config] and keeps no reference to it.
     */
    fun configure(config: DelayConfig) {
        val time = config.time
        val feedback = config.feedback
        val cap = config.cap
        val wet = config.wet
        val last = applied

        // An unchanged config in Active writes nothing: see [applied]. Never while the feedback glide moves.
        if (appliedValid && !feedbackGlide.isGliding && sameKnob(last[0], time) && sameKnob(last[1], feedback) &&
            sameKnob(last[2], cap) && sameKnob(last[3], wet)
        ) {
            return
        }

        appliedValid = false

        // Non-finite reads as OFF (time) or as the shared default (feedback, cap, wet), never as the
        // previous owner's value: DelayLine's setters DROP non-finite writes, so passing one through
        // would leave whatever the last owner set. The slot writer hands a non-finite wet only with an
        // off time (`stageAskedFor` is true only with a finite wet, a modulated one included); this guard
        // is the door's own contract for a direct caller (the reverb door reads a non-finite size as off
        // the same way).
        if (time.isFinite() && time >= MIN_ACTIVE_DELAY_SECONDS) {
            // No ring and none to be had: the orbit stays dry rather than the worklet dying.
            val line = ensureRing(time) ?: return

            // No tail bookkeeping here: the ceiling reads the period and feedback in force on
            // every block, so a change (or a grow, which keeps the content) is simply followed.
            // The old path, literally: while the feedback glides, [advanceGlide] overwrites it
            // before the block processes (see the class KDoc).
            val guardedFeedback = if (feedback.isFinite()) feedback else DELAY_FEEDBACK

            line.time = time
            line.feedback = guardedFeedback
            line.cap = if (cap.isFinite()) cap else DELAY_CAP
            feedbackGlide.retarget(guardedFeedback)

            // A modulated time returns to Active with its feed at 0 out of Off too, so the wet glides up from there
            // (out of Draining the fade left it at 0 already); a return mid-fade (Active) turns the falling glide
            // around where it stands (class KDoc).
            if (timeModulated && state !== active) {
                wetGlide.startFrom(0.0)
            }

            wetGlide.retarget(if (wet.isFinite()) wet else DELAY_WET)

            // A return mid-drain: the frozen ceiling may need a measurement (after the knobs, so
            // the scan reaches the longer of the old and the new tap).
            if (state === draining) {
                draining.resume(line)
            }

            active.enter()

            last[0] = time
            last[1] = feedback
            last[2] = cap
            last[3] = wet
            // Not while a refused grow leaves the ring short of the time: the latched retry runs every block.
            appliedValid = line.capacityFrames >= framesNeeded(time)
            return
        }

        // Never activated: nothing to drain.
        val line = this.delayLine ?: return

        // Only [Active] has work to do here (the fade, see the class KDoc); the other two say why they do not.
        state.deactivate(line)
    }

    /**
     * True while the ring can still contribute audio, the state-aware replacement for always
     * scanning: Off is empty by construction ([DelayLine.reset] on entry); Active asks the
     * CEILING, not the ring (see [activeTail], which is also where what that ceiling is worth is
     * written down).
     * Draining is tailed BY CONSTRUCTION, not by scan (settled in review round 4): it is entered
     * only when the tap window held content above the threshold (an already-silent ring goes
     * straight to Off from [Active.leave], which is what protects the empty-self-osc
     * engine-leak case round 1 found), and it CONSERVATIVELY reports a tail for the whole
     * countdown, which can
     * outlive the ring's last audible sample by up to one delay period (review round 5: at high
     * fb the countdown can even outlast a full ring revolution, so a whole-ring scan COULD answer
     * false near the end; this arm never cuts audio, it only holds the orbit a bounded moment
     * longer). (A CHARGED self-osc ring pins its orbit by design while the playback runs; a
     * stopped one is released by its engine, see the class KDoc.)
     */
    override fun hasTail(): Boolean = state.hasTail()

    /**
     * True while this delay holds a tail that can NEVER end on its own: a charged ring at
     * |feedback| >= 1, Active or Draining (its countdown is then infinite). What a stopped engine
     * releases after its hold (phase 3 step 12 decision (j), maintainer 2026-09-28); every other
     * tail rings out. The one stage that can: see `KatalystChain.sustainsItself`.
     */
    fun sustainsItself(): Boolean = state.sustainsItself()

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
     * Moves the feedback glide by one block and writes it into the line while it MOVES, before the
     * line processes the block, over whatever [configure] wrote. A settled feedback is not written:
     * the line already holds the configured value, which is what keeps a steady delay identical.
     */
    private fun advanceGlide(line: DelayLine) {
        if (feedbackGlide.isGliding) {
            line.feedback = feedbackGlide.advance()
        } else {
            feedbackGlide.advance()
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

/**
 * What a delay stage is configured with on every block an owner is committed ([KatalystDelayEffect.configure]): the
 * [time] in seconds (non-finite or below `MIN_ACTIVE_DELAY_SECONDS` is OFF), the [feedback], the [cap] and the [wet].
 *
 * **Mutable and reused, on purpose** (V8 allocation pass, 2026-10-08). The writer configures the stage on every block,
 * and on V8 a non-integral double handed to a function that is not inlined travels as a heap number, one allocation
 * per value per call (the rule in `audio/ref/performance.md`; `ResonatorConfig` is the precedent). So the numbers stay
 * in the fields of one holder, and the call hands over the reference. Each holder owns its instance: the slot writer
 * one (filled when it is built and at every resolve), the stage one for its four-number door; the stage reads the
 * values and keeps no reference.
 */
class DelayConfig {
    var time: Double = Double.NaN
    var feedback: Double = Double.NaN
    var cap: Double = Double.NaN
    var wet: Double = Double.NaN
}
