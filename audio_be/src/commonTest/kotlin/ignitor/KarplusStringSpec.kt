/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A stream that serves `0.0, 0.01, 0.02, ...` from [nextDouble] and counts the draws; every other draw fails, so an
 * excitation that drew anything else would show.
 */
private class CountingRandom : Random() {
    var draws: Int = 0

    override fun nextBits(bitCount: Int): Int = error("only nextDouble is expected")

    override fun nextDouble(): Double = (draws++) * 0.01
}

/**
 * The string core of both pluck nodes (engine tidy-up step 11). The node renders are pinned bit for bit elsewhere
 * (`BuiltInVoiceMatrixSpec`, the pluck rows) and their pitch by `PluckPitchSpec`; these rows pin the core's own
 * contract: the excitation's draws, its pick comb, its level and its DC-free sum, the filter state a re-pluck keeps, the plain write at gain 1,
 * and the loop delay taken out of the read.
 */
class KarplusStringSpec : StringSpec({

    "the pick draws one value per sample of the string, in index order, and writes the pick comb of them (L3-08)" {
        // (line delay, pick position) -> (string length L, comb distance d), worked out by hand from the law:
        // L = lineDelay clamped to the line, whole; d = max(1, (0.5 * pp * L + 0.5).toInt()), pp clamped to 0..1.
        // The line holds y[j] = (x[j] - x[(j - d) mod L]) / sqrt(2), x the draws; written here as a plain modular
        // formula, not the cycle walk the class runs. The distances cover one cycle (gcd 1), several (gcd 5, 9) and half
        // the string (gcd 50).
        val cases = listOf(
            Triple(100.0, 0.0, Pair(100, 1)),
            Triple(100.0, 0.28, Pair(100, 14)),
            Triple(100.0, 0.3, Pair(100, 15)),
            Triple(100.0, 1.0, Pair(100, 50)),
            Triple(37.9, 0.5, Pair(37, 9)),
            Triple(36.0, 0.5, Pair(36, 9)),
            Triple(100.0, -3.0, Pair(100, 1)),
            Triple(100.0, 7.0, Pair(100, 50)),
            Triple(100.0, Double.NaN, Pair(100, 1)),
            Triple(0.5, 1.0, Pair(2, 1)),
        )

        for ((lineDelay, pick, expected) in cases) {
            val (len, distance) = expected
            val rng = CountingRandom()
            val s = KarplusString()

            s.delayLine.fill(9.0)
            s.excite(lineDelay = lineDelay, pickPos = pick, rng = rng)

            rng.draws shouldBe len
            s.writePos shouldBe len

            val x = DoubleArray(len) { j -> j * 0.01 * 2.0 - 1.0 }

            for (j in 0 until KarplusString.MAX_DELAY) {
                val want = if (j >= len) 9.0 else (x[j] - x[(j - distance).mod(len)]) / sqrt(2.0)

                withClue("line delay $lineDelay, pick $pick, index $j") {
                    s.delayLine[j] shouldBe (want plusOrMinus 1e-12)
                }
            }
        }
    }

    "the pick position shapes the spectrum: every harmonic is the noise's times the comb 2 sin(pi k d / L) / sqrt(2) (L3-08)" {
        // The default 0.28 on a string of 100 picks at d = 14: the 7th harmonic keeps 0.09 of the noise's level
        // (2 sin(0.98 pi) / sqrt(2)), the first one the comb takes nearly out; 1.0 (d = 50) takes out every even one.
        val len = 100

        for (pick in listOf(0.0, 0.28, 0.5, 1.0)) {
            val distance = maxOf(1, (0.5 * pick * len + 0.5).toInt())
            val s = KarplusString()
            val draws = Random(17)
            val x = DoubleArray(len) { draws.nextDouble() * 2.0 - 1.0 }

            s.excite(lineDelay = len.toDouble(), pickPos = pick, rng = Random(17))

            for (k in 1 until len / 2) {
                var xr = 0.0
                var xi = 0.0
                var yr = 0.0
                var yi = 0.0

                for (j in 0 until len) {
                    val a = 2.0 * PI * k * j / len

                    xr += x[j] * cos(a)
                    xi -= x[j] * sin(a)
                    yr += s.delayLine[j] * cos(a)
                    yi -= s.delayLine[j] * sin(a)
                }

                val comb = 2.0 * abs(sin(PI * k * distance / len)) / sqrt(2.0)
                val want = sqrt(xr * xr + xi * xi) * comb

                withClue("pick $pick, harmonic $k") {
                    sqrt(yr * yr + yi * yi) shouldBe (want plusOrMinus 1e-9)
                }
            }
        }
    }

    "the level stays the same at every pick position: the comb's 1 / sqrt(2) holds the noise's power (L3-08)" {
        // The burst knob it replaced moved the level by about 10 dB (a short burst carries less energy). Mean energy per
        // sample over 400 seeds, against the plain noise's 1/3; within 0.25 dB at every position.
        val len = 436

        for (pick in listOf(0.0, 0.1, 0.28, 0.5, 0.77, 1.0)) {
            var energy = 0.0

            for (seed in 1..400) {
                val s = KarplusString()

                s.excite(lineDelay = len.toDouble(), pickPos = pick, rng = Random(seed))

                for (j in 0 until len) {
                    energy += s.delayLine[j] * s.delayLine[j]
                }
            }

            val perSample = energy / (400.0 * len)
            val db = 10.0 * log10(perSample * 3.0)

            withClue("pick $pick: the level re plain noise, $db dB") {
                abs(db) shouldBeLessThan 0.25
            }
        }
    }

    "the excitation carries no DC: the line's excited samples sum to zero, whatever the pick and the seed (L3-06)" {
        for (seed in 1..8) {
            for (pick in listOf(0.0, 0.25, 0.5, 1.0)) {
                for (lineDelay in listOf(21.7, 109.1, 436.4)) {
                    val s = KarplusString()

                    s.excite(lineDelay = lineDelay, pickPos = pick, rng = Random(seed))

                    var sum = 0.0
                    var energy = 0.0

                    for (j in 0 until s.writePos) {
                        sum += s.delayLine[j]
                        energy += s.delayLine[j] * s.delayLine[j]
                    }

                    withClue("seed $seed, pick $pick, line delay $lineDelay: the excitation is not silent") {
                        energy shouldBeGreaterThan 1e-6
                    }

                    withClue("seed $seed, pick $pick, line delay $lineDelay: the sum of the excitation") {
                        abs(sum) shouldBeLessThan 1e-12
                    }
                }
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

        s.excite(lineDelay = 120.0, pickPos = 0.3, rng = Random(5))

        s.lpState.toRawBits() shouldBe 0.123.toRawBits()
        s.apPrevIn.toRawBits() shouldBe (-0.4).toRawBits()
        s.apPrevOut.toRawBits() shouldBe 0.7.toRawBits()
        s.excited shouldBe true
        s.writePos shouldBe 120
    }

    "gain 1 without accumulation writes the raw sample, bit for bit; with accumulation it adds sample times gain" {
        // An integer delay of 100 reads the burst back unchanged for the first 100 samples (frac 0, and the
        // write-back lands from index 100 on), so the raw sample is the delay line as the burst left it.
        fun plucked(): KarplusString = KarplusString().apply { excite(lineDelay = 100.0, pickPos = 1.0, rng = Random(42)) }

        val plain = plucked()
        val snapshot = plain.delayLine.copyOf()
        val buf = AudioBuffer(128) { -777.0 }

        plain.render(
            buffer = buf, from = 3, to = 103, baseDelay = 100.0, phaseMod = null,
            hasDrift = false, driftStart = 1.0, driftStep = 0.0, loopDelay = 0.0,
            lpAlpha = KarplusString.lpAlphaOf(0.5), hasStiffness = KarplusString.hasStiffnessOf(0.3),
            apCoeff = KarplusString.apCoeffOf(0.3), feedback = 0.99, gain = 1.0, accumulate = false,
        )

        for (i in 0 until 128) {
            val want = if (i in 3 until 103) snapshot[i - 3] else -777.0

            buf[i].toRawBits() shouldBe want.toRawBits()
        }

        val summed = plucked()
        val acc = AudioBuffer(128) { 0.25 }

        summed.render(
            buffer = acc, from = 3, to = 103, baseDelay = 100.0, phaseMod = null,
            hasDrift = false, driftStart = 1.0, driftStep = 0.0, loopDelay = 0.0,
            lpAlpha = KarplusString.lpAlphaOf(0.5), hasStiffness = KarplusString.hasStiffnessOf(0.3),
            apCoeff = KarplusString.apCoeffOf(0.3), feedback = 0.99, gain = 0.5, accumulate = true,
        )

        for (i in 0 until 128) {
            val want = if (i in 3 until 103) 0.25 + snapshot[i - 3] * 0.5 else 0.25

            acc[i].toRawBits() shouldBe want.toRawBits()
        }
    }

    "the read delay is the base delay less the loop delay: the loop filters' delay is taken out of the line (L3-01)" {
        // A line delay of 97 = 100 - 3: the string reads its burst back unchanged for the first 97 samples (frac 0, the
        // write-back lands from index 97 on). Read at the base delay alone, it would read 3 samples of the line before
        // the burst first.
        val s = KarplusString().apply { excite(lineDelay = 97.0, pickPos = 1.0, rng = Random(42)) }
        val snapshot = s.delayLine.copyOf()
        val buf = AudioBuffer(128)

        s.render(
            buffer = buf, from = 0, to = 97, baseDelay = 100.0, phaseMod = null,
            hasDrift = false, driftStart = 1.0, driftStep = 0.0, loopDelay = 3.0,
            lpAlpha = KarplusString.lpAlphaOf(0.5), hasStiffness = false,
            apCoeff = 0.0, feedback = 0.99, gain = 1.0, accumulate = false,
        )

        for (i in 0 until 97) {
            withClue("the read at base delay 100 less loop delay 3, sample $i") {
                buf[i].toRawBits() shouldBe snapshot[i].toRawBits()
            }
        }
    }

    "a read between frames is the cubic Lagrange interpolation of the four frames around it (L3-07)" {
        // Read delay 100.3 on a plucked line: the read position `wp - 100.3` sits 0.7 past a frame. The oracle is the
        // Lagrange product formula over the frames base - 1 .. base + 2 of the line as the burst left it (the write-back
        // lands from index 100 on, ahead of every read here). A linear read misses it by up to the burst's own steps.
        val s = KarplusString().apply { excite(lineDelay = 100.0, pickPos = 1.0, rng = Random(42)) }
        val line = s.delayLine.copyOf()
        val buf = AudioBuffer(128)

        s.render(
            buffer = buf, from = 0, to = 90, baseDelay = 100.3, phaseMod = null,
            hasDrift = false, driftStart = 1.0, driftStep = 0.0, loopDelay = 0.0,
            lpAlpha = KarplusString.lpAlphaOf(0.5), hasStiffness = false,
            apCoeff = 0.0, feedback = 0.99, gain = 1.0, accumulate = false,
        )

        for (i in 1 until 90) {
            val pos = 100 + i - 100.3
            val base = floor(pos).toInt()
            val t = pos - base
            var want = 0.0

            for (k in -1..2) {
                var w = 1.0

                for (j in -1..2) {
                    if (j != k) {
                        w *= (t - j) / (k - j)
                    }
                }

                want += w * line[(base + k + line.size) % line.size]
            }

            withClue("sample $i, read position $pos") {
                buf[i] shouldBe (want plusOrMinus 1e-12)
            }
        }
    }

    "loopDelayOf puts the loop's pole at the note: on the angle of the note, at the radius where the loop gain is 1" {
        // The oracle is the loop's defining condition, solved another way than the engine's fixed point: with the read
        // delay `period - loopDelay` = M + d, the loop `feedback * H(z) * R(z) * z^-M` has a pole at z = r e^(jw) when its
        // gain there is 1 (r found by bisection) and its phase a whole number of turns. R is the cubic Lagrange read
        // (KLANG-00430, L3-07): the weights of the delay 1 + d over taps 0 to 3, taken here from the product formula,
        // on z^1, z^0, z^-1 and z^-2. The phase left over, as cents of the pole's angle (the loop's group delay is about
        // one period), must be under 0.2.
        fun residualCents(period: Double, brightness: Double, stiffness: Double, feedback: Double): Double {
            val alpha = KarplusString.lpAlphaOf(brightness)
            val hasStiffness = KarplusString.hasStiffnessOf(stiffness)
            val a = KarplusString.apCoeffOf(stiffness)
            val readDelay = period - KarplusString.loopDelayOf(
                period = period, lpAlpha = alpha, hasStiffness = hasStiffness, apCoeff = a, feedback = feedback,
            )
            val whole = floor(readDelay)
            val d = readDelay - whole
            val w = 2.0 * PI / period

            // (log gain, phase) of everything but z^-M, at radius r.
            fun rest(r: Double): Pair<Double, Double> {
                val zr = cos(w) / r
                val zi = -sin(w) / r
                var logGain = ln(abs(feedback) * alpha) - 0.5 * ln((1.0 - (1.0 - alpha) * zr).pow(2) + ((1.0 - alpha) * zi).pow(2))
                var phase = -atan2(-(1.0 - alpha) * zi, 1.0 - (1.0 - alpha) * zr)

                if (hasStiffness) {
                    logGain += 0.5 * ln((a + zr).pow(2) + zi.pow(2)) - 0.5 * ln((1.0 + a * zr).pow(2) + (a * zi).pow(2))
                    phase += atan2(zi, a + zr) - atan2(a * zi, 1.0 + a * zr)
                }

                var readRe = 0.0
                var readIm = 0.0

                for (k in 0..3) {
                    var h = 1.0

                    for (j in 0..3) {
                        if (j != k) {
                            h *= (1.0 + d - j) / (k - j)
                        }
                    }

                    // z^-(k - 1) at z = r e^(jw)
                    val n = k - 1

                    readRe += h * r.pow(-n) * cos(n * w)
                    readIm -= h * r.pow(-n) * sin(n * w)
                }

                logGain += 0.5 * ln(readRe * readRe + readIm * readIm)
                phase += atan2(readIm, readRe)

                return logGain to phase
            }

            // |loop| = 1: M ln r = log gain of the rest; increasing in r over (0.5, 1.5) for these loops.
            var lo = 0.5
            var hi = 1.5

            repeat(200) {
                val mid = 0.5 * (lo + hi)

                if (whole * ln(mid) - rest(mid).first < 0.0) {
                    lo = mid
                } else {
                    hi = mid
                }
            }

            val total = rest(0.5 * (lo + hi)).second - w * whole
            val turns = total / (2.0 * PI)
            val leftover = (turns - round(turns)) * 2.0 * PI

            return 1200.0 / ln(2.0) * (leftover / period) / w
        }

        for (period in listOf(872.73, 436.36, 218.18, 109.09, 54.55, 45.87, 22.93)) {
            for (brightness in listOf(0.1, 0.2, 0.5, 1.0)) {
                for (stiffness in listOf(0.0, 0.01, 0.5, 0.99)) {
                    for (feedback in listOf(0.996, 0.9)) {
                        withClue("period $period, brightness $brightness, stiffness $stiffness, feedback $feedback") {
                            abs(residualCents(period, brightness, stiffness, feedback)) shouldBeLessThan 0.2
                        }
                    }
                }
            }
        }
    }

    "loopDelayOf: the filters' DC delays at a long period, the pole's radius at a dark A4, and a NaN reads as 0" {
        // The longest period: nearly the one-pole's (1 - alpha) / alpha plus the allpass's (1 - a) / (1 + a), 1 + 0.6.
        KarplusString.loopDelayOf(period = 2499.0, lpAlpha = 0.5, hasStiffness = true, apCoeff = 0.25, feedback = 1.0) shouldBe
            (1.6 plusOrMinus 1e-4)
        // A4 at alpha 0.01: about 30.5 samples, where the one-pole's phase delay on the unit circle is 23.8 and its delay
        // at DC 99. (Values of the loop model, KLANG-00426.)
        KarplusString.loopDelayOf(period = 48000.0 / 440.0, lpAlpha = 0.01, hasStiffness = false, apCoeff = 0.0, feedback = 0.996) shouldBe
            (30.478 plusOrMinus 0.01)
        KarplusString.loopDelayOf(period = Double.NaN, lpAlpha = 0.5, hasStiffness = false, apCoeff = 0.0, feedback = 0.996) shouldBe 0.0
        KarplusString.loopDelayOf(period = 100.0, lpAlpha = Double.NaN, hasStiffness = false, apCoeff = 0.0, feedback = 0.996) shouldBe 0.0
    }
})
