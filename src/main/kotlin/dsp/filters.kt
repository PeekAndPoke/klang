package io.peekandpoke.dsp

interface AudioFilter {
    fun process(buffer: DoubleArray, offset: Int, length: Int)
}

// Helper to combine multiple filters
class ChainAudioFilter(private val filters: List<AudioFilter>) : AudioFilter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Apply each filter in sequence to the whole buffer
        for (i in filters.indices) {
            filters[i].process(buffer, offset, length)
        }
    }
}

// No-op filter
object NoOpAudioFilter : AudioFilter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {}
}
