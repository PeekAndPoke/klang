package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.JsAudioBackend
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioContextOptions
import kotlinx.coroutines.Dispatchers
import kotlin.js.json

/**
 * Create a KlangPlayer for JS
 */
actual fun klangPlayer(
    options: KlangPlayer.Options,
): KlangPlayer {
    val sampleRate = resolveBestSampleRate(options.sampleRate)

    val effectiveOptions = options.copy(sampleRate = sampleRate)

    return KlangPlayer(
        options = effectiveOptions,
        backendFactory = { config -> JsAudioBackend(config) },
        fetcherDispatcher = Dispatchers.Default,
        backendDispatcher = Dispatchers.Default,
        callbackDispatcher = Dispatchers.Default,
    )
}

fun resolveBestSampleRate(desired: Int): Int {
    return try {
        // Try to create a context with the desired rate.
        // If the browser supports this and the hardware allows it, it will work.
        val options = json("sampleRate" to desired).unsafeCast<AudioContextOptions>()
        val ctx = AudioContext(options)

        val actual = ctx.sampleRate.toInt()
        ctx.close()

        actual
    } catch (e: dynamic) {
        console.log("[KlangPlayer][JS] Failed to create AudioContext with desired sample rate $desired:")

        // Fallback: Create a default context and see what the browser prefers
        try {
            val ctx = AudioContext()
            val actual = ctx.sampleRate.toInt()
            ctx.close()
            actual
        } catch (e2: dynamic) {
            console.log("[KlangPlayer][JS] Failed to create default AudioContext:")

            // Absolute fallback ... safe bet as most hardware run natively on 48000
            48000
        }
    }
}
