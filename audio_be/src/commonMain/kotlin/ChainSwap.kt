/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystDuckEffect
import io.peekandpoke.klang.audio_be.effects.TailCeiling
import io.peekandpoke.klang.audio_be.utils.copyRangeInto
import io.peekandpoke.klang.audio_be.utils.finiteOrZero
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import io.peekandpoke.klang.common.infra.KlangMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * The swap of a whole effect chain under live audio: the chain leaving service fades out through
 * its INPUT while the arriving chain's output fades in over [Crossfade], and then the leaving chain
 * rings out (drains) on silent input at full weight until it has no tail left. Phase 3 step 12 C1
 * (KLANG-00238 (phase3 step12 master as katalyst) section 6) moved it out of `Cylinder`, where it
 * was five fields, into three states (`audio/ref/effect-state-machines.md`, the delay's shape);
 * five since the release (2026-09-28) and the cut (2026-10-17).
 *
 * **The capped drain** (step 12 decision (i), maintainer 2026-09-28). A drain has a maximum age,
 * [MAX_DRAIN_SECONDS] counted in frames from the fade's end. A chain that still reports a tail
 * then (a delay at feedback >= 1 never stops; any room has ended by then, see the constant) is
 * released by [Releasing]: its OUTPUT dies away EXPONENTIALLY, per sample, from exactly 1
 * ([TailRelease], 60 dB every 3 s), so a long echo still audible at the cap sounds as if it decayed
 * on its own (maintainer's option A, 2026-09-28), and the chain retires once the gain is under the
 * release's floor (-90 dB, 4.5 s). Without the cap a self-sustaining chain swapped away held the
 * one leaving slot for ever. While it drains, the leaving chain is asked to MEASURE its tails
 * ([KatalystChain.measureTails], once on entry and every [TAIL_MEASURE_SECONDS]): the chain's
 * input is silent for good, so a room's tail ceiling can come down from its conservative bound to
 * what the network actually holds, and the room answers against the drain's own floor
 * (`Reverb.TAIL_FLOOR`; ordinary playback keeps the engine's, maintainer 2026-10-17), so the drain
 * ends near the room's audible ring-out instead of at the cap (2026-10-17).
 *
 * **The cut: a waiting request does not wait for the ring-out** (maintainer, 2026-10-17, after
 * listening to `tmp/listening/chain-swap-drain/second-edit.md`: "the change should happen more or
 * less immediate ... it is really odd having to wait for that long. It feels as if it does not
 * work"; "cut the drain" in KLANG-00318 (chain swap request during drain)). The host raises
 * [requestWaiting] when it parks a request it could land. A drain or a release then becomes
 * [Cutting]: the leaving chain's OUTPUT is faded to 0 over [CUT_SECONDS] by a raised cosine, per
 * sample, from the weight it had (1 in a drain, the release's gain in a release), so neither end
 * steps, and the swap is Idle at the end of the block the weight reaches 0. The parked request then
 * lands on the host's next poll as an ordinary [Crossfade]. A request raised during the 60 ms fade
 * is remembered by [Fading] and takes effect where the fade ends: a leaving chain with a tail goes
 * straight into the cut instead of the drain. So a request waits at most the rest of the fade, the
 * cut and one block, about 0.15 to 0.22 s, and never the ring-out; at most two chains run at once,
 * as before. With no request waiting nothing changes: the drain rings out in full.
 *
 * **What the host keeps.** The chain in service (it is handed to every event as `chain`), the
 * cache, the rule for when a request installs at once instead of fading (the orbit is idle; at the
 * output, the engine has never rendered), and the PARKED request: a key the host holds, latest
 * wins, offered again once [settled]. A parked request is not a state here (decision (e) of the
 * plan, the `KatalystFilterSwap` 5c-11 precedent): [begin] while a fade, a drain, a release or a cut
 * runs is REFUSED, and the host asks [settled] first. What the swap learns of it is the one event
 * [requestWaiting]; a request the host withdraws later (the latest intent is the chain in service)
 * still finds the leaving tail cut, which is the edit the user made. So does a parked key that
 * resolves to the chain already in service (the host's landing is then a no-op): a rare live-coding
 * corner, a shortened tail and never a click, left as it is.
 *
 * **The duck handover** is data of [Fading] (it dies with the fade, rule 2 of the plan): the duck
 * of the leaving chain kept in service for the length of the ramp, or the flag that ramps the
 * arriving chain's duck in. The host decides which (`Cylinder.handOverDuck`) and hands it to
 * [begin]; a host without a duck pass passes `null` and `false` and never calls [processDuck].
 *
 * | state \ event | [begin] | [requestWaiting] | [process] | [processDuck] | [ownerClaimed] | [configureLeaving] | [hardCut] |
 * |---|---|---|---|---|---|---|---|
 * | **Idle** | **Fading** | nothing (the host lands its request itself) | the chain alone | the chain's duck | nothing | nothing | nothing |
 * | **Fading** | REFUSED, the offered chain retired | remembered: the fade ends in the cut | ramp not complete: both chains, the ramp; complete: the leaving chain has a tail ? (a request waits ? **Cutting** : **Draining**) : retired, **Idle**, and the entered state's block runs | the duck ramped in, ramped out, or the chain's own | the late duck takeover, only in the ramp's first block | the leaving chain is configured too | leaving chain retired, **Idle** |
 * | **Draining** | REFUSED, the offered chain retired | **Cutting** from weight 1 | both chains, the leaving one on silence, added at full weight, its tails measured on entry and every [TAIL_MEASURE_SECONDS]; no tail left and the block's output under `SILENCE_FLOOR`: retired, **Idle**; a tail or output left and the drain [MAX_DRAIN_SECONDS] old: **Releasing** (from the next block) | the chain's duck | nothing | nothing | leaving chain retired, **Idle** |
 * | **Releasing** | REFUSED, the offered chain retired | **Cutting** from the release's gain | both chains, the leaving one on silence, its output added under the exponential release; the gain under the floor (inside this block): retired, **Idle** | the chain's duck | nothing | nothing | leaving chain retired, **Idle** |
 * | **Cutting** | REFUSED, the offered chain retired | nothing (already cutting) | both chains, the leaving one on silence, its output added under the raised-cosine cut; the weight at 0 (inside this block): retired, **Idle** | the chain's duck | nothing | nothing | leaving chain retired, **Idle** |
 *
 * **The four questions of the plan, answered for the swap:**
 * 1. *What outlives its states:* the [Crossfade] and the [TailRelease], the leaving chain's mix and
 *    context, the duck scratch, [retiredDeniedRents]; and on the host, the chain in service, the
 *    cache and the parked key. Only [Fading.enter] restarts the ramp and only [Releasing.enter]
 *    restarts the release.
 * 2. *The record of a finished life:* the leaving reference (dropped by the edge that leaves
 *    [Fading], [Draining], [Releasing] or [Cutting], and by [hardCut]), the drain's age and its
 *    measuring clock (initialised by [Draining.enter] only, so a drain never inherits a previous
 *    one's), the cut's start weight and position (initialised by [Cutting.enter] only), the fade's
 *    "a request waits" flag (initialised by [Fading.enter] only) and the duck handover data
 *    (dropped when the fade ends, in either pass). [retire] carries a chain's denied rents BEFORE
 *    the chain's own retire zeroes them.
 * 3. *The Idle precondition:* the leaving chain reports no tail and its last block was under
 *    `SILENCE_FLOOR` ([Draining]), or its release gain is under the release's floor
 *    ([Releasing]), or its cut weight has reached 0 ([Cutting]), or the host guarantees silence
 *    ([hardCut], the cylinder's new life).
 * 4. *References and who drops them:* the leaving chain ([Fading], [Draining], [Releasing],
 *    [Cutting]) and the duck being faded out ([Fading]). The edges and [hardCut] drop them, so
 *    [hardCut] DISPATCHES.
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
        /** The chain leaving service, fading, draining, releasing or cutting; null in Idle. */
        abstract val leaving: KatalystChain?

        /** The leaving chain's duck while its effect is ramped out; null outside a fade. */
        abstract val duckingOut: KatalystDuckEffect?

        abstract fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean)

        abstract fun requestWaiting()

        abstract fun process(chain: KatalystChain, ctx: KatalystContext)

        abstract fun processDuck(chain: KatalystChain, ctx: KatalystContext)

        abstract fun ownerClaimed(chain: KatalystChain)

        abstract fun configureLeaving(params: KlangMap<Double>?)

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

        /** Nothing is leaving: the host lands its request itself, at once. */
        override fun requestWaiting() {}

        override fun process(chain: KatalystChain, ctx: KatalystContext) {
            chain.process(ctx)
        }

        override fun processDuck(chain: KatalystChain, ctx: KatalystContext) {
            chain.processDuck(ctx)
        }

        override fun ownerClaimed(chain: KatalystChain) {}

        override fun configureLeaving(params: KlangMap<Double>?) {}

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

        /**
         * True once the host has parked a request behind this fade ([requestWaiting]): where the fade
         * ends, a leaving chain with a tail is cut ([Cutting]) instead of drained. Dies with the state.
         */
        private var requestWaits: Boolean = false

        fun enter(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            this.leaving = leaving
            this.duckingOut = duckingOut
            this.duckFadingIn = duckFadingIn
            this.requestWaits = false
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

        /**
         * The fade is short (60 ms) and runs to its end; only what comes after it changes: the
         * leaving chain's tail is cut, not drained.
         */
        override fun requestWaiting() {
            requestWaits = true
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
                val cut = requestWaits

                leaving = null
                duckingOut = null
                duckFadingIn = false
                requestWaits = false

                if (out.hasTail()) {
                    if (cut) {
                        // The input ramp has just reached 0, so the output is at full weight: the
                        // cut starts from exactly 1.
                        cutting.enter(leaving = out, startWeight = 1.0)
                    } else {
                        draining.enter(out)
                    }
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
                mix.left.copyRangeInto(destination = duckMix.left, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
                mix.right.copyRangeInto(destination = duckMix.right, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
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
            mix.left.copyRangeInto(destination = duckMix.left, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
            mix.right.copyRangeInto(destination = duckMix.right, destinationOffset = 0, startIndex = 0, endIndex = blockFrames)
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
        override fun configureLeaving(params: KlangMap<Double>?) {
            leaving?.applyParams(params)
        }

        override fun hardCut() {
            leaving?.retire()
            leaving = null
            duckingOut = null
            duckFadingIn = false
            requestWaits = false
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

        /**
         * Frames since the leaving chain last measured its tails ([KatalystChain.measureTails]).
         * [enter] sets it to the interval, so the drain's first block measures.
         */
        private var unmeasuredFrames: Int = 0

        fun enter(leaving: KatalystChain) {
            this.leaving = leaving
            this.ageFrames = 0
            this.unmeasuredFrames = tailMeasureFrames
            state = this
        }

        override val duckingOut: KatalystDuckEffect? get() = null

        /**
         * REFUSED: the one leaving slot is taken until the ring-out ends or is cut. The offered chain
         * is retired rather than dropped, for the reason [Fading.begin] gives.
         */
        override fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            retire(leaving)
        }

        /** The ring-out is cut: the last block was added at full weight, so the cut starts from 1. */
        override fun requestWaiting() {
            val out = leaving ?: return

            leaving = null
            cutting.enter(leaving = out, startWeight = 1.0)
        }

        override fun process(chain: KatalystChain, ctx: KatalystContext) {
            // Never null in this state; the check only narrows the type.
            val out = leaving ?: return

            leavingMix.clear()
            out.process(leavingContext)
            chain.process(ctx)
            // The draining chain's ring-out, at full weight: it is the host's own tail, not a second mix.
            ctx.mixBuffer.addFrom(source = leavingMix, frames = blockFrames)

            // Its input is silent for good, so what its delays and rooms hold can only decay: a
            // measurement lets their tail ceilings come down to it (see [TAIL_MEASURE_SECONDS]).
            val unmeasured = unmeasuredFrames + blockFrames

            if (unmeasured > tailMeasureFrames) {
                out.measureTails()
                unmeasuredFrames = 0
            } else {
                unmeasuredFrames = unmeasured
            }

            // The tail is asked FIRST: a drain that ends on its own in the block that reaches the
            // cap is an ordinary drain, not released. Two answers end it: the stages hold no tail,
            // AND what the chain put out in this block is under the engine's silence floor, the
            // orbit's own deactivation test (`Cylinder.tryDeactivate`). The stages answer for what
            // they hold, not for what follows them: a gain, a compressor's makeup or a distort after
            // the reverb raises the step the retire leaves one for one (measured before this gate:
            // size 3 then +30 dB retired at -69 dBFS). The cap still bounds a chain that never gets
            // under the floor. O(block), one pass over the leaving mix.
            if (!out.hasTail() && TailCeiling.peakOf(buffer = leavingMix, frames = blockFrames) < SILENCE_FLOOR) {
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

        override fun configureLeaving(params: KlangMap<Double>?) {}

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

        /**
         * The release is cut short: the cut starts from the gain the release would have given the
         * next sample, so the two meet without a step.
         */
        override fun requestWaiting() {
            val out = leaving ?: return

            leaving = null
            cutting.enter(leaving = out, startWeight = release.nextGain())
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

        override fun configureLeaving(params: KlangMap<Double>?) {}

        override fun hardCut() {
            leaving?.retire()
            leaving = null
            idle.enter()
        }
    }

    /**
     * A request waits behind the ring-out ([requestWaiting]), so the leaving chain's remaining tail is
     * faded out quickly instead of drained (maintainer, 2026-10-17): it keeps running on SILENT
     * input, as in [Draining], and its OUTPUT is added under a raised cosine from [startWeight] to 0
     * over [CUT_SECONDS], per sample. The weight is `startWeight * 0.5 * (1 + cos(pi * k / n))`, k the
     * frames since [enter] and n the cut's length: its slope is 0 at both ends, so the cut neither
     * starts nor lands on a corner, and its largest step between two samples is `startWeight * pi /
     * (2 * n)`, about 2.2e-4 at 48 kHz. The block in which k reaches n retires the chain. One `cos`
     * per sample, as [TailRelease] spends one `exp`: a cut is rare and bounded. The leaving reference,
     * the start weight and the position die with this state, so they live here.
     */
    private inner class Cutting : State() {
        override var leaving: KatalystChain? = null
            private set

        /** The weight the cut starts from: 1 out of a drain or a fade, the release's gain out of a release. */
        private var startWeight: Double = 1.0

        /** Frames since [enter]: the k of the weight law. */
        private var cutFrames: Int = 0

        fun enter(leaving: KatalystChain, startWeight: Double) {
            this.leaving = leaving
            this.startWeight = startWeight
            this.cutFrames = 0
            state = this
        }

        override val duckingOut: KatalystDuckEffect? get() = null

        /** REFUSED until the chain has retired, for the reason [Draining.begin] gives. */
        override fun begin(leaving: KatalystChain, arrivingLatencyFrames: Int, duckingOut: KatalystDuckEffect?, duckFadingIn: Boolean) {
            retire(leaving)
        }

        /** Already cutting: a second request only replaces the host's parked key. */
        override fun requestWaiting() {}

        override fun process(chain: KatalystChain, ctx: KatalystContext) {
            // Never null in this state; the check only narrows the type.
            val out = leaving ?: return

            leavingMix.clear()
            out.process(leavingContext)
            chain.process(ctx)

            // Locals for the loop (`audio/ref/effect-state-machines.md`, section 2).
            val target = ctx.mixBuffer
            val targetLeft = target.left
            val targetRight = target.right
            val sourceLeft = leavingMix.left
            val sourceRight = leavingMix.right
            val n = cutTotalFrames
            val start = startWeight
            var k = cutFrames

            for (i in 0 until blockFrames) {
                val weight = if (k >= n) 0.0 else start * 0.5 * (1.0 + cos(PI * k / n))

                // Sterilised tap, as in [TailRelease.addReleased]: `Inf * 0.0` would be NaN.
                targetLeft[i] = targetLeft[i] + sourceLeft[i].finiteOrZero() * weight
                targetRight[i] = targetRight[i] + sourceRight[i].finiteOrZero() * weight

                k++
            }

            cutFrames = k

            if (k >= n) {
                leaving = null
                retire(out)
                idle.enter()
            }
        }

        override fun processDuck(chain: KatalystChain, ctx: KatalystContext) {
            chain.processDuck(ctx)
        }

        override fun ownerClaimed(chain: KatalystChain) {}

        override fun configureLeaving(params: KlangMap<Double>?) {}

        override fun hardCut() {
            leaving?.retire()
            leaving = null
            idle.enter()
        }
    }

    /** [MAX_DRAIN_SECONDS] in frames, at least one. */
    private val maxDrainFrames: Int = (MAX_DRAIN_SECONDS * sampleRate).toInt().coerceAtLeast(1)

    /** [CUT_SECONDS] in frames, at least one. */
    private val cutTotalFrames: Int = (CUT_SECONDS * sampleRate).toInt().coerceAtLeast(1)

    /** [TAIL_MEASURE_SECONDS] in frames, at least one. */
    private val tailMeasureFrames: Int = (TAIL_MEASURE_SECONDS * sampleRate).toInt().coerceAtLeast(1)


    private val idle = Idle()
    private val fading = Fading()
    private val draining = Draining()
    private val releasing = Releasing()
    private val cutting = Cutting()

    /**
     * The current state: one of the five instances above, never a fresh one. This initializer is
     * the one entry into Idle that does not run [Idle.enter], which is benign: Idle owns nothing.
     */
    private var state: State = idle

    /** Whether a [begin] can act now: nothing is fading, draining, releasing or cutting. The host asks this BEFORE it parks. */
    val settled: Boolean get() = state === idle

    /** The chain leaving service, fading, draining, releasing or cutting; null when [settled]. */
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

    /** Test seam: the drain outlived its cap and the leaving chain's output is released ([Releasing]). */
    internal val isReleasing: Boolean get() = state === releasing

    /** Test seam: a waiting request is cutting the leaving chain's tail ([Cutting]). */
    internal val isCutting: Boolean get() = state === cutting

    /**
     * The leaving chain ends within a bounded time by construction: it is released ([Releasing]) or
     * cut ([Cutting]). A host asking whether it holds an endless tail ignores it then
     * (`Cylinder.sustainsItself`).
     */
    internal val leavingIsEnding: Boolean get() = state === releasing || state === cutting

    /** Test seam: the current state OBJECT, for `ChainSwapStateIdentitySpec`. Production never reads it. */
    internal val currentState: Any get() = state

    /**
     * Test seam: whether any state's own fields still reference [chain], the current state or a
     * finished one. `ChainSwapSpec` asks it to see that a finished life leaves no record, which
     * the audio cannot show.
     */
    internal fun holds(chain: KatalystChain): Boolean =
        fading.leaving === chain || draining.leaving === chain || releasing.leaving === chain || cutting.leaving === chain

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

    /**
     * The host has parked a request it could land now if nothing were leaving (its name is
     * registered). A drain or a release is cut ([Cutting]); during the fade the cut is remembered
     * for the fade's end; settled or already cutting, nothing happens. Idempotent, so a host may
     * raise it every block its request stays parked.
     */
    fun requestWaiting() {
        state.requestWaiting()
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
    fun configureLeaving(params: KlangMap<Double>?) {
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

    companion object {
        /**
         * The longest a drain may run before its output is released ([Releasing], step 12 decision
         * (i)). Counted in frames from the end of the fade.
         *
         * 20 s, the hold the engine gives a stopped playback's endless tails
         * (`PlaybackEngine.MAX_TAIL_HOLD_SECONDS`), for the same reason: the longest room a user can
         * author (size 10, the `Reverb` ceiling) has an RT60 of about 12.5 s and is about 94 dB down
         * after 20 s (on its slowest mode), so no room is cut audibly, while a delay at feedback
         * >= 1 never ends on silence. Since the drain measures its rooms ([TAIL_MEASURE_SECONDS],
         * 2026-10-17) a size-10 room drains on its own in about 14 to 18 s, depending on its charge
         * (`ChainSwapCapSpec`), so the cap is for the delay's endless tails. A delay at a high but finite feedback CAN still be audible here
         * (0.95 at 0.5 s is about 18 dB down after 20 s): that is what the exponential release is
         * for. A request parked behind a drain no longer waits for it: it cuts it ([CUT_SECONDS]).
         */
        const val MAX_DRAIN_SECONDS: Double = 20.0

        /**
         * How long the cut of a ring-out takes when a request waits behind it ([Cutting], maintainer
         * 2026-10-17: "the change should happen more or less immediate").
         *
         * 150 ms, a raised cosine. Long enough that fading a loud room or echo out is a fade, not a
         * click: the weight's largest step between two samples is about 2.2e-4 at 48 kHz, and the
         * curve has no corner at either end. Short enough that the edit reads as immediate: with the
         * 60 ms fade that follows it, the new chain is at full weight about a fifth of a second after
         * the request. Shorter starts to sound like an edit of the tail itself (a 60 ms cut of a
         * long room is audible as a choke), longer than about 250 ms starts to read as latency.
         */
        const val CUT_SECONDS: Double = 0.15

        /**
         * How often a draining chain measures its tails ([KatalystChain.measureTails]), first on the
         * drain's first block. Its input is silent, so a measurement can only bring a tail ceiling
         * DOWN to what the unit really holds; between measurements the ceiling decays by the unit's
         * feedback, its usual bound. One measurement is a scan of the room's comb cells (about 25k
         * samples at 48 kHz): every 0.5 s of drain that costs nothing worth naming, and a drain ends
         * at most this long after its content is under the floor.
         */
        const val TAIL_MEASURE_SECONDS: Double = 0.5
    }
}
