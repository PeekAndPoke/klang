package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Compressor

/**
 * Compressor insert effect for the bus pipeline.
 *
 * Processes the mix buffer in-place with dynamic range compression.
 * Only active when a compressor has been configured.
 */
class KatalystCompressorEffect : KatalystEffect {

    /** Compressor instance — null means no compression configured */
    var compressor: Compressor? = null

    override fun process(ctx: KatalystContext) {
        compressor?.process(ctx.mixBuffer.left, ctx.mixBuffer.right, ctx.blockFrames)
    }
}
