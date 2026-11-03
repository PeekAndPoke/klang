/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.JsAudioBackend
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioContextOptions
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlin.js.json

private val log = Logger(area = LogArea.Engine, source = "KlangPlayer")

/**
 * Create a KlangPlayer for JS. Suspends until the audio backend has completed warmup.
 */
actual suspend fun klangPlayer(
    options: KlangPlayer.Options,
): KlangPlayer {
    val sampleRate = resolveBestSampleRate(options.sampleRate)

    log.info("Using sample rate $sampleRate")

    val effectiveOptions = options.copy(sampleRate = sampleRate)

    val player = KlangPlayer(
        options = effectiveOptions,
        backendFactory = { config -> JsAudioBackend(config) },
        fetcherDispatcher = Dispatchers.Default,
        backendDispatcher = Dispatchers.Default,
        callbackDispatcher = Dispatchers.Default,
    )

    player.backendReady.await()
    return player
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
        log.warn("Could not create an AudioContext at $desired Hz, trying the browser's own rate", e)

        // Fallback: Create a default context and see what the browser prefers
        try {
            val ctx = AudioContext()
            val actual = ctx.sampleRate.toInt()
            ctx.close()
            actual
        } catch (e2: dynamic) {
            log.warn("Could not create a default AudioContext either, taking 48000 Hz", e2)

            // Absolute fallback ... safe bet as most hardware run natively on 48000
            48000
        }
    }
}
