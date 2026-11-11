/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_fe.samples.AudioDecoder
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int8Array

private val log = Logger(area = LogArea.Engine, source = "BrowserAudioDecoder")

/**
 * Decodes a sample file with the browser's own decoder, at [sampleRate], the rate the ENGINE runs at (KLANG-00430, audit
 * L3-05 and L9-07, option 3): the browser resamples a file not at that rate once, here, and the engine's playhead
 * reads it 1:1. Until 2026-11-11 every file was decoded at a fixed 48 kHz, so on an engine at any other rate a file
 * was converted twice (the browser to 48 kHz, the playhead to the engine's rate).
 */
class BrowserAudioDecoder(
    /** The engine's sample rate (the player's resolved rate, `resolveBestSampleRate`), in Hz. */
    private val sampleRate: Int,
) : AudioDecoder {
    // We need an AudioContext to decode, even if we don't play anything with it.
    // Ideally reuse the one from the player, but for decoding a transient one is fine/standard.
    private val defaultCtx: AudioContext by lazy { AudioContext() }

    override suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm? {
        return try {
            // 1. Convert Kotlin ByteArray to JS ArrayBuffer
            // We need to copy the bytes because Kotlin ByteArray might not align with JS Buffer view expectations directly here
            val int8Array = Int8Array(audioBytes.toTypedArray())
            val arrayBuffer = int8Array.buffer

            val rate = sampleRate
            val ctx: AudioContext = try {
                js("new OfflineAudioContext(1, 1, rate)")
            } catch (e: Throwable) {
                log.error("Failed to create an OfflineAudioContext at $sampleRate Hz", e)
                // Use the default context, at the device's own rate: the engine's playhead converts what it decodes
                defaultCtx
            }

            // 2. Decode (Async)
            // decodeAudioData returns a Promise<AudioBuffer>
            val audioBuffer = ctx.decodeAudioData(arrayBuffer).await()

            // 3. Extract Mono Data
            val channels = audioBuffer.numberOfChannels

            if (channels == 0) return null

            // 4. Return: every channel averaged, as the JVM decoders do (KLANG-00430, audit findings L3-13 and L9-07:
            // this used to keep channel 0 only, so a stereo sample was the left side in the browser and the mean of
            // both offline). The engine's canonical sample type is Double: widen Float32Array to DoubleArray here
            // (Kotlin/JS DoubleArray is backed by Float64Array). One-shot per decoded asset.
            val first = audioBuffer.getChannelData(0).unsafeCast<FloatArray>()
            val pcm = DoubleArray(first.size) { first[it].toDouble() }

            for (c in 1 until channels) {
                val data: Float32Array = audioBuffer.getChannelData(c)
                val samples = data.unsafeCast<FloatArray>()

                for (i in pcm.indices) {
                    pcm[i] += samples[i].toDouble()
                }
            }

            if (channels > 1) {
                val scale = 1.0 / channels

                for (i in pcm.indices) {
                    pcm[i] *= scale
                }
            }

            MonoSamplePcm(
                sampleRate = audioBuffer.sampleRate,
                pcm = pcm,
            )
        } catch (e: Throwable) {
            log.error("Failed to decode audio", e)
            null
        }
    }
}
