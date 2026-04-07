package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSampleVoice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.sqrt

/**
 * Tests for pitch modulation (vibrato, accelerate, pitch envelope).
 * Verifies that pitch modulation is correctly applied before signal generation.
 */
class PitchModulationTest : StringSpec({

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

    "vibrato with depth 0 produces no modulation" {
        val voiceWith = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.0)
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0)
        )

        val ctxWith = createContext(blockFrames = bf)
        val ctxWithout = createContext(blockFrames = bf)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // With depth=0, output should be identical to no-vibrato
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff < 1e-6) shouldBe true
    }

    "vibrato with rate and depth modulates pitch" {
        val voiceWith = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.25)
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0)
        )

        val ctxWith = createContext(blockFrames = bf)
        val ctxWithout = createContext(blockFrames = bf)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // Vibrato should produce output that differs from non-vibrato
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "vibrato with high rate produces fast modulation" {
        val voiceFast = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 20.0, depth = 0.5)
        )
        val voiceSlow = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 2.0, depth = 0.5)
        )

        val ctxFast = createContext(blockFrames = bf)
        val ctxSlow = createContext(blockFrames = bf)
        voiceFast.render(ctxFast)
        voiceSlow.render(ctxSlow)

        // Fast and slow vibrato should produce different outputs
        val diff = diffRms(ctxFast.voiceBuffer, ctxSlow.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "vibrato with high depth produces wide pitch swings" {
        val voiceWide = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.5)
        )
        val voiceNarrow = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.01)
        )
        val voiceNone = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0)
        )

        val ctxWide = createContext(blockFrames = bf)
        val ctxNarrow = createContext(blockFrames = bf)
        val ctxNone = createContext(blockFrames = bf)
        voiceWide.render(ctxWide)
        voiceNarrow.render(ctxNarrow)
        voiceNone.render(ctxNone)

        // Higher depth should produce a larger difference from no-vibrato
        val diffWide = diffRms(ctxWide.voiceBuffer, ctxNone.voiceBuffer)
        val diffNarrow = diffRms(ctxNarrow.voiceBuffer, ctxNone.voiceBuffer)
        (diffWide > diffNarrow) shouldBe true
    }

    "accelerate with rate 0 produces no pitch change" {
        val voiceWith = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 0.0)
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
        )

        val ctxWith = createContext(blockFrames = bf)
        val ctxWithout = createContext(blockFrames = bf)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // Zero accelerate should be identical to no accelerate
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff < 1e-6) shouldBe true
    }

    "accelerate with positive amount increases pitch over time" {
        val blockSize = 256
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1024,
            blockFrames = blockSize,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 2.0)
        )
        val voiceRef = createSynthVoice(
            startFrame = 0,
            endFrame = 1024,
            blockFrames = blockSize,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 0.0)
        )

        // Render first half
        val ctxFirst = createContext(blockStart = 0, blockFrames = blockSize)
        voice.render(ctxFirst)
        val firstHalf = ctxFirst.voiceBuffer.copyOf()

        // Render second half
        val ctxSecond = createContext(blockStart = 512, blockFrames = blockSize)
        voice.render(ctxSecond)
        val secondHalf = ctxSecond.voiceBuffer.copyOf()

        // Render reference (no accelerate) at both positions
        val ctxRefFirst = createContext(blockStart = 0, blockFrames = blockSize)
        voiceRef.render(ctxRefFirst)
        val ctxRefSecond = createContext(blockStart = 512, blockFrames = blockSize)
        voiceRef.render(ctxRefSecond)

        // First half should differ from second half (pitch is changing)
        val diffFirstSecond = diffRms(firstHalf, secondHalf)
        (diffFirstSecond > 1e-4) shouldBe true

        // The accelerated voice should differ from the reference
        val diffFromRef = diffRms(ctxSecond.voiceBuffer, ctxRefSecond.voiceBuffer)
        (diffFromRef > 1e-4) shouldBe true
    }

    "accelerate with negative rate decreases pitch over time" {
        val voiceNeg = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = -0.5)
        )
        val voiceNone = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            blockFrames = bf,
            signal = Ignitors.sine(),
        )

        val ctxNeg = createContext(blockFrames = bf)
        val ctxNone = createContext(blockFrames = bf)
        voiceNeg.render(ctxNeg)
        voiceNone.render(ctxNone)

        // Negative accelerate should differ from no-accelerate
        val diff = diffRms(ctxNeg.voiceBuffer, ctxNone.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "pitch envelope with null is disabled" {
        val voiceWith = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            pitchEnvelope = null,
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
        )

        val ctxWith = createContext(blockFrames = bf)
        val ctxWithout = createContext(blockFrames = bf)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // Null pitch envelope should be identical to default (no pitch envelope)
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff < 1e-6) shouldBe true
    }

    "pitch envelope with attack phase" {
        val bfLocal = 128
        val voiceWith = createSynthVoice(
            blockFrames = bfLocal,
            signal = Ignitors.sine(),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                releaseFrames = 0.0,
                amount = 2.0,
                curve = 0.0,
                anchor = 0.0
            )
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bfLocal,
            signal = Ignitors.sine(),
        )

        val ctxWith = createContext(blockStart = 0, blockFrames = bfLocal)
        val ctxWithout = createContext(blockStart = 0, blockFrames = bfLocal)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // Early samples should differ from no-envelope (pitch is shifting during attack)
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "pitch envelope with decay phase" {
        val bfLocal = 128
        val voiceWith = createSynthVoice(
            blockFrames = bfLocal,
            signal = Ignitors.sine(),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 0.0,
                decayFrames = 100.0,
                releaseFrames = 0.0,
                amount = -1.0,
                curve = 0.0,
                anchor = 0.0
            )
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bfLocal,
            signal = Ignitors.sine(),
        )

        // Render during decay phase
        val ctxWith = createContext(blockStart = 0, blockFrames = bfLocal)
        val ctxWithout = createContext(blockStart = 0, blockFrames = bfLocal)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // During decay, pitch envelope shifts pitch — output should differ from baseline
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "pitch envelope with both attack and decay" {
        val bfLocal = 128
        val voiceWith = createSynthVoice(
            blockFrames = bfLocal,
            signal = Ignitors.sine(),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                releaseFrames = 0.0,
                amount = 1.0,
                curve = 0.0,
                anchor = 0.0
            )
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bfLocal,
            signal = Ignitors.sine(),
        )

        val ctxWith = createContext(blockStart = 0, blockFrames = bfLocal)
        val ctxWithout = createContext(blockStart = 0, blockFrames = bfLocal)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // Attack+decay pitch envelope creates a transient — output differs from baseline
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "vibrato and accelerate combine correctly" {
        val voiceBoth = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.25),
            accelerate = Voice.Accelerate(amount = 1.0)
        )
        val voiceVibratoOnly = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.25),
        )
        val voiceAccelOnly = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 1.0),
        )

        val ctxBoth = createContext(blockFrames = bf)
        val ctxVib = createContext(blockFrames = bf)
        val ctxAcc = createContext(blockFrames = bf)
        voiceBoth.render(ctxBoth)
        voiceVibratoOnly.render(ctxVib)
        voiceAccelOnly.render(ctxAcc)

        // Combined should differ from vibrato-only and accelerate-only
        val diffFromVib = diffRms(ctxBoth.voiceBuffer, ctxVib.voiceBuffer)
        val diffFromAcc = diffRms(ctxBoth.voiceBuffer, ctxAcc.voiceBuffer)
        (diffFromVib > 1e-4) shouldBe true
        (diffFromAcc > 1e-4) shouldBe true
    }

    "vibrato and pitch envelope combine correctly" {
        val voiceBoth = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.25),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 100.0, decayFrames = 0.0, releaseFrames = 0.0,
                amount = 2.0, curve = 0.0, anchor = 0.0
            )
        )
        val voiceVibratoOnly = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.25),
        )

        val ctxBoth = createContext(blockFrames = bf)
        val ctxVib = createContext(blockFrames = bf)
        voiceBoth.render(ctxBoth)
        voiceVibratoOnly.render(ctxVib)

        // Combined should differ from vibrato-only
        val diff = diffRms(ctxBoth.voiceBuffer, ctxVib.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "accelerate and pitch envelope combine correctly" {
        val voiceBoth = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 0.5),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0, decayFrames = 50.0, releaseFrames = 0.0,
                amount = 1.0, curve = 0.0, anchor = 0.0
            )
        )
        val voiceAccelOnly = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 0.5),
        )

        val ctxBoth = createContext(blockFrames = bf)
        val ctxAcc = createContext(blockFrames = bf)
        voiceBoth.render(ctxBoth)
        voiceAccelOnly.render(ctxAcc)

        // Combined should differ from accelerate-only
        val diff = diffRms(ctxBoth.voiceBuffer, ctxAcc.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "all three pitch modulations combine correctly" {
        val voiceAll = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.25),
            accelerate = Voice.Accelerate(amount = 0.5),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0, decayFrames = 0.0, releaseFrames = 0.0,
                amount = 1.0, curve = 0.0, anchor = 0.0
            )
        )
        val voiceNone = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
        )

        val ctxAll = createContext(blockFrames = bf)
        val ctxNone = createContext(blockFrames = bf)
        voiceAll.render(ctxAll)
        voiceNone.render(ctxNone)

        // All three combined should produce significant deviation from baseline
        val diff = diffRms(ctxAll.voiceBuffer, ctxNone.voiceBuffer)
        (diff > 1e-3) shouldBe true
    }

    "pitch modulation works with SampleVoice" {
        val bfLocal = 256
        val sample = TestSamples.sine(size = 4096)

        val voiceWith = createSampleVoice(
            sample = sample,
            blockFrames = bfLocal,
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.5)
        )
        val voiceWithout = createSampleVoice(
            sample = sample,
            blockFrames = bfLocal,
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0)
        )

        val ctxWith = createContext(blockFrames = bfLocal)
        val ctxWithout = createContext(blockFrames = bfLocal)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // Vibrato should modulate sample playback rate — output differs
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "pitch modulation affects FM modulator frequency" {
        val voiceWithVib = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.5),
            fm = Voice.Fm(ratio = 2.0, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )
        val voiceNoVib = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0),
            fm = Voice.Fm(ratio = 2.0, depth = 100.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0))
        )

        val ctxWithVib = createContext(blockFrames = bf)
        val ctxNoVib = createContext(blockFrames = bf)
        voiceWithVib.render(ctxWithVib)
        voiceNoVib.render(ctxNoVib)

        // Vibrato should affect both carrier and FM modulator, producing different output
        val diff = diffRms(ctxWithVib.voiceBuffer, ctxNoVib.voiceBuffer)
        (diff > 1e-4) shouldBe true
    }

    "vibrato with very small depth produces subtle modulation" {
        val voiceSubtle = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.01)
        )
        val voiceLarge = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = 1.0)
        )
        val voiceNone = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
        )

        val ctxSubtle = createContext(blockFrames = bf)
        val ctxLarge = createContext(blockFrames = bf)
        val ctxNone = createContext(blockFrames = bf)
        voiceSubtle.render(ctxSubtle)
        voiceLarge.render(ctxLarge)
        voiceNone.render(ctxNone)

        // Subtle vibrato should produce less deviation than large vibrato
        val diffSubtle = diffRms(ctxSubtle.voiceBuffer, ctxNone.voiceBuffer)
        val diffLarge = diffRms(ctxLarge.voiceBuffer, ctxNone.voiceBuffer)
        (diffLarge > diffSubtle) shouldBe true
    }

    "accelerate with very high rate produces extreme pitch sweep" {
        val voiceExtreme = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 10.0)
        )
        val voiceModerate = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            accelerate = Voice.Accelerate(amount = 1.0)
        )
        val voiceNone = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
        )

        val ctxExtreme = createContext(blockFrames = bf)
        val ctxModerate = createContext(blockFrames = bf)
        val ctxNone = createContext(blockFrames = bf)
        voiceExtreme.render(ctxExtreme)
        voiceModerate.render(ctxModerate)
        voiceNone.render(ctxNone)

        // Extreme accelerate should produce more deviation than moderate
        val diffExtreme = diffRms(ctxExtreme.voiceBuffer, ctxNone.voiceBuffer)
        val diffModerate = diffRms(ctxModerate.voiceBuffer, ctxNone.voiceBuffer)
        (diffExtreme > diffModerate) shouldBe true
    }

    "pitch envelope with zero attack/decay time" {
        val voiceWith = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                releaseFrames = 0.0,
                amount = 1.0,
                curve = 0.0,
                anchor = 0.0
            )
        )

        val ctx = createContext(blockFrames = bf)
        voiceWith.render(ctx)

        // Should render successfully — output has non-zero signal
        val outputRms = rms(ctx.voiceBuffer)
        (outputRms > 0.0) shouldBe true
    }

    "negative vibrato depth is treated as no modulation" {
        // Pipeline builder skips vibrato when depth <= 0, so negative depth = no vibrato
        val voiceNeg = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 5.0, depth = -0.25)
        )
        val voiceNone = createSynthVoice(
            blockFrames = bf,
            signal = Ignitors.sine(),
        )

        val ctxNeg = createContext(blockFrames = bf)
        val ctxNone = createContext(blockFrames = bf)
        voiceNeg.render(ctxNeg)
        voiceNone.render(ctxNone)

        // Negative depth is skipped — output should be identical to no-vibrato
        val diff = diffRms(ctxNeg.voiceBuffer, ctxNone.voiceBuffer)
        (diff < 1e-6) shouldBe true
    }
})
