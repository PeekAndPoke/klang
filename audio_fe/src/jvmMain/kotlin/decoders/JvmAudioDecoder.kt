package io.peekandpoke.klang.audio_fe.decoders

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_fe.samples.AudioDecoder

class JvmAudioDecoder : AudioDecoder {

    override suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm? {
        return try {
            when {
                isWav(audioBytes) -> JvmWavDecoder.decodeWav(audioBytes)
                isMp3(audioBytes) -> JvmMp3Decoder.decodeMp3(audioBytes)
                else -> JvmGenericAudioDecoder.decodeGeneric(audioBytes)
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

    private fun isMp3(bytes: ByteArray): Boolean {
        // Basic check for ID3 tag or Sync frame
        return (bytes.size > 3 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) ||
                (bytes.size > 1 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0)
    }
}
