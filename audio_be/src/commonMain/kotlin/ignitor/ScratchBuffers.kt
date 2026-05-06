package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Stack-based pool of reusable AudioBuffer buffers for binary composition operators.
 *
 * All external access goes through [use], which guarantees the buffer is returned even on exceptions.
 * Max simultaneous buffers = composition tree depth. E.g. `(a + b).mul(0.5) + c` needs 2.
 */
class ScratchBuffers(private val blockFrames: Int, initialCapacity: Int = 4) {

    private val pool = ArrayList<AudioBuffer>(initialCapacity).apply {
        repeat(initialCapacity) { add(AudioBuffer(blockFrames)) }
    }
    private var nextFree = 0

    @PublishedApi
    internal fun acquire(): AudioBuffer {
        if (nextFree >= pool.size) pool.add(AudioBuffer(blockFrames))
        return pool[nextFree++]
    }

    @PublishedApi
    internal fun release() {
        nextFree--
    }

    /** Scoped access — guarantees release even on exceptions. Never leak a buffer. */
    inline fun <R> use(block: (AudioBuffer) -> R): R {
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

    // ── DoubleArray pool (same stack discipline) ────────────────────────────────

    private val doublePool = ArrayList<DoubleArray>(2)
    private var doubleNextFree = 0

    @PublishedApi
    internal fun acquireDouble(): DoubleArray {
        if (doubleNextFree >= doublePool.size) doublePool.add(DoubleArray(blockFrames))
        return doublePool[doubleNextFree++]
    }

    @PublishedApi
    internal fun releaseDouble() {
        doubleNextFree--
    }

    /** Scoped access for DoubleArray buffers — same guarantees as [use]. */
    inline fun <R> useDouble(block: (DoubleArray) -> R): R {
        val buf = acquireDouble()
        try {
            return block(buf)
        } finally {
            releaseDouble()
        }
    }

    // ── Oversampled ScratchBuffers (cached by factor) ───────────────────────────

    private val oversampleCache = mutableMapOf<Int, ScratchBuffers>()

    /**
     * Returns a [ScratchBuffers] with buffer size = blockFrames * [factor].
     * Cached per factor. If [factor] <= 1, returns this instance as-is.
     */
    fun oversample(factor: Int): ScratchBuffers {
        if (factor <= 1) return this
        return oversampleCache.getOrPut(factor) { ScratchBuffers(blockFrames * factor) }
    }
}
