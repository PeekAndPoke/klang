/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.random.Random

/**
 * The 16-bit edge, [pcm16] and [writePcm16]: what the JVM `SourceDataLine` player and the WAV writer
 * get from the engine's floating-point output. Until 2026-10-07 the engine itself quantised its output
 * to 16 bits in `MasterStage`; the edge must produce the very same integers from the clipped floats, so
 * a WAV render's bytes do not move (`docs/tasks-archive/2026-10/20261007-float-output.md`).
 *
 * The oracle is the retired rule, restated here on its own, plus a table of literal values: in
 * `[-1, 1]` scale by 32767 and truncate toward zero, above 1 it is 32767, anything else -32768.
 */
class Pcm16EdgeSpec : StringSpec({

    /** The retired 16-bit rule of `MasterStage` (before 2026-10-07), as an Int. */
    fun retiredRule(sample: Double): Int =
        if (sample >= -1.0 && sample <= 1.0) {
            (sample * 32767.0).toInt()
        } else if (sample > 1.0) {
            32767
        } else {
            -32768
        }

    // The one value the edge cannot reproduce: the retired rule put a mix sample of exactly -1.0 on
    // -32767 and everything below it on -32768, and the float clip writes -1.0 for both.
    val exactlyMinusOne = -1.0

    "the literal table: mix sample to 16-bit value, through the clip and the edge" {
        val table = listOf(
            0.0 to 0,
            -0.0 to 0,
            1e-6 to 0,
            -1e-6 to 0,
            1e-4 to 3,
            -1e-4 to -3,
            0.5 to 16383,
            -0.5 to -16383,
            0.999 to 32734,
            -0.999 to -32734,
            1.0 to 32767,
            1.0.nextDown() to 32766,
            1.0.nextUp() to 32767,
            (-1.0).nextUp() to -32766,
            (-1.0).nextDown() to -32768,
            1.0001 to 32767,
            -1.0001 to -32768,
            2.0 to 32767,
            -2.0 to -32768,
            100.0 to 32767,
            -100.0 to -32768,
            1e300 to 32767,
            -1e300 to -32768,
            Double.POSITIVE_INFINITY to 32767,
            Double.NEGATIVE_INFINITY to -32768,
            Double.NaN to -32768,
        )

        for ((sample, expected) in table) {
            withClue("sample $sample") {
                pcm16(clipSample(sample)) shouldBe expected
                retiredRule(sample) shouldBe expected
            }
        }
    }

    "exactly -1.0 is the one documented move: one count lower than before, the same as every clip below it" {
        retiredRule(exactlyMinusOne) shouldBe -32767
        pcm16(clipSample(exactlyMinusOne)) shouldBe -32768
        pcm16(clipSample((-1.0).nextDown())) shouldBe -32768
    }

    "clip then edge equals the retired rule on a dense sweep and on random values, -1.0 aside" {
        val samples = ArrayList<Double>()

        // Every 1e-4 from -1.5 to 1.5, so both boundaries and both clamp ranges are crossed.
        for (i in -15_000..15_000) {
            samples.add(i / 10_000.0)
        }

        val rng = Random(20261007)

        repeat(20_000) {
            samples.add(rng.nextDouble(-3.0, 3.0))
        }

        repeat(2_000) {
            samples.add(rng.nextDouble(-1e-3, 1e-3))
        }

        var mismatches = 0
        var checked = 0

        for (sample in samples) {
            if (sample == exactlyMinusOne) {
                continue
            }

            checked++

            if (pcm16(clipSample(sample)) != retiredRule(sample)) {
                mismatches++
            }
        }

        withClue("checked $checked samples") {
            mismatches shouldBe 0
        }
    }

    "writePcm16 writes interleaved little-endian stereo, the WAV and SourceDataLine layout, and nothing past it" {
        val frames = 4
        val source = StereoBuffer(frames)

        doubleArrayOf(1.0, -1.0, 0.5, 0.0).copyInto(source.left)
        doubleArrayOf(-1.5, 2.0, -0.5, -1e-4).copyInto(source.right)

        val sentinel = 0x55.toByte()
        val bytes = ByteArray(frames * 4 + 4) { sentinel }

        writePcm16(source = source, frames = frames, bytes = bytes)

        val expected = listOf(
            0xFF, 0x7F, 0x00, 0x80, // L  32767, R -32768
            0x00, 0x80, 0xFF, 0x7F, // L -32768, R  32767
            0xFF, 0x3F, 0x01, 0xC0, // L  16383, R -16383
            0x00, 0x00, 0xFD, 0xFF, // L      0, R     -3
            0x55, 0x55, 0x55, 0x55, // untouched
        )

        bytes.map { it.toInt() and 0xFF } shouldBe expected
    }
})
