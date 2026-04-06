package io.peekandpoke.klang.audio_engine.cli

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams 16-bit stereo PCM data to a WAV file.
 *
 * Writes a placeholder header on open, appends PCM blocks during rendering,
 * then patches the RIFF/data chunk sizes on close.
 */
class WavFileWriter(
    private val filePath: String,
    private val sampleRate: Int,
    private val channels: Int = 2,
    private val bitsPerSample: Int = 16,
) {
    private var file: RandomAccessFile? = null
    private var dataBytes = 0

    fun open() {
        val f = RandomAccessFile(filePath, "rw")
        f.setLength(0)
        file = f

        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        // Write 44-byte WAV header with placeholder sizes
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        header.put("RIFF".toByteArray())
        header.putInt(0)  // placeholder: file size - 8
        header.put("WAVE".toByteArray())

        // fmt sub-chunk
        header.put("fmt ".toByteArray())
        header.putInt(16)                    // PCM format chunk size
        header.putShort(1)                   // audio format: PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())

        // data sub-chunk
        header.put("data".toByteArray())
        header.putInt(0)  // placeholder: data size

        f.write(header.array())
    }

    /**
     * Write a block of interleaved 16-bit PCM samples.
     * @param samples interleaved stereo ShortArray [L, R, L, R, ...]
     * @param count number of short values to write
     */
    fun writeBlock(samples: ShortArray, count: Int) {
        val f = file ?: return

        val buf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            buf.putShort(samples[i])
        }
        f.write(buf.array())
        dataBytes += count * 2
    }

    /**
     * Patches the RIFF and data chunk sizes, then closes the file.
     */
    fun close() {
        val f = file ?: return

        // Patch data chunk size at byte offset 40
        f.seek(40)
        val sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        sizeBuf.putInt(dataBytes)
        f.write(sizeBuf.array())

        // Patch RIFF chunk size at byte offset 4 (= dataBytes + 36)
        f.seek(4)
        sizeBuf.clear()
        sizeBuf.putInt(dataBytes + 36)
        f.write(sizeBuf.array())

        f.close()
        file = null
    }
}
