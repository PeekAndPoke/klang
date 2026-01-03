package io.peekandpoke.klang.audio_fe.decoders

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object JvmWavDecoder {

    @Throws(RuntimeException::class)
    fun decodeWav(audioBytes: ByteArray): MonoSamplePcm {
        return decodeInternal(audioBytes)
    }

    private fun decodeInternal(bytes: ByteArray): MonoSamplePcm {
        // Use wrap() which is safe (backed by array)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Basic header check is already done by isWav, but let's be strict about size
        if (buffer.remaining() < 12) throw RuntimeException("File too short for WAV header")

        buffer.position(12) // Skip RIFF (4) + Size (4) + WAVE (4)

        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var audioFormat = 0
        var foundFmt = false

        // Find chunks
        while (buffer.hasRemaining()) {
            if (buffer.remaining() < 8) break // Header incomplete

            val chunkId = ByteArray(4)
            buffer.get(chunkId)
            val chunkName = String(chunkId, StandardCharsets.US_ASCII)

            // unsigned int read
            val chunkSize = Integer.toUnsignedLong(buffer.getInt())

            // Security check: chunkSize must be feasible
            if (chunkSize > buffer.remaining()) {
                // If a chunk claims to be bigger than the file, we can't process it fully.
                // If it's 'data', we might process what we have. If it's 'fmt', it's corrupt.
                if (chunkName == "fmt ") throw RuntimeException("Corrupt WAV: fmt chunk truncated")
            }

            if (chunkName == "fmt ") {
                if (chunkSize < 16) throw RuntimeException("Corrupt WAV: fmt chunk too small")

                val startPos = buffer.position()

                audioFormat = buffer.getShort().toInt() and 0xFFFF
                channels = buffer.getShort().toInt() and 0xFFFF
                sampleRate = buffer.getInt()
                buffer.getInt() // byteRate
                buffer.getShort() // blockAlign
                bitsPerSample = buffer.getShort().toInt() and 0xFFFF
                foundFmt = true

                // Safely skip remaining bytes of this chunk
                val endPos = startPos + chunkSize
                if (endPos > buffer.limit()) break
                buffer.position(endPos.toInt())

            } else if (chunkName == "data") {
                // We need fmt to be read before data
                if (!foundFmt) throw RuntimeException("WAV 'data' chunk found before 'fmt ' chunk")

                if (audioFormat != 1 && audioFormat != 3) { // 1 = PCM, 3 = IEEE Float
                    throw RuntimeException("Unsupported WAV format code: $audioFormat")
                }

                // Determine safe size to read
                val available = buffer.remaining().toLong()
                val safeSize = if (chunkSize > available) available else chunkSize

                if (channels <= 0 || bitsPerSample <= 0) throw RuntimeException("Invalid WAV format parameters")

                val bytesPerFrame = (channels * bitsPerSample) / 8
                if (bytesPerFrame == 0) throw RuntimeException("Invalid bytesPerFrame: 0")

                // frames = floor(bytes / bytesPerFrame)
                val frames = (safeSize / bytesPerFrame).toInt()
                if (frames < 0) throw RuntimeException("Invalid frame count")

                val mono = FloatArray(frames)

                for (i in 0 until frames) {
                    var sum = 0.0f

                    repeat(channels) {
                        val sampleVal: Float = when (bitsPerSample) {
                            8 -> {
                                // 8-bit is unsigned 0..255, center at 128
                                val b = buffer.get().toInt() and 0xFF
                                (b - 128) / 128.0f
                            }

                            16 -> {
                                val s = buffer.getShort().toInt()
                                s / 32768.0f
                            }

                            24 -> {
                                val b1 = buffer.get().toInt() and 0xFF
                                val b2 = buffer.get().toInt() and 0xFF
                                val b3 = buffer.get().toInt()
                                val s = (b3 shl 16) or (b2 shl 8) or b1
                                s / 8388608.0f
                            }

                            32 -> {
                                if (audioFormat == 3) {
                                    buffer.getFloat()
                                } else {
                                    val s = buffer.getInt()
                                    s / 2147483648.0f
                                }
                            }

                            else -> {
                                // Skip unknown bit depth bytes to maintain alignment
                                repeat(bitsPerSample / 8) { if (buffer.hasRemaining()) buffer.get() }
                                0.0f
                            }
                        }
                        sum += sampleVal
                    }
                    mono[i] = sum / channels
                }

                // saveDebugWav(mono, sampleRate)

                return MonoSamplePcm(sampleRate, mono)
            } else {
                // Unknown chunk, safely skip it
                val nextPos = buffer.position() + chunkSize
                if (nextPos > buffer.limit() || nextPos < 0) {
                    // Stop if jumping past end of file
                    break
                }
                buffer.position(nextPos.toInt())
            }
        }

        throw RuntimeException("No 'data' chunk found in WAV")
    }
}
