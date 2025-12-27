package io.peekandpoke.klang.audio_be

actual fun createAudioLoop(sampleRate: Int, blockFrames: Int): KlangAudioLoop =
    JvmKlangAudioLoop(sampleRate, blockFrames)
