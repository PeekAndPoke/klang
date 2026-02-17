package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.registerProcessor

/**
 * Entry point for the AudioWorklet
 * This will be called when the worklet bundle loads
 *
 * Imports are at the file level, but main() is only called in worklet context,
 * so the classes are only instantiated/used in the worklet thread
 */
fun main() {
    console.log("[WORKLET] Registering KlangAudioWorklet")

    registerProcessor(
        "klang-audio-processor",
        KlangAudioWorklet::class.js,
    )

    console.log("[WORKLET] KlangAudioWorklet registered")
}
