package io.peekandpoke.klang.audio_be.filters

/**
 * Audio filter interface.
 */
interface AudioFilter {
    companion object {
        fun List<AudioFilter>.combine(): AudioFilter {
            if (isEmpty()) return NoOpAudioFilter
            if (size == 1) return this[0]

            return ChainAudioFilter(this)
        }
    }

    fun process(buffer: DoubleArray, offset: Int, length: Int)
}
