package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests for ADSR envelope implementation in AbstractVoice.
 * Verifies all phases (Attack, Decay, Sustain, Release) work correctly.
 */
class EnvelopeTest : StringSpec({

    "attack phase increases linearly from 0 to 1" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        // At frame 0, envelope should be ~0
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)

        // At frame 50 (middle of attack), envelope should be ~0.5
        ctx.voiceBuffer[50] shouldBe (0.5 plusOrMinus 0.02)

        // At frame 99 (end of attack), envelope should be ~1.0
        ctx.voiceBuffer[99] shouldBe (0.99 plusOrMinus 0.02)
    }

    "decay phase decreases from 1 to sustain level" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 100.0,
                sustainLevel = 0.5,
                releaseFrames = 0.0
            )
        )

        // Render at start of decay phase (frame 100-200)
        val ctx = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx)

        // At start of decay (frame 100), envelope should be ~1.0
        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.02)

        // At middle of decay (frame 150), envelope should be ~0.75
        ctx.voiceBuffer[50] shouldBe (0.75 plusOrMinus 0.02)

        // At end of decay (frame 199), envelope should be ~0.5
        ctx.voiceBuffer[99] shouldBe (0.5 plusOrMinus 0.02)
    }

    "sustain phase holds at sustain level" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            envelope = Voice.Envelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                sustainLevel = 0.6,
                releaseFrames = 0.0
            )
        )

        // Render at sustain phase (frame 200-300, after attack+decay)
        val ctx = createContext(blockStart = 200, blockFrames = 100)
        voice.render(ctx)

        // All samples should be at sustain level
        ctx.voiceBuffer[0] shouldBe (0.6 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (0.6 plusOrMinus 0.01)
        ctx.voiceBuffer[99] shouldBe (0.6 plusOrMinus 0.01)
    }

    "release phase decays from sustain to zero" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 300,
            gateEndFrame = 100, // Gate ends at 100, release starts
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 100.0
            )
        )

        // Render at release phase (frame 100-200)
        val ctx = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx)

        // At start of release (frame 100), envelope should be ~1.0
        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.02)

        // At middle of release (frame 150), envelope should be ~0.5
        ctx.voiceBuffer[50] shouldBe (0.5 plusOrMinus 0.02)

        // At end of release (frame 199), envelope should be near 0
        ctx.voiceBuffer[99] shouldBe (0.0 plusOrMinus 0.02)
    }

    "zero attack time produces immediate full amplitude" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        // First sample should already be at full amplitude
        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (1.0 plusOrMinus 0.01)
    }

    "zero decay time transitions immediately to sustain" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 200,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 0.5,
                releaseFrames = 0.0
            )
        )

        // Render at frame 100 (end of attack, start of decay)
        val ctx = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx)

        // Should immediately be at sustain level
        ctx.voiceBuffer[0] shouldBe (0.5 plusOrMinus 0.02)
    }

    "zero release time produces very fast decay" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 200,
            gateEndFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        // Render at release phase
        val ctx = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx)

        // First sample is still at sustain level (relPos = 0)
        ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.01)
        // Second sample should drop to 0 (relPos = 1, relRate = 1.0)
        ctx.voiceBuffer[1] shouldBe (0.0 plusOrMinus 0.01)
    }

    "full ADSR cycle works correctly" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 500,
            gateEndFrame = 300,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 100.0,
                sustainLevel = 0.5,
                releaseFrames = 100.0
            )
        )

        // Attack phase (0-100)
        val ctx1 = createContext(blockStart = 50, blockFrames = 1)
        voice.render(ctx1)
        ctx1.voiceBuffer[0] shouldBe (0.5 plusOrMinus 0.02) // Mid-attack

        // Decay phase (100-200)
        val ctx2 = createContext(blockStart = 150, blockFrames = 1)
        voice.render(ctx2)
        ctx2.voiceBuffer[0] shouldBe (0.75 plusOrMinus 0.02) // Mid-decay

        // Sustain phase (200-300)
        val ctx3 = createContext(blockStart = 250, blockFrames = 1)
        voice.render(ctx3)
        ctx3.voiceBuffer[0] shouldBe (0.5 plusOrMinus 0.01) // Sustain

        // Release phase (300-400)
        val ctx4 = createContext(blockStart = 350, blockFrames = 1)
        voice.render(ctx4)
        ctx4.voiceBuffer[0] shouldBe (0.25 plusOrMinus 0.02) // Mid-release
    }

    "envelope state is preserved across multiple renders" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 500,
            envelope = Voice.Envelope(
                attackFrames = 200.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        // Render first half of attack
        val ctx1 = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx1)
        val firstHalfValue = ctx1.voiceBuffer[99]

        // Render second half of attack
        val ctx2 = createContext(blockStart = 100, blockFrames = 100)
        voice.render(ctx2)
        val secondHalfStart = ctx2.voiceBuffer[0]

        // Second render should continue where first left off
        secondHalfStart shouldBe (firstHalfValue plusOrMinus 0.02)
    }

    "envelope clamps negative values to zero" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 300,
            gateEndFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 0.0,
                decayFrames = 0.0,
                sustainLevel = 0.5,
                releaseFrames = 50.0
            )
        )

        // Render well past release end (frame 200, release ends at 150)
        val ctx = createContext(blockStart = 200, blockFrames = 100)
        voice.render(ctx)

        // Should be clamped at 0, not negative
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (0.0 plusOrMinus 0.01)
    }

    "envelope with very small attack works correctly" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100,
            envelope = Voice.Envelope(
                attackFrames = 1.0, // Very short attack
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext(blockStart = 0, blockFrames = 10)
        voice.render(ctx)

        // After 1 frame, should be at full amplitude
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.1) // First sample
        ctx.voiceBuffer[1] shouldBe (1.0 plusOrMinus 0.1) // After attack
    }

    "envelope with sustain level of 0 produces silence after decay" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 300,
            envelope = Voice.Envelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                sustainLevel = 0.0,
                releaseFrames = 0.0
            )
        )

        // Render at sustain phase (after attack+decay)
        val ctx = createContext(blockStart = 150, blockFrames = 100)
        voice.render(ctx)

        // Should be silent
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (0.0 plusOrMinus 0.01)
    }

    "envelope respects gate end frame for release timing" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 500,
            gateEndFrame = 200, // Gate ends at 200
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 100.0
            )
        )

        // Just before gate ends (frame 199)
        val ctx1 = createContext(blockStart = 199, blockFrames = 1)
        voice.render(ctx1)
        val beforeRelease = ctx1.voiceBuffer[0]

        // Just after gate ends (frame 200)
        val ctx2 = createContext(blockStart = 200, blockFrames = 1)
        voice.render(ctx2)
        val atReleaseStart = ctx2.voiceBuffer[0]

        // Should start releasing
        beforeRelease shouldBe (1.0 plusOrMinus 0.02)
        atReleaseStart shouldBe (1.0 plusOrMinus 0.02)

        // Halfway through release (frame 250)
        val ctx3 = createContext(blockStart = 250, blockFrames = 1)
        voice.render(ctx3)
        ctx3.voiceBuffer[0] shouldBe (0.5 plusOrMinus 0.02)
    }
})
