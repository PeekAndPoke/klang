package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

/**
 * Guards for the unified pulse shape ([PulseWaveState] / `pulseTrapezoidShape`) shared by
 * square / pulze / triangle. See the pulse-oscillator plan.
 */
class PulseShapeSpec : StringSpec({

    // freq/sr → base increment; the sample floor is PULSE_MIN_FLANK_SAMPLES * dt.
    val dt = 110.0 / 48_000.0   // ~2.29e-3

    fun state(duty: Double, rise: Double, fall: Double) =
        PulseWaveState().apply { setShape(duty, rise, fall, dt) }

    fun mean(s: PulseWaveState, n: Int = 200_000): Double {
        var sum = 0.0
        for (i in 0 until n) sum += s.sampleAt(i.toDouble() / n)
        return sum / n
    }

    "square preset (duty 0.5, flanks 0) is ±1 between the edges" {
        val s = state(0.5, 0.0, 0.0)
        s.sampleAt(0.25) shouldBe (1.0 plusOrMinus 1e-9)   // mid high plateau
        s.sampleAt(0.75) shouldBe (-1.0 plusOrMinus 1e-9)  // mid low plateau
    }

    "square preset edges carry the minimum sample-flank floor (no instant edge)" {
        val s = state(0.5, 0.0, 0.0)
        val floor = PULSE_MIN_FLANK_SAMPLES * dt
        s.fallStart shouldBe ((0.5 - floor) plusOrMinus 1e-9)  // fall ramp starts a floor before 0.5
        s.riseStart shouldBe ((1.0 - floor) plusOrMinus 1e-9)  // rise ramp starts a floor before 1
        (s.fallSlope > 0.0) shouldBe true                      // finite slope, never instant
        (s.riseSlope > 0.0) shouldBe true
    }

    "square preset is zero-mean" {
        mean(state(0.5, 0.0, 0.0)) shouldBe (0.0 plusOrMinus 1e-3)
    }

    "triangle preset (duty 0.5, flanks 1) is a zero-mean triangle" {
        val s = state(0.5, 1.0, 1.0)
        s.sampleAt(0.0) shouldBe (1.0 plusOrMinus 1e-9)
        s.sampleAt(0.25) shouldBe (0.0 plusOrMinus 1e-9)
        s.sampleAt(0.5) shouldBe (-1.0 plusOrMinus 1e-9)
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
})
