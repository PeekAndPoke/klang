package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer

/**
 * Wraps one or more [AudioFilter] instances as a [BlockRenderer].
 *
 * Used for pre-filters (crush, coarse), main filter (LP/HP/BP/Notch),
 * and post-filters (distortion).
 */
class AudioFilterRenderer(
    private val filters: List<AudioFilter>,
) : BlockRenderer {

    override fun render(ctx: BlockContext) {
        for (filter in filters) {
            filter.process(ctx.audioBuffer, ctx.offset, ctx.length)
        }
    }

    companion object {
        /** Creates a renderer from a list of filters, or null if the list is empty. */
        fun ofNullable(filters: List<AudioFilter>): AudioFilterRenderer? =
            if (filters.isEmpty()) null else AudioFilterRenderer(filters)

        /** Creates a renderer from a single filter. */
        fun of(filter: AudioFilter): AudioFilterRenderer = AudioFilterRenderer(listOf(filter))
    }
}
