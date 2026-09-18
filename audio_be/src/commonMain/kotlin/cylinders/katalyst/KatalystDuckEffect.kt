/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Ducking

/**
 * Sidechain ducking effect for the bus pipeline.
 *
 * Reduces the mix buffer volume based on a sidechain signal from another cylinder.
 * Uses linked stereo detection (max of L/R) to preserve the stereo image.
 *
 * The sidechain buffer is resolved externally by [io.peekandpoke.klang.audio_be.cylinders.Cylinders]
 * and set on [KatalystContext.sidechainBuffer] before the pipeline runs.
 *
 * Short-circuits when no ducking is configured or the sidechain buffer is not available.
 *
 * **The envelope is ORBIT state, not chain state** (decided 2026-09-18, Katalyst step 3b): a chain
 * swap must not step the orbit's gain by whatever reduction is in force, so a crossfade hands a
 * live envelope from the outgoing chain's duck to the incoming chain's ([takeOver]). When the
 * arriving chain will not duck, the cylinder keeps running the outgoing one and crossfades its
 * EFFECT out over the ramp instead; when it will duck and nothing was ducking before, the arriving
 * one is crossfaded IN. See `Cylinder.processDuck`.
 */
class KatalystDuckEffect : KatalystEffect {

    /** Sidechain source orbit ID (which orbit triggers the ducking) */
    var duckCylinderId: Int? = null

    /** Ducking processor instance */
    var ducking: Ducking? = null

    /**
     * True when another chain's duck has taken this one's envelope over ([takeOver]): this effect
     * is out of service for the rest of its life in this chain, and its writer must not build a
     * replacement instance behind the swap's back (see `KatalystChainBuilder.writeDuck`). Cleared
     * by [reset], which is what a chain coming back into service goes through.
     */
    var handedOver: Boolean = false
        private set

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
        from.ducking = null
        from.handedOver = true
    }

    override fun process(ctx: KatalystContext) {
        val duck = ducking ?: return
        val sidechain = ctx.sidechainBuffer ?: return

        duck.processStereo(
            inputL = ctx.mixBuffer.left,
            inputR = ctx.mixBuffer.right,
            sidechainL = sidechain.left,
            sidechainR = sidechain.right,
            blockSize = ctx.blockFrames,
        )
    }

    /** Clears the ducking configuration: no source orbit, no processor, fresh envelope next time. */
    override fun reset() {
        duckCylinderId = null
        ducking = null
        handedOver = false
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
