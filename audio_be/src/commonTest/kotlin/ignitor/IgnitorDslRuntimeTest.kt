package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.onePoleLowpass
import io.peekandpoke.klang.audio_bridge.plus

class IgnitorDslRuntimeTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx(): IgniteContext {
        return IgniteContext(
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

    fun generateBlock(signalGen: Ignitor, freqHz: Double = 440.0): FloatArray {
        val buffer = FloatArray(blockFrames)
        val ctx = createCtx()
        signalGen.generate(buffer, freqHz, ctx)
        return buffer
    }

    fun FloatArray.hasNonZeroSamples(): Boolean = any { it != 0.0f }

    "Freq DSL maps to FreqIgnitor" {
        val sig = IgnitorDsl.Freq.toExciter()
        sig shouldBe FreqIgnitor
    }

    "FreqIgnitor fills buffer with voice frequency" {
        val buffer = FloatArray(blockFrames)
        val ctx = createCtx()
        FreqIgnitor.generate(buffer, 440.0, ctx)
        buffer.all { it == 440.0f } shouldBe true
    }

    "Sine DSL produces non-zero output" {
        val sig = IgnitorDsl.Sine().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Sawtooth DSL produces non-zero output" {
        val sig = IgnitorDsl.Sawtooth().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Square DSL produces non-zero output" {
        val sig = IgnitorDsl.Square().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Triangle DSL produces non-zero output" {
        val sig = IgnitorDsl.Triangle().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "WhiteNoise DSL produces non-zero output" {
        val sig = IgnitorDsl.WhiteNoise.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Silence DSL produces zero output" {
        val sig = IgnitorDsl.Silence.toExciter()
        generateBlock(sig).all { it == 0.0f } shouldBe true
    }

    "Plus composition produces non-zero output" {
        val dsl = IgnitorDsl.Sine() + IgnitorDsl.Sawtooth()
        val sig = dsl.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgpad composition produces non-zero output" {
        val dsl = (IgnitorDsl.Sawtooth() + IgnitorDsl.Sawtooth().detune(0.1))
            .div(IgnitorDsl.Param("divisor", 2.0))
            .onePoleLowpass(3000.0)
        val sig = dsl.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "sgbell composition produces non-zero output" {
        val dsl = IgnitorDsl.Sine().fm(
            modulator = IgnitorDsl.Sine(),
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
        val dsl = IgnitorDsl.Square().lowpass(2000.0)
        val sig = dsl.toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "toExciter creates independent instances" {
        val dsl = IgnitorDsl.Sine()
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
