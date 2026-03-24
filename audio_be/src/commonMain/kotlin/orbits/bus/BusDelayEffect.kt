package io.peekandpoke.klang.audio_be.orbits.bus

import io.peekandpoke.klang.audio_be.effects.DelayLine

/**
 * Delay send/return effect for the bus pipeline.
 *
 * Reads from the delay send buffer and mixes the delayed signal into the mix buffer.
 * Short-circuits when delay time is negligible (< 10ms).
 */
class BusDelayEffect(
    val delayLine: DelayLine,
) : BusEffect {

    override fun process(ctx: BusContext) {
        if (delayLine.delayTimeSeconds < 0.01) return

        delayLine.process(ctx.delaySendBuffer, ctx.mixBuffer, ctx.blockFrames)
    }
}
