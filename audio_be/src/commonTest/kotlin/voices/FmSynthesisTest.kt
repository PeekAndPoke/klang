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
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.wrapPhase
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tests for FM (Frequency Modulation) synthesis.
 * Verifies that FM correctly modulates the carrier frequency.
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

        val env = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
        val none = render(null)
        val zeroDepth = render(Voice.Fm(ratio = 2.0, depth = 0.0, envelope = env))
        val realDepth = render(Voice.Fm(ratio = 2.0, depth = 100.0, envelope = env))

        diffRms(zeroDepth, none) shouldBeLessThan 1e-6
        diffRms(realDepth, none) shouldBeGreaterThan 1e-3
        // and the carrier is actually sounding, so the comparisons are not all-silence
        rms(none) shouldBeGreaterThan 0.0
    }

    "FM modulator ratio affects modulation frequency" {
        val carrierFreq = 440.0

        val voice1 = createSynthVoice(
            blockFrames = bf,
            freqHz = carrierFreq,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 2.0, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voice2 = createSynthVoice(
            blockFrames = bf,
            freqHz = carrierFreq,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 0.5, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )

        val ctx1 = createContext(blockFrames = bf)
        val ctx2 = createContext(blockFrames = bf)
        voice1.render(ctx1)
        voice2.render(ctx2)

        // Different ratios should produce different timbres
        val diff = diffRms(ctx1.voiceBuffer, ctx2.voiceBuffer)
        (diff > 1e-3) shouldBe true
    }

    "FM depth controls modulation intensity" {
        val voiceHigh = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 1.0, depth = 200.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceLow = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 1.0, depth = 10.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceNone = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        val ctxHigh = createContext(blockFrames = bf)
        val ctxLow = createContext(blockFrames = bf)
        val ctxNone = createContext(blockFrames = bf)
        voiceHigh.render(ctxHigh)
        voiceLow.render(ctxLow)
        voiceNone.render(ctxNone)

        // Higher depth should produce more harmonic content (larger difference from clean sine)
        val diffHigh = diffRms(ctxHigh.voiceBuffer, ctxNone.voiceBuffer)
        val diffLow = diffRms(ctxLow.voiceBuffer, ctxNone.voiceBuffer)
        (diffHigh > diffLow) shouldBe true
    }

    "FM envelope modulates FM depth over time" {
        val bfLocal = 64
        // FM with attack envelope: depth ramps from 0 to full over 256 frames
        val voiceFmEnv = createSynthVoice(
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(
                ratio = 2.0, depth = 200.0,
                envelope = Voice.Envelope(attackFrames = 256.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0)
            )
        )
        // Clean sine for comparison
        val voiceClean = createSynthVoice(
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        // Render early block (FM envelope near 0)
        val ctxEarly = createContext(blockStart = 0.0, blockFrames = bfLocal)
        val ctxCleanEarly = createContext(blockStart = 0.0, blockFrames = bfLocal)
        voiceFmEnv.render(ctxEarly)
        voiceClean.render(ctxCleanEarly)
        val diffEarly = diffRms(ctxEarly.voiceBuffer, ctxCleanEarly.voiceBuffer)

        // Render late block (FM envelope near 1.0)
        val ctxLate = createContext(blockStart = 256.0, blockFrames = bfLocal)
        val ctxCleanLate = createContext(blockStart = 256.0, blockFrames = bfLocal)
        voiceFmEnv.render(ctxLate)
        voiceClean.render(ctxCleanLate)
        val diffLate = diffRms(ctxLate.voiceBuffer, ctxCleanLate.voiceBuffer)

        // Late block (full FM) should deviate more from clean sine than early block (low FM)
        (diffLate > diffEarly) shouldBe true
    }

    "FM envelope with decay phase" {
        val bfLocal = 64
        val voiceFm = createSynthVoice(
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(
                ratio = 1.5, depth = 200.0,
                envelope = Voice.Envelope(attackFrames = 100.0, decayFrames = 100.0, sustainLevel = 0.5, releaseFrames = 0.0)
            )
        )
        val voiceClean = createSynthVoice(
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        // At decay phase (frame 150), envelope should be between 1.0 and 0.5
        val ctxFm = createContext(blockStart = 150.0, blockFrames = bfLocal)
        val ctxClean = createContext(blockStart = 150.0, blockFrames = bfLocal)
        voiceFm.render(ctxFm)
        voiceClean.render(ctxClean)

        // FM should still be active during decay — output differs from clean
        val diff = diffRms(ctxFm.voiceBuffer, ctxClean.voiceBuffer)
        (diff > 1e-3) shouldBe true
    }

    "FM works with SampleVoice" {
        val bfLocal = 256
        val sample = TestSamples.sine(size = 4096)

        val voiceFm = VoiceTestHelpers.createSampleVoice(
            sample = sample,
            blockFrames = bfLocal,
            fm = Voice.Fm(ratio = 2.0, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceClean = VoiceTestHelpers.createSampleVoice(
            sample = sample,
            blockFrames = bfLocal,
            fm = null
        )

        val ctxFm = createContext(blockFrames = bfLocal)
        val ctxClean = createContext(blockFrames = bfLocal)
        voiceFm.render(ctxFm)
        voiceClean.render(ctxClean)

        // FM should modulate sample playback rate — output differs from clean
        val diff = diffRms(ctxFm.voiceBuffer, ctxClean.voiceBuffer)
        (diff > 1e-4) shouldBe true
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

        val fm = Voice.Fm(ratio = ratio, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
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

    "FM with very high ratio produces complex spectrum" {
        val voiceHigh = createSynthVoice(
            blockFrames = bf,
            freqHz = 100.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 10.0, depth = 500.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceClean = createSynthVoice(
            blockFrames = bf,
            freqHz = 100.0,
            signal = Ignitors.sine(),
            fm = null
        )

        val ctxHigh = createContext(blockFrames = bf)
        val ctxClean = createContext(blockFrames = bf)
        voiceHigh.render(ctxHigh)
        voiceClean.render(ctxClean)

        // High ratio + high depth should produce drastically different output
        val diff = diffRms(ctxHigh.voiceBuffer, ctxClean.voiceBuffer)
        (diff > 0.01) shouldBe true
    }

    "FM with fractional ratio works correctly" {
        val voiceFm = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 0.25, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceClean = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        val ctxFm = createContext(blockFrames = bf)
        val ctxClean = createContext(blockFrames = bf)
        voiceFm.render(ctxFm)
        voiceClean.render(ctxClean)

        // Sub-harmonic FM should produce different output from clean sine
        val diff = diffRms(ctxFm.voiceBuffer, ctxClean.voiceBuffer)
        (diff > 1e-3) shouldBe true
    }

    "FM combined with vibrato" {
        val voiceBoth = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 1.5, depth = 50.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)),
            vibrato = Voice.Vibrato(rate = 5.0, semitones = 0.25)
        )
        val voiceFmOnly = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 1.5, depth = 50.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)),
        )

        val ctxBoth = createContext(blockFrames = bf)
        val ctxFm = createContext(blockFrames = bf)
        voiceBoth.render(ctxBoth)
        voiceFmOnly.render(ctxFm)

        // Adding vibrato to FM should change the output
        val diff = diffRms(ctxBoth.voiceBuffer, ctxFm.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "FM envelope at sustain level" {
        val bfLocal = 128
        // Sustain at 0.3 -> effective depth = 30
        val voiceSustain = createSynthVoice(
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(
                ratio = 2.0, depth = 100.0,
                envelope = Voice.Envelope(attackFrames = 50.0, decayFrames = 50.0, sustainLevel = 0.3, releaseFrames = 0.0)
            )
        )
        // Full depth (sustain = 1.0) for comparison
        val voiceFull = createSynthVoice(
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(
                ratio = 2.0, depth = 100.0,
                envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 0.0)
            )
        )
        val voiceClean = createSynthVoice(
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        // All at sustain phase (frame 200, well past attack+decay)
        val ctxSustain = createContext(blockStart = 200.0, blockFrames = bfLocal)
        val ctxFull = createContext(blockStart = 200.0, blockFrames = bfLocal)
        val ctxClean = createContext(blockStart = 200.0, blockFrames = bfLocal)
        voiceSustain.render(ctxSustain)
        voiceFull.render(ctxFull)
        voiceClean.render(ctxClean)

        // Sustain at 0.3 should produce less FM deviation from clean than full depth
        val diffSustain = diffRms(ctxSustain.voiceBuffer, ctxClean.voiceBuffer)
        val diffFull = diffRms(ctxFull.voiceBuffer, ctxClean.voiceBuffer)
        (diffFull > diffSustain) shouldBe true
    }

    "FM envelope release phase" {
        val bfLocal = 64
        // Amp envelope with matching release so the voice output doesn't go silent
        val ampEnv = Voice.Envelope(0.0, 0.0, 1.0, 200.0, level = 1.0)

        val voiceRelease = createSynthVoice(
            startFrame = 0.0, endFrame = 300.0, gateEndFrame = 100.0,
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            envelope = ampEnv,
            fm = Voice.Fm(
                ratio = 2.0, depth = 200.0,
                envelope = Voice.Envelope(attackFrames = 0.0, decayFrames = 0.0, sustainLevel = 1.0, releaseFrames = 100.0)
            )
        )
        val voiceClean = createSynthVoice(
            startFrame = 0.0, endFrame = 300.0, gateEndFrame = 100.0,
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            envelope = ampEnv,
            fm = null
        )

        // During release phase (frame 150 — midway through release)
        val ctxRelease = createContext(blockStart = 150.0, blockFrames = bfLocal)
        val ctxClean = createContext(blockStart = 150.0, blockFrames = bfLocal)
        voiceRelease.render(ctxRelease)
        voiceClean.render(ctxClean)

        // FM depth should still be active during release
        val diff = diffRms(ctxRelease.voiceBuffer, ctxClean.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "FM with negative depth works" {
        val voiceNeg = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 2.0, depth = -100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceClean = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        val ctxNeg = createContext(blockFrames = bf)
        val ctxClean = createContext(blockFrames = bf)
        voiceNeg.render(ctxNeg)
        voiceClean.render(ctxClean)

        // Negative depth should still produce FM modulation
        val diff = diffRms(ctxNeg.voiceBuffer, ctxClean.voiceBuffer)
        (diff > 1e-3) shouldBe true
    }

    "FM ratio of 1.0 produces harmonic sidebands" {
        val voiceFm = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 1.0, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceClean = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        val ctxFm = createContext(blockFrames = bf)
        val ctxClean = createContext(blockFrames = bf)
        voiceFm.render(ctxFm)
        voiceClean.render(ctxClean)

        // FM at ratio 1.0 should produce harmonic sidebands — differs from clean sine
        val diff = diffRms(ctxFm.voiceBuffer, ctxClean.voiceBuffer)
        (diff > 1e-3) shouldBe true
    }
})
