/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * C2 guard (docs/plans/filter-unification.md): the bandpass FAMILY is normalised to unity
 * peak at fc — q is a pure WIDTH control, never a level control.
 *
 * For each normalised implementation the rows drive a sine AT fc through the filter at two
 * very different q values and assert the SAME output level (unity). A clamped-q row pins
 * that normalisation uses the CLAMPED k (q = 500 clamps to 200 and must normalise by 200).
 */
class FilterNormalizationSpec : StringSpec({

    val sr = 48000.0
    val blockFrames = 128

    /** Steady-state peak of a sine at [freq] through [process], warm-up discarded. */
    fun sinePeakThrough(freq: Double, warmBlocks: Int = 60, measureBlocks: Int = 10, process: (AudioBuffer) -> Unit): Double {
        var phase = 0.0
        val inc = 2.0 * PI * freq / sr
        val buf = AudioBuffer(blockFrames)
        var peak = 0.0
        repeat(warmBlocks + measureBlocks) { blk ->
            for (i in 0 until blockFrames) {
                buf[i] = sin(phase)
                phase += inc
            }
            process(buf)
            if (blk >= warmBlocks) {
                for (i in 0 until blockFrames) {
                    if (abs(buf[i]) > peak) peak = abs(buf[i])
                }
            }
        }
        return peak
    }

    fun svfBpfPeak(q: Double, freq: Double = 1000.0): Double {
        val f = LowPassHighPassFilters.SvfBPF(freq, q, sr)
        return sinePeakThrough(freq) { buf -> f.process(buf, 0, blockFrames) }
    }

    fun eqCorePeak(type: Int, q: Double, freq: Double = 1000.0, gain: Double = 1.0, warmBlocks: Int = 60): Double {
        val core = EqCore(1)
        core.configureSection(0, type, freq, q, db = 0.0, gain = gain, sampleRate = sr)
        return sinePeakThrough(freq, warmBlocks = warmBlocks) { buf -> core.process(buf, 0, blockFrames) }
    }

    "SvfBPF peaks at unity at fc regardless of q" {
        svfBpfPeak(q = 4.0) shouldBe (1.0 plusOrMinus 0.02)
        svfBpfPeak(q = 0.5) shouldBe (1.0 plusOrMinus 0.02)
    }

    "EqCore BANDPASS peaks at unity at fc regardless of q" {
        eqCorePeak(EqCore.BANDPASS, q = 4.0) shouldBe (1.0 plusOrMinus 0.02)
        eqCorePeak(EqCore.BANDPASS, q = 0.5) shouldBe (1.0 plusOrMinus 0.02)
    }

    "EqCore RAW_TAP at gain 1 lifts fc by the SAME amount for q 4 and q 0.5" {
        // A tap ADDS its unity-peak band onto the dry signal: at fc the sum approaches
        // dry + band. The q-independence of the LEVEL is the C2 contract.
        val hi = eqCorePeak(EqCore.RAW_TAP, q = 4.0)
        val lo = eqCorePeak(EqCore.RAW_TAP, q = 0.5)
        hi shouldBe (lo plusOrMinus lo * 0.03)
    }

    "clamped q normalises by the CLAMPED k (q = 500 behaves as q = 200)" {
        // narrow high-q filters ring long: extended warm-up
        val clamped = eqCorePeak(EqCore.BANDPASS, q = 500.0, warmBlocks = 400)
        val explicit = eqCorePeak(EqCore.BANDPASS, q = 200.0, warmBlocks = 400)
        clamped shouldBe (explicit plusOrMinus explicit * 0.01)
    }

    "notch keeps its math (fc is REJECTED, not normalised)" {
        // The normalisation is bandpass-family only: a notch at fc must still cut deeply.
        val peak = eqCorePeak(EqCore.NOTCH, q = 2.0)
        (peak < 0.2) shouldBe true
    }
})
