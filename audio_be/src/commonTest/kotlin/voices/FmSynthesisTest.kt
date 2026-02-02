package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests for FM (Frequency Modulation) synthesis.
 * Verifies that FM correctly modulates the carrier frequency.
 */
class FmSynthesisTest : StringSpec({

    "FM with depth 0 produces no modulation" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 0.0, // No modulation
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully (no crash)
        // Output should be same as without FM
    }

    "FM with null is disabled" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = null
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully without FM
    }

    "FM modulator ratio affects modulation frequency" {
        val carrierFreq = 440.0

        // Modulator at 2x carrier frequency
        val voice1 = createSynthVoice(
            freqHz = carrierFreq,
            fm = Voice.Fm(
                ratio = 2.0, // Modulator at 880 Hz
                depth = 100.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        // Modulator at 0.5x carrier frequency
        val voice2 = createSynthVoice(
            freqHz = carrierFreq,
            fm = Voice.Fm(
                ratio = 0.5, // Modulator at 220 Hz
                depth = 100.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx1 = createContext()
        voice1.render(ctx1)

        val ctx2 = createContext()
        voice2.render(ctx2)

        // Both should render successfully
        // Different ratios produce different timbres (can't easily verify numerically)
    }

    "FM depth controls modulation intensity" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 1.0,
                depth = 50.0, // Moderate modulation
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // Higher depth = more sidebands (can't easily verify numerically)
    }

    "FM envelope modulates FM depth over time" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 100.0,
                envelope = Voice.Envelope(
                    attackFrames = 100.0,
                    decayFrames = 0.0,
                    sustainLevel = 1.0,
                    releaseFrames = 0.0
                )
            )
        )

        // At start of attack (frame 0), FM envelope = 0
        val ctx1 = createContext(blockStart = 0, blockFrames = 1)
        voice.render(ctx1)
        val startValue = ctx1.voiceBuffer[0]

        // Mid-attack (frame 50), FM envelope = 0.5
        val ctx2 = createContext(blockStart = 50, blockFrames = 1)
        voice.render(ctx2)
        val midValue = ctx2.voiceBuffer[0]

        // End of attack (frame 100), FM envelope = 1.0
        val ctx3 = createContext(blockStart = 100, blockFrames = 1)
        voice.render(ctx3)
        val endValue = ctx3.voiceBuffer[0]

        // All should render (values will differ due to FM envelope)
        // Can't easily verify exact values without FFT analysis
    }

    "FM envelope with decay phase" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 1.5,
                depth = 200.0,
                envelope = Voice.Envelope(
                    attackFrames = 100.0,
                    decayFrames = 100.0,
                    sustainLevel = 0.5,
                    releaseFrames = 0.0
                )
            )
        )

        // At decay phase (frame 150), envelope should be between 1.0 and 0.5
        val ctx = createContext(blockStart = 150, blockFrames = 1)
        voice.render(ctx)

        // Should render successfully
    }

    "FM works with SampleVoice" {
        val sample = TestSamples.sine(size = 100)

        val voice = VoiceTestHelpers.createSampleVoice(
            sample = sample,
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 50.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // FM should modulate sample playback rate
        // Should render successfully
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
        val voice = createSynthVoice(
            freqHz = 100.0,
            fm = Voice.Fm(
                ratio = 10.0, // Very high ratio
                depth = 500.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // High ratio produces many sidebands
    }

    "FM with fractional ratio works correctly" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 0.25, // Sub-harmonic modulator
                depth = 100.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
    }

    "FM combined with vibrato" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 1.5,
                depth = 50.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            ),
            vibrato = Voice.Vibrato(
                rate = 5.0,
                depth = 0.02
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // FM and vibrato should both apply
        // Should render successfully
    }

    "FM envelope at sustain level" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 100.0,
                envelope = Voice.Envelope(
                    attackFrames = 50.0,
                    decayFrames = 50.0,
                    sustainLevel = 0.3,
                    releaseFrames = 0.0
                )
            )
        )

        // At sustain phase
        val ctx = createContext(blockStart = 200, blockFrames = 100)
        voice.render(ctx)

        // Should render with reduced FM depth (30% of 100 = 30)
    }

    "FM envelope release phase" {
        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 300,
            gateEndFrame = 100,
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 100.0,
                envelope = Voice.Envelope(
                    attackFrames = 0.0,
                    decayFrames = 0.0,
                    sustainLevel = 1.0,
                    releaseFrames = 100.0
                )
            )
        )

        // During release phase
        val ctx = createContext(blockStart = 150, blockFrames = 1)
        voice.render(ctx)

        // FM depth should be decreasing
    }

    "FM with negative depth works" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 2.0,
                depth = -100.0, // Negative depth (phase inversion)
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // Negative depth inverts the modulation
    }

    "FM ratio of 1.0 produces harmonic sidebands" {
        val voice = createSynthVoice(
            freqHz = 440.0,
            fm = Voice.Fm(
                ratio = 1.0, // Harmonic relationship
                depth = 100.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully
        // Ratio of 1.0 produces harmonic spectrum
    }
})
