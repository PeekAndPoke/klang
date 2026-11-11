/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.random.Random

/**
 * The 16-bit edge, [pcm16] and [writePcm16]: what the JVM `SourceDataLine` player and the WAV writer
 * get from the engine's floating-point output. Since KLANG-00432 (audit L8-06, maintainer 2026-11-11) it rounds to
 * nearest, a half away from zero; until then it truncated toward zero (the 16-bit rule of the engine before its
 * output became floating point, KLANG-00280 (float output)), which this spec pinned bit for bit.
 *
 * The oracle is the rounding rule, restated here on its own (`sign * floor(|x| * 32767 + 0.5)`), plus a table of
 * literal values: in `(-1, 1]` scale by 32767 and round, above 1 it is 32767, anything else -32768.
 */
class Pcm16EdgeSpec : StringSpec({

    /** The rounding rule, written out without the edge's own arithmetic: nearest, a half away from zero. */
    fun roundingRule(sample: Double): Int =
        if (sample > -1.0 && sample <= 1.0) {
            val magnitude = floor(abs(sample) * 32767.0 + 0.5).toInt()

            if (sample < 0.0) -magnitude else magnitude
        } else if (sample > 1.0) {
            32767
        } else {
            -32768
        }

    "the literal table: mix sample to 16-bit value, through the clip and the edge" {
        val table = listOf(
            0.0 to 0,
            -0.0 to 0,
            1e-6 to 0,
            -1e-6 to 0,
            // 3.2767 and 1.6383 (a truncation said 3 and 1 alike; rounding tells 1.6 from 1.4)
            1e-4 to 3,
            -1e-4 to -3,
            5e-5 to 2,
            -5e-5 to -2,
            // A tie: 16383.5 rounds away from zero (truncation said 16383)
            0.5 to 16384,
            -0.5 to -16384,
            0.999 to 32734,
            -0.999 to -32734,
            1.0 to 32767,
            // 32766.99999...: rounding says 32767 (truncation said 32766)
            1.0.nextDown() to 32767,
            1.0.nextUp() to 32767,
            (-1.0).nextUp() to -32767,
            -1.0 to -32768,
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
                roundingRule(sample) shouldBe expected
            }
        }
    }

    "clip then edge equals the rounding rule on a dense sweep and on random values" {
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

        for (sample in samples) {
            if (pcm16(clipSample(sample)) != roundingRule(sample)) {
                mismatches++
            }
        }

        withClue("checked ${samples.size} samples") {
            mismatches shouldBe 0
        }
    }

    "the error is at most half a step, and it does not follow the sign" {
        val rng = Random(20261111)
        var worst = 0.0
        var sum = 0.0
        val count = 100_000

        repeat(count) {
            val sample = rng.nextDouble(-0.99, 0.99)
            val error = pcm16(sample) - sample * 32767.0

            worst = max(worst, abs(error))
            // Folded by the sign: a truncation's error points toward zero, so this sum would be about -count / 2
            sum += if (sample < 0.0) -error else error
        }

        withClue("worst error $worst steps") {
            (worst <= 0.5).shouldBeTrue()
        }

        withClue("mean error toward the sign ${sum / count} steps") {
            (abs(sum / count) < 0.01).shouldBeTrue()
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
            0x00, 0x40, 0x00, 0xC0, // L  16384, R -16384
            0x00, 0x00, 0xFD, 0xFF, // L      0, R     -3
            0x55, 0x55, 0x55, 0x55, // untouched
        )

        bytes.map { it.toInt() and 0xFF } shouldBe expected
    }
})
