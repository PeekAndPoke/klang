package io.peekandpoke.klang.dsp

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

/**
 * Performance optimized sequential combination of multiple [filters]
 */
class ChainAudioFilter(private val filters: List<AudioFilter>) : AudioFilter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Apply each filter in sequence to the whole buffer
        for (i in filters.indices) {
            filters[i].process(buffer, offset, length)
        }
    }
}

/**
 * No-op filter
 */
object NoOpAudioFilter : AudioFilter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {}
}
