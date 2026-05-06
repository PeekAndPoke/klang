package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * No-op filter
 */
object NoOpAudioFilter : AudioFilter {
    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {}
}
