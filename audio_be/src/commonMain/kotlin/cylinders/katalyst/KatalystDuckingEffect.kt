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
class KatalystDuckingEffect : KatalystEffect {

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

    /** Clears ducking configuration. */
    fun clear() {
        duckCylinderId = null
        ducking = null
    }
}
