package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Wraps an [AudioFilter] as a [BlockRenderer].
 *
 * Used for the main subtractive filter (LP/HP/BP/Notch/Formant) which needs the [AudioFilter]
 * interface for [AudioFilter.Tunable] cutoff modulation support.
 */
class AudioFilterRenderer(
    private val filter: AudioFilter,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        filter.process(ctx.audioBuffer, ctx.offset, ctx.length)
    }

    companion object {
        fun of(filter: AudioFilter): AudioFilterRenderer = AudioFilterRenderer(filter)
    }
}
