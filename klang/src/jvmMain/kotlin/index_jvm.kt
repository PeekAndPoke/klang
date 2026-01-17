package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.JvmAudioBackend
import kotlinx.coroutines.Dispatchers
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs

/**
 * Create a KlangPlayer for the JVM
 */
actual fun klangPlayer(
    options: KlangPlayer.Options,
): KlangPlayer {
    val sampleRate = resolveBestSampleRate(options.sampleRate)

    val effectiveOptions = options.copy(sampleRate = sampleRate)

    println("[KlangPlayer][JVM] using sample rate $sampleRate")

    return KlangPlayer(
        options = effectiveOptions,
        backendFactory = { config -> JvmAudioBackend(config) },
        fetcherDispatcher = Dispatchers.Default,
        backendDispatcher = Dispatchers.IO,
    )
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
