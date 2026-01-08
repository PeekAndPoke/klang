package io.peekandpoke.klang.audio_be

/**
 * A stereo buffer.
 */
class StereoBuffer(blockFrames: Int) {
    val left = DoubleArray(blockFrames)
    val right = DoubleArray(blockFrames)

    init {
        clear()
    }

    fun clear() {
        left.fill(0.0)
        right.fill(0.0)
    }

    fun fill(value: Double) {
        left.fill(value)
        right.fill(value)
    }
}
