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
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Guards for the saw config of the unified shape ([WaveVoiceState.setSawShape]) and the super-saw
 * tuning anchor ([Ignitors.getUnisonDetune]).
 */
class AnalogSawSpec : StringSpec({

    // Shape sample at phase [p] for a voice configured with the given flyback fraction.
    fun analogSaw(p: Double, rf: Double): Double =
        WaveVoiceState().apply { setSawShape(rf) }.sampleAt(p)

    "analogSaw - pure ramp (rf=0) spans -1..+1" {
        analogSaw(0.0, 0.0) shouldBe (-1.0 plusOrMinus 1e-9)
        analogSaw(0.5, 0.0) shouldBe (0.0 plusOrMinus 1e-9)
        analogSaw(0.999999, 0.0) shouldBe (1.0 plusOrMinus 1e-3)
    }

    "analogSaw - is zero-mean across flyback fractions" {
        val n = 100_000
        for (rf in listOf(0.0, 0.1, 0.2, 0.4, 0.49)) {
            var sum = 0.0
            for (i in 0 until n) sum += analogSaw(i.toDouble() / n, rf)
            (sum / n) shouldBe (0.0 plusOrMinus 1e-3)
        }
    }

    "analogSaw - rises to the peak then flies back down" {
        val rf = 0.2
        val n = 4000
        val vals = DoubleArray(n) { analogSaw(it.toDouble() / n, rf) }
        val peakIdx = vals.indices.maxByOrNull { vals[it] }!!
        // monotone non-decreasing through the rise up to the peak
        for (i in 1..peakIdx) (vals[i] >= vals[i - 1] - 1e-9) shouldBe true
        // monotone non-increasing through the flyback to the cycle end
        val flybackStart = ((1.0 - rf) * n).toInt() + 1
        for (i in flybackStart until n) (vals[i] <= vals[i - 1] + 1e-9) shouldBe true
    }

    "analogSaw - higher pitch (larger flyback fraction) softens: smaller max slope" {
        val n = 100_000
        fun maxAbsSlope(rf: Double): Double {
            var prev = analogSaw(0.0, rf)
            var m = 0.0
            for (i in 1 until n) {
                val cur = analogSaw(i.toDouble() / n, rf)
                val s = abs(cur - prev); if (s > m) m = s
                prev = cur
            }
            return m
        }
        // a wide flyback (high note → near-triangle) has a gentler reset than a narrow one (low note)
        maxAbsSlope(0.45) shouldBeLessThan maxAbsSlope(0.02)
    }

    "getUnisonDetune - symmetric spread sums to zero (in-tune centroid)" {
        for (u in listOf(2, 3, 5, 7, 8, 12)) {
            var sum = 0.0
            for (n in 0 until u) sum += Ignitors.getUnisonDetune(u, 0.2, n)
            sum shouldBe (0.0 plusOrMinus 1e-9)
        }
    }

    // ── SuperSaw onset: the on-pitch CENTER voice gets only a scaled-down share of the gain jitter
    // (SUPERSAW_CENTER_JITTER_SCALE in computeVoiceGains), so its fundamental stays strong → no "won't ring",
    // even at a high SUPERSAW_GAIN_JITTER. Side voices + phases stay random, so onsets still vary (character
    // intact). Ring *consistency* itself is by-ear; these guard against dead onsets and over-uniformising.
    val onsetSampleRate = 44100

    fun renderOnset(sig: Ignitor, freqHz: Double, blockFrames: Int): AudioBuffer {
        val buffer = AudioBuffer(blockFrames)
        val ctx = IgniteContext(
            sampleRate = onsetSampleRate,
            voiceDurationFrames = onsetSampleRate,
            gateEndFrame = onsetSampleRate,
            releaseFrames = blockFrames,
            voiceEndFrame = onsetSampleRate + blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply { offset = 0; length = blockFrames; voiceElapsedFrames = 0 }
        sig.generate(buffer, freqHz, ctx)
        return buffer
    }

    fun AudioBuffer.rms(): Double {
        var s = 0.0
        for (i in 0 until size) s += this[i] * this[i]
        return sqrt(s / size)
    }

    // 17-voice super-saw (odd → exact center), analog off to isolate the gain/phase mechanism. Uses the
    // production SUPERSAW_GAIN_JITTER, so this also covers the high-jitter case.
    fun superSaw17(seed: Int) = Ignitors.superSaw(
        voices = ParamIgnitor("voices", 17.0),
        analog = ParamIgnitor("analog", 0.0),
        rng = Random(seed),
    )

    "superSaw - center voice exempt from jitter: onsets never go dead across notes" {
        // Different seeds = different notes. The exempt on-pitch center guarantees a live onset every time
        // (no "won't ring" lottery), even though side gains + phases are random.
        (1..16).forEach { renderOnset(superSaw17(it), 220.0, 882).rms() shouldBeGreaterThan 0.02 }
    }

    "superSaw - onsets still vary across notes (side jitter + phases keep the character)" {
        val a = renderOnset(superSaw17(1), 220.0, 882)
        val b = renderOnset(superSaw17(2), 220.0, 882)
        var differs = false
        for (i in 0 until a.size) if (abs(a[i] - b[i]) > 1e-6) {
            differs = true; break
        }
        differs shouldBe true
    }
})
