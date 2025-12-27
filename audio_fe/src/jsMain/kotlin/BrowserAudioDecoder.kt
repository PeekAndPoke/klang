package io.peekandpoke.klang.audio_fe

import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_fe.samples.AudioDecoder
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

class BrowserAudioDecoder : AudioDecoder {
    // We need an AudioContext to decode, even if we don't play anything with it.
    // Ideally reuse the one from the player, but for decoding a transient one is fine/standard.
    private val ctx by lazy { AudioContext() }

    override suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm? {
        return try {
            // 1. Convert ByteArray (Kotlin) -> ArrayBuffer (JS)
            val arrayBuffer = audioBytes.toArrayBuffer()

            // 2. Decode (Async)
            val audioBuffer = ctx.decodeAudioData(arrayBuffer).await()

            // 3. Extract Mono Data
            val pcm = if (audioBuffer.numberOfChannels > 0) {
                audioBuffer.getChannelData(0)
            } else {
                return null
            }

            // 4. Return
            MonoSamplePcm(
                sampleRate = audioBuffer.sampleRate,
                pcm = pcm.toFloatArray() // Convert Float32Array to FloatArray
            ).also {
                console.log("Decoded audio with ${pcm.length} samples")
            }
        } catch (e: Throwable) {
            console.error("Failed to decode audio", e)
            null
        }
    }

    private fun ByteArray.toArrayBuffer(): ArrayBuffer {
        return Int8Array(this.toTypedArray()).buffer
    }

    private fun Float32Array.toFloatArray(): FloatArray {
        val res = FloatArray(this.length)
        for (i in 0 until this.length) {
            res[i] = this[i]
        }
        return res
    }
}
