/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices.strip.filter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.ignitor.AnalogDrift
import io.peekandpoke.klang.audio_be.voices.Voice
import kotlin.math.abs
import kotlin.random.Random

/**
 * C3 guard (docs/plans/filter-unification.md): envelope depth is SEMITONES on BOTH engine
 * paths — `cutoff = base * 2^(depth/12 * env)`. The two paths apply the law independently
 * (the ignitor svf kernel and the strip-pipeline `FilterModRenderer`); a guard that
 * exercises only one is exactly the `drivePerAnalog` failure mode, so each path has its
 * own rows here (the ignitor rows live in a black-box output comparison; the strip rows
 * pin the formula exactly through a recording filter stub).
 */
class FilterEnvSemitoneSpec : StringSpec({

    // ── Strip path (FilterModRenderer): exact formula ────────────────────────

    // Tunable is a standalone interface (no process contract) - a pure recorder suffices.
    class RecordingFilter : AudioFilter.Tunable {
        var lastCutoff: Double = Double.NaN
        override fun setCutoff(cutoffHz: Double) {
            lastCutoff = cutoffHz
        }
    }

    fun stripCutoffFor(depth: Double, base: Double = 800.0): Double {
        val filter = RecordingFilter()
        val mod = Voice.FilterModulator(
            filter = filter,
            // attack 0 / decay 0 / sustain 1: the envelope is exactly 1.0 during the gate
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0,
            ),
            depth = depth,
            baseCutoff = base,
            drift = null,
        )
        val renderer = FilterModRenderer(
            modulators = listOf(mod),
            startFrame = 0.0,
        )
        renderer.renderInPlace(AudioBuffer(128))
        return filter.lastCutoff
    }

    "strip path: +12 semitones sweeps to exactly 2x the base cutoff" {
        stripCutoffFor(+12.0) shouldBe 1600.0
    }

    "strip path: -12 semitones sweeps to exactly 0.5x the base cutoff" {
        stripCutoffFor(-12.0) shouldBe 400.0
    }

    "strip path: depth 0 is the exact identity" {
        stripCutoffFor(0.0) shouldBe 800.0
    }

    "strip path: +7 semitones is a fifth up (2^(7/12))" {
        stripCutoffFor(+7.0) shouldBe (800.0 * 1.4983070768766815 plusOrMinus 1e-9)
    }

    "strip path: clamp saturation goes THROUGH the shared clamp (deep depth on a real HPF)" {
        // A REAL SvfHPF as the Tunable: FilterModRenderer computes 15000 * 2^(48/12) = 240 kHz,
        // setCutoff funnels it through bilinearK's [5, Nyquist-1] clamp, and a highpass at
        // Nyquist passes (near) nothing.
        val sr = 48000.0
        val real = LowPassHighPassFilters.SvfHPF(15000.0, 0.707, sr)
        val mod = Voice.FilterModulator(
            filter = real,
            envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0),
            depth = +48.0,
            baseCutoff = 15000.0,
            drift = null,
        )
        val renderer = FilterModRenderer(modulators = listOf(mod), startFrame = 0.0)
        val rng = Random(99)
        var peakTail = 0.0
        repeat(40) { blk ->
            val buf = AudioBuffer(128)
            for (i in 0 until 128) buf[i] = rng.nextDouble() * 2.0 - 1.0
            renderer.renderInPlace(buf)
            real.process(buf, 0, 128)
            if (blk >= 20) {
                for (i in 0 until 128) {
                    val a = abs(buf[i])
                    if (a > peakTail) peakTail = a
                }
            }
        }
        peakTail shouldBe (0.0 plusOrMinus 0.02)
    }

    "strip path: drift stays a pure multiplier OUTSIDE the semitone exponent" {
        // Two seeded drifts produce the same multiplier sequence. With depth +12 vs depth 0,
        // the recorded cutoffs must differ by exactly 2x: cutoff = base * 2^(depth/12) * drift.
        // A mutant that moves driftMul INTO the exponent breaks the exact ratio.
        fun cutoffWithDrift(depth: Double): Double {
            val filter = RecordingFilter()
            val drift = AnalogDrift(
                analog = 1.0, sampleRate = 48000, rng = Random(1234),
            )
            val mod = Voice.FilterModulator(
                filter = filter,
                envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0),
                depth = depth,
                baseCutoff = 800.0,
                drift = drift,
            )
            FilterModRenderer(modulators = listOf(mod), startFrame = 0.0)
                .renderInPlace(AudioBuffer(128))
            return filter.lastCutoff
        }
        val with12 = cutoffWithDrift(+12.0)
        val with0 = cutoffWithDrift(0.0)
        (with12 / with0) shouldBe (2.0 plusOrMinus 1e-12)
        // and the drift actually moved the cutoff off the pure formula value
        (with0 == 800.0) shouldBe false
    }
})
