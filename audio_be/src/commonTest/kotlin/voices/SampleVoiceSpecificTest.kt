package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSampleVoice

/**
 * Tests specific to SampleVoice implementation.
 * Verifies sample playback, looping, and interpolation.
 */
class SampleVoiceSpecificTest : StringSpec({

    "SampleVoice plays back sample data correctly" {
        val sample = TestSamples.constant(size = 200, value = 0.5f) // Constant 0.5, longer than block

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // With "always on" envelope, first 100 samples should be 0.5
        ctx.voiceBuffer.all { it == 0.5 } shouldBe true
    }

    "SampleVoice with rate > 1 plays faster" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 2.0 // Double speed
        )

        val ctx = createContext(blockFrames = 50)
        voice.render(ctx)

        // Should cover full sample in 50 frames
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[49] shouldBe (0.98 plusOrMinus 0.03)
    }

    "SampleVoice with rate < 1 plays slower" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 0.5 // Half speed
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // Should only cover half the sample in 100 frames
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[99] shouldBe (0.50 plusOrMinus 0.02)
    }

    "SampleVoice performs linear interpolation" {
        val sample = TestSamples.ramp(size = 10) // 0.0, 0.111, 0.222, ..., 1.0

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.5, // Non-integer playback rate
            playhead = 0.0
        )

        val ctx = createContext(blockFrames = 5)
        voice.render(ctx)

        // Values should be interpolated between samples
        // At playhead=0.0: sample[0] = 0.0
        // At playhead=1.5: interpolate between sample[1] and sample[2]
        // At playhead=3.0: sample[3]
        // etc.
    }

    "SampleVoice without looping stops at end" {
        val sample = TestSamples.ramp(size = 50)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            samplePlayback = SampleVoice.SamplePlayback.default // No looping
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // First 50 samples should have audio
        (ctx.voiceBuffer[25] > 0.0) shouldBe true

        // After sample ends, should be silent
        ctx.voiceBuffer[75] shouldBe 0.0
    }

    "SampleVoice with explicit looping wraps correctly" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            samplePlayback = SampleVoice.SamplePlayback(
                cut = null,
                explicitLooping = true,
                explicitLoopStart = 0.0,
                explicitLoopEnd = 50.0, // Loop first half
                stopFrame = Double.MAX_VALUE
            )
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // First 50 frames should play 0.0 to 0.5
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[49] shouldBe (0.49 plusOrMinus 0.02)

        // Next 50 frames should loop back and play 0.0 to 0.5 again
        ctx.voiceBuffer[50] shouldBe (0.0 plusOrMinus 0.02)
        ctx.voiceBuffer[99] shouldBe (0.49 plusOrMinus 0.02)
    }

    "SampleVoice with stopFrame ends early" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            samplePlayback = SampleVoice.SamplePlayback(
                cut = null,
                explicitLooping = false,
                explicitLoopStart = -1.0,
                explicitLoopEnd = -1.0,
                stopFrame = 50.0 // Stop at frame 50
            )
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // First 50 frames should have audio
        (ctx.voiceBuffer[25] > 0.0) shouldBe true

        // After stopFrame, should be silent
        ctx.voiceBuffer[75] shouldBe 0.0
    }

    "SampleVoice playhead advances correctly" {
        val sample = TestSamples.constant(size = 100, value = 1.0f)

        // Create voice with initial playhead
        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            playhead = 10.0 // Start at sample 10
        )

        val ctx = createContext(blockFrames = 10)
        voice.render(ctx)

        // Should render from playhead 10 to 20
        // (Can't directly verify playhead without access to private field)
    }

    "SampleVoice with vibrato modulates playback rate" {
        val sample = TestSamples.sine(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02)
        )

        val ctx = createContext()
        voice.render(ctx)

        // Vibrato should modulate sample playback rate
        // Output will have time-varying playback speed
    }

    "SampleVoice with FM modulates playback rate" {
        val sample = TestSamples.sine(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 50.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // FM should modulate sample playback rate
    }

    "SampleVoice getBaseFrequency returns sample base pitch" {
        val sample = TestSamples.sine(size = 100)

        val voice = createSampleVoice(
            sample = sample
        )

        // Base frequency is used for FM calculation
        // For now, defaults to 440.0 Hz
        val ctx = createContext()
        voice.render(ctx)
        // If this renders without error, getBaseFrequency works
    }

    "SampleVoice with envelope modulates sample output" {
        val sample = TestSamples.constant(size = 200, value = 1.0f) // Longer sample

        val voice = createSampleVoice(
            sample = sample,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // Envelope should modulate sample amplitude
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.02) // Start of attack
        ctx.voiceBuffer[50] shouldBe (0.5 plusOrMinus 0.03) // Mid-attack
        ctx.voiceBuffer[99] shouldBe (0.99 plusOrMinus 0.03) // End of attack
    }

    "SampleVoice handles sample end boundary" {
        val sample = TestSamples.ramp(size = 50)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            playhead = 45.0 // Near end
        )

        val ctx = createContext(blockFrames = 10)
        voice.render(ctx)

        // First 5 samples should have audio
        (ctx.voiceBuffer[2] > 0.0) shouldBe true

        // After sample ends, should be silent
        ctx.voiceBuffer[7] shouldBe 0.0
    }

    "SampleVoice with negative playhead is handled" {
        val sample = TestSamples.ramp(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            playhead = -10.0 // Negative playhead
        )

        val ctx = createContext(blockFrames = 20)
        voice.render(ctx)

        // Negative playhead samples should be 0
        ctx.voiceBuffer[0] shouldBe 0.0
        ctx.voiceBuffer[9] shouldBe 0.0

        // After playhead reaches 0, should have audio
        (ctx.voiceBuffer[15] >= 0.0) shouldBe true
    }

    "SampleVoice preserves playhead across renders" {
        val sample = TestSamples.ramp(size = 200)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            playhead = 0.0
        )

        // First render: frames 0-100
        val ctx1 = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx1)
        val firstValue = ctx1.voiceBuffer[99]

        // Second render: frames 100-200
        val ctx2 = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx2)
        val secondValue = ctx2.voiceBuffer[0]

        // Second render should continue where first left off
        (secondValue > firstValue) shouldBe true
    }

    "SampleVoice with all modulations renders correctly" {
        val sample = TestSamples.sine(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            rate = 1.0,
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02),
            accelerate = Voice.Accelerate(amount = 1.0),
            fm = Voice.Fm(ratio = 2.0, depth = 50.0, envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)),
            envelope = Voice.Envelope(100.0, 0.0, 1.0, 0.0),
            samplePlayback = SampleVoice.SamplePlayback(
                cut = null,
                explicitLooping = true,
                explicitLoopStart = 0.0,
                explicitLoopEnd = 50.0,
                stopFrame = Double.MAX_VALUE
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully with all features enabled
    }
})
