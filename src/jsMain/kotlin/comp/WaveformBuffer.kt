package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.VisualizerBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set

/**
 * Manages waveform sample data for visualization.
 *
 * In single-frame mode ([numFrames] = 1), acts as a simple pass-through for the latest frame.
 * In multi-frame mode ([numFrames] > 1), accumulates the last N frames in a ring buffer
 * and provides them in chronological order for rendering.
 *
 * @param frameSize Number of samples per frame (e.g. 2048 for a standard AnalyserNode)
 * @param numFrames Number of frames to accumulate (1 = single frame, >1 = ring buffer)
 */
class WaveformBuffer(
    val frameSize: Int,
    val numFrames: Int,
) {
    private val totalSize = frameSize * numFrames
    private val storage: Float32Array = Float32Array(totalSize)
    private var writePos: Int = 0
    private var filled: Boolean = false

    /** The number of usable samples currently available. */
    val availableLength: Int
        get() = if (numFrames == 1) frameSize else if (filled) totalSize else writePos

    /** Appends the given [source] frame into the buffer. */
    fun write(source: VisualizerBuffer) {
        if (numFrames == 1) {
            for (i in 0 until frameSize) {
                storage[i] = source[i]
            }
            return
        }

        for (i in 0 until frameSize) {
            storage[writePos + i] = source[i]
        }
        writePos += frameSize
        if (writePos >= totalSize) {
            writePos = 0
            filled = true
        }
    }

    /**
     * Returns a [Float32Array] with samples ordered oldest-to-newest, and the usable length.
     * For single-frame mode this is just the storage buffer itself.
     * For multi-frame mode, the ring buffer is re-ordered if necessary.
     */
    fun getReadBuffer(): Pair<Float32Array, Int> {
        if (numFrames == 1) {
            return Pair(storage, frameSize)
        }

        if (!filled) {
            return Pair(storage, writePos)
        }

        if (writePos == 0) {
            return Pair(storage, totalSize)
        }

        // Ring buffer is full and write pointer is mid-buffer — re-order
        val ordered = Float32Array(totalSize)
        val tailLen = totalSize - writePos
        for (i in 0 until tailLen) {
            ordered[i] = storage[writePos + i]
        }
        for (i in 0 until writePos) {
            ordered[tailLen + i] = storage[i]
        }
        return Pair(ordered, totalSize)
    }

    /** Resets the buffer state without reallocating. */
    fun reset() {
        writePos = 0
        filled = false
    }
}
