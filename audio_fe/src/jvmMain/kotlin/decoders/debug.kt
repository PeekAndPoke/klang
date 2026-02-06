package io.peekandpoke.klang.audio_fe.decoders

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

private val lock = Any()

fun saveDebugWav(floatData: FloatArray, sampleRate: Int, dir: String = "./tmp") = synchronized(lock) {
    try {
        lateinit var file: File
        var counter = 0

        while (true) {
            file = File("$dir/decoded_debug_${(counter++).toString().padStart(4, '0')}.wav")
            if (!file.exists()) break
        }

        file.parentFile.mkdirs()

        // Convert floats back to 16-bit PCM bytes for WAV
        val byteBuffer = ByteBuffer.allocate(floatData.size * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (f in floatData) {
            val s = (f * 32767).toInt().coerceIn(-32768, 32767).toShort()
            byteBuffer.putShort(s)
        }
        val pcmBytes = byteBuffer.array()
        val byteArrayInputStream = ByteArrayInputStream(pcmBytes)

        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val audioInputStream = AudioInputStream(byteArrayInputStream, format, floatData.size.toLong())

        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, file)
        println("Saved debug WAV to: ${file.absolutePath}")
    } catch (e: Exception) {
        println("Failed to save debug WAV: ${e.message}")
    }
}
