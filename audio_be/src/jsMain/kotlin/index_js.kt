package io.peekandpoke.klang.audio_be

fun main() {
    registerProcessor(
        "klang-audio-processor",
        KlangAudioProcessor::class.js,
    )

    println("KlangAudioProcessor registered")
}
