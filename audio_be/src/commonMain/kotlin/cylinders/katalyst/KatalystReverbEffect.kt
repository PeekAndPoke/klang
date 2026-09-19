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
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import kotlin.math.min

/**
 * The orbit reverb, an insert-style stage (Katalyst step 5b-2, 2026-09-19): it is fed from the orbit
 * mix AT ITS POSITION in the chain, scaled by the orbit owner's ONE `wet`, and adds its room into
 * that same mix, so the dry signal stays. In the classic order it sits after body, vowel and the
 * delay, so the room hears all three, the delay's echoes included. The master's
 * `MasterStageDsl.Reverb` model, on the orbit bus.
 *
 * **The `wet` glides** per sample, a LEVEL knob ([KnobGlide.advanceScaled]) into the feed, with the
 * size glide's lifecycle below: the first configure out of Off snaps, [Off.enter] forgets it. Not
 * advanced while Draining, where nothing is fed.
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
 *   own timeline (the mix is no longer fed in: the owner said off). A closed-form sample-counted
 *   countdown ([Reverb.drainSamplesUntilSilent]) says when the combs are provably inaudible,
 *   and [hasTail] answers true for the whole countdown BY CONSTRUCTION (round 2 measured that a
 *   live scan would beat the countdown by only ~one revolution for real content — see there).
 *   Unlike the delay there is NO self-oscillating regime: comb feedback is structurally < 1
 *   ([Reverb.normalizeSize] bounds size in VoiceFactory, [configure] bounds it again),
 *   so every finite drain terminates — and a NON-finite countdown (an Inf or NaN comb cell from
 *   a hot feed: neither ever decays) resets immediately instead: the heal the old gate's
 *   takeover path provided, and the only exit such an orbit would otherwise ever have.
 * - **Off** — countdown done: one [Reverb.reset] (combs, allpasses and LPF stores literally
 *   zero), then a true short-circuit until an owner re-enables. A reverb-carrying owner
 *   arriving MID-drain goes straight to Active with the network kept — the tail deliberately
 *   continues under the new room (the network keeps what it holds, same as the delay).
 *
 * The transition table on [State] is AUTHORITATIVE for the edges: which event moves which state
 * where is settled there and nowhere else. The bullets above give the reasoning.
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
 *    and the way into Off is where a glide is forgotten, see [sizeGlide]). A chain arriving
 *    through a swap carries its own stage, whose first configure snaps like any other.
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

    /** All-zero input for the draining phase: the owner said off, so the mix is no longer fed in. */
    private val silentInput = StereoBuffer(blockFrames)

    /** What the network is fed while Active: the orbit mix at this stage's position, times [wetGlide]. */
    private val feed = StereoBuffer(blockFrames)

    /** The owner's `wet`, a LEVEL knob (see the class KDoc). Forgotten in [Off.enter]. */
    private val wetGlide = KnobGlide(sampleRate = units.sampleRate, blockFrames = blockFrames)

    /**
     * The Active-state tail question from a ceiling on the combs' content (see [TailCeiling]); replaces the comb scan.
     *
     * It lives on the effect rather than on [Active] because it OUTLIVES that state: nothing clears
     * it on the way into [Draining], so a reverb that returns before the drain ends resumes with the
     * ceiling it had, frozen across the drain while the network decays underneath it. [Off.enter]
     * is the one place it is forgotten, because there the network is empty. Guarded by "a reverb that
     * returns mid-drain keeps the network AND the tail ceiling" and "a life that ended in Off starts
     * the next one with an empty ceiling" in `KatalystReverbEffectSpec`.
     */
    private val activeTail = TailCeiling()

    /**
     * The room's size on the normalized 0..1 axis [Reverb.size] holds (see the class KDoc).
     *
     * It outlives [Active] like [activeTail]: [Draining] keeps advancing it, and a return mid-drain
     * glides on from where the room stands. [Off.enter] forgets it, the same line that forgets the
     * ceiling, so the first configure out of Off snaps. Until the state machine that reset sat on
     * the ON arm of [configure], guarded by "the state is Off"; nothing reads or advances the glide
     * between entering Off and leaving it, so forgetting it on the way in is the same behaviour, and
     * it leaves that arm the same from every state. Guarded by "reset and retire mid-glide forget
     * the glide" in `KatalystReverbGlideSpec`.
     */
    private val sizeGlide = KnobGlide(sampleRate = units.sampleRate, blockFrames = blockFrames)

    /**
     * The lifecycle as a state machine (`docs/plans/effect-state-machines.md`, the shape of
     * [KatalystDelayEffect]), one class per state. One instance of each is created with the effect
     * and [state] points at the current one, so a transition is a pointer swap and nothing
     * allocates on the audio thread. `enter` is the only way into a state, and it is what
     * initialises that state's own data.
     *
     * | state \ event | `configure`, reverb ON | `configure`, reverb OFF | `process`, countdown ends | `reset` | `retire` / `release` |
     * |---|---|---|---|---|---|
     * | **Off** | **Active** (a unit is rented if there is none; the size snaps). Stays Off in ONE case: there is no unit AND the shelf refuses one | Off | (never) | Off | Off |
     * | **Active** | Active (the knobs are rewritten, the size glide retargets) | **Draining**, or **Off** when the network is already silent OR poisoned (a non-finite countdown) | (never) | Off | Off |
     * | **Draining** | **Active** (the network, its ceiling and the size glide carry on; a new size glides from where the room stands) | Draining (the countdown keeps running) | **Off** | Off | Off |
     *
     * The ON arm of [configure] is the same from every state, so it stays on the effect and only
     * the OFF arm dispatches ([deactivate]). `sealed` buys no exhaustive `when` here, because no
     * `when` over the states is left: it documents that the set is closed.
     */
    private sealed class State {
        /**
         * One block. Everything the block needs is read into a local first (the unit, the frame
         * count, the countdown); the per-sample loop itself belongs to [Reverb].
         */
        abstract fun process(ctx: KatalystContext)

        /** This state's answer to [KatalystReverbEffect.hasTail], where the reasoning lives. */
        abstract fun hasTail(): Boolean

        /**
         * The owner's off-config arrived. [unit] is the effect's network, which [configure] has
         * already proven non-null. A state that has nothing to do with it ignores it.
         */
        abstract fun deactivate(unit: Reverb)
    }

    /** Nothing in the network and nothing to do: a true short-circuit until an owner re-enables. */
    private inner class Off : State() {
        /**
         * PRECONDITION: the caller has already emptied the unit, because [hasTail] here answers a
         * hardcoded `false` and cannot check. Entering Off with a charged network is a silent cut.
         *
         * The four callers and how each satisfies it: [Active.deactivate] resets the unit when the
         * countdown it measured is already over OR not finite (a poisoned network, which the reset
         * heals); [Draining.process] resets it when the countdown runs out; [reset] resets it and
         * puts the DSP params back to factory; [release] hands the unit to the shelf DIRTY and drops
         * it, so there is nothing left to empty. What is left here is forgetting the RECORDS of
         * the finished life: the tail ceiling and the two glides (see [activeTail], [sizeGlide],
         * [wetGlide]).
         */
        fun enter() {
            activeTail.reset()
            sizeGlide.reset()
            wetGlide.reset()
            state = this
        }

        override fun process(ctx: KatalystContext) {}

        override fun hasTail(): Boolean = false

        /** Already off: an owner that says off again says nothing. */
        override fun deactivate(unit: Reverb) {}
    }

    /**
     * The owner wants reverb. It carries no data of its own: the knobs live on the [Reverb], where
     * the DSP reads them, and the tail ceiling and the size glide outlive this state.
     */
    private inner class Active : State() {
        fun enter() {
            state = this
        }

        override fun process(ctx: KatalystContext) {
            // A unit is implied here; the guard is so no path can throw in render.
            val unit = reverb ?: return

            // First, so the tail ceiling below reads the feedback this block runs at.
            advanceGlide(unit)

            // min(): [feed] is sized from the constructor blockFrames, like the silent input.
            val frames = min(ctx.blockFrames, feed.left.size)

            wetGlide.advanceScaled(into = feed, source = ctx.mixBuffer, frames = frames)

            // Same shape as the delay's: the feed peak feeds a ceiling on the combs' content,
            // decaying by the comb feedback once per longest-comb revolution (TailCeiling).
            activeTail.observe(
                inputPeak = TailCeiling.peakOf(feed, frames),
                frames = frames,
                windowSamples = unit.tailWindowSamples,
                feedback = unit.tailFeedback,
                lapsPerWindow = unit.tailLapsPerWindow,
            )
            unit.process(feed, ctx.mixBuffer, frames)
        }

        /** A ceiling, not a scan: [process] maintains it from the feed. */
        override fun hasTail(): Boolean = reverb != null && activeTail.hasTail

        override fun deactivate(unit: Reverb) {
            // One comb scan at the transition: the countdown starts from what the combs actually
            // hold, so a barely-charged network drains in proportion to its content. An
            // already-silent network goes straight to Off, and so does a BROKEN one: a
            // non-finite comb cell makes the countdown non-finite (combPeakAbs reports any
            // poisoned cell as +Inf), and the reset is the heal (review rounds 1-2; the old
            // gate's takeover path provided exactly this exit). That second half is the reverb's
            // own; the delay's test has no such arm (docs/plans/effect-state-machines.md, "copy the
            // shape, not the condition").
            //
            // Mid-glide the size keeps moving through the drain (see [advanceGlide]), between the
            // one in force and the target, so the larger of the two bounds every revolution to come
            // (an over-hold for a falling glide, see the class KDoc). Read from the glide: an Active
            // configure earlier in this block may have written its target into the unit already.
            val drainSize = if (sizeGlide.isGliding) maxOf(sizeGlide.value, sizeGlide.target) else unit.size

            val remaining = unit.drainSamplesUntilSilent(peak = unit.combPeakAbs(), size = drainSize)

            if (remaining <= 0.0 || !remaining.isFinite()) {
                unit.reset()
                off.enter()
            } else {
                draining.enter(remaining)
            }
        }
    }

    /**
     * The owner turned the reverb off while the combs still held a tail: keep processing with
     * SILENT input under the retained last-active parameters (and the size glide still moving
     * towards the last Active size), so the tail mixes out on its own timeline. The countdown is
     * this state's whole data, and [enter] is its only INITIALISER: [process] advances it every
     * block, nothing outside this class touches it. That made three writes of the old flag version
     * dead code: `drainRemaining = 0.0` in [reset] and [release], and the field write on the arm
     * that went straight to Off.
     */
    private inner class Draining : State() {
        /** Remaining drain samples ([Reverb.drainSamplesUntilSilent], captured at the off-transition). */
        private var remaining = 0.0

        fun enter(remaining: Double) {
            this.remaining = remaining
            state = this
        }

        override fun process(ctx: KatalystContext) {
            // A unit is implied here; the guard is so no path can throw in render.
            val unit = reverb ?: return
            // min(): silentInput is sized from the constructor blockFrames; a mismatched ctx
            // must not read past it. The countdown MUST decrement by the SAME clamped count
            // the DSP processed: a countdown outrunning the network would fire the terminal
            // reset while the tail is still audible (the delay's review-round-2 rationale).
            val frames = min(ctx.blockFrames, silentInput.left.size)

            advanceGlide(unit)
            unit.process(silentInput, ctx.mixBuffer, frames)

            // A plain end test: a non-finite countdown never gets here, [Active.deactivate] heals it.
            val left = remaining - frames
            remaining = left

            if (left <= 0.0) {
                unit.reset()
                off.enter()
            }
        }

        /** Tailed BY CONSTRUCTION for the whole countdown: see [KatalystReverbEffect.hasTail]. */
        override fun hasTail(): Boolean = true

        /**
         * Already draining: the countdown keeps running on the parameters it started with. A
         * second off-config must NOT restart it, or an owner re-applied every block would hold
         * the tail open for ever.
         */
        override fun deactivate(unit: Reverb) {}
    }

    private val off = Off()
    private val active = Active()
    private val draining = Draining()

    /**
     * The current state: one of the three instances above, never a fresh one.
     *
     * This initializer is the one entry into Off that does not run [Off.enter]: a freshly built
     * [TailCeiling] equals a reset one, and a fresh [KnobGlide] equals a reset one in everything a
     * reader can see (not gliding, the next retarget snaps), and there is no
     * unit yet, or the test seam's fresh one, which is empty, so the precondition holds.
     */
    private var state: State = off

    /**
     * Test seam: the current state OBJECT, for `KatalystReverbStateIdentitySpec`, which walks the
     * transition table and asserts that only ever those three instances appear. Production never
     * reads it.
     */
    internal val currentState: Any get() = state

    /**
     * Applies the orbit owner's reverb settings. Called by `KatalystChain.applyParams` on every
     * block the lease is (re)claimed. An off-config does NOT reach the [reverb]: the retained
     * last-active parameters are what the drain runs on.
     *
     * [wet] is how much of the orbit mix feeds the room. Raw: no clamp; a non-finite wet reads as
     * the shared default, like the master's (the slot writer never hands one to a running stage).
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
        wet: Double,
    ) {
        if (size.isFinite() && size >= MIN_ACTIVE_SIZE) {
            // Out of Off the glide was forgotten on the way in, so the room ARRIVES, it does not
            // move (see [sizeGlide]).
            val unit = reverb ?: rentUnit() ?: return

            // The old path, literally: while a glide moves, [advanceGlide] overwrites the size
            // before the block processes (see the class KDoc).
            val boundedSize = size.coerceIn(0.0, 1.0)

            unit.size = boundedSize
            unit.lowpass = lowpass?.takeIf { it.isFinite() }
            sizeGlide.retarget(boundedSize)
            // NaN-guard on a value a direct caller can pass: the shared default, see above.
            wetGlide.retarget(if (wet.isFinite()) wet else REVERB_WET)
            active.enter()
            return
        }

        // Never activated: nothing to drain.
        val unit = reverb ?: return

        // Only [Active] has work to do here; the other two say why they do not.
        state.deactivate(unit)
    }

    /**
     * True while the combs can still contribute audio — the state-aware replacement for the
     * cylinder's old param-gated scan, which reported "no tail" the moment a no-reverb owner
     * zeroed size and so hid a still-charged network from cleanup. Off is empty by
     * construction (every entry empties the network or hands it back, see [Off.enter]); Active asks the network; Draining is tailed
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
    override fun hasTail(): Boolean = state.hasTail()

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
        off.enter()
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
        off.enter()
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

    override fun process(ctx: KatalystContext) = state.process(ctx)

    companion object {
        /**
         * Below this normalized size the reverb is "off" — the comb feedback floor (0.7, see
         * `Reverb.FEEDBACK_OFFSET`) makes even size 0.0 ring for ~0.7 s, so "no reverb" must be a
         * threshold decision (see [configure]). Authored, that is a `reverb(size = ...)` below 0.1.
         */
        const val MIN_ACTIVE_SIZE = 0.01
    }
}
