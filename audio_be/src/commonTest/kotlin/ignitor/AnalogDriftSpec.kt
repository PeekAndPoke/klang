/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.FILTER_CUTOFF_OFFSET_PER_ANALOG
import io.peekandpoke.klang.audio_be.filters.FILTER_DRIFT_RELATIVE_TO_OSC
import kotlin.math.abs
import kotlin.math.log2
import kotlin.random.Random

/**
 * Guards for [AnalogDrift] / [PolyAnalogDrift] stability and the `analog` → cents budget.
 *
 * Two perceptual properties:
 *  1. **In-tune attack** — a note must START centred (multiplier ≈ 1.0). The slow layer is
 *     seeded at centre precisely so short / melodic notes don't inherit a per-note random
 *     detune. Only the tiny fast layer is present at attack.
 *  2. **No runaway** — over millions of samples the multiplier stays centred on 1.0 with a
 *     bounded excursion. Both layers are stable AR(1); neither is a pure random walk, so it
 *     cannot drift away from centre.
 *
 * Plus a budget guard so the `analog` → cents mapping can't be silently cranked back up.
 */
class AnalogDriftSpec : StringSpec({

    val sr = 48_000

    // Multiplier (centred on 1.0) → cents away from centre.
    fun cents(mult: Double): Double = 1200.0 * log2(mult)

    "attack is in tune - slow layer seeded at centre (mono)" {
        // First multiplier across many seeds: centred, and only the fast layer is present
        // (~±0.2·analog cents). If the slow layer were seeded at steady-state (the old
        // behaviour) the spread would be ~4× larger and notes would start audibly detuned.
        val analog = 8.0
        val n = 20_000
        var sum = 0.0
        var maxAbs = 0.0
        for (i in 0 until n) {
            val first = cents(AnalogDrift(analog, sr, Random(i + 1)).nextMultiplier())
            sum += first
            val a = abs(first); if (a > maxAbs) maxAbs = a
        }
        (sum / n) shouldBe (0.0 plusOrMinus 0.1)        // centred at attack
        // fast-only peak ≈ 0.2·8 = 1.6 cents; allow tail headroom but stay well under the
        // fast+slow peak (~8 cents) the old steady-state seeding would have produced.
        maxAbs shouldBeLessThan 3.5
    }

    "attack is in tune - slow layer seeded at centre (poly / unison)" {
        val drift = PolyAnalogDrift(analog = 8.0, voiceCount = 16, sampleRate = sr, rng = Random(1))
        drift.advanceAll()
        var sum = 0.0
        for (m in drift.multipliers) {
            val c = cents(m)
            abs(c) shouldBeLessThan 3.5
            sum += c
        }
        (sum / drift.multipliers.size) shouldBe (0.0 plusOrMinus 0.6)
    }

    "does not run away - centred and bounded over millions of samples" {
        val d = AnalogDrift(analog = 8.0, sampleRate = sr, rng = Random(42))
        val n = 2_000_000
        var sum = 0.0
        var maxAbs = 0.0
        for (i in 0 until n) {
            val c = cents(d.nextMultiplier())
            sum += c
            val a = abs(c); if (a > maxAbs) maxAbs = a
        }
        // A pure random walk would reach hundreds/thousands of cents; a stable
        // mean-reverting process stays small. Loose bounds catch divergence robustly.
        (sum / n) shouldBe (0.0 plusOrMinus 2.0)
        maxAbs shouldBeLessThan 25.0
    }

    "analog cents budget stays tamed at analog=3 (Der Schmetterling)" {
        val analog = 3.0
        val oscPeak = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * analog
        val filterOffsetPeak = cents(1.0 + FILTER_CUTOFF_OFFSET_PER_ANALOG * analog)
        val filterDriftPeak = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * analog * FILTER_DRIFT_RELATIVE_TO_OSC

        // Post-tuning ceilings — if a constant gets cranked back up, this fails loudly.
        oscPeak shouldBeLessThan 3.5             // ±~3 cents pitch
        filterOffsetPeak shouldBeLessThan 6.0    // was ±15 cents at 0.003; now ±~5
        filterDriftPeak shouldBeLessThan 9.0     // was ±12 cents at 5.0; now ±~7.5

        // Eyeball table across the range people actually use (analog 1–8).
        for (a in listOf(1.0, 2.0, 3.0, 5.0, 8.0)) {
            val osc = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * a
            val off = cents(1.0 + FILTER_CUTOFF_OFFSET_PER_ANALOG * a)
            val drf = (ANALOG_FAST_PEAK_CENTS + ANALOG_SLOW_PEAK_CENTS) * a * FILTER_DRIFT_RELATIVE_TO_OSC
            println("analog=$a  oscPitch=±${osc}c  filterOffset=±${off}c  filterDrift=±${drf}c")
        }
    }
})