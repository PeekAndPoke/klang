package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Performance optimized sequential combination of multiple [filters]
 */
class ChainAudioFilter(private val filters: List<AudioFilter>) : AudioFilter {
    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        // Apply each filter in sequence to the whole buffer
        for (f in filters) {
            f.process(buffer, offset, length)
        }
    }
}
