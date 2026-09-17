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
 */
class KatalystDuckEffect : KatalystEffect {

    /** Sidechain source orbit ID (which orbit triggers the ducking) */
    var duckCylinderId: Int? = null

    /** Ducking processor instance */
    var ducking: Ducking? = null

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
