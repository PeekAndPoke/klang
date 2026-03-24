package io.peekandpoke.klang.audio_be.orbits.bus

import io.peekandpoke.klang.audio_be.effects.Reverb

/**
 * Reverb send/return effect for the bus pipeline.
 *
 * Reads from the reverb send buffer and mixes the wet reverb signal into the mix buffer.
 * Short-circuits when room size is negligible (< 0.01).
 */
class BusReverbEffect(
    val reverb: Reverb,
) : BusEffect {

    override fun process(ctx: BusContext) {
        if (reverb.roomSize < 0.01) return

        reverb.process(ctx.reverbSendBuffer, ctx.mixBuffer, ctx.blockFrames)
    }
}
