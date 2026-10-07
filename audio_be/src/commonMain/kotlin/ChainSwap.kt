/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDuckEffect
import kotlin.math.max

/**
 * The swap of a whole effect chain under live audio: the chain leaving service fades out through
 * its INPUT while the arriving chain's output fades in over [Crossfade], and then the leaving chain
 * rings out (drains) on silent input at full weight until it has no tail left. Phase 3 step 12 C1
 * (`docs/tasks-archive/2026-09/20260928-phase3-step12-master-as-katalyst.md` section 6) moved it out of `Cylinder`, where it
 * was five fields, into three states (`docs/plans/effect-state-machines.md`, the delay's shape).
 *
 * **The capped drain** (step 12 decision (i), maintainer 2026-09-28). A drain has a maximum age,
 * [MAX_DRAIN_SECONDS] counted in frames from the fade's end. A chain that still reports a tail
 * then (a delay at feedback >= 1 never stops; any room is inaudible by then, see the constant) is
 * released by a fourth state, [Releasing]: its OUTPUT dies away EXPONENTIALLY, per sample, from
 * exactly 1 ([TailRelease], 60 dB every 3 s), so a long echo still audible at the cap sounds as if
 * it decayed on its own (maintainer's option A, 2026-09-28), and the chain retires once the gain is
 * under the release's floor (-90 dB, 4.5 s). The parked request of the host lands then, and the orbit or the
 * engine can go idle. The longest a request can wait behind a drain is therefore the cap plus the
 * release: 20 s + 4.5 s, about 24.5 s. Without the cap a self-sustaining chain swapped away held
 * the one leaving slot for ever, and every later request waited for ever (decision (g): a request
 * waits for the drain).
 *
 * **What the host keeps.** The chain in service (it is handed to every event as `chain`), the
 * cache, the rule for when a request installs at once instead of fading (the orbit is idle; at the
 * output, the engine has never rendered), and the PARKED request: a key the host holds, latest
 * wins, offered again once [settled]. A parked request is not a state here (decision (e) of the
 * plan, the `KatalystFilterSwap` 5c-11 precedent): [begin] while a fade or a drain runs is REFUSED,
 * and the host asks [settled] first.
 *
 * **The duck handover** is data of [Fading] (it dies with the fade, rule 2 of the plan): the duck
 * of the leaving chain kept in service for the length of the ramp, or the flag that ramps the
 * arriving chain's duck in. The host decides which (`Cylinder.handOverDuck`) and hands it to
 * [begin]; a host without a duck pass passes `null` and `false` and never calls [processDuck].
 *
 * | state \ event | [begin] | [process] | [processDuck] | [ownerClaimed] | [configureLeaving] | [hardCut] |
 * |---|---|---|---|---|---|---|
 * | **Idle** | **Fading** | the chain alone | the chain's duck | nothing | nothing | nothing |
 * | **Fading** | REFUSED, the offered chain retired | ramp not complete: both chains, the ramp; complete: the leaving chain has a tail ? **Draining** : retired, **Idle**, and the entered state's block runs | the duck ramped in, ramped out, or the chain's own | the late duck takeover, only in the ramp's first block | the leaving chain is configured too | leaving chain retired, **Idle** |
 * | **Draining** | REFUSED, the offered chain retired | both chains, the leaving one on silence, added at full weight; no tail left: retired, **Idle**; a tail left and the drain [MAX_DRAIN_SECONDS] old: **Releasing** (from the next block) | the chain's duck | nothing | nothing | leaving chain retired, **Idle** |
 * | **Releasing** | REFUSED, the offered chain retired | both chains, the leaving one on silence, its output added under the exponential release; the gain under the floor (inside this block): retired, **Idle** | the chain's duck | nothing | nothing | leaving chain retired, **Idle** |
 *
 * **The four questions of the plan, answered for the swap:**
 * 1. *What outlives its states:* the [Crossfade] and the [TailRelease], the leaving chain's mix and
 *    context, the duck scratch, [retiredDeniedRents]; and on the host, the chain in service, the
 *    cache and the parked key. Only [Fading.enter] restarts the ramp and only [Releasing.enter]
 *    restarts the release.
 * 2. *The record of a finished life:* the leaving reference (dropped by the edge that leaves
 *    [Fading], [Draining] or [Releasing], and by [hardCut]), the drain's age (initialised by
 *    [Draining.enter] only, so a drain never inherits a previous one's) and the duck handover data
 *    (dropped when the fade ends, in either pass). [retire] carries a chain's denied rents BEFORE
 *    the chain's own retire zeroes them.
 * 3. *The Idle precondition:* the leaving chain reports no tail, or its release gain is under the
 *    release's floor ([Releasing]), or the host guarantees silence ([hardCut], the cylinder's new
 *    life).
 * 4. *References and who drops them:* the leaving chain ([Fading], [Draining], [Releasing]) and the
 *    duck being faded out ([Fading]). The edges and [hardCut] drop them, so [hardCut] DISPATCHES.
 *
 * **Timing identity depends on.** The fade-to-drain edge fires at the START of the block after the
 * ramp's last block, not inside it: the duck pass of the ramp's last block still blends with that
 * block's weights ([Crossfade.blendHeld]). So [Fading.process] tests [Crossfade.isComplete] FIRST and
 * then runs the entered state's block in the same call.
 *
 * Not thread-safe; everything runs on the audio thread. Nothing here allocates after construction.
 */
internal class ChainSwap(sampleRate: Int, private val blockFrames: Int) {

    /** The ramp the two chains are blended over. One per host, created once; it outlives every state. */
    private val fade: Crossfade = Crossfade(sampleRate)

    /** The release at the drain's cap ([Releasing]). One per host, created once; it outlives every state. */
    private val release: TailRelease = TailRelease(sampleRate = sampleRate, blockFrames = blockFrames)

    /**
     * What the leaving chain is fed: the host's mix scaled by the outgoing weight during the fade,
     * silence during the drain. It is ALL the leaving chain hears, its delay and reverb included:
     * they take their feed from the mix at their position, so their wet fades with the dry through
     * this one ramp, and the ring-out keeps the chain ACTIVE on this cleared buffer instead of
     * switching its stages to their own silent input (which would cut the room's feed of echoes in
     * one sample, measured in Katalyst step 5b-2).
     */
    private val leavingMix = StereoBuffer(blockFrames)

    /** The context the leaving chain runs in, so the live mix stays the arriving chain's. */
    private val leavingContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = leavingMix,
    )

    /**
     * The host's mix as it was BEFORE a duck entering or leaving service ducked it, so that duck's
     * effect can be crossfaded per sample ([processDuck]). Only written while a fade carries a duck.
     */
    private val duckMix = StereoBuffer(blockFrames)

    /**
     * Rents the warehouse refused chains that have left service through [retire], summed for the
     * host's life. A stage zeroes its own count when it retires (the count is per unit life), so
     * without this the diagnostics number would drop back to zero on every chain edit. [hardCut]
     * starts it over.
     */
    private var retiredDeniedRents: Int = 0

    /** One class per state; the table on [ChainSwap] is authoritative for the edges. */
    private sealed class State {
        /** The chain leaving service, fading, draining or releasing; null in Idle. */
        abstract val leaving: KatalystChain?

        /** The leaving chain's duck while its effect is ramped out; null outside a fade. */
        abstract val duckingOut: KatalystDuckEffect?

        abstract fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean)

        abstract fun process(chain: KatalystChain, ctx: KatalystContext)

        abstract fun processDuck(chain: KatalystChain, ctx: KatalystContext)

        abstract fun ownerClaimed(chain: KatalystChain)

        abstract fun configureLeaving(params: Map<String, Double>?)

        abstract fun hardCut()
    }

    /** One chain in service and nothing leaving. Owns no data. */
    private inner class Idle : State() {
        fun enter() {
            state = this
        }

        override val leaving: KatalystChain? get() = null

        override val duckingOut: KatalystDuckEffect? get() = null

        override fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            fading.enter(leaving, arrivingLatencyFrames, duckingOut, duckFadingIn)
        }

        override fun process(chain: KatalystChain, ctx: KatalystContext) {
            chain.process(ctx)
        }

        override fun processDuck(chain: KatalystChain, ctx: KatalystContext) {
            chain.processDuck(ctx)
        }

        override fun ownerClaimed(chain: KatalystChain) {}

        override fun configureLeaving(params: Map<String, Double>?) {}

        override fun hardCut() {}
    }

    /**
     * The ramp runs: the leaving chain is handed a shrinking copy of the mix, the arriving chain's
     * own output is ramped up, and the leaving chain's output is added at full weight (it was
     * already attenuated through its input). With latency on either side both weights are placed
     * so they meet at the output, and the fade ends the later latency after the ramp ([begin],
     * [Crossfade]); at no latency, the ramp as it always was. The leaving reference and the duck handover data die
     * with this state, so they live here, and [enter] is their only initialiser.
     */
    private inner class Fading : State() {
        override var leaving: KatalystChain? = null
            private set

        override var duckingOut: KatalystDuckEffect? = null
            private set

        /**
         * True while the arriving chain carries a duck and nothing ducked the orbit before the swap:
         * its effect is ramped in over the fade, the mirror of [duckingOut].
         */
        private var duckFadingIn: Boolean = false

        fun enter(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            this.leaving = leaving
            this.duckingOut = duckingOut
            this.duckFadingIn = duckFadingIn
            // The two weights must meet at the OUTPUT (the [Crossfade] class KDoc): the later of the
            // two latencies delays the incoming weight, and the leaving INPUT ramp waits the
            // difference, so its fade-out comes out of its own latency at the same sample.
            val later = max(arrivingLatencyFrames, leaving.latencyFrames)

            fade.restart(incomingDelayFrames = later, outgoingInputDelayFrames = later - leaving.latencyFrames)
            state = this
        }

        /**
         * REFUSED: the one leaving slot is taken. The host parks the request (the class KDoc). The
         * offered chain is retired rather than dropped, so a host that forgot to ask [settled]
         * strands no rent: it leaves service here, having never entered it.
         */
        override fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            retire(leaving)
        }

        override fun process(chain: KatalystChain, ctx: KatalystContext) {
            // Never null in this state; the check only narrows the type (`!!` could throw on the audio thread).
            val out = leaving ?: return

            if (fade.isComplete) {
                // The ramp ran out on the PREVIOUS block and the duck pass that follows it has had
                // its last turn, so the chain leaves the fade now, not in the middle of the block
                // that finished the ramp. The duck data goes too: an orbit whose sidechain has
                // meanwhile disappeared never ran the duck pass that normally drops it, and
                // `Crossfade.blendHeld` would replay the last block's weights.
                leaving = null
                duckingOut = null
                duckFadingIn = false

                if (out.hasTail()) {
                    draining.enter(out)
                } else {
                    retire(out)
                    idle.enter()
                }

                state.process(chain, ctx)

                return
            }

            // The two halves of one block's ramp, with both chains processed in between. `rampDown`
            // does not advance the ramp, `rampUpAndAdd` does.
            val mix = ctx.mixBuffer

            fade.rampDown(target = leavingMix, source = mix, frames = blockFrames)
            out.process(leavingContext)
            chain.process(ctx)
            fade.rampUpAndAdd(target = mix, outgoing = leavingMix, frames = blockFrames)
        }

        override fun processDuck(chain: KatalystChain, ctx: KatalystContext) {
            val mix = ctx.mixBuffer

            if (duckFadingIn) {
                // The arriving chain's duck runs as it always does, and the UN-ducked mix is blended
                // back in with the weights reversed, so the orbit's gain travels from "not ducked" to
                // "ducked" across the fade instead of dropping by the whole reduction on the first
                // sample the trigger is seen.
                mix.left.copyInto(destination = duckMix.left, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
                mix.right.copyInto(destination = duckMix.right, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
                chain.processDuck(ctx)
                fade.blendHeld(target = mix, incoming = mix, outgoing = duckMix, frames = blockFrames)

                if (fade.isComplete) {
                    duckFadingIn = false
                }

                return
            }

            val leavingDuck = duckingOut

            if (leavingDuck == null) {
                chain.processDuck(ctx)

                return
            }

            // The arriving chain declares no duck, so the orbit's gain has to travel from "ducked"
            // to "not ducked" across the fade, per sample like every other weight in the swap: the
            // leaving duck runs on the mix as it always does, and the UN-DUCKED mix is blended back
            // in with the weights this block's chains were blended with. NOT by ramping the duck's
            // depth: that knob can only be written per block, and a per-block step in a gain that
            // multiplies the whole orbit is a zipper (measured at 0.038 on a 0.5 probe, depth 0.8).
            mix.left.copyInto(destination = duckMix.left, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
            mix.right.copyInto(destination = duckMix.right, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
            leavingDuck.process(ctx)
            fade.blendHeld(target = mix, incoming = duckMix, outgoing = mix, frames = blockFrames)

            if (fade.isComplete) {
                // This block's last sample was fully un-ducked, so the duck is inaudible now and this
                // is the ordinary place it goes. The fade's exit edge (next block, before this pass)
                // drops it too, as the backstop for an orbit whose duck pass stopped running; for
                // the audio the two are equivalent, because the host reads [duckingOut] only after
                // every orbit has processed, and a late takeover needs the ramp's first block.
                duckingOut = null
            }
        }

        /**
         * A ducking owner whose FIRST claim lands in the swap's own block: the swap was decided
         * before that voice offered itself, so the host saw no owner and chose the ramp-out path.
         * Only while the ramp has not moved ([Crossfade.isAtStart]); later, handing the envelope
         * over would jump the orbit's gain back from where the ramp has taken it.
         */
        override fun ownerClaimed(chain: KatalystChain) {
            val lateDuck = duckingOut

            if (lateDuck != null && fade.isAtStart && chain.ducksWith()) {
                chain.duck?.takeOver(lateDuck)
                duckingOut = null
            }
        }

        /** The leaving chain is still audible, so the owner still steers it until the fade ends. */
        override fun configureLeaving(params: Map<String, Double>?) {
            leaving?.applyParams(params)
        }

        override fun hardCut() {
            leaving?.retire()
            leaving = null
            duckingOut = null
            duckFadingIn = false
            idle.enter()
        }
    }

    /**
     * The ramp is over and the leaving chain rings out: still Active, on SILENT input, its output
     * added at FULL weight until it reports no tail, or until the drain is [MAX_DRAIN_SECONDS] old
     * and [Releasing] releases it. Nobody configures it any more: the ring-out runs on the settings
     * the chain had when it left service. The leaving reference and the age die with this state, so
     * they live here.
     */
    private inner class Draining : State() {
        override var leaving: KatalystChain? = null
            private set

        /** Frames drained so far. Counted, not timed, so an offline render releases at the same sample. */
        private var ageFrames: Int = 0

        fun enter(leaving: KatalystChain) {
            this.leaving = leaving
            this.ageFrames = 0
            state = this
        }

        override val duckingOut: KatalystDuckEffect? get() = null

        /**
         * REFUSED: the one leaving slot is taken until the ring-out ends (decision (g), kept). The
         * offered chain is retired rather than dropped, for the reason [Fading.begin] gives.
         */
        override fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            retire(leaving)
        }

        override fun process(chain: KatalystChain, ctx: KatalystContext) {
            // Never null in this state; the check only narrows the type.
            val out = leaving ?: return

            leavingMix.clear()
            out.process(leavingContext)
            chain.process(ctx)
            addLeavingMix(ctx.mixBuffer)

            // The tail is asked FIRST: a drain that ends on its own in the block that reaches the
            // cap is an ordinary drain, not released.
            if (!out.hasTail()) {
                leaving = null
                retire(out)
                idle.enter()

                return
            }

            val age = ageFrames + blockFrames
            ageFrames = age

            if (age >= maxDrainFrames) {
                // This block was added at full weight; the release gain is exactly 1 on the next
                // block's first sample.
                leaving = null
                releasing.enter(out)
            }
        }

        override fun processDuck(chain: KatalystChain, ctx: KatalystContext) {
            chain.processDuck(ctx)
        }

        override fun ownerClaimed(chain: KatalystChain) {}

        override fun configureLeaving(params: Map<String, Double>?) {}

        override fun hardCut() {
            leaving?.retire()
            leaving = null
            idle.enter()
        }
    }

    /**
     * The drain outlived [MAX_DRAIN_SECONDS] with a tail still reported: the leaving chain keeps
     * running on SILENT input, as in [Draining], and its OUTPUT is added under the [TailRelease]
     * gain, restarted at exactly 1 by [enter], so the cap's block and the release's first sample
     * meet without a step. The block the gain falls under the release's floor retires the chain.
     * The input cannot be the handle here: it is already silent, and the tail does not fall with
     * it. The leaving reference dies with this state, so it lives here.
     */
    private inner class Releasing : State() {
        override var leaving: KatalystChain? = null
            private set

        fun enter(leaving: KatalystChain) {
            this.leaving = leaving
            release.restart()
            state = this
        }

        override val duckingOut: KatalystDuckEffect? get() = null

        /** REFUSED until the chain has retired, for the reason [Draining.begin] gives. */
        override fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            retire(leaving)
        }

        override fun process(chain: KatalystChain, ctx: KatalystContext) {
            // Never null in this state; the check only narrows the type.
            val out = leaving ?: return

            leavingMix.clear()
            out.process(leavingContext)
            chain.process(ctx)

            if (release.addReleased(target = ctx.mixBuffer, source = leavingMix)) {
                leaving = null
                retire(out)
                idle.enter()
            }
        }

        override fun processDuck(chain: KatalystChain, ctx: KatalystContext) {
            chain.processDuck(ctx)
        }

        override fun ownerClaimed(chain: KatalystChain) {}

        override fun configureLeaving(params: Map<String, Double>?) {}

        override fun hardCut() {
            leaving?.retire()
            leaving = null
            idle.enter()
        }
    }

    /** [MAX_DRAIN_SECONDS] in frames, at least one. */
    private val maxDrainFrames: Int = (MAX_DRAIN_SECONDS * sampleRate).toInt().coerceAtLeast(1)


    private val idle = Idle()
    private val fading = Fading()
    private val draining = Draining()
    private val releasing = Releasing()

    /**
     * The current state: one of the four instances above, never a fresh one. This initializer is
     * the one entry into Idle that does not run [Idle.enter], which is benign: Idle owns nothing.
     */
    private var state: State = idle

    /** Whether a [begin] can act now: nothing is fading, draining or releasing. The host asks this BEFORE it parks. */
    val settled: Boolean get() = state === idle

    /** The chain leaving service, fading, draining or releasing; null when [settled]. */
    val leaving: KatalystChain? get() = state.leaving

    /**
     * The leaving chain's duck while its effect is ramped out: what is audible, so the host's duck
     * pass must keep resolving the sidechain it was pointed at. Null in every other case.
     */
    val duckingOut: KatalystDuckEffect? get() = state.duckingOut

    /** Rents refused to the chains that have left service plus the one leaving now. */
    val deniedRents: Int get() = retiredDeniedRents + (state.leaving?.deniedRents ?: 0)

    /** Test seam: the ramp runs. */
    internal val isFading: Boolean get() = state === fading

    /** Test seam: the leaving chain rings out. */
    internal val isDraining: Boolean get() = state === draining

    /**
     * The drain outlived its cap and the leaving chain's output is released: that chain ends within
     * the release, so a host asking whether it holds an endless tail ignores it now
     * (`Cylinder.sustainsItself`). Also a test seam.
     */
    internal val isReleasing: Boolean get() = state === releasing

    /** Test seam: the current state OBJECT, for `ChainSwapStateIdentitySpec`. Production never reads it. */
    internal val currentState: Any get() = state

    /**
     * Test seam: whether any state's own fields still reference [chain], the current state or a
     * finished one. `ChainSwapSpec` asks it to see that a finished life leaves no record, which
     * the audio cannot show.
     */
    internal fun holds(chain: KatalystChain): Boolean =
        fading.leaving === chain || draining.leaving === chain || releasing.leaving === chain

    /** Test seam: [holds] for the duck handover data, which only [Fading] carries. */
    internal fun holdsDuck(duck: KatalystDuckEffect): Boolean = fading.duckingOut === duck

    /**
     * Starts the swap from [leaving] (the chain that was in service until the host replaced it) to
     * the chain the host now passes to [process]. REFUSED unless [settled], and a refused [leaving]
     * is retired, never silently dropped (the host is expected to ask [settled] first). [duckingOut] and
     * [duckFadingIn] are the duck handover the host decided (see the class KDoc).
     *
     * [arrivingLatencyFrames] is the ARRIVING chain's `KatalystChain.latencyFrames`; the leaving
     * chain's is read from [leaving]. Both weights are delayed so they are complementary at the
     * OUTPUT, by the later of the two latencies ([Crossfade], phase 3 step 12 C2); 0 and 0 is the
     * ramp as it always was. The duck handover blends with the same delayed weights.
     *
     * The incoming weight is exactly 0 until its delayed start, on the chains and on the duck
     * handover alike, so what the arriving chain emits there is dropped by the law. When the
     * arriving chain is the LATER one that is its own ring, silent because the host hands in a chain
     * that is fresh or was reset when it retired (`Cylinder.chainFor`, `KatalystChain.retire`); a
     * host that handed in a used chain would drop stale audio there, not play it. When it is the
     * EARLIER one it is its first frames, dropped on purpose so the two copies meet under the
     * crossfade. For the duck the partner is the live mix, and the 0 keeps the leaving duck fully in
     * force, or the arriving one fully out, until the delayed start.
     */
    fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
        state.begin(leaving, arrivingLatencyFrames, duckingOut, duckFadingIn)
    }

    /** One block of the host's mix (`ctx.mixBuffer`), through [chain] and whatever is leaving. */
    fun process(chain: KatalystChain, ctx: KatalystContext) {
        state.process(chain, ctx)
    }

    /** The duck pass, after every orbit has processed; the host sets the sidechain around it. */
    fun processDuck(chain: KatalystChain, ctx: KatalystContext) {
        state.processDuck(chain, ctx)
    }

    /** An owner claimed the host, and [chain] (in service) has resolved its state. */
    fun ownerClaimed(chain: KatalystChain) {
        state.ownerClaimed(chain)
    }

    /** The owner's params, for the leaving chain while it is still audible. */
    fun configureLeaving(params: Map<String, Double>?) {
        state.configureLeaving(params)
    }

    /**
     * Ends any fade or drain at whatever weight it had reached, retiring the leaving chain (its
     * units back to the shelves, no denied rents carried) and starting the counters over. Only for
     * a host that will not render the leaving chain's audio again: the cylinder's new life.
     */
    fun hardCut() {
        state.hardCut()
        retiredDeniedRents = 0
        leavingMix.clear()
        duckMix.clear()
    }

    /**
     * A chain leaves service for good: its denied rents are carried BEFORE its own retire zeroes
     * them, then its rented units go back to the shelves. The host's instant install goes through
     * here too.
     */
    fun retire(chain: KatalystChain) {
        retiredDeniedRents += chain.deniedRents
        chain.retire()
    }

    /** The draining chain's ring-out, at full weight: it is the host's own tail, not a second mix. */
    private fun addLeavingMix(mix: StereoBuffer) {
        val mixLeft = mix.left
        val mixRight = mix.right
        val outLeft = leavingMix.left
        val outRight = leavingMix.right
        val frames = blockFrames

        for (i in 0 until frames) {
            mixLeft[i] = mixLeft[i] + outLeft[i]
            mixRight[i] = mixRight[i] + outRight[i]
        }
    }

    companion object {
        /**
         * The longest a drain may run before its output is released ([Releasing], step 12 decision
         * (i)). Counted in frames from the end of the fade.
         *
         * 20 s, the hold the engine gives a stopped playback's endless tails
         * (`PlaybackEngine.MAX_TAIL_HOLD_SECONDS`), for the same reason: the longest room a user can
         * author (size 10, the `Reverb` ceiling) has an RT60 of about 12.5 s and is about 94 dB down
         * after 20 s (on its slowest mode), so no room is cut audibly, while a delay at feedback
         * >= 1 never ends on silence. Size 9 drains on its own in about 10 s. Size 10's tail
         * CEILING, which is conservative, can outlast the cap: measured on a 0.5 charge, the release
         * then differs from the room's own ring-out by under 1e-6, under half a 16-bit step
         * (`ChainSwapCapSpec`). A delay at a high but finite feedback CAN still be audible here
         * (0.95 at 0.5 s is about 18 dB down after 20 s): that is what the exponential release is
         * for. A request parked behind a drain waits at most this plus the release, about 24.5 s.
         */
        const val MAX_DRAIN_SECONDS: Double = 20.0
    }
}
