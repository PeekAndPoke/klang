package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ClippingFuncs
import io.peekandpoke.klang.audio_be.filters.effects.DistortionFilter
import kotlin.math.abs

class DistortionSpec : StringSpec({

    // ===== Waveshaper function tests =====

    "fastTanh output is bounded to [-1, 1]" {
        ClippingFuncs.fastTanh(100.0) shouldBe (1.0 plusOrMinus 0.01)
        ClippingFuncs.fastTanh(-100.0) shouldBe (-1.0 plusOrMinus 0.01)
        ClippingFuncs.fastTanh(0.0) shouldBe (0.0 plusOrMinus 0.001)
    }

    "fastTanh is symmetric" {
        for (x in listOf(0.1, 0.5, 1.0, 2.0, 3.0)) {
            ClippingFuncs.fastTanh(x) shouldBe (-ClippingFuncs.fastTanh(-x) plusOrMinus 0.0001)
        }
    }

    "hardClip clamps to [-1, 1]" {
        ClippingFuncs.hardClip(5.0) shouldBe 1.0
        ClippingFuncs.hardClip(-5.0) shouldBe -1.0
        ClippingFuncs.hardClip(0.5) shouldBe 0.5
    }

    "softClip output is bounded and symmetric" {
        ClippingFuncs.softClip(0.0) shouldBe 0.0
        ClippingFuncs.softClip(100.0) shouldBe (1.0 plusOrMinus 0.02)
        ClippingFuncs.softClip(-100.0) shouldBe (-1.0 plusOrMinus 0.02)
        ClippingFuncs.softClip(1.0) shouldBe (-ClippingFuncs.softClip(-1.0) plusOrMinus 0.0001)
    }

    "cubicClip output is bounded and symmetric" {
        ClippingFuncs.cubicClip(0.0) shouldBe 0.0
        ClippingFuncs.cubicClip(5.0) shouldBe 1.0
        ClippingFuncs.cubicClip(-5.0) shouldBe -1.0
        ClippingFuncs.cubicClip(0.5) shouldBe (-ClippingFuncs.cubicClip(-0.5) plusOrMinus 0.0001)
    }

    "diodeClip is asymmetric — positive compresses more than negative" {
        val pos = ClippingFuncs.diodeClip(2.0)
        val neg = ClippingFuncs.diodeClip(-2.0)
        // Positive should saturate more (tanh(2) vs tanh(2*0.75))
        abs(pos) shouldBeGreaterThan abs(neg)
    }

    "diodeClip output is bounded" {
        ClippingFuncs.diodeClip(100.0) shouldBe (1.0 plusOrMinus 0.01)
        ClippingFuncs.diodeClip(-100.0) shouldBe (-1.0 plusOrMinus 0.01)
    }

    "chebyshevT3 output is bounded when input is clamped" {
        // Input is clamped to [-1, 1] inside the function
        ClippingFuncs.chebyshevT3(5.0) shouldBe (1.0 plusOrMinus 0.01)
        ClippingFuncs.chebyshevT3(-5.0) shouldBe (-1.0 plusOrMinus 0.01)
        ClippingFuncs.chebyshevT3(0.0) shouldBe 0.0
    }

    "chebyshevT3 generates 3rd harmonic — T3(cos(t)) = cos(3t)" {
        // At x=0.5, T3 = 4*(0.125) - 1.5 = -1.0
        ClippingFuncs.chebyshevT3(0.5) shouldBe (-1.0 plusOrMinus 0.01)
    }

    "rectify output is non-negative and bounded" {
        ClippingFuncs.rectify(0.5) shouldBe 0.5
        ClippingFuncs.rectify(-0.5) shouldBe 0.5
        ClippingFuncs.rectify(5.0) shouldBe 1.0
        ClippingFuncs.rectify(0.0) shouldBe 0.0
    }

    "expClip output is bounded and symmetric" {
        ClippingFuncs.expClip(0.0) shouldBe (0.0 plusOrMinus 0.001)
        ClippingFuncs.expClip(100.0) shouldBe (1.0 plusOrMinus 0.01)
        ClippingFuncs.expClip(-100.0) shouldBe (-1.0 plusOrMinus 0.01)
        ClippingFuncs.expClip(1.0) shouldBe (-ClippingFuncs.expClip(-1.0) plusOrMinus 0.0001)
    }

    "sineFold wraps instead of clipping" {
        // At high drive, sin(x) wraps through zero
        ClippingFuncs.sineFold(Math.PI) shouldBe (0.0 plusOrMinus 0.001)
        ClippingFuncs.sineFold(Math.PI / 2.0) shouldBe (1.0 plusOrMinus 0.001)
    }

    // ===== DistortionFilter tests =====

    "DistortionFilter with amount=0 passes signal through unchanged" {
        val filter = DistortionFilter(amount = 0.0)
        val buffer = floatArrayOf(0.5f, -0.3f, 0.8f, -0.9f)
        val original = buffer.copyOf()
        filter.process(buffer, 0, buffer.size)
        for (i in buffer.indices) {
            buffer[i] shouldBe original[i]
        }
    }

    "DistortionFilter soft shape produces bounded output" {
        val filter = DistortionFilter(amount = 1.0, shape = "soft")
        val buffer = floatArrayOf(0.5f, -0.3f, 0.8f, -0.9f, 1.0f, -1.0f)
        filter.process(buffer, 0, buffer.size)
        for (sample in buffer) {
            abs(sample.toDouble()) shouldBeLessThan 1.01
        }
    }

    "DistortionFilter hard shape clips to [-1, 1]" {
        val filter = DistortionFilter(amount = 1.0, shape = "hard")
        val buffer = floatArrayOf(0.5f, -0.3f, 0.8f, -0.9f, 1.0f, -1.0f)
        filter.process(buffer, 0, buffer.size)
        for (sample in buffer) {
            abs(sample.toDouble()) shouldBeLessThan 1.01
        }
    }

    "DistortionFilter all shapes produce bounded output" {
        val shapes = listOf("soft", "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp")
        for (shape in shapes) {
            val filter = DistortionFilter(amount = 0.8, shape = shape)
            val buffer = floatArrayOf(0.5f, -0.3f, 0.8f, -0.9f, 1.0f, -1.0f, 0.0f, 0.1f)
            filter.process(buffer, 0, buffer.size)
            for (sample in buffer) {
                abs(sample.toDouble()) shouldBeLessThan 3.0 // generous bound, accounts for normalization
            }
        }
    }

    "DistortionFilter unknown shape falls back to soft" {
        val filterUnknown = DistortionFilter(amount = 0.5, shape = "nonexistent")
        val filterSoft = DistortionFilter(amount = 0.5, shape = "soft")
        val buf1 = floatArrayOf(0.5f, -0.3f, 0.8f)
        val buf2 = floatArrayOf(0.5f, -0.3f, 0.8f)
        filterUnknown.process(buf1, 0, buf1.size)
        filterSoft.process(buf2, 0, buf2.size)
        for (i in buf1.indices) {
            buf1[i].toDouble() shouldBe (buf2[i].toDouble() plusOrMinus 0.0001)
        }
    }

    "DistortionFilter diode shape DC blocker removes offset over time" {
        val filter = DistortionFilter(amount = 0.8, shape = "diode")
        // Feed a constant positive signal — should be DC-blocked toward zero
        val buffer = FloatArray(4096) { 0.5f }
        filter.process(buffer, 0, buffer.size)
        // After many samples, the DC blocker should have driven the signal close to zero
        val lastSample = buffer[buffer.size - 1]
        abs(lastSample.toDouble()) shouldBeLessThan 0.1
    }

    "DistortionFilter rectify shape DC blocker removes offset over time" {
        val filter = DistortionFilter(amount = 0.5, shape = "rectify")
        // Alternating signal that becomes all-positive after rectification
        val buffer = FloatArray(4096) { i -> if (i % 2 == 0) 0.3f else -0.3f }
        filter.process(buffer, 0, buffer.size)
        // DC blocker should center the output around zero over time
        val avg = buffer.takeLast(100).map { it.toDouble() }.average()
        abs(avg) shouldBeLessThan 0.15
    }

    "DistortionFilter with offset processes only the specified range" {
        val filter = DistortionFilter(amount = 1.0, shape = "hard")
        val buffer = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        val original0 = buffer[0]
        val original4 = buffer[4]
        filter.process(buffer, 1, 3) // Only process indices 1, 2, 3
        buffer[0] shouldBe original0
        buffer[4] shouldBe original4
    }
})
