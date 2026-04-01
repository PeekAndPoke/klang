package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.sqrt

/**
 * Tests for FM (Frequency Modulation) synthesis.
 * Verifies that FM correctly modulates the carrier frequency.
 */
class FmSynthesisTest : StringSpec({

    val bf = 512

    /** Compute RMS of a float array slice. */
    fun rms(buf: FloatArray, from: Int = 0, to: Int = buf.size): Double {
        var sum = 0.0
        for (i in from until to) {
            sum += buf[i].toDouble() * buf[i].toDouble()
        }
        return sqrt(sum / (to - from))
    }

    /** Compute RMS of the element-wise difference of two buffers. */
    fun diffRms(a: FloatArray, b: FloatArray, from: Int = 0, to: Int = a.size): Double {
        var sum = 0.0
        for (i in from until to) {
            val d = a[i].toDouble() - b[i].toDouble()
            sum += d * d
        }
        return sqrt(sum / (to - from))
    }

    "FM with depth 0 produces no modulation" {
        val voiceWith = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = Voice.Fm(ratio = 2.0, depth = 0.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        val ctxWith = createContext(blockFrames = bf)
        val ctxWithout = createContext(blockFrames = bf)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // FM with depth 0 should produce output identical to no-FM
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff < 1e-6) shouldBe true
    }

    "FM with null is disabled" {
        val voice = createSynthVoice(
            blockFrames = bf,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            fm = null
        )

        val ctx = createContext(blockFrames = bf)
        voice.render(ctx)

        // Should produce non-zero output (plain sine)
        val outputRms = rms(ctx.voiceBuffer)
        (outputRms > 0.0) shouldBe true
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
        val ctxEarly = createContext(blockStart = 0, blockFrames = bfLocal)
        val ctxCleanEarly = createContext(blockStart = 0, blockFrames = bfLocal)
        voiceFmEnv.render(ctxEarly)
        voiceClean.render(ctxCleanEarly)
        val diffEarly = diffRms(ctxEarly.voiceBuffer, ctxCleanEarly.voiceBuffer)

        // Render late block (FM envelope near 1.0)
        val ctxLate = createContext(blockStart = 256, blockFrames = bfLocal)
        val ctxCleanLate = createContext(blockStart = 256, blockFrames = bfLocal)
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
        val ctxFm = createContext(blockStart = 150, blockFrames = bfLocal)
        val ctxClean = createContext(blockStart = 150, blockFrames = bfLocal)
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

    "FM modulator phase advances correctly" {
        val fm = Voice.Fm(
            ratio = 1.0,
            depth = 100.0,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
        )

        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = fm
        )

        val initialPhase = fm.modPhase

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        val afterPhase = fm.modPhase

        // Phase should have advanced
        (afterPhase > initialPhase) shouldBe true
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
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02)
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
        val ctxSustain = createContext(blockStart = 200, blockFrames = bfLocal)
        val ctxFull = createContext(blockStart = 200, blockFrames = bfLocal)
        val ctxClean = createContext(blockStart = 200, blockFrames = bfLocal)
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
        val ampEnv = Voice.Envelope(0.0, 0.0, 1.0, 200.0, 1.0)

        val voiceRelease = createSynthVoice(
            startFrame = 0, endFrame = 300, gateEndFrame = 100,
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
            startFrame = 0, endFrame = 300, gateEndFrame = 100,
            blockFrames = bfLocal,
            freqHz = 440.0,
            signal = Ignitors.sine(),
            envelope = ampEnv,
            fm = null
        )

        // During release phase (frame 150 — midway through release)
        val ctxRelease = createContext(blockStart = 150, blockFrames = bfLocal)
        val ctxClean = createContext(blockStart = 150, blockFrames = bfLocal)
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
