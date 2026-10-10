/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.accelerate
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.vibrato
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pitch modulation switched on and off through a real voice: the vibrato and accelerate as tree nodes (sprudel's `vib`
 * and `accelerate` are `classic()`'s stages since pitch pipeline steps 2 and 3), and the classic vibrato and FM stages
 * together (sprudel's `fm` since step 4). The laws are pinned elsewhere: the vibrato in `ClassicVibratoSpec` and
 * `ModulatorPhaseWrapSpec`, accelerate in `AccelerateSemitoneLawSpec` and `ClassicAccelerateSpec`, the pitch envelope in `EnvelopeLawSpec` and
 * `PitchEnvelopeModFastExp2Spec`.
 */
class PitchModulationTest : StringSpec({

    val bf = 512

    /** Compute RMS of the element-wise difference of two buffers. */
    fun diffRms(a: AudioBuffer, b: AudioBuffer, from: Int = 0, to: Int = a.size): Double {
        var sum = 0.0
        for (i in from until to) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum / (to - from))
    }

    "vibrato with depth 0 produces no modulation, and a real depth does" {
        // Audit F12. The vibrato is a tree node since pitch pipeline step 2 (`classic()`'s stage), so the voice
        // renders it through the instrument: depth 0 and a negative depth are gated off (the bare sine), a real
        // depth is the positive control that makes the zero claims falsifiable.
        fun render(rate: Double, semitones: Double?): AudioBuffer {
            val dsl = if (semitones == null) IgnitorDsl.Sine() else IgnitorDsl.Sine().vibrato(rate = rate, semitones = semitones)
            val voice = createSynthVoice(blockFrames = bf, signal = dsl.toExciter(random = Random(1)))
            val ctx = createContext(blockFrames = bf)
            voice.render(ctx)
            return ctx.voiceBuffer
        }

        val bare = render(rate = 0.0, semitones = null)
        val zeroDepth = render(rate = 5.0, semitones = 0.0)
        val negativeDepth = render(rate = 5.0, semitones = -0.25)
        val realDepth = render(rate = 5.0, semitones = 2.0)

        // The claim: depth 0 is inert whatever the rate says.
        diffRms(a = zeroDepth, b = bare) shouldBeLessThan 1e-6
        // ...and so is a negative depth: the gate builds the vibrato only for a depth above 0.
        diffRms(a = negativeDepth, b = bare) shouldBeLessThan 1e-6
        // The control, which is what makes the line above falsifiable at all.
        diffRms(a = realDepth, b = bare) shouldBeGreaterThan 1e-3
    }

    "accelerate with 0 semitones produces no pitch change, and a real glide does" {
        // Audit F12. Accelerate is a tree node since pitch pipeline step 3 (`classic()`'s stage), so the voice renders
        // it through the instrument: 0 semitones is gated off (the bare sine), a real glide is the positive control.
        fun render(semitones: Double?): AudioBuffer {
            val dsl = if (semitones == null) IgnitorDsl.Sine() else IgnitorDsl.Sine().accelerate(semitones = semitones)
            val voice = createSynthVoice(blockFrames = bf, signal = dsl.toExciter(random = Random(1)))
            val ctx = createContext(blockFrames = bf)
            voice.render(ctx)
            return ctx.voiceBuffer
        }

        val bare = render(semitones = null)
        val zero = render(semitones = 0.0)
        val glide = render(semitones = 12.0)

        diffRms(a = zero, b = bare) shouldBeLessThan 1e-6
        diffRms(a = glide, b = bare) shouldBeGreaterThan 1e-3
    }

    "the classic vibrato and FM stages combine: the product differs from each alone" {
        // Sprudel's `vib` and `fm` as `classic()`'s stages (pitch pipeline steps 2 and 4), written as the slots the
        // doors write: both reach the source.
        val vib = mapOf("vibrato.rate" to 5.0, "vibrato.semitones" to 0.5)
        val fm = mapOf("fm.ratio" to 2.0, "fm.depth" to 100.0)

        fun render(bag: Map<String, Double>): AudioBuffer {
            val instrument = IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.0)).classic()
            val voice = createSynthVoice(blockFrames = bf, signal = instrument.toExciter(ignitorParams = bag, random = Random(1)))
            val ctx = createContext(blockFrames = bf)
            voice.render(ctx)
            return ctx.voiceBuffer
        }

        val both = render(vib + fm)
        val noVib = render(fm)
        val noFm = render(vib)

        // Both reach the source: without the vibrato, and without the FM, the voice is something else.
        (diffRms(a = both, b = noVib) > 1e-4) shouldBe true
        (diffRms(a = both, b = noFm) > 1e-4) shouldBe true
    }
})
