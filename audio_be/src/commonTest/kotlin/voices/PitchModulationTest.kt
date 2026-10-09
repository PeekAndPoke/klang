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
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.toExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.vibrato
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pitch modulation switched on and off through a real voice: the vibrato as a tree node (sprudel's `vib` is
 * `classic()`'s stage since pitch pipeline step 2), the strip's accelerate, and a tree mod combined with the strip's
 * buffer in `ModApplyingIgnitor`. The laws are pinned elsewhere: the vibrato in `ClassicVibratoSpec` and
 * `ModulatorPhaseWrapSpec`, accelerate in `AccelerateSemitoneLawSpec`, the pitch envelope in `EnvelopeLawSpec` and
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

    "accelerate with 0 semitones produces no pitch change — and a real glide does" {
        // Audit F12: `Voice.Accelerate(semitones = 0.0)` IS the helper default, so the two
        // voices were configured identically. Positive control added.
        fun render(accelerate: Voice.Accelerate): AudioBuffer {
            val voice = createSynthVoice(blockFrames = bf, signal = Ignitors.sine(), accelerate = accelerate)
            val ctx = createContext(blockFrames = bf)
            voice.render(ctx)
            return ctx.voiceBuffer
        }

        val bare = render(Voice.Accelerate(0.0))
        val zero = render(Voice.Accelerate(semitones = 0.0))
        val glide = render(Voice.Accelerate(semitones = 12.0))

        diffRms(a = zero, b = bare) shouldBeLessThan 1e-6
        diffRms(a = glide, b = bare) shouldBeGreaterThan 1e-3
    }

    "a tree vibrato and the strip's accelerate combine" {
        // The vibrato is the instrument's (a tree node, pitch pipeline step 2), the accelerate still the strip's:
        // the source reads their product (`ModApplyingIgnitor`: the tree mod times the strip's `phaseMod`).
        val vibrato = { IgnitorDsl.Sine().vibrato(rate = 5.0, semitones = 0.25).toExciter(random = Random(1)) }
        val voiceBoth = createSynthVoice(blockFrames = bf, signal = vibrato(), accelerate = Voice.Accelerate(semitones = 1.0))
        val voiceVibratoOnly = createSynthVoice(blockFrames = bf, signal = vibrato())
        val voiceAccelOnly = createSynthVoice(blockFrames = bf, signal = Ignitors.sine(), accelerate = Voice.Accelerate(semitones = 1.0))

        val ctxBoth = createContext(blockFrames = bf)
        val ctxVib = createContext(blockFrames = bf)
        val ctxAcc = createContext(blockFrames = bf)
        voiceBoth.render(ctxBoth)
        voiceVibratoOnly.render(ctxVib)
        voiceAccelOnly.render(ctxAcc)

        // Combined should differ from vibrato-only and accelerate-only
        val diffFromVib = diffRms(a = ctxBoth.voiceBuffer, b = ctxVib.voiceBuffer)
        val diffFromAcc = diffRms(a = ctxBoth.voiceBuffer, b = ctxAcc.voiceBuffer)
        (diffFromVib > 1e-4) shouldBe true
        (diffFromAcc > 1e-4) shouldBe true
    }

    "a tree vibrato and the strip's FM combine: the product differs from each alone" {
        val fm = { Voice.Fm(ratio = 2.0, depth = 100.0, envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0)) }
        val voiceWithVib = createSynthVoice(
            blockFrames = bf,
            signal = IgnitorDsl.Sine().vibrato(rate = 5.0, semitones = 0.5).toExciter(random = Random(1)),
            fm = fm(),
        )
        val voiceNoVib = createSynthVoice(blockFrames = bf, signal = Ignitors.sine(), fm = fm())
        val voiceNoFm = createSynthVoice(blockFrames = bf, signal = IgnitorDsl.Sine().vibrato(rate = 5.0, semitones = 0.5).toExciter(random = Random(1)))

        val ctxWithVib = createContext(blockFrames = bf)
        val ctxNoVib = createContext(blockFrames = bf)
        val ctxNoFm = createContext(blockFrames = bf)
        voiceWithVib.render(ctxWithVib)
        voiceNoVib.render(ctxNoVib)
        voiceNoFm.render(ctxNoFm)

        // Both reach the source: without the vibrato, and without the FM, the voice is something else.
        (diffRms(a = ctxWithVib.voiceBuffer, b = ctxNoVib.voiceBuffer) > 1e-4) shouldBe true
        (diffRms(a = ctxWithVib.voiceBuffer, b = ctxNoFm.voiceBuffer) > 1e-4) shouldBe true
    }
})
