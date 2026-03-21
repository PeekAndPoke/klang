package io.peekandpoke.klang.audio_be.filters

/**
 * No-op filter
 */
object NoOpAudioFilter : AudioFilter {
    override fun process(buffer: FloatArray, offset: Int, length: Int) {}
}
