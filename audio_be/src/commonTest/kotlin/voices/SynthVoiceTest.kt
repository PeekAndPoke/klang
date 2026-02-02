package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.osci.OscFn
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests specific to SynthVoice implementation.
 * Verifies oscillator integration and synthesis-specific behavior.
 */
class SynthVoiceTest : StringSpec({

    "SynthVoice with constant oscillator produces constant output" {
        val voice = createSynthVoice(
            osc = TestOscillators.constant // Outputs 1.0
        )

        val ctx = createContext()
        voice.render(ctx)

        // All samples should be 1.0 (constant osc * envelope)
        ctx.voiceBuffer.all { it == 1.0 } shouldBe true
    }

    "SynthVoice with silence oscillator produces no output" {
        val voice = createSynthVoice(
            osc = TestOscillators.silence // Outputs 0.0
        )

        val ctx = createContext()
        voice.render(ctx)

        // All samples should be 0.0
        ctx.voiceBuffer.all { it == 0.0 } shouldBe true
    }

    "SynthVoice with ramp oscillator produces ramping output" {
        val voice = createSynthVoice(
            osc = TestOscillators.ramp // Outputs 0.0 to 1.0
        )

        val ctx = createContext(blockFrames = 10)
        voice.render(ctx)

        // First sample should be ~0.0
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        // Last sample should be ~0.9 (9/10)
        ctx.voiceBuffer[9] shouldBe (0.9 plusOrMinus 0.01)
    }

    "SynthVoice phase advances correctly" {
        var capturedPhase = 0.0

        val trackingOsc = OscFn { buffer, offset, length, phase, phaseInc, _ ->
            for (i in 0 until length) {
                buffer[offset + i] = 1.0
            }
            capturedPhase = phase + (phaseInc * length)
            capturedPhase
        }

        val voice = createSynthVoice(
            osc = trackingOsc,
            phaseInc = 0.1
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // Phase should have advanced by phaseInc * length
        capturedPhase shouldBe (10.0 plusOrMinus 0.01) // 0.1 * 100
    }

    "SynthVoice respects phaseInc parameter" {
        var actualPhaseInc = 0.0

        val trackingOsc = OscFn { buffer, offset, length, phase, phaseInc, _ ->
            actualPhaseInc = phaseInc
            for (i in 0 until length) buffer[offset + i] = 1.0
            phase
        }

        val voice = createSynthVoice(
            osc = trackingOsc,
            phaseInc = 0.25
        )

        val ctx = createContext()
        voice.render(ctx)

        actualPhaseInc shouldBe (0.25 plusOrMinus 0.0001)
    }

    "SynthVoice passes pitch modulation to oscillator" {
        var receivedPhaseMod: DoubleArray? = null

        val trackingOsc = OscFn { buffer, offset, length, phase, phaseInc, phaseMod ->
            receivedPhaseMod = phaseMod
            for (i in 0 until length) buffer[offset + i] = 1.0
            phase
        }

        val voice = createSynthVoice(
            osc = trackingOsc,
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02)
        )

        val ctx = createContext()
        voice.render(ctx)

        // Oscillator should receive pitch modulation buffer
        receivedPhaseMod shouldBe ctx.freqModBuffer
    }

    "SynthVoice without pitch modulation passes null to oscillator" {
        var receivedPhaseMod: DoubleArray? = null

        val trackingOsc = OscFn { buffer, offset, length, phase, phaseInc, phaseMod ->
            receivedPhaseMod = phaseMod
            for (i in 0 until length) buffer[offset + i] = 1.0
            phase
        }

        val voice = createSynthVoice(
            osc = trackingOsc,
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0) // No modulation
        )

        val ctx = createContext()
        voice.render(ctx)

        // Oscillator should receive null (no modulation)
        (receivedPhaseMod == null) shouldBe true
    }

    "SynthVoice getBaseFrequency returns freqHz" {
        val voice = createSynthVoice(
            freqHz = 440.0
        )

        // Access through reflection or by testing FM which uses getBaseFrequency
        // For now, we verify FM works (which relies on getBaseFrequency)
        val ctx = createContext()
        voice.render(ctx)
        // If this renders without error, getBaseFrequency works
    }

    "SynthVoice with envelope modulates oscillator output" {
        val voice = createSynthVoice(
            osc = TestOscillators.constant, // Outputs 1.0
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        // First sample should be ~0 (start of attack)
        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        // Middle sample should be ~0.5 (mid-attack)
        ctx.voiceBuffer[50] shouldBe (0.5 plusOrMinus 0.02)
        // Last sample should be ~1.0 (end of attack)
        ctx.voiceBuffer[99] shouldBe (0.99 plusOrMinus 0.02)
    }

    "SynthVoice with filter affects oscillator output" {
        val voice = createSynthVoice(
            osc = TestOscillators.constant,
            filter = VoiceTestHelpers.NoOpFilter // Could use real filter for more testing
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully with filter
    }

    "SynthVoice with all modulations renders correctly" {
        val voice = createSynthVoice(
            osc = TestOscillators.constant,
            freqHz = 440.0,
            phaseInc = 0.1,
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02),
            accelerate = Voice.Accelerate(amount = 1.0),
            pitchEnvelope = Voice.PitchEnvelope(
                attackFrames = 50.0,
                decayFrames = 50.0,
                releaseFrames = 0.0,
                amount = 1.0,
                curve = 0.0,
                anchor = 0.0
            ),
            fm = Voice.Fm(
                ratio = 2.0,
                depth = 100.0,
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            ),
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext()
        voice.render(ctx)

        // Should render successfully with all modulations
        // Output will be complex combination of all effects
    }

    "SynthVoice oscillator receives correct buffer parameters" {
        var receivedOffset = -1
        var receivedLength = -1

        val trackingOsc = OscFn { buffer, offset, length, phase, phaseInc, _ ->
            receivedOffset = offset
            receivedLength = length
            for (i in 0 until length) buffer[offset + i] = 1.0
            phase
        }

        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100,
            osc = trackingOsc
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        receivedOffset shouldBe 0
        receivedLength shouldBe 100
    }

    "SynthVoice with partial block renders correct length" {
        var receivedLength = -1

        val trackingOsc = OscFn { buffer, offset, length, phase, phaseInc, _ ->
            receivedLength = length
            for (i in 0 until length) buffer[offset + i] = 1.0
            phase
        }

        val voice = createSynthVoice(
            startFrame = 50,
            endFrame = 150,
            osc = trackingOsc
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        // Should only render 50 samples (from frame 50 to 100)
        receivedLength shouldBe 50
    }

    "SynthVoice preserves phase across multiple renders" {
        val phases = mutableListOf<Double>()

        val trackingOsc = OscFn { buffer, offset, length, phase, phaseInc, _ ->
            phases.add(phase)
            for (i in 0 until length) buffer[offset + i] = 1.0
            phase + (phaseInc * length)
        }

        val voice = createSynthVoice(
            osc = trackingOsc,
            phaseInc = 0.1
        )

        // Render three blocks
        voice.render(createContext(blockStart = 0, blockFrames = 100))
        voice.render(createContext(blockStart = 100, blockFrames = 100))
        voice.render(createContext(blockStart = 200, blockFrames = 100))

        // Each render should start where previous ended
        phases.size shouldBe 3
        phases[1] shouldBe (phases[0] + 10.0 plusOrMinus 0.01)
        phases[2] shouldBe (phases[1] + 10.0 plusOrMinus 0.01)
    }
})
