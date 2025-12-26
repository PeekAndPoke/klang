package io.peekandpoke.klang.dsp

fun main() {
    registerProcessor(
        "klang-audio-processor",
        KlangAudioProcessor::class.js,
    )

    println("KlangAudioProcessor registered")
}
