package io.peekandpoke.klang.audio_bridge.analyzer

class AnalyzerBufferHistory(val bufferSize: Int, val capacity: Int = 10) {
    private val buffers = Array(capacity) { createAnalyzerBuffer(bufferSize) }
    private var writeIndex = 0
    var size = 0; private set

    /** Returns the next buffer to be filled. Advances the write head. */
    fun nextBuffer(): AnalyzerBuffer {
        val buf = buffers[writeIndex]
        writeIndex = (writeIndex + 1) % capacity
        if (size < capacity) size++
        return buf
    }

    /** Get a filled buffer by age: 0 = most recent, 1 = previous, etc. */
    operator fun get(age: Int): AnalyzerBuffer {
        return buffers[((writeIndex - 1 - age) % capacity + capacity) % capacity]
    }
}
