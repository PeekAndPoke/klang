/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine.cli

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.writePcm16
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams the engine's stereo output to a 16-bit PCM WAV file.
 *
 * Writes a placeholder header on open, appends PCM blocks during rendering,
 * then patches the RIFF/data chunk sizes on close. The 16-bit conversion is
 * [writePcm16], the one home of it.
 */
class WavFileWriter(
    private val filePath: String,
    private val sampleRate: Int,
) {
    private companion object {
        const val CHANNELS = 2
        const val BITS_PER_SAMPLE = 16
        const val BYTES_PER_FRAME = CHANNELS * BITS_PER_SAMPLE / 8
    }

    private var file: RandomAccessFile? = null
    private var dataBytes = 0
    private var blockBytes = ByteArray(0)

    fun open() {
        val f = RandomAccessFile(filePath, "rw")
        f.setLength(0)
        file = f

        val byteRate = sampleRate * BYTES_PER_FRAME

        // Write 44-byte WAV header with placeholder sizes
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        header.put("RIFF".toByteArray())
        header.putInt(0)  // placeholder: file size - 8
        header.put("WAVE".toByteArray())

        // fmt sub-chunk
        header.put("fmt ".toByteArray())
        header.putInt(16)                    // PCM format chunk size
        putUInt16(header, 1)                 // audio format: PCM
        putUInt16(header, CHANNELS)
        header.putInt(sampleRate)
        header.putInt(byteRate)
        putUInt16(header, BYTES_PER_FRAME)   // block align
        putUInt16(header, BITS_PER_SAMPLE)

        // data sub-chunk
        header.put("data".toByteArray())
        header.putInt(0)  // placeholder: data size

        f.write(header.array())
    }

    /**
     * Write the first [frames] frames of [out], the engine's floating-point stereo block, as
     * interleaved 16-bit little-endian PCM.
     */
    fun writeBlock(out: StereoBuffer, frames: Int) {
        val f = file ?: return
        val needed = frames * BYTES_PER_FRAME

        // Reuse the byte buffer across blocks to avoid per-block allocation
        if (blockBytes.size < needed) {
            blockBytes = ByteArray(needed)
        }

        writePcm16(source = out, frames = frames, bytes = blockBytes)
        f.write(blockBytes, 0, needed)
        dataBytes += needed
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

    /** A 16-bit header field, little-endian, written as two bytes from an [Int]. */
    private fun putUInt16(buf: ByteBuffer, value: Int) {
        buf.put((value and 0xFF).toByte())
        buf.put((value shr 8 and 0xFF).toByte())
    }
}
