package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.signalgen.SignalGen
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice

/**
 * Tests specific to SynthVoice implementation.
 * Verifies SignalGen integration and synthesis-specific behavior.
 */
class SynthVoiceTest : StringSpec({

    "SynthVoice with constant signal produces constant output" {
        val voice = createSynthVoice(
            signal = TestSignalGens.constant
        )

        val ctx = createContext()
        voice.render(ctx)

        ctx.voiceBuffer.all { it == 1.0 } shouldBe true
    }

    "SynthVoice with silence signal produces no output" {
        val voice = createSynthVoice(
            signal = TestSignalGens.silence
        )

        val ctx = createContext()
        voice.render(ctx)

        ctx.voiceBuffer.all { it == 0.0 } shouldBe true
    }

    "SynthVoice with ramp signal produces ramping output" {
        val voice = createSynthVoice(
            signal = TestSignalGens.ramp,
            blockFrames = 10,
        )

        val ctx = createContext(blockFrames = 10)
        voice.render(ctx)

        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[9] shouldBe (0.9 plusOrMinus 0.01)
    }

    "SynthVoice passes pitch modulation to signal" {
        var receivedPhaseMod: DoubleArray? = null

        val trackingSignal = SignalGen { buffer, _, ctx ->
            receivedPhaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) buffer[i] = 1.0
        }

        val voice = createSynthVoice(
            signal = trackingSignal,
            vibrato = Voice.Vibrato(rate = 5.0, depth = 0.02),
        )

        val ctx = createContext()
        voice.render(ctx)

        // Signal should receive pitch modulation
        receivedPhaseMod shouldBe ctx.freqModBuffer
    }

    "SynthVoice without pitch modulation passes null to signal" {
        var receivedPhaseMod: DoubleArray? = null

        val trackingSignal = SignalGen { buffer, _, ctx ->
            receivedPhaseMod = ctx.phaseMod
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) buffer[i] = 1.0
        }

        val voice = createSynthVoice(
            signal = trackingSignal,
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0),
        )

        val ctx = createContext()
        voice.render(ctx)

        (receivedPhaseMod == null) shouldBe true
    }

    "SynthVoice getBaseFrequency returns freqHz" {
        val voice = createSynthVoice(freqHz = 440.0)

        val ctx = createContext()
        voice.render(ctx)
    }

    "SynthVoice with envelope modulates signal output" {
        val voice = createSynthVoice(
            signal = TestSignalGens.constant,
            blockFrames = 100,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            )
        )

        val ctx = createContext(blockFrames = 100)
        voice.render(ctx)

        ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.01)
        ctx.voiceBuffer[50] shouldBe (0.5 plusOrMinus 0.02)
        ctx.voiceBuffer[99] shouldBe (0.99 plusOrMinus 0.02)
    }

    "SynthVoice with filter affects signal output" {
        val voice = createSynthVoice(
            signal = TestSignalGens.constant,
            filter = VoiceTestHelpers.NoOpFilter,
        )

        val ctx = createContext()
        voice.render(ctx)
    }

    "SynthVoice with all modulations renders correctly" {
        val voice = createSynthVoice(
            signal = TestSignalGens.constant,
            freqHz = 440.0,
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
    }

    "SynthVoice signal receives correct buffer parameters" {
        var receivedOffset = -1
        var receivedLength = -1

        val trackingSignal = SignalGen { buffer, _, ctx ->
            receivedOffset = ctx.offset
            receivedLength = ctx.length
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) buffer[i] = 1.0
        }

        val voice = createSynthVoice(
            startFrame = 0,
            endFrame = 100,
            signal = trackingSignal,
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        receivedOffset shouldBe 0
        receivedLength shouldBe 100
    }

    "SynthVoice with partial block renders correct length" {
        var receivedLength = -1

        val trackingSignal = SignalGen { buffer, _, ctx ->
            receivedLength = ctx.length
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) buffer[i] = 1.0
        }

        val voice = createSynthVoice(
            startFrame = 50,
            endFrame = 150,
            signal = trackingSignal,
        )

        val ctx = createContext(blockStart = 0, blockFrames = 100)
        voice.render(ctx)

        receivedLength shouldBe 50
    }

    "SynthVoice tracks elapsed frames across multiple renders" {
        val elapsedFrames = mutableListOf<Int>()

        val trackingSignal = SignalGen { buffer, _, ctx ->
            elapsedFrames.add(ctx.voiceElapsedFrames)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) buffer[i] = 1.0
        }

        val voice = createSynthVoice(
            signal = trackingSignal,
        )

        voice.render(createContext(blockStart = 0, blockFrames = 100))
        voice.render(createContext(blockStart = 100, blockFrames = 100))
        voice.render(createContext(blockStart = 200, blockFrames = 100))

        elapsedFrames.size shouldBe 3
        elapsedFrames[0] shouldBe 0
        elapsedFrames[1] shouldBe 100
        elapsedFrames[2] shouldBe 200
    }
})
