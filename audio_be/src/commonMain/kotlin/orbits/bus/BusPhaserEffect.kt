package io.peekandpoke.klang.audio_be.orbits.bus

import io.peekandpoke.klang.audio_be.effects.Phaser

/**
 * Phaser insert effect for the bus pipeline.
 *
 * Processes the mix buffer in-place with a multi-stage all-pass phaser.
 * Short-circuits when depth is negligible (< 0.01).
 */
class BusPhaserEffect(
    val phaser: Phaser,
) : BusEffect {

    override fun process(ctx: BusContext) {
        if (phaser.depth < 0.01) return

        phaser.process(ctx.mixBuffer, ctx.blockFrames)
    }
}
