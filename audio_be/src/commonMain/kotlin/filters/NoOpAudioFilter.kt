package io.peekandpoke.klang.audio_be.filters

/**
 * No-op filter
 */
object NoOpAudioFilter : AudioFilter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {}
}
