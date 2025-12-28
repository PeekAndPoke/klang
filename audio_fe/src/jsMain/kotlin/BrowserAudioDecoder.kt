package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_fe.samples.AudioDecoder
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int8Array

class BrowserAudioDecoder : AudioDecoder {
    // We need an AudioContext to decode, even if we don't play anything with it.
    // Ideally reuse the one from the player, but for decoding a transient one is fine/standard.
    private val defaultCtx: AudioContext by lazy { AudioContext() }

    override suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm? {
        return try {
            // 1. Convert Kotlin ByteArray to JS ArrayBuffer
            // We need to copy the bytes because Kotlin ByteArray might not align with JS Buffer view expectations directly here
            val int8Array = Int8Array(audioBytes.toTypedArray())
            val arrayBuffer = int8Array.buffer

            val ctx: AudioContext = try {
                js("new OfflineAudioContext(1, 1, 48000)")
            } catch (e: Throwable) {
                console.error("[BrowserAudioDecoder] Failed to create OfflineAudioContext", e.printStackTrace())
                // Use the default context
                defaultCtx
            }

            // console.log("[BrowserAudioDecoder] Created AudioContext", ctx, "with sample rate", ctx.sampleRate)

            // 2. Decode (Async)
            // decodeAudioData returns a Promise<AudioBuffer>
            val audioBuffer = ctx.decodeAudioData(arrayBuffer).await()

            // 3. Extract Mono Data
            if (audioBuffer.numberOfChannels == 0) return null

            // getChannelData returns a Float32Array
            val pcmFloat32: Float32Array = audioBuffer.getChannelData(0)

            // console.log("Decoded ${pcmFloat32.length} samples ... use pcmFloat32.unsafeCast<FloatArray>()")
            // console.log("pcmFloat32", pcmFloat32)

            // 4. Return
            MonoSamplePcm(
                sampleRate = audioBuffer.sampleRate,
                // OPTIMIZATION: unsafeCast avoids the O(n) loop copy.
                // In Kotlin/JS, FloatArray is backed by Float32Array.
                pcm = pcmFloat32.unsafeCast<FloatArray>()
            )
        } catch (e: Throwable) {
            console.error("[BrowserAudioDecoder] Failed to decode audio", e)
            null
        }
    }
}
