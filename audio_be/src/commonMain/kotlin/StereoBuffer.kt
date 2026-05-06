package io.peekandpoke.klang.audio_be

/**
 * A stereo buffer.
 */
class StereoBuffer(blockFrames: Int) {
    val left = AudioBuffer(blockFrames)
    val right = AudioBuffer(blockFrames)

    init {
        clear()
    }

    fun clear() {
        left.fill(0.0)
        right.fill(0.0)
    }

    fun fill(value: AudioSample) {
        left.fill(value)
        right.fill(value)
    }
}
