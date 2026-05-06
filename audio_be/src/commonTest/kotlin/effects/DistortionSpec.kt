package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ClippingFuncs
import io.peekandpoke.klang.audio_be.voices.strip.filter.DistortionRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.renderInPlace
import kotlin.math.abs

class DistortionSpec : StringSpec({

    // ===== Waveshaper function tests =====

    "fastTanh clips input to [-1, 1] range" {
        ClippingFuncs.fastTanh(0.0) shouldBe 0.0
        ClippingFuncs.fastTanh(100.0) shouldBe (1.0 plusOrMinus 0.01)
        ClippingFuncs.fastTanh(-100.0) shouldBe (-1.0 plusOrMinus 0.01)
    }

    "hardClip strictly clips to [-1, 1]" {
        ClippingFuncs.hardClip(0.5) shouldBe 0.5
        ClippingFuncs.hardClip(2.0) shouldBe 1.0
        ClippingFuncs.hardClip(-2.0) shouldBe -1.0
    }

    "softClip is bounded" {
        abs(ClippingFuncs.softClip(100.0)) shouldBeLessThan 1.01
        abs(ClippingFuncs.softClip(-100.0)) shouldBeLessThan 1.01
    }

    "cubicClip provides soft saturation" {
        ClippingFuncs.cubicClip(0.0) shouldBe 0.0
        ClippingFuncs.cubicClip(0.5).shouldBeGreaterThan(0.0)
        abs(ClippingFuncs.cubicClip(10.0)) shouldBeLessThan 1.5
    }

    "diodeClip is asymmetric (passes positive, attenuates negative)" {
        ClippingFuncs.diodeClip(1.0).shouldBeGreaterThan(0.0)
        // Negative values should be attenuated (closer to zero than positive)
        abs(ClippingFuncs.diodeClip(-1.0)) shouldBeLessThan abs(ClippingFuncs.diodeClip(1.0))
    }

    "sineFold wraps around at boundaries" {
        // At zero, output should be near zero
        ClippingFuncs.sineFold(0.0) shouldBe (0.0 plusOrMinus 0.001)
        // At PI, should wrap back near zero
        ClippingFuncs.sineFold(kotlin.math.PI) shouldBe (0.0 plusOrMinus 0.001)
        ClippingFuncs.sineFold(kotlin.math.PI / 2.0) shouldBe (1.0 plusOrMinus 0.001)
    }

    // ===== DistortionRenderer tests =====

    "DistortionRenderer with amount=0 passes signal through unchanged" {
        val renderer = DistortionRenderer(amount = 0.0)
        val buffer = doubleArrayOf(0.5, -0.3, 0.8, -0.9)
        val original = buffer.copyOf()
        renderer.renderInPlace(buffer)
        for (i in buffer.indices) {
            buffer[i] shouldBe original[i]
        }
    }

    "DistortionRenderer soft shape produces bounded output" {
        val renderer = DistortionRenderer(amount = 1.0, shape = "soft")
        val buffer = doubleArrayOf(0.5, -0.3, 0.8, -0.9, 1.0, -1.0)
        renderer.renderInPlace(buffer)
        for (sample in buffer) {
            abs(sample.toDouble()) shouldBeLessThan 1.01
        }
    }

    "DistortionRenderer hard shape clips to [-1, 1]" {
        val renderer = DistortionRenderer(amount = 1.0, shape = "hard")
        val buffer = doubleArrayOf(0.5, -0.3, 0.8, -0.9, 1.0, -1.0)
        renderer.renderInPlace(buffer)
        for (sample in buffer) {
            abs(sample.toDouble()) shouldBeLessThan 1.01
        }
    }

    "DistortionRenderer all shapes produce bounded output" {
        val shapes = listOf("soft", "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp")
        for (shape in shapes) {
            val renderer = DistortionRenderer(amount = 0.8, shape = shape)
            val buffer = doubleArrayOf(0.5, -0.3, 0.8, -0.9, 1.0, -1.0, 0.0, 0.1)
            renderer.renderInPlace(buffer)
            for (sample in buffer) {
                abs(sample.toDouble()) shouldBeLessThan 3.0
            }
        }
    }

    "DistortionRenderer unknown shape falls back to soft" {
        val rendererUnknown = DistortionRenderer(amount = 0.5, shape = "nonexistent")
        val rendererSoft = DistortionRenderer(amount = 0.5, shape = "soft")
        val buf1 = doubleArrayOf(0.5, -0.3, 0.8)
        val buf2 = doubleArrayOf(0.5, -0.3, 0.8)
        rendererUnknown.renderInPlace(buf1)
        rendererSoft.renderInPlace(buf2)
        for (i in buf1.indices) {
            buf1[i].toDouble() shouldBe (buf2[i].toDouble() plusOrMinus 0.0001)
        }
    }

    "DistortionRenderer diode shape DC blocker removes offset over time" {
        val renderer = DistortionRenderer(amount = 0.8, shape = "diode")
        val buffer = AudioBuffer(4096) { 0.5 }
        renderer.renderInPlace(buffer)
        val lastSample = buffer[buffer.size - 1]
        abs(lastSample.toDouble()) shouldBeLessThan 0.1
    }

    "DistortionRenderer rectify shape DC blocker removes offset over time" {
        val renderer = DistortionRenderer(amount = 0.5, shape = "rectify")
        val buffer = AudioBuffer(4096) { i -> if (i % 2 == 0) 0.3 else -0.3 }
        renderer.renderInPlace(buffer)
        val avg = buffer.takeLast(100).map { it.toDouble() }.average()
        abs(avg) shouldBeLessThan 0.15
    }
})
