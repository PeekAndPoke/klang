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
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_be.utils.wrapPhase
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The voice's FM ([Voice.Fm], the strip pitch pipeline's `FmRenderer`) through a real voice: when it is built,
 * and how far its modulator's phase moves. Its law (the multiplier per sample against the ratio, the depth and
 * the envelope level) is pinned in `ModulatorPhaseWrapSpec`, the control-rate envelope in `EnvelopeLawSpec`.
 */
class FmSynthesisTest : StringSpec({

    val bf = 512

    /** Compute RMS of a float array slice. */
    fun rms(buf: AudioBuffer, from: Int = 0, to: Int = buf.size): Double {
        var sum = 0.0
        for (i in from until to) {
            sum += buf[i] * buf[i]
        }
        return sqrt(sum / (to - from))
    }

    /** Compute RMS of the element-wise difference of two buffers. */
    fun diffRms(a: AudioBuffer, b: AudioBuffer, from: Int = 0, to: Int = a.size): Double {
        var sum = 0.0
        for (i in from until to) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum / (to - from))
    }

    "FM at depth 0 is the unmodulated carrier, and a real depth is not" {
        // Audit F12, two tests merged into one because they were the same claim. "FM with
        // depth 0 produces no modulation" compared depth-0 against null — identical by
        // construction, since the pipeline gate builds no FmRenderer in either case — and
        // "FM with null is disabled" only asserted the voice made SOME sound, which its name
        // does not promise. Neither could be falsified.
        //
        // The three-way is what has teeth: null and depth-0 must agree (either one applying
        // modulation breaks it), and a real depth must NOT agree with them (which is what
        // makes the first half mean something).
        fun render(fm: Voice.Fm?): AudioBuffer {
            val voice = createSynthVoice(blockFrames = bf, freqHz = 440.0, signal = Ignitors.sine(), fm = fm)
            val ctx = createContext(blockFrames = bf)
            voice.render(ctx)
            return ctx.voiceBuffer
        }

        val env = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0)
        val none = render(null)
        val zeroDepth = render(Voice.Fm(ratio = 2.0, depth = 0.0, envelope = env))
        val realDepth = render(Voice.Fm(ratio = 2.0, depth = 100.0, envelope = env))
        val negativeDepth = render(Voice.Fm(ratio = 2.0, depth = -100.0, envelope = env))

        diffRms(a = zeroDepth, b = none) shouldBeLessThan 1e-6
        diffRms(a = realDepth, b = none) shouldBeGreaterThan 1e-3
        // A negative depth is a raw value like any other: the pitch pipeline builds the modulator for any depth but 0.
        diffRms(a = negativeDepth, b = none) shouldBeGreaterThan 1e-3
        // and the carrier is actually sounding, so the comparisons are not all-silence
        rms(none) shouldBeGreaterThan 0.0
    }

    "FM modulator phase advances by the EXPECTED amount, not merely upward" {
        // Audit F11, re-confirmed 2026-08-31 against the current tree: the assertion was
        // `afterPhase > initialPhase` — the phase moved by SOME positive amount. Multiplying
        // `modInc` by 0.001 in FmRenderer (a modulator running 1000x too slow: a different
        // instrument, not a detuned patch) leaves the WHOLE audio_be suite green. The
        // quantity IS the behaviour.
        val ratio = 1.0
        val freqHz = 440.0
        val frames = 100
        val sampleRate = 44100

        val fm = Voice.Fm(ratio = ratio, depth = 100.0, envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0))
        val voice = createSynthVoice(freqHz = freqHz, fm = fm, sampleRate = sampleRate)

        fm.modPhase shouldBe 0.0

        val ctx = createContext(blockFrames = frames)
        voice.render(ctx)

        // Derived from the DEFINITION of an FM modulator rather than from the renderer: the
        // modulator runs at freq x ratio, so its phase advances TWO_PI x modFreq / sr per
        // sample, and the renderer wraps once at the end of the block.
        val expected = (frames * TWO_PI * (freqHz * ratio) / sampleRate).wrapPhase(TWO_PI)

        abs(fm.modPhase - expected) shouldBeLessThan 1e-9
    }
})
