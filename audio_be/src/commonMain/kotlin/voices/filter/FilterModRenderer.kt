package io.peekandpoke.klang.audio_be.voices.filter

import io.peekandpoke.klang.audio_be.voices.BlockContext
import io.peekandpoke.klang.audio_be.voices.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.calculateControlRateEnvelope

/**
 * Updates filter cutoff frequencies from envelope modulation.
 * Runs at control rate (once per block) for efficiency.
 */
class FilterModRenderer(
    private val modulators: List<Voice.FilterModulator>,
    private val startFrame: Long,
    private val gateEndFrame: Long,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        for (mod in modulators) {
            val envValue = calculateControlRateEnvelope(mod.envelope, ctx.blockStart, startFrame, gateEndFrame)
            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue)
            mod.filter.setCutoff(newCutoff)
        }
    }
}
