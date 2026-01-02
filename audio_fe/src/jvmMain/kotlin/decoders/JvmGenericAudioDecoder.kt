package io.peekandpoke.klang.audio_fe.decoders

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

object JvmGenericAudioDecoder {

    @Throws(RuntimeException::class)
    fun decodeGeneric(audioBytes: ByteArray): MonoSamplePcm {
        return decodeInternal(audioBytes)
    }

    private fun decodeInternal(audioBytes: ByteArray): MonoSamplePcm {
        val bais = ByteArrayInputStream(audioBytes)

        val sourceStream = try {
            AudioSystem.getAudioInputStream(bais)
        } catch (e: Exception) {
            throw RuntimeException("Failed to create AudioInputStream", e)
        }

        sourceStream.use { ais ->
            val base = ais.format

            // Convert to PCM_SIGNED 16-bit for predictable decoding
            val target = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.sampleRate,
                16,
                base.channels,
                base.channels * 2,
                base.sampleRate,
                false, // little endian
            )

            val pcmStream = try {
                AudioSystem.getAudioInputStream(target, ais)
            } catch (e: Exception) {
                throw e
            }

            pcmStream.use { validPcmStream ->
                val pcmBytes = validPcmStream.readAllBytes()
                val channels = target.channels

                if (pcmBytes.isEmpty()) {
                    return MonoSamplePcm(target.sampleRate.toInt(), FloatArray(0))
                }

                val frames = pcmBytes.size / (2 * channels)
                val mono = FloatArray(frames)

                var byteIdx = 0
                for (i in 0 until frames) {
                    var sum = 0.0f
                    repeat(channels) {
                        if (byteIdx + 1 < pcmBytes.size) {
                            val lo = pcmBytes[byteIdx].toInt() and 0xff
                            val hi = pcmBytes[byteIdx + 1].toInt()
                            val s = ((hi shl 8) or lo).toShort().toInt()
                            sum += (s / 32768.0f)
                        }
                        byteIdx += 2
                    }
                    mono[i] = (sum / channels.toFloat())
                }

                return MonoSamplePcm(sampleRate = target.sampleRate.toInt(), pcm = mono)
            }
        }
    }

}
