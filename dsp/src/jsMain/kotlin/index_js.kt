package io.peekandpoke.klang.dsp

fun main() {
    val ctor = { TestAudioProcessor() }

    registerProcessor(
        "test-audio-processor",
        TestAudioProcessor::class.js,
    )

    println("TestAudioProcessor registered")
}
