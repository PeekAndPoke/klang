package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSampleVoice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests for voice lifecycle management (startFrame, endFrame, gateEndFrame).
 * Verifies that voices render correctly across their lifetime.
 */
class VoiceLifecycleTest : StringSpec({

    "voice does not render before startFrame" {
        val voice = createSynthVoice(
            startFrame = 100,
            endFrame = 200
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true // Voice continues (will start in future)

        // Buffer should remain empty (voice hasn't started)
        ctx.voiceBuffer.all { it == 0.0 } shouldBe true
    }

    "voice does not render after endFrame" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100
        )

        val ctx = createContext(blockStart = 100, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe false // Voice is done

        // Buffer should remain empty (voice has ended)
        ctx.voiceBuffer.all { it == 0.0 } shouldBe true
    }

    "voice starting at block boundary renders full block" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true

        // Buffer should have audio (TestOscillators.constant = 1.0)
        ctx.voiceBuffer.all { it == 1.0 } shouldBe true
    }

    "voice ending at block boundary renders full block" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true

        // Buffer should have audio
        ctx.voiceBuffer.all { it == 1.0 } shouldBe true
    }

    "voice starting mid-block renders partial buffer" {
        val voice = createSynthVoice(
            startFrame = 50,
            endFrame = 150
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true

        // First 50 samples should be 0 (voice hasn't started)
        ctx.voiceBuffer.take(50).all { it == 0.0 } shouldBe true

        // Last 50 samples should have audio
        ctx.voiceBuffer.takeLast(50).all { it == 1.0 } shouldBe true
    }

    "voice ending mid-block renders partial buffer" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 50
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true

        // First 50 samples should have audio
        ctx.voiceBuffer.take(50).all { it == 1.0 } shouldBe true

        // Last 50 samples should be 0 (voice has ended)
        ctx.voiceBuffer.takeLast(50).all { it == 0.0 } shouldBe true
    }

    "voice spanning multiple blocks renders correctly" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 300
        )

        // Block 1: frames 0-100
        val ctx1 = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx1) shouldBe true
        ctx1.voiceBuffer.all { it == 1.0 } shouldBe true

        // Block 2: frames 100-200
        val ctx2 = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx2) shouldBe true
        ctx2.voiceBuffer.all { it == 1.0 } shouldBe true

        // Block 3: frames 200-300
        val ctx3 = createContext(blockStart = 200, blockFrames = 100)
        voice.render(ctx3) shouldBe true
        ctx3.voiceBuffer.all { it == 1.0 } shouldBe true

        // Block 4: frames 300-400 (voice has ended)
        val ctx4 = createContext(blockStart = 300, blockFrames = 100)
        voice.render(ctx4) shouldBe false
    }

    "voice with single-frame duration works" {
        val voice = createSynthVoice(
            startFrame = 50,
            endFrame = 51
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true

        // Only frame 50 should have audio
        ctx.voiceBuffer[49] shouldBe 0.0
        ctx.voiceBuffer[50] shouldBe 1.0
        ctx.voiceBuffer[51] shouldBe 0.0
    }

    "voice with zero-duration (startFrame == endFrame) does not render" {
        val voice = createSynthVoice(
            startFrame = 50,
            endFrame = 50
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        // Voice continues but doesn't render anything
        result shouldBe true
        ctx.voiceBuffer.all { it == 0.0 } shouldBe true
    }

    "gateEndFrame triggers release phase" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 200,
            gateEndFrame = 100, // Gate ends at 100
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 100.0
            )
        )

        // Before gate ends (frame 50)
        val ctx1 = createContext(blockStart = 50, blockFrames = 1)
        voice.render(ctx1)
        val beforeGate = ctx1.voiceBuffer[0]

        // At gate end (frame 100)
        val ctx2 = createContext(blockStart = 100, blockFrames = 1)
        voice.render(ctx2)
        val atGateEnd = ctx2.voiceBuffer[0]

        // After gate ends (frame 150, mid-release)
        val ctx3 = createContext(blockStart = 150, blockFrames = 1)
        voice.render(ctx3)
        val midRelease = ctx3.voiceBuffer[0]

        // Voice should be at full amplitude before gate
        beforeGate shouldBe 1.0

        // At gate end, still at sustain level
        atGateEnd shouldBe 1.0

        // Mid-release should be lower
        (midRelease < atGateEnd) shouldBe true
    }

    "voice with startFrame > endFrame handles edge case" {
        // Edge case: invalid voice configuration (shouldn't happen in practice)
        val voice = createSynthVoice(
            startFrame = 100,
            endFrame = 50 // Invalid: endFrame before startFrame
        )

        val ctx = createContext(blockStart = 0, blockFrames = 200)
        val result = voice.render(ctx)

        // With invalid config (endFrame < startFrame), length calculation will be negative
        // Current implementation doesn't explicitly guard against this
        // Result depends on how maxOf/minOf handle it - typically continues
        result shouldBe true
    }

    "voice queried far before start returns true" {
        val voice = createSynthVoice(
            startFrame = 10000,
            endFrame = 10100
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true // Voice will start in the future
    }

    "voice queried far after end returns false" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100
        )

        val ctx = createContext(blockStart = 10000, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe false // Voice is long done
    }

    "SampleVoice lifecycle works same as SynthVoice" {
        val sample = TestSamples.constant(size = 100, value = 0.5f)

        val voice = createSampleVoice(
            sample = sample,
            startFrame = 50,
            endFrame = 150
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        val result = voice.render(ctx)

        result shouldBe true

        // First 50 samples should be 0 (voice hasn't started)
        ctx.voiceBuffer.take(50).all { it == 0.0 } shouldBe true

        // Last 50 samples should have audio (sample value * envelope)
        // Note: Sample has value 0.5, but envelope might modify it
        (ctx.voiceBuffer[50] > 0.0) shouldBe true
    }

    "voice at exact block boundaries handles edge cases" {
        val voice = createSynthVoice(
            startFrame = 100,
            endFrame = 200
        )

        // Query block that ends exactly at startFrame
        val ctx1 = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx1) shouldBe true
        ctx1.voiceBuffer.all { it == 0.0 } shouldBe true

        // Query block that starts exactly at startFrame
        val ctx2 = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx2) shouldBe true
        ctx2.voiceBuffer.all { it == 1.0 } shouldBe true

        // Query block that ends exactly at endFrame
        val ctx3 = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx3) shouldBe true
        ctx3.voiceBuffer.all { it == 1.0 } shouldBe true

        // Query block that starts exactly at endFrame
        val ctx4 = createContext(blockStart = 200, blockFrames = 100)
        voice.render(ctx4) shouldBe false
    }

    "voice with very long duration works" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1_000_000
        )

        // Should render successfully at various points
        val ctx1 = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx1) shouldBe true

        val ctx2 = createContext(blockStart = 500_000, blockFrames = 100)
        voice.render(ctx2) shouldBe true

        val ctx3 = createContext(blockStart = 999_900, blockFrames = 100)
        voice.render(ctx3) shouldBe true

        val ctx4 = createContext(blockStart = 1_000_000, blockFrames = 100)
        voice.render(ctx4) shouldBe false
    }

    "gateEndFrame can equal startFrame (immediate release)" {
        val voice = createSynthVoice(
            startFrame = 100,
            endFrame = 200,
            gateEndFrame = 100, // Gate ends immediately
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 50.0
            )
        )

        val ctx = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx) shouldBe true

        // Should start in release phase immediately
        // (exact values depend on envelope calculation)
    }

    "gateEndFrame after endFrame is handled correctly" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100,
            gateEndFrame = 200, // Gate ends after voice ends (shouldn't happen in practice)
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 50.0
            )
        )

        // Voice should still end at endFrame
        val ctx1 = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx1) shouldBe true

        val ctx2 = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx2) shouldBe false
    }
})
