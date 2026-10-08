/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/**
 * A stream that serves `0.0, 0.01, 0.02, ...` from [nextDouble] and counts the draws; every other draw fails, so a
 * burst that drew anything else would show.
 */
private class CountingRandom : Random() {
    var draws: Int = 0

    override fun nextBits(bitCount: Int): Int = error("only nextDouble is expected")

    override fun nextDouble(): Double = (draws++) * 0.01
}

/**
 * The string core of both pluck nodes (engine tidy-up step 11). The node renders are pinned bit for bit elsewhere
 * (`BuiltInVoiceMatrixSpec`, the pluck rows); these rows pin the core's own contract: the burst's draws, the filter
 * state a re-pluck keeps, and the plain write at gain 1.
 */
class KarplusStringSpec : StringSpec({

    "the burst draws exactly burstLen values, in index order, and writes zeros around it" {
        // (base delay, pick position) -> (delay length, burst length, burst start), worked out by hand from the law:
        // burstLen = max(1, (delayLen * (0.1 + 0.9 * pp)).toInt()), burstStart = ((delayLen - burstLen) * pp).toInt().
        val cases = listOf(
            Triple(100.0, 0.0, Triple(100, 10, 0)),
            Triple(100.0, 0.3, Triple(100, 37, 18)),
            Triple(100.0, 1.0, Triple(100, 100, 0)),
            Triple(37.9, 0.5, Triple(37, 20, 8)),
            Triple(100.0, -3.0, Triple(100, 10, 0)),
            Triple(100.0, 7.0, Triple(100, 100, 0)),
        )

        for ((baseDelay, pick, expected) in cases) {
            val (delayLen, burstLen, burstStart) = expected
            val rng = CountingRandom()
            val s = KarplusString()

            s.delayLine.fill(9.0)
            s.excite(baseDelay = baseDelay, pickPos = pick, rng = rng)

            rng.draws shouldBe burstLen
            s.writePos shouldBe delayLen

            for (j in 0 until KarplusString.MAX_DELAY) {
                val want = when {
                    j >= delayLen -> 9.0
                    j < burstStart || j >= burstStart + burstLen -> 0.0
                    else -> (j - burstStart) * 0.01 * 2.0 - 1.0
                }

                s.delayLine[j].toRawBits() shouldBe want.toRawBits()
            }
        }
    }

    "excite keeps the filter state and leaves the excited flag set: a re-plucked string carries them over" {
        val s = KarplusString()

        s.lpState = 0.123
        s.apPrevIn = -0.4
        s.apPrevOut = 0.7
        s.excited = true
        s.writePos = 1234

        s.excite(baseDelay = 120.0, pickPos = 0.3, rng = Random(5))

        s.lpState.toRawBits() shouldBe 0.123.toRawBits()
        s.apPrevIn.toRawBits() shouldBe (-0.4).toRawBits()
        s.apPrevOut.toRawBits() shouldBe 0.7.toRawBits()
        s.excited shouldBe true
        s.writePos shouldBe 120
    }

    "gain 1 without accumulation writes the raw sample, bit for bit; with accumulation it adds sample times gain" {
        // An integer delay of 100 reads the burst back unchanged for the first 100 samples (frac 0, and the
        // write-back lands from index 100 on), so the raw sample is the delay line as the burst left it.
        fun plucked(): KarplusString = KarplusString().apply { excite(baseDelay = 100.0, pickPos = 1.0, rng = Random(42)) }

        val plain = plucked()
        val snapshot = plain.delayLine.copyOf()
        val buf = AudioBuffer(128) { -777.0 }

        plain.render(
            buffer = buf, from = 3, to = 103, baseDelay = 100.0, phaseMod = null,
            hasDrift = false, driftStart = 1.0, driftStep = 0.0,
            lpAlpha = KarplusString.lpAlphaOf(0.5), hasStiffness = KarplusString.hasStiffnessOf(0.3),
            apCoeff = KarplusString.apCoeffOf(0.3), decay = 0.99, gain = 1.0, accumulate = false,
        )

        for (i in 0 until 128) {
            val want = if (i in 3 until 103) snapshot[i - 3] else -777.0

            buf[i].toRawBits() shouldBe want.toRawBits()
        }

        val summed = plucked()
        val acc = AudioBuffer(128) { 0.25 }

        summed.render(
            buffer = acc, from = 3, to = 103, baseDelay = 100.0, phaseMod = null,
            hasDrift = false, driftStart = 1.0, driftStep = 0.0,
            lpAlpha = KarplusString.lpAlphaOf(0.5), hasStiffness = KarplusString.hasStiffnessOf(0.3),
            apCoeff = KarplusString.apCoeffOf(0.3), decay = 0.99, gain = 0.5, accumulate = true,
        )

        for (i in 0 until 128) {
            val want = if (i in 3 until 103) 0.25 + snapshot[i - 3] * 0.5 else 0.25

            acc[i].toRawBits() shouldBe want.toRawBits()
        }
    }
})
