package io.peekandpoke.klang.audio_be.signalgen

/**
 * Stack-based pool of reusable FloatArray buffers for binary composition operators.
 *
 * All external access goes through [use], which guarantees the buffer is returned even on exceptions.
 * Max simultaneous buffers = composition tree depth. E.g. `(a + b).mul(0.5) + c` needs 2.
 */
class ScratchBuffers(private val blockFrames: Int, initialCapacity: Int = 4) {

    private val pool = ArrayList<FloatArray>(initialCapacity).apply {
        repeat(initialCapacity) { add(FloatArray(blockFrames)) }
    }
    private var nextFree = 0

    @PublishedApi
    internal fun acquire(): FloatArray {
        if (nextFree >= pool.size) pool.add(FloatArray(blockFrames))
        return pool[nextFree++]
    }

    @PublishedApi
    internal fun release() {
        nextFree--
    }

    /** Scoped access — guarantees release even on exceptions. Never leak a buffer. */
    inline fun <R> use(block: (FloatArray) -> R): R {
        val buf = acquire()
        try {
            return block(buf)
        } finally {
            release()
        }
    }

    fun reset() {
        nextFree = 0
    }
}
