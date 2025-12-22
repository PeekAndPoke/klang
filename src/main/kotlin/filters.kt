package io.peekandpoke

interface Filter {
    fun process(buffer: DoubleArray, offset: Int, length: Int)
}

// Helper to combine multiple filters
class ChainFilter(private val filters: List<Filter>) : Filter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {
        // Apply each filter in sequence to the whole buffer
        for (i in filters.indices) {
            filters[i].process(buffer, offset, length)
        }
    }
}

// No-op filter
object NoOpFilter : Filter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {}
}
