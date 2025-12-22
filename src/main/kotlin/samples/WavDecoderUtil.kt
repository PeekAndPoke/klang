package io.peekandpoke.samples

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

/**
 * WAV-only decoder using JavaSound.
 *
 * Output:
 * - mono FloatArray in [-1, 1]
 * - sampleRate as in file (resample later in renderer/registry if needed)
 */
class WavDecoder {

    fun decodeMonoFloatPcm(wavBytes: ByteArray): DecodedSample {
        val bais = ByteArrayInputStream(wavBytes)
        AudioSystem.getAudioInputStream(bais).use { ais ->
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

            AudioSystem.getAudioInputStream(target, ais).use { pcmStream ->
                val pcmBytes = pcmStream.readAllBytes()
                val channels = target.channels
                val frames = pcmBytes.size / (2 * channels)

                val mono = FloatArray(frames)

                var byteIdx = 0
                for (i in 0 until frames) {
                    var sum = 0.0f

                    repeat(channels) {
                        val lo = pcmBytes[byteIdx].toInt() and 0xff
                        val hi = pcmBytes[byteIdx + 1].toInt()
                        val s = ((hi shl 8) or lo).toShort().toInt()
                        sum += (s / 32768.0f)
                        byteIdx += 2
                    }
                    mono[i] = (sum / channels.toFloat())
                }

                return DecodedSample(sampleRate = target.sampleRate.toInt(), pcm = mono)
            }
        }
    }
}
