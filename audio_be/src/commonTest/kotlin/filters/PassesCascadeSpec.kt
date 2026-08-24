/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.FILTER_MAX_PASSES
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * C5 guards: `passes` cascades the SVF stage with the STAGGERED Butterworth q ladder
 * (maintainer decision: the cascade stays -3 dB AT fc — `lpf(800, passes = 2)` still means
 * 800; a plain per-stage-q cascade would be -6 dB at fc with the knee at ~0.64·fc).
 */
class PassesCascadeSpec : StringSpec({

    val sr = 48000.0
    val frames = 48000

    /** RMS of a [freq] sine pushed through [filter], steady-state half only. */
    fun rmsThrough(filter: AudioFilter, freq: Double): Double {
        val block = 128
        val buf = AudioBuffer(block)
        var sum = 0.0
        var n = 0
        var t = 0
        while (t < frames) {
            for (i in 0 until block) {
                buf[i] = sin(2.0 * PI * freq * (t + i) / sr)
            }
            filter.process(buf, 0, block)
            if (t >= frames / 2) {
                for (i in 0 until block) {
                    sum += buf[i] * buf[i]
                    n++
                }
            }
            t += block
        }
        return sqrt(sum / n)
    }

    fun db(x: Double): Double = 20.0 * log10(x)

    /** Steady-state RMS of a [freq] sine rendered THROUGH the ignitor door (no optimizer). */
    fun rmsIgnitor(dsl: IgnitorDsl, freq: Double): Double {
        val chain = dsl.toExciter()
        val block = 128
        val ctx = IgniteContext(
            sampleRate = sr.toInt(),
            voiceDurationFrames = frames,
            gateEndFrame = frames,
            releaseFrames = 0,
            voiceEndFrame = frames,
            scratchBuffers = ScratchBuffers(blockFrames = block),
        )
        ctx.offset = 0
        ctx.length = block
        val buf = AudioBuffer(block)
        var sum = 0.0
        var n = 0
        var t = 0
        while (t < frames / 4) {
            chain.generate(buf, freq, ctx)
            if (t >= frames / 8) {
                for (i in 0 until block) {
                    sum += buf[i] * buf[i]
                    n++
                }
            }
            ctx.voiceElapsedFrames += block
            t += block
        }
        return sqrt(sum / n)
    }

    fun rmsIgnitorLp(passes: Int, freq: Double): Double =
        rmsIgnitor(IgnitorDsl.Sine().lowpass(1000.0, 0.707, passes = passes), freq)

    fun rmsIgnitorHp(passes: Int, freq: Double): Double =
        rmsIgnitor(IgnitorDsl.Sine().highpass(1000.0, 0.707, passes = passes), freq)

    "the q ladder: passes = 1 is the user q VERBATIM; passes = 2 is the 4th-order Butterworth pair" {
        butterworthQLadder(1, 1.2).toList() shouldBe listOf(1.2)
        val two = butterworthQLadder(2, 0.707)
        two.size shouldBe 2
        // classic 4th-order Butterworth stage Qs, scaled by 0.707/0.7071 ≈ 1
        two[0] shouldBe (0.5412 plusOrMinus 0.001)
        two[1] shouldBe (1.3065 plusOrMinus 0.002)
        // 0 and negative coerce to a single stage
        butterworthQLadder(0, 0.9).toList() shouldBe listOf(0.9)
        butterworthQLadder(-3, 0.9).toList() shouldBe listOf(0.9)
        // ...and the resource ceiling holds: a live-typed `lpx(1e9)` must not allocate a
        // billion stages inside a note-on. FILTER_MAX_PASSES is the ONE bound (coercePasses).
        butterworthQLadder(1_000_000, 0.707).size shouldBe FILTER_MAX_PASSES
        LowPassHighPassFilters.createLPF(cutoffHz = 800.0, q = 0.707, sampleRate = sr, passes = 1_000_000)
            .shouldBeInstanceOf<LowPassHighPassFilters.PassCascadeFilter>()
    }

    "passes = 1 builds the plain SvfLPF — no wrapper, bit-identical path" {
        LowPassHighPassFilters.createLPF(cutoffHz = 800.0, q = 0.707, sampleRate = sr)
            .shouldBeInstanceOf<LowPassHighPassFilters.SvfLPF>()
        LowPassHighPassFilters.createLPF(cutoffHz = 800.0, q = 0.707, sampleRate = sr, passes = 2)
            .shouldBeInstanceOf<LowPassHighPassFilters.PassCascadeFilter>()
    }

    "STAGGERED q: the passes = 2 cascade is still ~-3 dB AT the cutoff (the C5 decision)" {
        val fc = 1000.0
        val cascade = LowPassHighPassFilters.createLPF(cutoffHz = fc, q = 0.707, sampleRate = sr, passes = 2)
        val atFc = rmsThrough(cascade, fc) / rmsThrough(NoOpAudioFilter, fc)
        db(atFc) shouldBe (-3.0 plusOrMinus 0.5)
    }

    "slope: passes = 2 measures ~-24 dB one octave above fc, passes = 1 ~-12 (lowpass)" {
        val fc = 1000.0
        fun attAt2Fc(passes: Int): Double {
            val f = LowPassHighPassFilters.createLPF(cutoffHz = fc, q = 0.707, sampleRate = sr, passes = passes)
            return db(rmsThrough(f, 2.0 * fc) / rmsThrough(NoOpAudioFilter, 2.0 * fc))
        }
        attAt2Fc(1) shouldBe (-12.3 plusOrMinus 1.0)
        attAt2Fc(2) shouldBe (-24.1 plusOrMinus 1.5)
    }

    "slope mirror: highpass passes = 2 measures ~-24 dB one octave BELOW fc" {
        val fc = 1000.0
        fun attAtHalfFc(passes: Int): Double {
            val f = LowPassHighPassFilters.createHPF(cutoffHz = fc, q = 0.707, sampleRate = sr, passes = passes)
            return db(rmsThrough(f, fc / 2.0) / rmsThrough(NoOpAudioFilter, fc / 2.0))
        }
        attAtHalfFc(1) shouldBe (-12.3 plusOrMinus 1.0)
        attAtHalfFc(2) shouldBe (-24.1 plusOrMinus 1.5)
    }

    "ignitor door: the runtime folds passes into a cascade — one octave up loses ~12 dB more" {
        // Authored tree straight through toExciter (no optimizer): sine at 2 kHz through a
        // 1 kHz lowpass; passes = 2 must attenuate ~12 dB more than passes = 1. Kills a
        // dropped cascade fold in IgnitorDslRuntime.
        db(rmsIgnitorLp(2, 2000.0) / rmsIgnitorLp(1, 2000.0)) shouldBe (-11.8 plusOrMinus 1.5)
    }

    "ignitor door, AT fc: the STAGGER survives — passes = 2 is the same -3 dB as passes = 1" {
        // The discriminating row for the C5 decision, on the door that had none: at fc a
        // staggered pair multiplies to 0.5412*1.3065 = 0.707 (-3.01 dB), a plain q-per-stage
        // pair to 0.707^2 = 0.5 (-6.02 dB). Dropping `.scaledBy(rel[k])` in IgnitorDslRuntime
        // reads -3.0 here; the octave-above rows above cannot tell the two apart.
        db(rmsIgnitorLp(2, 1000.0) / rmsIgnitorLp(1, 1000.0)) shouldBe (0.0 plusOrMinus 0.4)
    }

    "ignitor door, HIGHPASS: cascade slope one octave below fc, and the stagger AT fc" {
        db(rmsIgnitorHp(2, 500.0) / rmsIgnitorHp(1, 500.0)) shouldBe (-11.8 plusOrMinus 1.5)
        db(rmsIgnitorHp(2, 1000.0) / rmsIgnitorHp(1, 1000.0)) shouldBe (0.0 plusOrMinus 0.4)
    }

    "Tunable: setCutoff retunes EVERY stage of the cascade" {
        val fc = 500.0
        val cascade = LowPassHighPassFilters.createLPF(cutoffHz = 8000.0, q = 0.707, sampleRate = sr, passes = 2)
        (cascade as AudioFilter.Tunable).setCutoff(fc)
        // after retuning to 500 Hz, a 1 kHz sine (one octave above) must see the full
        // -24 dB/oct cascade attenuation — a single-stage-forwarding mutant reads ~-12
        val att = db(rmsThrough(cascade, 2.0 * fc) / rmsThrough(NoOpAudioFilter, 2.0 * fc))
        att shouldBe (-24.1 plusOrMinus 1.5)
    }
})
