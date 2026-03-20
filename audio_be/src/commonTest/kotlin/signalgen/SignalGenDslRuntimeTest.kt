package io.peekandpoke.klang.audio_be.signalgen

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.*

class SignalGenDslRuntimeTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx(): SignalContext {
        return SignalContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate, // 1 second
            gateEndFrame = sampleRate,
            releaseFrames = (0.1 * sampleRate).toInt(),
            voiceEndFrame = sampleRate + (0.1 * sampleRate).toInt(),
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply {
            offset = 0
            length = blockFrames
            voiceElapsedFrames = 0
        }
    }

    fun generateBlock(signalGen: SignalGen, freqHz: Double = 440.0): DoubleArray {
        val buffer = DoubleArray(blockFrames)
        val ctx = createCtx()
        signalGen.generate(buffer, freqHz, ctx)
        return buffer
    }

    fun DoubleArray.hasNonZeroSamples(): Boolean = any { it != 0.0 }

    "Sine DSL produces non-zero output" {
        val sig = SignalGenDsl.Sine().toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Sawtooth DSL produces non-zero output" {
        val sig = SignalGenDsl.Sawtooth().toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Square DSL produces non-zero output" {
        val sig = SignalGenDsl.Square().toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Triangle DSL produces non-zero output" {
        val sig = SignalGenDsl.Triangle().toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "WhiteNoise DSL produces non-zero output" {
        val sig = SignalGenDsl.WhiteNoise().toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Silence DSL produces zero output" {
        val sig = SignalGenDsl.Silence.toSignalGen()
        generateBlock(sig).all { it == 0.0 } shouldBe true
    }

    "Plus composition produces non-zero output" {
        val dsl = SignalGenDsl.Sine() + SignalGenDsl.Sawtooth()
        val sig = dsl.toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgpad composition produces non-zero output" {
        val dsl = (SignalGenDsl.Sawtooth() + SignalGenDsl.Sawtooth().detune(0.1))
            .div(2.0)
            .onePoleLowpass(3000.0)
        val sig = dsl.toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgbell composition produces non-zero output" {
        val dsl = SignalGenDsl.Sine().fm(
            modulator = SignalGenDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envAttackSec = 0.001,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        val sig = dsl.toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgbuzz composition produces non-zero output" {
        val dsl = SignalGenDsl.Square().lowpass(2000.0)
        val sig = dsl.toSignalGen()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "toSignalGen creates independent instances" {
        val dsl = SignalGenDsl.Sine()
        val sig1 = dsl.toSignalGen()
        val sig2 = dsl.toSignalGen()

        val buf1 = generateBlock(sig1)
        val buf2 = generateBlock(sig2)

        // Both should produce the same output since they start from the same state
        for (i in buf1.indices) {
            buf1[i] shouldBe buf2[i]
        }

        // After generating more blocks, they should still be independent
        val ctx = createCtx()
        sig1.generate(buf1, 440.0, ctx)
        sig2.generate(buf2, 880.0, ctx) // Different frequency

        // Now they should differ
        var differs = false
        for (i in buf1.indices) {
            if (buf1[i] != buf2[i]) {
                differs = true
                break
            }
        }
        differs shouldBe true
    }
})
