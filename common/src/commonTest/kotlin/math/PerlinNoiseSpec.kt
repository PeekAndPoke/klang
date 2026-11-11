/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

class PerlinNoiseSpec : StringSpec({

    "PerlinNoise: stability - same seed produces same permutation and noise" {
        val noise1 = PerlinNoise(Random(42))
        val noise2 = PerlinNoise(Random(42))

        // Check multiple points across different cycles
        for (i in 0..100) {
            val t = i * 0.123
            noise1.noise(t) shouldBe noise2.noise(t)
        }
    }

    "PerlinNoise: different seeds produce different landscapes" {
        val noise1 = PerlinNoise(Random(101))
        val noise2 = PerlinNoise(Random(202))

        // We try a few points to ensure we aren't just hitting a zero-crossing
        val points = listOf(0.123, 0.456, 0.789)
        val results1 = points.map { noise1.noise(it) }
        val results2 = points.map { noise2.noise(it) }

        results1 shouldNotBe results2

        // Ensure we aren't just getting all zeros
        results1.any { it != 0.0 } shouldBe true
    }

    "PerlinNoise: output range is roughly -1.0 to 1.0" {
        val noise = PerlinNoise(Random(0))
        for (i in 0..1000) {
            val v = noise.noise(i * 0.01)
            // Perlin 1D is typically within -1..1, but we allow a tiny epsilon
            v.shouldBeBetween(-1.0, 1.0, 0.0001)
        }
    }

    "PerlinNoise: smooth transitions" {
        val noise = PerlinNoise(Random(123))
        val v1 = noise.noise(1.0)
        val v2 = noise.noise(1.00001)

        // Differences should be very small for very small time steps
        (v2 - v1).shouldBeBetween(-0.001, 0.001)
    }

    "PerlinNoise: reaches values close to limits" {
        val noise = PerlinNoise(Random(0))
        var min = 0.0
        var max = 0.0

        // Scan a large range to find peaks
        for (i in 0..10000) {
            val v = noise.noise(i * 0.1)
            if (v < min) min = v
            if (v > max) max = v
        }

        // We expect the noise to have significant amplitude
        // It won't necessarily hit exactly -1.0 or 1.0 depending on gradients,
        // but it should definitely go beyond +/- 0.5
        min.shouldBeBetween(-1.0, -0.8, 0.0)
        max.shouldBeBetween(0.8, 1.0, 0.0)
    }

    "PerlinNoise.fbm: octaves<=1 is byte-identical to noise (the perf-neutral default)" {
        val noise = PerlinNoise(Random(7))
        for (i in 0..200) {
            val t = i * 0.137
            noise.fbm(t, octaves = 1, persistence = 0.5) shouldBe noise.noise(t)
            noise.fbm(t, octaves = 0, persistence = 0.5) shouldBe noise.noise(t)
        }
    }

    "PerlinNoise.fbm: bit for bit the two-site form it replaced, at every octave count and on integral, signed-zero and non-finite points" {
        // The form before engine follow-up 39 (one noise call for octaves <= 1, one in the loop), kept here as the
        // reference: the single call site must not move a bit, signed zeros included (the Ignitor and the Katalyst
        // knob both read this law).
        fun reference(noise: PerlinNoise, x: Double, octaves: Int, persistence: Double): Double {
            if (octaves <= 1) {
                return noise.noise(x)
            }

            var sum = 0.0
            var amp = 1.0
            var freq = 1.0
            var norm = 0.0

            for (o in 0 until octaves) {
                sum += amp * noise.noise(x * freq)
                norm += amp
                freq *= 2.0
                amp *= persistence
            }

            return if (norm > 0.0) sum / norm else 0.0
        }

        val noise = PerlinNoise(Random(7))
        val points = (0..255).map { it.toDouble() } + (0..400).map { it * 0.137 - 20.0 } + listOf(
            -0.0, 0.0, -1.0, 1e9, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        )

        var signedZeros = 0

        for (octaves in -1..9) {
            for (persistence in listOf(0.0, 0.5, 0.6, 1.0, -0.5, Double.NaN)) {
                for (p in points) {
                    val got = noise.fbm(p, octaves = octaves, persistence = persistence)
                    val want = reference(noise, p, octaves, persistence)

                    // The same value, a NaN for a NaN, and a zero of the same sign (`1 / -0.0` is `-Infinity`).
                    val same = if (want != want) got != got else got == want && (want != 0.0 || 1.0 / got == 1.0 / want)

                    same shouldBe true

                    if (want == 0.0 && 1.0 / want < 0.0) {
                        signedZeros++
                    }
                }
            }
        }

        // The integral points do reach a negative zero (both gradients negative), so the sign test has teeth.
        signedZeros shouldNotBe 0
    }

    "PerlinNoise.fbm: more octaves changes the output (fractal detail takes effect)" {
        val noise = PerlinNoise(Random(7))
        val single = (0..200).map { noise.fbm(it * 0.137, 1, 0.5) }
        val multi = (0..200).map { noise.fbm(it * 0.137, 4, 0.5) }
        single shouldNotBe multi
    }

    "PerlinNoise.fbm: stays within the noise range (amplitude-normalized)" {
        val noise = PerlinNoise(Random(0))
        for (i in 0..2000) {
            noise.fbm(i * 0.05, octaves = 5, persistence = 0.6).shouldBeBetween(-1.0, 1.0, 0.01)
        }
    }

    // ── No period (KLANG-00430, audit finding L3-09) ─────────────────────────────────────────────────────────────

    "PerlinNoise has no period: 256 lattice units on (and 512, 65536) the curve is another one (L3-09)" {
        // The 256-entry permutation table repeated every curve every 256 units, value for value: 1.78 s at the engine's
        // rate 1. The hashed lattice repeats nowhere; a few points may agree by chance (both gradients the same).
        val noise = PerlinNoise(Random(11))

        for (shift in listOf(256.0, 512.0, 1024.0, 65536.0)) {
            var same = 0

            for (i in 0 until 2000) {
                val x = i * 0.137

                if (noise.noise(x) == noise.noise(x + shift)) {
                    same++
                }
            }

            withClue("shift $shift: $same of 2000 points repeat") {
                (same < 200) shouldBe true
            }
        }
    }

    "PerlinNoise takes one value from its random stream: the seed, one nextInt (it took 255 for the table)" {
        var draws = 0
        val counting = object : Random() {
            override fun nextBits(bitCount: Int): Int {
                draws++
                return 0x2468ACE
            }
        }

        PerlinNoise(counting)

        draws shouldBe 1
    }

    "PerlinNoise is the hashed-lattice law, the same on every host: an oracle in 64-bit arithmetic (L3-09)" {
        // The law written again without the Int multiply's wrap: every product in Long, masked to 32 bits. A host whose
        // Int arithmetic meant something else (a multiply that did not wrap, a sign that did not shift) fails here; the
        // spec runs on the JVM and in JS alike.
        val mask = 0xFFFFFFFFL

        fun mul(a: Long, b: Long): Long = (a * b) and mask

        fun hash(seed: Int, index: Int): Int {
            var h = (seed.toLong() and mask) xor mul(index.toLong() and mask, 0x9E3779B1L)
            h = mul(h xor (h ushr 16), 0x85EBCA6BL)
            h = mul(h xor (h ushr 13), 0xC2B2AE35L)
            return (h xor (h ushr 16)).toInt()
        }

        fun grad(h: Int, x: Double): Double {
            val g = 1.0 + (h and 7)

            return if ((h and 8) != 0) -g * x else g * x
        }

        fun oracle(seed: Int, x: Double): Double {
            val i = floor(x).toInt()
            val f = x - floor(x)
            val u = f * f * f * (f * (f * 6 - 15) + 10)
            val a = grad(hash(seed, i), f)
            val b = grad(hash(seed, i + 1), f - 1.0)

            return (a + u * (b - a)) * 0.25
        }

        for (seed in listOf(1, 42, -7, 123456)) {
            val noise = PerlinNoise(Random(seed))
            val drawn = Random(seed).nextInt()

            for (x in listOf(0.0, 0.3, 1.5, 255.75, 256.25, -3.2, 1.0e6 + 0.4, -2.0e9, 2.1e9)) {
                withClue("seed $seed, x $x") {
                    noise.noise(x) shouldBe (oracle(drawn, x) plusOrMinus 1e-15)
                }
            }
        }
    }

    "PerlinNoise keeps the table's statistics: the RMS of the 16 equally weighted gradients through the fade (L3-09)" {
        // E[res^2] = 25.5 (E[g^2] over +-1 .. +-8) times the mean over the fraction f of (1 - u)^2 f^2 + u^2 (1 - f)^2,
        // the two lattice gradients independent; times 0.25^2. Measured over 200,000 points of one seed, within 3 percent.
        var integral = 0.0
        val steps = 100_000

        for (k in 0 until steps) {
            val f = (k + 0.5) / steps
            val u = f * f * f * (f * (f * 6 - 15) + 10)

            integral += (1 - u) * (1 - u) * f * f + u * u * (1 - f) * (1 - f)
        }

        val want = sqrt(25.5 * (integral / steps) * 0.0625)
        val noise = PerlinNoise(Random(5))
        var sum = 0.0
        var sumSq = 0.0
        val n = 200_000

        for (i in 0 until n) {
            val v = noise.noise(i * 0.0731)

            sum += v
            sumSq += v * v
        }

        val rms = sqrt(sumSq / n)

        withClue("RMS $rms against $want") {
            (abs(rms / want - 1.0) < 0.03) shouldBe true
        }

        withClue("mean ${sum / n}") {
            (abs(sum / n) < 0.01) shouldBe true
        }
    }
})
