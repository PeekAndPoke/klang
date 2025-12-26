package io.peekandpoke.klang.audio_be

/**
 * Entry point for the AudioWorklet
 */
fun main() {
    registerProcessor(
        "klang-audio-processor",
        KlangAudioProcessor::class.js,
    )

    println("KlangAudioProcessor registered")
}

actual fun <S> createAudioLoop(sampleRate: Int, blockFrames: Int): KlangAudioLoop<S> =
    TODO("Not yet implemented")
