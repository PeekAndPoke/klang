/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random

class BerlinNoiseSpec : StringSpec({

    "BerlinNoise: stability - same seed produces same ridges" {
        val noise1 = BerlinNoise(Random(99))
        val noise2 = BerlinNoise(Random(99))

        for (i in 0..100) {
            val t = i * 0.15
            noise1.noise(t) shouldBe noise2.noise(t)
        }
    }

    "BerlinNoise: different seeds produce different ridge heights" {
        val noise1 = BerlinNoise(Random(1))
        val noise2 = BerlinNoise(Random(2))

        noise1.noise(0.75) shouldNotBe noise2.noise(0.75)
    }

    "BerlinNoise: internal cache stability" {
        val noise = BerlinNoise(Random(123))

        val val1 = noise.noise(5.5)
        // Simulate "forgetting" and re-querying or same-instance query
        val val2 = noise.noise(5.5)

        val1 shouldBe val2
    }

    "BerlinNoise: output range is 0.0 to 1.0" {
        val noise = BerlinNoise(Random(42))
        for (i in 0..500) {
            val v = noise.noise(i * 0.1)
            v.shouldBeBetween(0.0, 1.0, 0.0001)
        }
    }

    "BerlinNoise: ridge behavior - height increases between integers" {
        val noise = BerlinNoise(Random(0))

        // At integer boundaries, the height is purely based on the random point
        // In this implementation, interp is: prev + t * (next_top - prev)
        // JS: result / 2.0
        val vAtInt = noise.noise(1.0)
        vAtInt.shouldBeBetween(0.0, 1.0)
    }

    "BerlinNoise: reaches values close to limits" {
        val noise = BerlinNoise(Random(0))
        var min = 1.0
        var max = 0.0

        // Scan a large range to find peaks
        for (i in 0..10000) {
            val v = noise.noise(i * 0.1)
            if (v < min) min = v
            if (v > max) max = v
        }

        // Berlin noise produces values strictly between 0.0 and 1.0
        // We expect it to reach near the bottom (0.0) and near the top (1.0)
        min.shouldBeBetween(0.0, 0.2, 0.0)
        max.shouldBeBetween(0.8, 1.0, 0.0)
    }

    "BerlinNoise.fbm: octaves<=1 is byte-identical to noise (the perf-neutral default)" {
        val noise = BerlinNoise(Random(13))
        for (i in 0..200) {
            val t = i * 0.15
            noise.fbm(t, octaves = 1, persistence = 0.5) shouldBe noise.noise(t)
            noise.fbm(t, octaves = 0, persistence = 0.5) shouldBe noise.noise(t)
        }
    }

    "BerlinNoise.fbm: bit for bit the two-site form it replaced, at every octave count and on integral, signed-zero and non-finite points" {
        // The form before engine follow-up 39 (one noise call for octaves <= 1, one in the loop), kept here as the
        // reference: the single call site must not move a bit (the Ignitor and the Katalyst knob both read this law).
        // The sign test is kept for symmetry with the perlin row; berlin's noise never yields -0.0 (its values are
        // `v0 + p * (v1 - v0)`, and `v0 = (h / MAX + 1) * 0.5` is never -0.0), so here it never fires.
        fun reference(noise: BerlinNoise, t: Double, octaves: Int, persistence: Double): Double {
            if (octaves <= 1) {
                return noise.noise(t)
            }

            var sum = 0.0
            var amp = 1.0
            var freq = 1.0
            var norm = 0.0

            for (o in 0 until octaves) {
                sum += amp * noise.noise(t * freq)
                norm += amp
                freq *= 2.0
                amp *= persistence
            }

            return if (norm > 0.0) sum / norm else 0.0
        }

        val noise = BerlinNoise(Random(13))
        val points = (0..255).map { it.toDouble() } + (0..400).map { it * 0.137 - 20.0 } + listOf(
            -0.0, 0.0, -1.0, 1e9, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        )

        for (octaves in -1..9) {
            for (persistence in listOf(0.0, 0.5, 0.6, 1.0, -0.5, Double.NaN)) {
                for (p in points) {
                    val got = noise.fbm(p, octaves = octaves, persistence = persistence)
                    val want = reference(noise, p, octaves, persistence)

                    // The same value, a NaN for a NaN, and a zero of the same sign (`1 / -0.0` is `-Infinity`).
                    val same = if (want != want) got != got else got == want && (want != 0.0 || 1.0 / got == 1.0 / want)

                    same shouldBe true
                }
            }
        }
    }

    "BerlinNoise.fbm: more octaves changes the output (fractal detail takes effect)" {
        val noise = BerlinNoise(Random(13))
        val single = (0..200).map { noise.fbm(it * 0.15, 1, 0.5) }
        val multi = (0..200).map { noise.fbm(it * 0.15, 4, 0.5) }
        single shouldNotBe multi
    }

    "BerlinNoise.fbm: stays within 0..1 (amplitude-normalized)" {
        val noise = BerlinNoise(Random(42))
        for (i in 0..2000) {
            noise.fbm(i * 0.05, octaves = 5, persistence = 0.6).shouldBeBetween(0.0, 1.0, 0.01)
        }
    }
})
