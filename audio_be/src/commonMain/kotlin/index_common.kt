package io.peekandpoke.klang.audio_be

expect fun <S> createAudioLoop(sampleRate: Int, blockFrames: Int): KlangAudioLoop<S>

