package io.peekandpoke.klang.audio_fe.decoders

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.Header
import javazoom.jl.decoder.SampleBuffer
import java.io.ByteArrayInputStream

object JvmMp3Decoder {
    // Simple helper class to avoid resizing arrays constantly
    private class FloatArrayList {
        private var data = FloatArray(4096)
        private var size = 0

        fun add(value: Float) {
            if (size >= data.size) {
                val newData = FloatArray(data.size * 2)
                System.arraycopy(data, 0, newData, 0, data.size)
                data = newData
            }
            data[size++] = value
        }

        fun toArray(): FloatArray {
            val result = FloatArray(size)
            System.arraycopy(data, 0, result, 0, size)
            return result
        }
    }

    fun decodeMp3(bytes: ByteArray): MonoSamplePcm? {
        try {
            val inputStream = ByteArrayInputStream(bytes)
            val bitstream = Bitstream(inputStream)
            val decoder = Decoder()

            val allSamples = FloatArrayList()
            var sampleRate = 0

            var header: Header? = bitstream.readFrame()
            while (header != null) {
                // In JLayer 1.0.1, use .frequency() instead of .sampleRate()
                sampleRate = header.frequency()

                val sampleBuffer = decoder.decodeFrame(header, bitstream) as SampleBuffer
                val bufferData = sampleBuffer.buffer // Short samples
                val channelCount = sampleBuffer.channelCount

                // Convert to Mono Float
                for (i in 0 until sampleBuffer.bufferLength step channelCount) {
                    var sum = 0f
                    for (c in 0 until channelCount) {
                        sum += bufferData[i + c]
                    }
                    // Average channels to mono and normalize short to float (-1.0 to 1.0)
                    allSamples.add((sum / channelCount) / 32768f)
                }

                bitstream.closeFrame()
                header = bitstream.readFrame()
            }

            val pcmData = allSamples.toArray()

            // --- DEBUG: Save as WAV to verify decoding ---
            // saveDebugWav(pcmData, sampleRate)
            // ---------------------------------------------

            return MonoSamplePcm(
                sampleRate = sampleRate,
                pcm = pcmData,
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
