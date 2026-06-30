/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import kotlin.math.floor
import kotlin.random.Random

/**
 * 1D Berlin Noise implementation.
 *
 * Smooth random noise via linear interpolation between deterministic hash values.
 * Uses Int-only hashing (no Long) — safe for Kotlin/JS where Long is boxed.
 */
class BerlinNoise(random: Random) {
    private val seed = random.nextInt()

    /** Deterministic hash for an integer index — zero allocations, Int-only. */
    private fun getValAt(index: Int): Double {
        // MurmurHash3-style 32-bit finalizer: fast, good distribution, no Long
        var h = seed xor (index * 0x9E3779B1.toInt())
        h = (h xor (h ushr 16)) * 0x85EBCA6B.toInt()
        h = (h xor (h ushr 13)) * 0xC2B2AE35.toInt()
        h = h xor (h ushr 16)
        return (h.toDouble() / Int.MAX_VALUE.toDouble() + 1.0) * 0.5 // map to ~[0, 1]
    }

    fun noise(t: Double): Double {
        val currentIndex = floor(t).toInt()
        val nextIndex = currentIndex + 1

        val v0 = getValAt(currentIndex)
        val v1 = getValAt(nextIndex)

        val currentPercent = t - currentIndex

        return v0 + currentPercent * (v1 - v0)
    }

    /**
     * Fractal Brownian motion over [noise]: sums [octaves] evaluations at ×2 frequency (lacunarity 2.0) and
     * ×[persistence] amplitude per octave, normalized by the amplitude sum so the result keeps [noise]'s
     * ≈[0, 1] range. Higher octaves add finer detail; lower [persistence] makes the upper octaves quieter.
     *
     * [octaves] <= 1 short-circuits to a single [noise] call — byte-identical to the single-octave path, so the
     * default (octaves = 1) carries zero extra cost. Cost scales **linearly** with [octaves] (engine caps at 8).
     */
    fun fbm(t: Double, octaves: Int, persistence: Double): Double {
        if (octaves <= 1) return noise(t)
        var sum = 0.0
        var amp = 1.0
        var freq = 1.0
        var norm = 0.0
        for (o in 0 until octaves) {
            sum += amp * noise(t * freq)
            norm += amp
            freq *= 2.0
            amp *= persistence
        }
        return if (norm > 0.0) sum / norm else 0.0
    }
}
