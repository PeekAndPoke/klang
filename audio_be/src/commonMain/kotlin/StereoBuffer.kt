package io.peekandpoke.klang.audio_be

/**
 * A stereo buffer.
 */
class StereoBuffer(blockFrames: Int) {
    val left = FloatArray(blockFrames)
    val right = FloatArray(blockFrames)

    init {
        clear()
    }

    fun clear() {
        left.fill(0.0f)
        right.fill(0.0f)
    }

    fun fill(value: Float) {
        left.fill(value)
        right.fill(value)
    }
}
