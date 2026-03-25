package io.peekandpoke.klang.audio_be.orbits.bus

import io.peekandpoke.klang.audio_be.effects.Ducking

/**
 * Sidechain ducking effect for the bus pipeline.
 *
 * Reduces the mix buffer volume based on a sidechain signal from another orbit.
 * Uses linked stereo detection (max of L/R) to preserve the stereo image.
 *
 * The sidechain buffer is resolved externally by [io.peekandpoke.klang.audio_be.orbits.Orbits]
 * and set on [BusContext.sidechainBuffer] before the pipeline runs.
 *
 * Short-circuits when no ducking is configured or the sidechain buffer is not available.
 */
class BusDuckingEffect : BusEffect {

    /** Sidechain source orbit ID (which orbit triggers the ducking) */
    var duckOrbitId: Int? = null

    /** Ducking processor instance */
    var ducking: Ducking? = null

    override fun process(ctx: BusContext) {
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
        duckOrbitId = null
        ducking = null
    }
}
