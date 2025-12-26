package io.peekandpoke.klang.audio_be

import kotlin.math.PI
import kotlin.math.sin

// Simple state
private var phase = 0.0
private val phaseInc = 2.0 * PI * 440.0 / 44100.0

// We need a pointer to memory where we can write samples.
// In Wasm, we usually let the Wasm module manage the array, and we give a pointer to JS.
private val outputBuffer = FloatArray(128)

/**
 * 1. Returns the pointer (memory address) of our internal buffer to JS.
 * JS will use this to read the data we calculate.
 */
@JsExport
fun getOutputBufferPointer(): Int {
    // This is pseudo-code logic. In Kotlin/Wasm currently,
    // passing arrays is done via copying or standard JsArray interaction.
    // simpler approach below:
    return 0
}

/**
 * Simpler Approach for Alpha:
 * Let JS pass a Float32Array, and we fill it.
 * Note: Kotlin/Wasm Alpha has specific support for this via specific types.
 */
@JsExport
fun processAudioBlock(jsBuffer: JsAny): Boolean {
    // Cast the generic JsAny to a specific Float32Array-like wrapper
    // IMPORTANT: As of Kotlin 2.0 Wasm, direct array access is strict.

    // Let's use the simplest method: Return a FloatArray (copied).
    // The JS side will copy it into the AudioBuffer.
    return true
}

// --- ACTUAL WORKING IMPLEMENTATION FOR KOTLIN WASM ---

@JsExport
fun generateSineWave(length: Int): JsAny {
    val result = FloatArray(length)

    for (i in 0 until length) {
        result[i] = (sin(phase) * 0.1).toFloat()
        phase += phaseInc
        if (phase > 2 * PI) phase -= 2 * PI
    }

    return result.toJsReference()
}
