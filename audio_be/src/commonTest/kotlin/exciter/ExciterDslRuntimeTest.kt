package io.peekandpoke.klang.audio_be.exciter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.*

class ExciterDslRuntimeTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx(): ExciteContext {
        return ExciteContext(
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

    fun generateBlock(signalGen: Exciter, freqHz: Double = 440.0): FloatArray {
        val buffer = FloatArray(blockFrames)
        val ctx = createCtx()
        signalGen.generate(buffer, freqHz, ctx)
        return buffer
    }

    fun FloatArray.hasNonZeroSamples(): Boolean = any { it != 0.0f }

    "Sine DSL produces non-zero output" {
        val sig = ExciterDsl.Sine().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Sawtooth DSL produces non-zero output" {
        val sig = ExciterDsl.Sawtooth().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Square DSL produces non-zero output" {
        val sig = ExciterDsl.Square().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Triangle DSL produces non-zero output" {
        val sig = ExciterDsl.Triangle().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "WhiteNoise DSL produces non-zero output" {
        val sig = ExciterDsl.WhiteNoise.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Silence DSL produces zero output" {
        val sig = ExciterDsl.Silence.toExciter()
        generateBlock(sig).all { it == 0.0f } shouldBe true
    }

    "Plus composition produces non-zero output" {
        val dsl = ExciterDsl.Sine() + ExciterDsl.Sawtooth()
        val sig = dsl.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgpad composition produces non-zero output" {
        val dsl = (ExciterDsl.Sawtooth() + ExciterDsl.Sawtooth().detune(0.1))
            .div(ExciterDsl.Param("divisor", 2.0))
            .onePoleLowpass(3000.0)
        val sig = dsl.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgbell composition produces non-zero output" {
        val dsl = ExciterDsl.Sine().fm(
            modulator = ExciterDsl.Sine(),
            ratio = 1.4,
            depth = 300.0,
            envAttackSec = 0.001,
            envDecaySec = 0.5,
            envSustainLevel = 0.0,
        )
        val sig = dsl.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgbuzz composition produces non-zero output" {
        val dsl = ExciterDsl.Square().lowpass(2000.0)
        val sig = dsl.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "toExciter creates independent instances" {
        val dsl = ExciterDsl.Sine()
        val sig1 = dsl.toExciter()
        val sig2 = dsl.toExciter()

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
