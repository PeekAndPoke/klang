/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * C4 guard (docs/plans/filter-unification.md): THE wet/dry law. Two exponents, two
 * statistics — the rows pin each law's defining invariant, the floor's pinning region,
 * and the domain coercions.
 */
class WetDryMixSpec : StringSpec({

    "p = 1 holds constant POWER across the knob (decorrelated inputs)" {
        // For decorrelated dry/wet of equal power, total power = dryC^2 + wetC^2.
        // p = 1: cos^2 + sin^2 = 1 exactly, for every w.
        for (w in listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0)) {
            val d = WetDryMix.dryCoeff(w, floor = 0.0, p = 1)
            val g = WetDryMix.wetCoeff(w, p = 1)
            (d * d + g * g) shouldBe (1.0 plusOrMinus 1e-12)
        }
    }

    "p = 2 holds constant AMPLITUDE across the knob (correlated inputs)" {
        // For fully correlated dry/wet of equal amplitude, total amplitude = dryC + wetC.
        // p = 2: cos^2 + sin^2 = 1 exactly, for every w.
        for (w in listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0)) {
            val d = WetDryMix.dryCoeff(w, floor = 0.0, p = 2)
            val g = WetDryMix.wetCoeff(w, p = 2)
            (d + g) shouldBe (1.0 plusOrMinus 1e-12)
        }
    }

    "the two laws genuinely differ between the endpoints" {
        // A regression collapsing them to one exponent passes both rows above only if it
        // keeps the invariants; this row kills an accidental p-swap outright.
        val d1 = WetDryMix.dryCoeff(0.5, floor = 0.0, p = 1)
        val d2 = WetDryMix.dryCoeff(0.5, floor = 0.0, p = 2)
        d1 shouldBe (sqrt(2.0) / 2.0 plusOrMinus 1e-12)
        d2 shouldBe (0.5 plusOrMinus 1e-12)
        abs(d1 - d2) shouldBe (0.2071067811865476 plusOrMinus 1e-12)
    }

    "floor pins the dry coefficient above w* and never below the floor" {
        val floor = 0.4
        // Below w* the cos curve rules; above it the floor rules. Never below the floor.
        for (w in listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)) {
            val d = WetDryMix.dryCoeff(w, floor = floor, p = 2)
            (d >= floor) shouldBe true
        }
        // At w = 1 the cos term is 0: the floor alone remains.
        WetDryMix.dryCoeff(1.0, floor = floor, p = 2) shouldBe (floor plusOrMinus 1e-12)
        // Orbit-phaser configuration: floor 1.0 pins the dry at 1 for EVERY w — checked at
        // w = 0.5 where the wet laws differ, per the plan (not at w = 1 where all agree).
        WetDryMix.dryCoeff(0.5, floor = 1.0, p = 2) shouldBe 1.0
        WetDryMix.wetCoeff(0.5, p = 2) shouldBe (0.5 plusOrMinus 1e-12)
    }

    "domain coercions: w and floor clamp, non-finite falls to safe values" {
        WetDryMix.dryCoeff(-0.5, floor = 0.0, p = 1) shouldBe (1.0 plusOrMinus 1e-12)
        WetDryMix.wetCoeff(1.5, p = 1) shouldBe (1.0 plusOrMinus 1e-12)
        WetDryMix.dryCoeff(Double.NaN, floor = 0.0, p = 2) shouldBe (1.0 plusOrMinus 1e-12)
        WetDryMix.wetCoeff(Double.NaN, p = 2) shouldBe (0.0 plusOrMinus 1e-12)
        WetDryMix.dryCoeff(0.5, floor = Double.NaN, p = 2) shouldBe (0.5 plusOrMinus 1e-12)
        // endpoints are exact: w = 0 is full dry / no wet, w = 1 is no dry (floor 0) / full wet
        WetDryMix.dryCoeff(0.0, floor = 0.0, p = 2) shouldBe 1.0
        WetDryMix.wetCoeff(0.0, p = 2) shouldBe 0.0
        WetDryMix.dryCoeff(1.0, floor = 0.0, p = 2) shouldBeLessThan 1e-30
        WetDryMix.wetCoeff(1.0, p = 2) shouldBe (1.0 plusOrMinus 1e-12)
    }
})
