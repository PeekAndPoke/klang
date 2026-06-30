/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
})
