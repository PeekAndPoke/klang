package io.peekandpoke.klang.dsp

/**
 * A stereo buffer.
 */
class StereoBuffer(blockFrames: Int) {
    val left = DoubleArray(blockFrames)
    val right = DoubleArray(blockFrames)

    fun clear() {
        left.fill(0.0)
        right.fill(0.0)
    }

    fun fill(value: Double) {
        left.fill(value)
        right.fill(value)
    }
}
