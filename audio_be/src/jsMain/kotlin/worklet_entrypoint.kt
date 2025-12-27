package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.worklet.KlangAudioWorklet

/**
 * Entry point for the AudioWorklet
 */
fun main() {
    registerProcessor(
        "klang-audio-processor",
        KlangAudioWorklet::class.js,
    )

    println("KlangAudioProcessor registered")
}
