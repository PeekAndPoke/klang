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
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.sqrt

/**
 * The voice's pitch pipeline (vibrato, accelerate, FM) switched on and off through a real voice, and two of its
 * stages multiplying into one frequency-modulation buffer. The stage laws are pinned elsewhere: vibrato and FM in
 * `ModulatorPhaseWrapSpec`, accelerate in `AccelerateSemitoneLawSpec`, the pitch envelope in `EnvelopeLawSpec` and
 * `FastExp2Spec` (which also pins its multiply-in).
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

    "vibrato with depth 0 produces no modulation — and a real depth does" {
        // Audit F12. Both voices used to be built with `semitones = 0.0` (only `rate` differed,
        // and rate alone modulates nothing), so this compared a run against ITSELF: true by
        // construction, and no mutation anywhere could falsify it. The zero-claim only means
        // something next to a positive control proving the comparison can see a difference.
        fun render(vibrato: Voice.Vibrato): AudioBuffer {
            val voice = createSynthVoice(blockFrames = bf, signal = Ignitors.sine(), vibrato = vibrato)
            val ctx = createContext(blockFrames = bf)
            voice.render(ctx)
            return ctx.voiceBuffer
        }

        val bare = render(Voice.Vibrato(rate = 0.0, semitones = 0.0))
        val zeroDepth = render(Voice.Vibrato(rate = 5.0, semitones = 0.0))
        val negativeDepth = render(Voice.Vibrato(rate = 5.0, semitones = -0.25))
        val realDepth = render(Voice.Vibrato(rate = 5.0, semitones = 2.0))

        // The claim: depth 0 is inert whatever the rate says.
        diffRms(a = zeroDepth, b = bare) shouldBeLessThan 1e-6
        // ...and so is a negative depth: the pitch pipeline builds the vibrato only for a depth above 0.
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

    "vibrato and accelerate combine correctly" {
        // The only guard of accelerate's multiply-in branch (it runs after the vibrato): a mutant that
        // overwrote the vibrato's buffer instead of multiplying into it is red here and nowhere else in
        // the audio_be suite (probed 2026-09-27).
        val voiceBoth = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, semitones = 0.25),
            accelerate = Voice.Accelerate(semitones = 1.0)
        )
        val voiceVibratoOnly = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, semitones = 0.25),
        )
        val voiceAccelOnly = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(semitones = 1.0),
        )

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

    "pitch modulation affects FM modulator frequency" {
        val voiceWithVib = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, semitones = 0.5),
            fm = Voice.Fm(ratio = 2.0, depth = 100.0, envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0))
        )
        val voiceNoVib = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 0.0, semitones = 0.0),
            fm = Voice.Fm(ratio = 2.0, depth = 100.0, envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0))
        )

        val ctxWithVib = createContext(blockFrames = bf)
        val ctxNoVib = createContext(blockFrames = bf)
        voiceWithVib.render(ctxWithVib)
        voiceNoVib.render(ctxNoVib)

        // Vibrato should affect both carrier and FM modulator, producing different output
        val diff = diffRms(a = ctxWithVib.voiceBuffer, b = ctxNoVib.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }
})
