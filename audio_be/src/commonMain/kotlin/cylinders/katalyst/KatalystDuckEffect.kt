/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS

/**
 * Sidechain ducking effect for the bus pipeline.
 *
 * Reduces the mix buffer volume based on a sidechain signal from another cylinder.
 * Uses linked stereo detection (max of L/R) to preserve the stereo image.
 *
 * The sidechain buffer is resolved externally by [io.peekandpoke.klang.audio_be.cylinders.Cylinders]
 * and set on [KatalystContext.sidechainBuffer] before the pipeline runs.
 *
 * **The envelope is ORBIT state, not chain state** (decided 2026-09-18, Katalyst step 3b): a chain
 * swap must not step the orbit's gain by whatever reduction is in force, so a crossfade hands a
 * live envelope from the outgoing chain's duck to the incoming chain's ([takeOver]). When the
 * arriving chain will not duck, the cylinder keeps running the outgoing one and crossfades its
 * EFFECT out over the ramp instead; when it will duck and nothing was ducking before, the arriving
 * one is crossfaded IN. See `Cylinder.processDuck`. That pair of ramps is the SWAP's, and it stays
 * exactly as it was.
 *
 * **How it switches and changes** (decided with the maintainer 2026-09-19,
 * `docs/tasks/katalyst-dsl.md` step 5c; built in Katalyst 5c-9). What the swap's ramps never
 * covered is the stage's OWN edges, a `.katp("duck.orbit", ...)` or a `duck.depth` that reaches 0,
 * or an owner handover on one chain. Those were a hard cut, and this is where they glide:
 *
 * - **OFF rides the WEIGHT down to 0** over [KNOB_GLIDE_SECONDS], `gain = 1 + w * (g - 1)`, so the
 *   gain reduction reaches exactly 0 dB while the envelope keeps running on the live sidechain.
 *   Only then does the stage let go of the envelope and of the sidechain orbit ([endLife]).
 *   Letting go earlier IS the click: measured -45.8 to -18.7 dB above 8 kHz against the signal,
 *   on a floor of -52.8 to -49.1, and -50.5 to -47.4 after.
 * - **ON** starts a new life (a fresh envelope at 1.0, as before) and rides the same weight up,
 *   so a duck that engages under a sidechain already at full level arrives over 50 ms instead of
 *   in one sample (measured -53.1 to -16.8 dB before, -52.6 to -47.1 after). An owner HANDOVER,
 *   off and straight back on, was the worst edge of the two stages: -16.4 to -6.0 dB, and above
 *   60 Hz of the signal it reached +0.5 dB; it lands at -49.5 to -48.3.
 * - **A return** mid-fade retargets the weight and turns the fade around where it stands
 *   ([KnobGlide]'s own rule), on the SAME envelope.
 * - **The first initialisation is instant**, and what closes that window depends on which side of
 *   the switch the stage is on. It is [KnobGlide]'s snap flag on the weight. While the stage is OFF
 *   the ORBIT's blocks close it ([orbitBlockRan], called by `KatalystChain.process`), which is what
 *   makes a duck that engages at bar five fade in rather than snap: the duck runs in a LATER pass
 *   than the chain and not at all while it is off, so its own `process` cannot be that tick. Once
 *   the stage is ON, only the first duck PASS closes it, because [orbitBlockRan] deliberately
 *   advances nothing while a life is running. So a duck configured ON whose sidechain orbit never
 *   sounds keeps an armed window for as long as that lasts, and its first switch acts at once.
 *   Review round 2 could not construct an audible failure from that (the first pass can only come
 *   on the block that creates the sidechain cylinder, whose own onset covers the reduction, or on
 *   a silent orbit, where there is no reduction to cover), and spending the window while ON would
 *   change the bar-five case and wants its own measurement.
 * - **[reset] and [retire] are a HARD cut** to the clean slate, for the cylinder's deactivation
 *   and the shelf: the orbit is silent by then, and the next life's first switch snaps again.
 *
 * **What glides while it runs** (`docs/plans/knob-glide.md`, measured first):
 * - **depth** glides PER SAMPLE inside [Ducking.processStereoGliding], on its own axis: the target
 *   gain is linear in depth, and the detector's attack is instantaneous, so a depth step is a
 *   level step. Measured -16.8 dB at worst on a rise; a FALL already sat at the floor, because the
 *   release smooths that direction, and it glides anyway so that one law covers both.
 * - **attack** does not glide. It is the release envelope's time constant and the envelope's state
 *   stays continuous across a jump: 0.001 s to 1 s and back measured AT the floor, both
 *   directions, on every source (the compressor's attack and release measured the same way).
 * - **the sidechain ORBIT** gets nothing HERE, and one direction of it is an OPEN question, not a
 *   solved one. The envelope is the effect's, not the buffer's, so a changed orbit carries the
 *   reduction in force across, and switching onto a QUIETER or silent source is at the floor
 *   (-50.4 to -49.2 dB against a floor of -52.8 to -52.1): the release smooths it.
 *   Switching onto a LOUDER one steps, and it steps in the CLICK class: -21.8 to -19.0 dB on a
 *   saw and -40.8 to -19.0 on a bass, whether the new source was silent before or already
 *   sounding at 0.2 (measured both ways in review round 1). The magnitude is exactly what the same
 *   orbit's own onset makes, `Ducking`'s instantaneous attack; what does NOT follow is that it is
 *   harmless, because an orbit-to-orbit switch brings no new sound into the mix to cover it.
 *   Smoothing it means blending two sidechain sources, which is a decision about what a sidechain
 *   switch MEANS and is not this step's to take.
 *
 * **No state classes** (the complexity rule of `docs/plans/effect-state-machines.md`, the
 * [KatalystGainEffect] precedent). Off is [ducking] being null, which is the field the stage has
 * always had and which `Cylinders` already reads through [duckCylinderId]; fresh, fading and
 * engaged are exactly [KnobGlide]'s snap flag, block countdown and rest. Inner classes would copy
 * the helper and the field for an identical result.
 *
 * **The plan's four questions, for this stage:**
 *  1. *What outlives its states*: nothing but the two glides. The envelope does NOT: it belongs to
 *     a life, and a life ends when the weight lands on 0. Row: "a duck switched off keeps ducking,
 *     by less every block, and lets go of the envelope only on the block the weight reaches 0".
 *  2. *What record of a finished life is forgotten, and where*: the envelope, the sidechain orbit
 *     and the depth glide, all in [endLife], so the next ON starts from an envelope at rest and
 *     snaps its depth. The WEIGHT is put at 0 there WITHOUT re-arming the snap window, which is
 *     what makes the next ON a fade-in instead of a step. Row: "a second life snaps its depth and
 *     fades its weight in", and "a life that ended without a pass still fades the next one in".
 *  3. *Its Off precondition*: the orbit's mix already carries 0 dB of reduction. [endLife] has
 *     THREE doors and each establishes it its own way: the fade landing on a weight of exactly 0
 *     ([process]); a switch-off after a block in which no duck pass ran at all, so the mix went out
 *     unducked ([duckedLastBlock]); and [reset], where the host guarantees silence. Only the first
 *     arrives with the weight already at 0, so [endLife] is what puts it there.
 *  4. *Which state data are references*: the [Ducking] instance. [endLife] drops it, and
 *     [takeOver] moves it; [reset] drops it too. Nothing else holds one.
 *
 * **What a handover carries** (since 5c-9): the [Ducking] instance, the weight and the depth, so
 * nothing on that path steps. What it still does NOT carry is the SIDECHAIN ORBIT's own smoothing:
 * switching `duck.orbit` onto a louder, already sounding source steps the reduction (measured
 * -40.8 to -19.0 dB above 8 kHz against a floor of -52.8), which is a decision about what a
 * sidechain switch means and is recorded as open under "What glides while it runs".
 */
class KatalystDuckEffect(
    private val sampleRate: Int,
    /** The frames of one render block, pinned to 128 in the engine. */
    blockFrames: Int,
) : KatalystEffect {

    /** Sidechain source orbit ID (which orbit triggers the ducking), null while the stage is off. */
    var duckCylinderId: Int? = null
        private set

    /** Ducking processor instance: the LIFE. Null while the stage is off. */
    var ducking: Ducking? = null
        private set

    /**
     * True when another chain's duck has taken this one's envelope over ([takeOver]): this effect
     * is out of service for the rest of its life in this chain, and [configure] must not build a
     * replacement instance behind the swap's back. Cleared by [reset], which is what a chain coming
     * back into service goes through.
     */
    var handedOver: Boolean = false
        private set

    /**
     * The stage's weight against the dry mix: 1 is the full gain reduction, 0 is exactly 0 dB of
     * it. Switching on and off rides this, and its snap flag is the stage's "first initialisation
     * is instant" window (see the class KDoc).
     */
    private val fade = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /** The depth in force: a LEVEL knob, ramped per sample. Forgotten with the life ([endLife]). */
    private val depthGlide = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames)

    /**
     * True while the duck pass has run since the orbit's last block. The host's order inside one
     * block is `configure`, then the orbit's chain ([orbitBlockRan]), then the duck pass, so what
     * [configure] reads here is whether the PREVIOUS block was ducked at all.
     *
     * It answers one question, in the OFF arm: a stage whose sidechain orbit has disappeared gets
     * no pass, so the orbit's mix already goes out unducked and a fade-out has nothing left to
     * ride down. Without it such a stage would hold its envelope and its sidechain orbit for good,
     * and duck again for the rest of the fade if that orbit ever came back.
     */
    private var duckedLastBlock: Boolean = false

    /** Test seam: the weight in force right now, the target once a fade has landed. */
    internal val weight: Double get() = fade.value

    /** Test seam: the depth in force right now, which is what the block just processed ran at. */
    internal val depth: Double get() = depthGlide.value

    /**
     * Takes [from]'s live envelope over, follower state and all, and leaves [from] out of service.
     *
     * Called by `Cylinder.beginFade` when the arriving chain will duck too
     * (`KatalystChain.ducksWith`), BEFORE that chain's writers run: the instance moves, so the gain
     * reduction in force carries across the swap, and the arriving chain's own writer then updates
     * it in place with ITS params (depth, attack, sidechain orbit) rather than building a second
     * one. Moving rather than sharing, because two chains' writers on one instance would fight
     * every block.
     */
    fun takeOver(from: KatalystDuckEffect) {
        ducking = from.ducking
        duckCylinderId = from.duckCylinderId
        // The WEIGHT and the DEPTH move with the envelope, because both scale the reduction that
        // is in force and the arriving chain has to glide from it, not jump to its own.
        //
        // The weight: a stage that arrives at full weight on a duck that stood at `w` steps the
        // orbit by the whole of `1 - w` in one sample, and one whose snap window is spent would
        // instead ramp up from 0 and step the other way.
        //
        // The depth: `Cylinder.beginFade` resets the arriving chain BEFORE this runs, so without
        // carrying it the arriving writer's retarget snaps, and two chains on one orbit at depth
        // 0.2 and 0.9 step a saturated sidechain by 18 dB in one sample (measured -38.5 to -18.0
        // dB above 8 kHz against a floor of -52.8, review round 2). Only the DEEPER direction
        // steps; the other is the detector's own release and was already at the floor.
        //
        // Carrying over spends the snap window too, so the arriving stage's first `configure`
        // turns each glide around exactly as a returning owner's would.
        //
        // One narrow window (review round 3), left as it is: a duck whose sidechain orbit has
        // never existed runs no pass, so its glides still hold their snapped values, and a swap
        // inside that window carries a depth that was never IN FORCE; the arriving chain then
        // glides down from it, so the first hit after the swap ducks too deep for one glide.
        // Detecting it would mean asking a shared helper whether its snap is still armed, for a
        // case no measurement has shown to matter.
        fade.carryOver(from.fade)
        depthGlide.carryOver(from.depthGlide)
        from.ducking = null
        from.handedOver = true
    }

    /**
     * Applies the orbit owner's duck settings, null for none (the stage is off). Called by the
     * chain's writer on every block the lease is held, so an unchanged owner must cost nothing:
     * every write here is a store of the same number or a [KnobGlide.retarget] that already stands.
     */
    fun configure(settings: Voice.Ducking?) {
        if (handedOver) {
            // The incoming chain of a running crossfade owns this envelope now ([takeOver]);
            // writing here would build a second `Ducking` nobody runs and undo the handover.
            return
        }

        if (settings == null) {
            if (ducking == null) {
                // Never started, or already let go: the clean slate, as before.
                duckCylinderId = null

                return
            }

            if (!duckedLastBlock) {
                // Nothing was ducked last block (see [duckedLastBlock]): the output is already
                // identity, so there is nothing to ride down. [endLife] puts the weight at 0 all
                // the same, or the next switch-on would install at full reduction.
                endLife()

                return
            }

            // No "the snap window is still open" arm here, and there cannot be one: getting this
            // far needs [duckedLastBlock], which only a finished [process] sets, and that block
            // advanced the fade and so spent the window. A switch-off while the window is open is
            // the branch above, where nothing was ducked. Review round 2 modelled the reachable
            // states and found the arm dead; the plan says delete what no row can guard.
            fade.retarget(0.0)

            return
        }

        duckCylinderId = settings.cylinderId

        val existing = ducking
        val live: Ducking

        if (existing == null) {
            live = Ducking(
                sampleRate = sampleRate,
                attackSeconds = settings.attackSeconds,
                depth = settings.depth,
            )
            ducking = live
        } else {
            live = existing
            live.attackSeconds = settings.attackSeconds
            live.depth = settings.depth
        }

        // Aim the glide at what the instance STORED, never at the raw setting: the setter coerces
        // to [0, 1] and drops non-finite input, and the glide must land on exactly the number the
        // settled path then reads.
        depthGlide.retarget(live.depth)
        fade.retarget(1.0)
    }

    /**
     * The orbit rendered one block with this stage in its chain. It is the tick that closes the
     * "first initialisation is instant" window, and `KatalystChain.process` is its one caller: the
     * duck runs in a LATER pass and not at all while it is off, so its own [process] cannot be the
     * tick (see the class KDoc).
     *
     * It never moves anything: it advances the weight only while the stage is off AND the weight
     * is settled, which is exactly the case where an advance is nothing but the snap flag falling.
     */
    fun orbitBlockRan() {
        duckedLastBlock = false

        if (ducking == null && !fade.isGliding) {
            fade.advance()
        }
    }

    override fun process(ctx: KatalystContext) {
        val duck = ducking ?: return
        val sidechain = ctx.sidechainBuffer ?: return

        val depthFrom = depthGlide.value
        val depthTo = depthGlide.advance()
        val weightFrom = fade.value
        val weightTo = fade.advance()

        if (depthFrom == depthTo && weightFrom == 1.0 && weightTo == 1.0) {
            // Settled and fully engaged: the path a duck whose knobs stand has always taken.
            duck.processStereo(
                inputL = ctx.mixBuffer.left,
                inputR = ctx.mixBuffer.right,
                sidechainL = sidechain.left,
                sidechainR = sidechain.right,
                blockSize = ctx.blockFrames,
            )
        } else {
            duck.processStereoGliding(
                inputL = ctx.mixBuffer.left,
                inputR = ctx.mixBuffer.right,
                sidechainL = sidechain.left,
                sidechainR = sidechain.right,
                blockSize = ctx.blockFrames,
                depthFrom = depthFrom,
                depthTo = depthTo,
                weightFrom = weightFrom,
                weightTo = weightTo,
            )
        }

        duckedLastBlock = true

        if (weightTo == 0.0 && !fade.isGliding) {
            // The block just processed ended at exactly 0 dB of reduction, so the output IS the
            // mix and the life ends here.
            endLife()
        }
    }

    /**
     * The end of one life: the envelope and the sidechain orbit go, the depth glide is forgotten so
     * the next life snaps onto its own depth, and the WEIGHT is put at 0 with nothing left to
     * travel.
     *
     * The weight has to be SET, not merely left alone. On the landing path in [process] it is
     * already exactly 0 and settled, so [KnobGlide.settleAt] is a no-op; on the short-circuit in
     * [configure] (no pass ran last block, so the orbit's mix went out unducked) it normally stands
     * at 1.0, and leaving it there would install the NEXT switch-on at full reduction with no fade,
     * because `retarget(1.0)` on an unchanged target is free. Reached mid-fade it would also freeze
     * the glide, since [orbitBlockRan] only advances a settled one.
     *
     * [KnobGlide.settleAt] and not `reset()` + `retarget(0.0)`: re-arming the snap window would
     * make that next switch-on INSTANT, which is the same click by another door.
     */
    private fun endLife() {
        duckCylinderId = null
        ducking = null
        duckedLastBlock = false
        depthGlide.reset()
        fade.settleAt(0.0)
    }

    /**
     * A HARD cut: no source orbit, no processor, a fresh envelope next time, and the snap window
     * re-armed so the next life's first switch acts at once. Only for a silent orbit (the
     * cylinder's deactivation) or the shelf ([retire]).
     */
    override fun reset() {
        // [endLife] is what puts the weight at 0; [KnobGlide.reset] only re-arms the snap window on
        // top of it, so the next life's first switch acts at once. No `retarget(0.0)` after it: the
        // weight is already there, and a second way of putting it there would hide whether the
        // first one works.
        endLife()
        handedOver = false
        fade.reset()
    }

    /**
     * False: the duck's envelope is state, but like the compressor it only attenuates and emits
     * nothing from silence. See [KatalystBodyEffect.hasTail].
     */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }
}
