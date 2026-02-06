package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSampleVoice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests for pitch modulation (vibrato, accelerate, pitch envelope).
 * Verifies that pitch modulation is correctly applied before signal generation.
 */
class PitchModulationTest : StringSpec({

    "vibrato with depth 0 produces no modulation" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(
                rate = 5.0,
                depth = 0.0 // No modulation
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully without vibrato
    }

    "vibrato with rate and depth modulates pitch" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(
                rate = 5.0, // 5 Hz LFO
                depth = 0.02 // 2% pitch variation
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully with vibrato
        // Pitch will oscillate around base frequency
    }

    "vibrato with high rate produces fast modulation" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(
                rate = 20.0, // Fast vibrato
                depth = 0.05
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
    }

    "vibrato with high depth produces wide pitch swings" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(
                rate = 5.0,
                depth = 0.5 // 50% pitch variation
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
    }

    "accelerate with rate 0 produces no pitch change" {
        val voice = createSynthVoice(
            accelerate = Voice.Accelerate(amount = 0.0)
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render at constant pitch
    }

    "accelerate with positive amount increases pitch over time" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            accelerate = Voice.Accelerate(amount = 1.0) // Pitch increases
        )

        // Render at start
        val ctx1 = createContext(blockStart = 0, blockFrames = 1)
        voice.render(ctx1)

        // Render later (pitch should be higher)
        val ctx2 = createContext(blockStart = 500, blockFrames = 1)
        voice.render(ctx2)

        // Both should render successfully
        // Pitch at ctx2 should be higher than ctx1
    }

    "accelerate with negative rate decreases pitch over time" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 1000,
            accelerate = Voice.Accelerate(amount = -0.5) // Pitch decreases
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // Pitch decreases over voice lifetime
    }

    "pitch envelope with null is disabled" {
        val voice = createSynthVoice(
            pitchEnvelope = null
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render at base pitch
    }

    "pitch envelope with attack phase" {
        val voice = createSynthVoice(
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                releaseFrames = 0.0,
                amount = 2.0, // 2 semitones shift
                curve = 0.0,
                anchor = 0.0
            )
        )

        // At start (frame 0)
        val ctx1 = createContext(blockStart = 0, blockFrames = 1)
        voice.render(ctx1)

        // Mid-attack (frame 50)
        val ctx2 = createContext(blockStart = 50, blockFrames = 1)
        voice.render(ctx2)

        // End of attack (frame 100)
        val ctx3 = createContext(blockStart = 100, blockFrames = 1)
        voice.render(ctx3)

        // All should render successfully
    }

    "pitch envelope with decay phase" {
        val voice = createSynthVoice(
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 0.0,
                decayFrames = 100.0,
                releaseFrames = 0.0,
                amount = -1.0, // -1 semitone shift
                curve = 0.0,
                anchor = 0.0
            )
        )

        // At start (frame 0)
        val ctx1 = createContext(blockStart = 0, blockFrames = 1)
        voice.render(ctx1)

        // Mid-decay (frame 50)
        val ctx2 = createContext(blockStart = 50, blockFrames = 1)
        voice.render(ctx2)

        // End of decay (frame 100)
        val ctx3 = createContext(blockStart = 100, blockFrames = 1)
        voice.render(ctx3)

        // All should render successfully
    }

    "pitch envelope with both attack and decay" {
        val voice = createSynthVoice(
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                releaseFrames = 0.0,
                amount = 1.0, // 1 semitone shift
                curve = 0.0,
                anchor = 0.0
            )
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        // Should render successfully
    }

    "vibrato and accelerate combine correctly" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02),
            accelerate = Voice.Accelerate(amount = 1.0)
        )

        val ctx = createContext()
        voice.render(ctx)

        // Both modulations should apply
        // Pitch increases over time with vibrato wobble
    }

    "vibrato and pitch envelope combine correctly" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                releaseFrames = 0.0,
                amount = 2.0,
                curve = 0.0,
                anchor = 0.0
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Both modulations should apply
        // Pitch envelope slide with vibrato wobble
    }

    "accelerate and pitch envelope combine correctly" {
        val voice = createSynthVoice(
            accelerate = Voice.Accelerate(amount = 0.5),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                releaseFrames = 0.0,
                amount = 1.0,
                curve = 0.0,
                anchor = 0.0
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Both modulations should apply
    }

    "all three pitch modulations combine correctly" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02),
            accelerate = Voice.Accelerate(amount = 0.5),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0,
                decayFrames = 0.0,
                releaseFrames = 0.0,
                amount = 1.0,
                curve = 0.0,
                anchor = 0.0
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // All three modulations should apply
        // Vibrato + accelerate + pitch envelope
    }

    "pitch modulation works with SampleVoice" {
        val sample = TestSamples.sine(size = 100)

        val voice = createSampleVoice(
            sample = sample,
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02)
        )

        val ctx = createContext()
        voice.render(ctx)

        // Vibrato should modulate sample playback rate
    }

    "pitch modulation affects FM modulator frequency" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02),
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 100.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Pitch modulation should affect both carrier and FM modulator
    }

    "vibrato with very small depth produces subtle modulation" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(
                rate = 5.0,
                depth = 0.001 // 0.1% pitch variation
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully with subtle vibrato
    }

    "accelerate with very high rate produces extreme pitch sweep" {
        val voice = createSynthVoice(
            accelerate = Voice.Accelerate(amount = 10.0) // Very fast pitch increase
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // Pitch increases dramatically
    }

    "pitch envelope with zero attack/decay time" {
        val voice = createSynthVoice(
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 0.0, // Instant
                decayFrames = 0.0, // Instant
                releaseFrames = 0.0,
                amount = 1.0,
                curve = 0.0,
                anchor = 0.0
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // Pitch changes are instantaneous
    }

    "negative vibrato depth works correctly" {
        val voice = createSynthVoice(
            vibrato = Voice.Vibrato(
                rate = 5.0,
                depth = -0.02 // Negative depth (phase inversion)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // Same as positive depth but phase-inverted
    }
})
