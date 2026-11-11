/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * **The browser decodes a stereo file to the mean of its channels, as the JVM does** (KLANG-00430, audit findings L3-13
 * and L9-07). It used to keep channel 0 only: a sample whose sound sat on the right was silent in the browser and at half
 * level offline. The files are 16-bit WAVs at 48 kHz, the decoder's own rate, so the browser does not resample them; it
 * scales an integer by its own rule (Chrome divides a positive one by 32767), so the values are read to 1e-3.
 */
class BrowserAudioDecoderSpec : StringSpec({

    /** A 16-bit PCM WAV of [frames] frames at 48 kHz whose channel c holds the integer [levels]`[c]` on every frame. */
    fun wav(levels: IntArray, frames: Int): ByteArray {
        val channels = levels.size
        val dataBytes = frames * channels * 2
        val out = ByteArray(44 + dataBytes)
        var at = 0

        fun ascii(text: String) {
            for (ch in text) {
                out[at++] = ch.code.toByte()
            }
        }

        fun u32(v: Int) {
            for (i in 0 until 4) {
                out[at++] = ((v shr (8 * i)) and 0xFF).toByte()
            }
        }

        fun u16(v: Int) {
            out[at++] = (v and 0xFF).toByte()
            out[at++] = ((v shr 8) and 0xFF).toByte()
        }

        ascii("RIFF")
        u32(36 + dataBytes)
        ascii("WAVE")
        ascii("fmt ")
        u32(16)
        u16(1)
        u16(channels)
        u32(48000)
        u32(48000 * channels * 2)
        u16(channels * 2)
        u16(16)
        ascii("data")
        u32(dataBytes)

        repeat(frames) {
            for (level in levels) {
                u16(level)
            }
        }

        return out
    }

    "a stereo file decodes to the mean of its two channels, the right channel included" {
        val decoder = BrowserAudioDecoder()

        for ((levels, want) in listOf(
            intArrayOf(16384, 8192) to 0.375, // left 0.5, right 0.25
            intArrayOf(0, 16384) to 0.25, // the sound on the right only: it used to decode to silence
            intArrayOf(8192) to 0.25, // mono stays as it is
        )) {
            val pcm = decoder.decodeMonoFloatPcm(wav(levels = levels, frames = 256)).shouldNotBeNull()

            withClue("channels ${levels.joinToString()}") {
                pcm.sampleRate shouldBe 48000
                pcm.pcm.size shouldBe 256

                for (i in pcm.pcm.indices) {
                    pcm.pcm[i] shouldBe (want plusOrMinus 1e-3)
                }
            }
        }
    }
})
