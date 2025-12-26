package io.peekandpoke.klang.audio_be

actual fun <S> createAudioLoop(sampleRate: Int, blockFrames: Int): KlangAudioLoop<S> =
    JvmKlangAudioLoop(sampleRate, blockFrames)
