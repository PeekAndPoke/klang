/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class LowPassHighPassFiltersSpec : StringSpec({

    val sampleRate = 44100.0
    val blockFrames = 4096

    /** Generate a sine wave at [freq] Hz into a AudioBuffer of [length] samples. */
    fun sine(freq: Double, length: Int, amplitude: Double = 1.0): AudioBuffer {
        return AudioBuffer(length) { i ->
            (amplitude * sin(2.0 * PI * freq * i / sampleRate))
        }
    }

    /** RMS of an entire AudioBuffer. */
    fun rms(buf: AudioBuffer): Double {
        if (buf.isEmpty()) return 0.0
        val sumSq = buf.fold(0.0) { acc, v -> acc + v * v }
        return sqrt(sumSq / buf.size)
    }

    // -----------------------------------------------------------------------
    // SvfBPF (the orbit resonators' bandpass, on BaseSvf). The strip's SvfLPF, SvfHPF
    // and SvfNotch retired in phase 3 step 9; their laws that survive on the tree's
    // SVF node are in `ignitor/SvfNodeLawSpec`, and the coefficient guards shared by
    // every BaseSvf are pinned here on the class that is left. Its unity peak at the
    // centre is `FilterNormalizationSpec`'s.
    // -----------------------------------------------------------------------

    "SvfBPF - attenuates signal far below center" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 5000.0, q = 2.0, sampleRate = sampleRate)
        val buf = sine(freq = 100.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buffer = buf, offset = 0, length = buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeLessThan (inputRms * 0.15)
    }

    "SvfBPF - attenuates signal far above center" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 500.0, q = 2.0, sampleRate = sampleRate)
        val buf = sine(freq = 15000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buffer = buf, offset = 0, length = buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeLessThan (inputRms * 0.15)
    }

    "SvfBPF - sweepCutoff updates behavior" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 1000.0, q = 2.0, sampleRate = sampleRate)

        // Center at 1 kHz - 1 kHz signal passes well
        val buf1 = sine(freq = 1000.0, length = blockFrames)
        filter.process(buffer = buf1, offset = 0, length = buf1.size)
        val rmsCenter = rms(buf1)

        // Move center far away from 1 kHz
        filter.sweepCutoff(startHz = 15000.0, endHz = 15000.0, frames = blockFrames)
        repeat(3) {
            val settle = sine(freq = 1000.0, length = blockFrames)
            filter.process(buffer = settle, offset = 0, length = settle.size)
        }
        val buf2 = sine(freq = 1000.0, length = blockFrames)
        filter.process(buffer = buf2, offset = 0, length = buf2.size)
        val rmsOffCenter = rms(buf2)

        rmsOffCenter shouldBeLessThan (rmsCenter * 0.5)
    }

    "SvfBPF - Q extremes do not crash" {
        for (q in listOf(0.1, 50.0)) {
            val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 1000.0, q = q, sampleRate = sampleRate)
            val buf = sine(freq = 1000.0, length = blockFrames)
            filter.process(buffer = buf, offset = 0, length = buf.size)
            buf.none { it.isNaN() || it.isInfinite() } shouldBe true
        }
    }

    "SvfBPF - cutoff at 5 Hz edge case: a 1 kHz sine is heavily attenuated" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 5.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 1000.0, length = blockFrames)
        filter.process(buffer = buf, offset = 0, length = buf.size)
        rms(buf) shouldBeLessThan 0.01
    }

    "SvfBPF - a cutoff at or past Nyquist is clamped to Nyquist - 1 Hz: finite, and the clamp's own filter" {
        val nyquist = sampleRate / 2.0
        val src = sine(freq = 1000.0, length = blockFrames)
        val clamped = AudioBuffer(blockFrames) { src[it] }
        LowPassHighPassFilters.SvfBPF(cutoffHz = nyquist - 1.0, q = 1.0, sampleRate = sampleRate).process(buffer = clamped, offset = 0, length = blockFrames)

        for (cutoff in listOf(nyquist, sampleRate, 1e9)) {
            val buf = AudioBuffer(blockFrames) { src[it] }
            LowPassHighPassFilters.SvfBPF(cutoffHz = cutoff, q = 1.0, sampleRate = sampleRate).process(buffer = buf, offset = 0, length = blockFrames)
            buf.none { it.isNaN() || it.isInfinite() } shouldBe true
            (0 until blockFrames).all { buf[it].toRawBits() == clamped[it].toRawBits() } shouldBe true
        }
    }

    "SvfBPF - a NaN cutoff is guarded, at construction and in a sweep (state stays finite)" {
        // `bilinearK` falls back to 1000 Hz for a non-finite cutoff: a NaN must not poison the IIR state.
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = Double.NaN, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 440.0, length = blockFrames)
        filter.process(buffer = buf, offset = 0, length = buf.size)
        buf.all { it.isFinite() } shouldBe true

        filter.sweepCutoff(startHz = Double.NaN, endHz = Double.NaN, frames = blockFrames)
        val buf2 = sine(freq = 440.0, length = blockFrames)
        filter.process(buffer = buf2, offset = 0, length = buf2.size)
        buf2.all { it.isFinite() } shouldBe true
    }

    "SvfBPF against the TPT kernel written here, from the FIRST frame: the tap is k * v1, the coefficients static" {
        // The oracle is the recurrence itself, fed the coefficients `computeSvfCoeffs` gives for the constructor's
        // cutoff and q. From frame 0 on, so it also pins that construction SNAPS to the cutoff (no ramp on the
        // first samples) and that a static filter steps nothing across a long block.
        val cutoff = 800.0
        val q = 1.5
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = cutoff, q = q, sampleRate = sampleRate)
        val coefs = SvfCoeffs()
        computeSvfCoeffs(cutoffHz = cutoff, q = q, sampleRate = sampleRate, out = coefs)
        var ic1eq = 0.0
        var ic2eq = 0.0

        val out = sine(freq = 440.0, length = blockFrames)
        val ref = AudioBuffer(blockFrames) { out[it] }
        filter.process(buffer = out, offset = 0, length = blockFrames)

        for (i in 0 until blockFrames) {
            val v0 = ref[i]
            val v3 = v0 - ic2eq
            val v1 = coefs.a1 * ic1eq + coefs.a2 * v3
            val v2 = ic2eq + coefs.a2 * ic1eq + coefs.a3 * v3
            ic1eq = 2.0 * v1 - ic1eq
            ic2eq = 2.0 * v2 - ic2eq
            ref[i] = coefs.k * v1
        }

        var maxAbsDiff = 0.0
        for (i in 0 until blockFrames) {
            maxAbsDiff = maxOf(maxAbsDiff, abs(out[i] - ref[i]))
        }
        maxAbsDiff shouldBeLessThan 1e-12
    }

    "SvfBPF cutoffOffsetMul = 1.0 is bit-identical to unset" {
        val unset = LowPassHighPassFilters.SvfBPF(1000.0, q = 2.0, sampleRate = sampleRate)
        val unity = LowPassHighPassFilters.SvfBPF(1000.0, q = 2.0, sampleRate = sampleRate, cutoffOffsetMul = 1.0)

        val a = sine(freq = 1000.0, length = 1024, amplitude = 0.5)
        val b = AudioBuffer(1024) { i -> a[i] }

        unset.process(buffer = a, offset = 0, length = a.size)
        unity.process(buffer = b, offset = 0, length = b.size)

        for (i in 0 until 1024) {
            b[i] shouldBe a[i]
        }
    }

    "SvfBPF cutoffOffsetMul moves the centre" {
        // An offset of 1.05 centres the band 5% higher, on the 1050 Hz sine: it passes more of it.
        val nominal = LowPassHighPassFilters.SvfBPF(1000.0, q = 8.0, sampleRate = sampleRate, cutoffOffsetMul = 1.0)
        val shifted = LowPassHighPassFilters.SvfBPF(1000.0, q = 8.0, sampleRate = sampleRate, cutoffOffsetMul = 1.05)

        val a = sine(freq = 1050.0, length = blockFrames, amplitude = 0.5)
        val b = AudioBuffer(blockFrames) { i -> a[i] }

        nominal.process(buffer = a, offset = 0, length = a.size)
        shifted.process(buffer = b, offset = 0, length = b.size)

        rms(b) shouldBeGreaterThan (rms(a) * 1.2)
    }
})
