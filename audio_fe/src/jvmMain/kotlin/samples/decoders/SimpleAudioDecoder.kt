package io.peekandpoke.klang.audio_fe.samples.decoders

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_fe.samples.AudioDecoder

class SimpleAudioDecoder : AudioDecoder {

    override fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm? {
        return try {
            when {
                isWav(audioBytes) -> WavFileDecoder.decodeMonoFloatPcm(audioBytes)
                else -> GenericAudioFileDecoder.decodeMonoFloatPcm(audioBytes)
            }
        } catch (e: Exception) {
            System.err.println("Manual WAV decode failed, falling back to AudioSystem:")
            e.printStackTrace()
            // return
            null
        }
    }

    private fun isWav(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        // RIFF
        if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
            bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) return false

        // WAVE
        if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
            bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
        ) return false

        return true
    }
}
