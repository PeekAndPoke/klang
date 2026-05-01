package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.JvmAudioBackend
import kotlinx.coroutines.Dispatchers
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs

/**
 * Single pinned high-priority audio thread. One thread, MAX priority, non-daemon.
 *
 * Real-time audio cannot tolerate thread migration. With a multi-thread pool the
 * audio coroutine bounces between threads on every `delay()`/suspend, evicting all
 * the renderer's hot state from the previous core's L1/L2 — block-rendering time
 * spikes intermittently and audio underruns. A single-thread executor pins the
 * loop to one OS thread, keeping caches warm. See `audio/ref/performance.md`.
 */
private val audioDispatcher = createHighPriorityDispatcher(
    threadCount = 1,
    namePrefix = "klang-audio-",
    priority = Thread.MAX_PRIORITY,
    daemon = false,
)

/**
 * Create a KlangPlayer for the JVM. Suspends until the audio backend has completed warmup.
 */
actual suspend fun klangPlayer(
    options: KlangPlayer.Options,
): KlangPlayer {
    val sampleRate = resolveBestSampleRate(options.sampleRate)

    val effectiveOptions = options.copy(sampleRate = sampleRate)

    println("[KlangPlayer][JVM] using sample rate $sampleRate")

    val player = KlangPlayer(
        options = effectiveOptions,
        backendFactory = { config -> JvmAudioBackend(config) },
        fetcherDispatcher = Dispatchers.Default,
        backendDispatcher = audioDispatcher,
        callbackDispatcher = Dispatchers.Default,
    )

    player.backendReady.await()
    return player
}

/**
 * Resolves the sample rate that is closest to the desired sample rate
 */
fun resolveBestSampleRate(desired: Int): Int {
    // List of standard rates, plus the desired one
    val candidates = setOf(desired, 44100, 48000, 88200, 96000, 22050, 16000)

    // Sort by distance to desired rate
    val sorted = candidates.sortedBy { abs(it - desired) }

    for (rate in sorted) {
        // Check if 16-bit Stereo PCM is supported at this rate
        val format = AudioFormat(rate.toFloat(), 16, 2, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, format)

        if (AudioSystem.isLineSupported(info)) {
            return rate
        }
    }

    return desired
}
