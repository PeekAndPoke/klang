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

    /**
     * Interface for filters that support runtime cutoff frequency changes.
     */
    interface Tunable {
        fun setCutoff(cutoffHz: Double)
    }

    fun process(buffer: DoubleArray, offset: Int, length: Int)
}
