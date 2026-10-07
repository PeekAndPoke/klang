/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_engine.cli.WavFileWriter
import java.io.File

/**
 * The WAV writer's bytes, pinned literally: the 44-byte header (its 16-bit fields written as two bytes
 * from an Int since 2026-10-07, where they were `ByteBuffer.putShort` before) and the data chunk the
 * 16-bit edge (`writePcm16`) writes from the engine's floating-point blocks.
 */
class WavFileWriterSpec : StringSpec({

    "a two-block file: the header and the data chunk, byte for byte" {
        val file = File.createTempFile("wav-writer-spec", ".wav")

        try {
            val writer = WavFileWriter(file.path, sampleRate = 48_000)
            val block = StereoBuffer(2)

            writer.open()

            doubleArrayOf(1.0, -0.5).copyInto(block.left)
            doubleArrayOf(-1.0, 1e-4).copyInto(block.right)
            writer.writeBlock(block, frames = 2)

            // Only the first frame of the second block is written.
            doubleArrayOf(0.5, 0.25).copyInto(block.left)
            doubleArrayOf(-1e-4, 0.25).copyInto(block.right)
            writer.writeBlock(block, frames = 1)

            writer.close()

            val expected = listOf(
                // RIFF chunk: "RIFF", size 36 + 12 data bytes = 48, "WAVE"
                0x52, 0x49, 0x46, 0x46, 0x30, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45,
                // "fmt ", chunk size 16, format 1 (PCM), 2 channels
                0x66, 0x6D, 0x74, 0x20, 0x10, 0x00, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00,
                // sample rate 48000, byte rate 192000
                0x80, 0xBB, 0x00, 0x00, 0x00, 0xEE, 0x02, 0x00,
                // block align 4, 16 bits per sample
                0x04, 0x00, 0x10, 0x00,
                // "data", size 12
                0x64, 0x61, 0x74, 0x61, 0x0C, 0x00, 0x00, 0x00,
                // frame 0: L 32767, R -32768
                0xFF, 0x7F, 0x00, 0x80,
                // frame 1: L -16383, R 3
                0x01, 0xC0, 0x03, 0x00,
                // frame 2: L 16383, R -3
                0xFF, 0x3F, 0xFD, 0xFF,
            )

            file.readBytes().map { it.toInt() and 0xFF } shouldBe expected
        } finally {
            file.delete()
        }
    }
})
