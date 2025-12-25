package io.peekandpoke.klang.dsp.filters

/**
 * No-op filter
 */
object NoOpAudioFilter : AudioFilter {
    override fun process(buffer: DoubleArray, offset: Int, length: Int) {}
}
