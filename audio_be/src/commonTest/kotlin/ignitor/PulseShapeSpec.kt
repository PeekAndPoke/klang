/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * Guards for the pulse config of the unified shape ([WaveVoiceState.setPulseShape] / `waveTrapezoid`)
 * shared by square / pulze / triangle. Rise-first layout: rise → high plateau → fall → low plateau.
 */
class PulseShapeSpec : StringSpec({

    val dt = 110.0 / 48_000.0                       // ~2.29e-3
    val floor = PULSE_MIN_FLANK_SAMPLES * dt        // min flank length in phase

    fun state(duty: Double, rise: Double, fall: Double) =
        WaveVoiceState().apply { setPulseShape(duty, rise, fall, floor) }

    fun mean(s: WaveVoiceState, n: Int = 200_000): Double {
        var sum = 0.0
        for (i in 0 until n) sum += s.sampleAt(i.toDouble() / n)
        return sum / n
    }

    "square (duty 0.5, flanks 0) is ±1 between the edges" {
        val s = state(0.5, 0.0, 0.0)
        s.sampleAt(0.25) shouldBe (1.0 plusOrMinus 1e-9)   // mid high plateau
        s.sampleAt(0.75) shouldBe (-1.0 plusOrMinus 1e-9)  // mid low plateau
    }

    "square edges carry the minimum sample-flank floor (no instant edge)" {
        val s = state(0.5, 0.0, 0.0)
        s.riseEnd shouldBe (floor plusOrMinus 1e-9)           // rise ramp = a floor at the start
        s.highEnd shouldBe (0.5 plusOrMinus 1e-9)             // high plateau ends at duty
        s.fallEnd shouldBe ((0.5 + floor) plusOrMinus 1e-9)   // fall ramp = a floor after duty
        (s.riseSlope > 0.0) shouldBe true                     // finite slopes, never instant
        (s.fallSlope > 0.0) shouldBe true
    }

    "square is zero-mean" {
        mean(state(0.5, 0.0, 0.0)) shouldBe (0.0 plusOrMinus 1e-3)
    }

    "triangle (duty 0.5, flanks 1) is a zero-mean triangle (peak at 0.5)" {
        val s = state(0.5, 1.0, 1.0)
        s.sampleAt(0.0) shouldBe (-1.0 plusOrMinus 1e-9)
        s.sampleAt(0.25) shouldBe (0.0 plusOrMinus 1e-9)
        s.sampleAt(0.5) shouldBe (1.0 plusOrMinus 1e-9)
        s.sampleAt(0.75) shouldBe (0.0 plusOrMinus 1e-9)
        mean(s) shouldBe (0.0 plusOrMinus 1e-3)
    }

    "pulze duty 0.25 is high for the first quarter" {
        val s = state(0.25, 0.0, 0.0)
        s.sampleAt(0.1) shouldBe (1.0 plusOrMinus 1e-9)    // inside the high quarter
        s.sampleAt(0.6) shouldBe (-1.0 plusOrMinus 1e-9)   // inside the low region
        // a narrow-duty pulse is mostly low → negative DC (raw, not corrected)
        mean(s) shouldBeLessThan -0.4
        mean(s) shouldBeGreaterThan -0.6
    }

    "raw config (floor 0) has instant edges" {
        val s = WaveVoiceState().apply { setPulseShape(0.25, 0.0, 0.0, 0.0) }
        s.riseEnd shouldBe (0.0 plusOrMinus 1e-12)         // no rise ramp
        s.sampleAt(0.1) shouldBe (1.0 plusOrMinus 1e-12)   // +1 while phase < duty
        s.sampleAt(0.6) shouldBe (-1.0 plusOrMinus 1e-12)  // −1 otherwise
    }
})
