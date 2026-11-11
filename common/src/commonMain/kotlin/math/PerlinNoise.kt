/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import kotlin.math.floor
import kotlin.random.Random

/**
 * 1D Perlin noise: a gradient at every whole lattice point, picked by hashing the lattice index with a seed, and a
 * quintic fade between the two around `x`.
 *
 * **No period** (KLANG-00430, audit finding L3-09, the maintainer's option 2 of 2026-11-11): the gradient comes from an
 * Int hash of the index, the idiom of [BerlinNoise] (a MurmurHash3-style 32-bit finalizer), not from a 256-entry
 * permutation table, so the walk never repeats. The table made every curve repeat every 256 lattice units: 1.78 s at
 * the engine's `rate` 1. The statistics are the table's: the hash's low four bits pick one of the same 16 gradients
 * (+-1 to +-8) with the same equal weight, the fade and the scale are unchanged; every curve is another one. The seed
 * is ONE draw of [random] (`nextInt`), where the table took 255; the curve is the same for the same seed on the JVM and
 * in JS (Int arithmetic only: Kotlin's `Int` multiply wraps alike on both, `Math.imul` in JS).
 */
class PerlinNoise(random: Random = Random(0)) {
    private val seed = random.nextInt()

    /** The lattice point's hash, Int only (no Long, boxed in Kotlin/JS): [BerlinNoise]'s finalizer, its constants. */
    private fun hashAt(index: Int): Int {
        var h = seed xor (index * 0x9E3779B1.toInt())
        h = (h xor (h ushr 16)) * 0x85EBCA6B.toInt()
        h = (h xor (h ushr 13)) * 0xC2B2AE35.toInt()
        return h xor (h ushr 16)
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)

    /**
     * Standard "Improved Perlin Noise" gradient function for 1D.
     * Uses the hash to select a gradient from a small set of predefined gradients.
     */
    private fun grad(hash: Int, x: Double): Double {
        // Simple 1D gradient: use the last 4 bits of the hash to pick a gradient.
        // We want gradients like 1.0, -1.0, etc.
        val h = hash and 15
        var grad = 1.0 + (h and 7) // Gradient value between 1.0 and 8.0
        if ((h and 8) != 0) grad = -grad // Randomly invert sign based on the 8 bit
        return grad * x
    }

    /**
     * Generates a 1D Perlin noise value for [x].
     * Result is in range [-1.0, 1.0].
     */
    fun noise(x: Double): Double {
        // A lattice index past the Int range saturates (`toInt`) and a NaN reads as 0 (NaN-guard, nothing throws); the
        // index after `Int.MAX_VALUE` wraps, the hash does not mind.
        val xi = floor(x).toInt()
        val xf = x - floor(x)
        val u = fade(xf)

        val a = hashAt(xi)
        val b = hashAt(xi + 1)

        val res = lerp(u, grad(a, xf), grad(b, xf - 1.0))

        // Perlin noise typically has a range of about -0.5 to 0.5 or slightly larger in 1D.
        // Scaling by 2.0 brings it closer to -1.0 to 1.0, but might overshoot slightly.
        // For audio/signals, we clamp it to be safe or accept the slight variance.
        // We'll scale by 0.5 to keep it roughly in -1..1 range given the large gradients above (1..8).
        // Actually, with gradients up to 8.0, the output can be quite large.
        // Let's stick to the simpler gradient of just 1.0 / -1.0 for standard behavior if we want predictability.

        return res * 0.25 // Tuning factor to keep it roughly within -1..1
    }

    /**
     * Fractal Brownian motion: sums [octaves] evaluations of [noise] at ×2 frequency (lacunarity 2.0) and
     * ×[persistence] amplitude per octave, normalized by the amplitude sum so the result keeps [noise]'s
     * ≈[-1, 1] range. Higher octaves add finer detail; lower [persistence] makes the upper octaves quieter.
     *
     * [octaves] <= 1 is a single [noise] call, bit for bit the single-octave path, so the
     * default (octaves = 1) carries zero extra cost. Cost then scales **linearly** with [octaves]; callers
     * should cap it (the engine caps at 8).
     */
    fun fbm(x: Double, octaves: Int, persistence: Double): Double {
        var sum = 0.0
        var amp = 1.0
        var freq = 1.0
        var norm = 0.0
        val passes = if (octaves <= 1) 1 else octaves

        // ONE call site of [noise] (V8, engine follow-up 39): the single-octave path returns from the loop's first
        // pass, `noise(x * 1.0)`, which is `noise(x)` bit for bit. Two sites (the old early return plus the loop)
        // made V8 inline [noise] twice, and an fbm that heavy did not fit a caller's inlining budget: the Katalyst
        // knob's `sample` then called it with its argument, its persistence and its result as heap numbers. Keep one
        // site. No test can pin a box, so this comment guards the site; `PerlinNoiseSpec`'s "bit for bit the two-site
        // form it replaced" row guards the bits.
        for (o in 0 until passes) {
            val v = noise(x * freq)

            if (octaves <= 1) {
                return v
            }

            sum += amp * v
            norm += amp
            freq *= 2.0
            amp *= persistence
        }

        return if (norm > 0.0) sum / norm else 0.0
    }
}
