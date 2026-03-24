package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import kotlin.math.floor
import kotlin.math.pow

/**
 * BitCrush effect — reduces bit depth for a lo-fi digital sound.
 * Quantizes the amplitude to discrete levels.
 */
class CrushRenderer(amount: Double) : BlockRenderer {

    private val levels: Double = if (amount > 0.0) 2.0.pow(amount).toInt().toDouble() else 0.0
    private val halfLevels: Double = levels / 2.0

    override fun render(ctx: BlockContext) {
        if (levels <= 1.0) return

        val hl = halfLevels
        val buf = ctx.audioBuffer
        for (i in 0 until ctx.length) {
            val idx = ctx.offset + i
            buf[idx] = (floor(buf[idx].toDouble() * hl) / hl).toFloat()
        }
    }
}
