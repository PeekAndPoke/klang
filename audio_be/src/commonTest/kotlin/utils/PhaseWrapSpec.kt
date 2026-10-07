/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The phase wraps of `phase_wrap.kt`. [wrapPhase] is the safe one (any finite value lands in `[0, period)` up to
 * rounding at both ends, pinned below and described in its KDoc; non-finite is 0); [smallNumFastMod] is
 * one conditional step and nothing more, for an accumulator whose increment is under one period, with the same
 * edge; [wrapPhaseFastOrSafe] picks one of the two; [wrapToUnitCycle] wraps a phase OFFSET into
 * `[0, 1)`.
 */
class PhaseWrapSpec : StringSpec({

    "wrapPhase: one overshoot either way is one step" {
        (0.25).wrapPhase(1.0) shouldBe 0.25
        (1.25).wrapPhase(1.0) shouldBe 0.25
        (-0.25).wrapPhase(1.0) shouldBe 0.75
        (1.0).wrapPhase(1.0) shouldBe 0.0
        (-1.0).wrapPhase(1.0) shouldBe 0.0
    }

    "wrapPhase: a phase two or more periods out lands in range in one modulo step" {
        (1000.5).wrapPhase(1.0) shouldBe 0.5
        (2.0).wrapPhase(1.0) shouldBe 0.0
        (-3.5).wrapPhase(1.0) shouldBe 0.5
        (9.0).wrapPhase(4.0) shouldBe 1.0
    }

    "wrapPhase: a tiny negative phase rounds up onto the period itself, one ulp past [0, period)" {
        (-1e-20).wrapPhase(1.0) shouldBe 1.0
        (-1e-17).wrapPhase(TWO_PI) shouldBe TWO_PI
    }

    "wrapPhase: the modulo branch can miss by rounding, a tiny negative or the period itself" {
        (106.81415022205296).wrapPhase(TWO_PI).toRawBits() shouldBe (-1.4210854715202004e-14).toRawBits()
        (-0.20000000000000004).wrapPhase(0.1).toRawBits() shouldBe (0.1).toRawBits()
    }

    "wrapPhase: a non-finite phase reads as 0" {
        Double.NaN.wrapPhase(1.0) shouldBe 0.0
        Double.POSITIVE_INFINITY.wrapPhase(1.0) shouldBe 0.0
        Double.NEGATIVE_INFINITY.wrapPhase(1.0) shouldBe 0.0
    }

    "smallNumFastMod: one conditional step, never more" {
        (0.25).smallNumFastMod(1.0) shouldBe 0.25
        (1.25).smallNumFastMod(1.0) shouldBe 0.25
        (-0.25).smallNumFastMod(1.0) shouldBe 0.75
        (1.0).smallNumFastMod(1.0) shouldBe 0.0
        // The contract's edge: two periods out stays a period out. That is what wrapPhase is for.
        (2.5).smallNumFastMod(1.0) shouldBe 1.5
        // The same one-ulp edge as wrapPhase: a tiny negative lands on the period itself.
        (-1e-20).smallNumFastMod(1.0) shouldBe 1.0
        // NaN fails both compares and passes through.
        Double.NaN.smallNumFastMod(1.0).isNaN() shouldBe true
    }

    "wrapToUnitCycle: into [0, 1), a value inside unchanged bit for bit" {
        (0.3).wrapToUnitCycle().toRawBits() shouldBe (0.3).toRawBits()
        (1.25).wrapToUnitCycle() shouldBe 0.25
        (-0.25).wrapToUnitCycle() shouldBe 0.75
        (5.0).wrapToUnitCycle() shouldBe 0.0
    }

    "wrapToUnitCycle: a tiny negative whose wrap rounds up to 1 is 0, and so is a non-finite value" {
        (-1e-20).wrapToUnitCycle() shouldBe 0.0
        Double.NaN.wrapToUnitCycle() shouldBe 0.0
        Double.POSITIVE_INFINITY.wrapToUnitCycle() shouldBe 0.0
        Double.NEGATIVE_INFINITY.wrapToUnitCycle() shouldBe 0.0
    }

    "wrapPhaseFastOrSafe: the safe wrap when asked, else the one-step fast wrap" {
        (2.5).wrapPhaseFastOrSafe(period = 1.0, safe = true) shouldBe 0.5
        (2.5).wrapPhaseFastOrSafe(period = 1.0, safe = false) shouldBe 1.5
        Double.NaN.wrapPhaseFastOrSafe(period = 1.0, safe = true) shouldBe 0.0
        Double.NaN.wrapPhaseFastOrSafe(period = 1.0, safe = false).isNaN() shouldBe true
        (1.25).wrapPhaseFastOrSafe(period = 1.0, safe = false) shouldBe 0.25
    }
})
