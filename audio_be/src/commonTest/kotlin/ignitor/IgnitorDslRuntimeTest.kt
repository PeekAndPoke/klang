/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.drive
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

    fun generateBlock(signalGen: Ignitor, freqHz: Double = 440.0): AudioBuffer {
        val buffer = AudioBuffer(blockFrames)
        val ctx = createCtx()
        signalGen.generate(buffer, freqHz, ctx)
        return buffer
    }

    fun AudioBuffer.hasNonZeroSamples(): Boolean = any { it != 0.0 }

    "Freq DSL maps to FreqIgnitor" {
        val sig = IgnitorDsl.Freq.toExciter()
        sig shouldBe FreqIgnitor
    }

    "FreqIgnitor fills buffer with voice frequency" {
        val buffer = AudioBuffer(blockFrames)
        val ctx = createCtx()
        FreqIgnitor.generate(buffer, 440.0, ctx)
        buffer.all { it == 440.0 } shouldBe true
    }

    "Constant(0.0) as freq produces 0 Hz (silence), not voice frequency" {
        val dsl = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(0.0))
        val sig = dsl.toExciter()
        val buffer = generateBlock(sig, freqHz = 440.0)
        // 0 Hz sine stays at sin(0) = 0, so all samples should be zero
        buffer.all { it == 0.0 } shouldBe true
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
        val sig = IgnitorDsl.WhiteNoise().toExciter()
        generateBlock(sig).hasNonZeroSamples() shouldBe true
    }

    "Silence DSL produces zero output" {
        val sig = IgnitorDsl.Silence.toExciter()
        generateBlock(sig).all { it == 0.0 } shouldBe true
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

    "Sine with Freq.div(2) as freq produces half the frequency" {
        val blockFrames = 4410 // 100ms at 44100
        val sr = 44100

        fun ctx() = IgniteContext(
            sampleRate = sr,
            voiceDurationFrames = sr,
            gateEndFrame = sr,
            releaseFrames = (0.1 * sr).toInt(),
            voiceEndFrame = sr + (0.1 * sr).toInt(),
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply {
            offset = 0
            length = blockFrames
            voiceElapsedFrames = 0
        }

        fun AudioBuffer.zeroCrossings(): Int {
            var count = 0
            for (i in 1 until size) {
                if ((this[i - 1] >= 0.0 && this[i] < 0.0) || (this[i - 1] < 0.0 && this[i] >= 0.0)) count++
            }
            return count
        }

        // Normal sine at voice frequency
        val normalDsl = IgnitorDsl.Sine()
        val normalSig = normalDsl.toExciter()
        val normalBuf = AudioBuffer(blockFrames)
        normalSig.generate(normalBuf, 440.0, ctx())

        // Sine with freq = Freq / 2 (should be 220 Hz)
        val halfFreqDsl = IgnitorDsl.Sine(freq = IgnitorDsl.Div(left = IgnitorDsl.Freq, right = IgnitorDsl.Constant(2.0)))
        val halfFreqSig = halfFreqDsl.toExciter()
        val halfBuf = AudioBuffer(blockFrames)
        halfFreqSig.generate(halfBuf, 440.0, ctx())

        val normalCrossings = normalBuf.zeroCrossings()
        val halfCrossings = halfBuf.zeroCrossings()

        // Half-frequency signal should have roughly half the zero crossings
        val ratio = normalCrossings.toDouble() / halfCrossings.toDouble()
        ratio shouldBe (2.0 plusOrMinus 0.1)
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

    // ─── Variants dispatch ────────────────────────────────────────────────────

    fun referenceBlock(dsl: IgnitorDsl): AudioBuffer = generateBlock(dsl.toExciter())

    "Variants with soundIndex=0 picks the first child" {
        val dsl = IgnitorDsl.Variants(listOf(IgnitorDsl.Sine(), IgnitorDsl.Sawtooth()))
        val picked = generateBlock(dsl.toExciter(soundIndex = 0))
        val expected = referenceBlock(IgnitorDsl.Sine())
        for (i in picked.indices) picked[i] shouldBe expected[i]
    }

    "Variants with soundIndex=1 picks the second child" {
        val dsl = IgnitorDsl.Variants(listOf(IgnitorDsl.Sine(), IgnitorDsl.Sawtooth()))
        val picked = generateBlock(dsl.toExciter(soundIndex = 1))
        val expected = referenceBlock(IgnitorDsl.Sawtooth())
        for (i in picked.indices) picked[i] shouldBe expected[i]
    }

    "Variants wraps via floor-mod for overflow indices" {
        val dsl = IgnitorDsl.Variants(listOf(IgnitorDsl.Sine(), IgnitorDsl.Sawtooth()))
        val picked = generateBlock(dsl.toExciter(soundIndex = 2)) // 2.mod(2) = 0 → Sine
        val expected = referenceBlock(IgnitorDsl.Sine())
        for (i in picked.indices) picked[i] shouldBe expected[i]
    }

    "Variants wraps via floor-mod for negative indices" {
        val dsl = IgnitorDsl.Variants(listOf(IgnitorDsl.Sine(), IgnitorDsl.Sawtooth()))
        val picked = generateBlock(dsl.toExciter(soundIndex = -1)) // -1.mod(2) = 1 → Sawtooth
        val expected = referenceBlock(IgnitorDsl.Sawtooth())
        for (i in picked.indices) picked[i] shouldBe expected[i]
    }

    "Nested Variants dispatch on the same soundIndex" {
        val inner = IgnitorDsl.Variants(listOf(IgnitorDsl.Sine(), IgnitorDsl.Square()))
        val outer = IgnitorDsl.Variants(
            listOf(
                inner.lowpass(2000.0),
                inner.drive(0.4),
            )
        )

        val expectedIndex0 = referenceBlock(IgnitorDsl.Sine().lowpass(2000.0))
        val expectedIndex1 = referenceBlock(IgnitorDsl.Square().drive(0.4))

        val pickedIndex0 = generateBlock(outer.toExciter(soundIndex = 0))
        val pickedIndex1 = generateBlock(outer.toExciter(soundIndex = 1))

        for (i in pickedIndex0.indices) pickedIndex0[i] shouldBe expectedIndex0[i]
        for (i in pickedIndex1.indices) pickedIndex1[i] shouldBe expectedIndex1[i]
    }

    "Variants with empty children throws at build time" {
        val dsl = IgnitorDsl.Variants(emptyList())
        var thrown: Throwable? = null
        try {
            dsl.toExciter()
        } catch (t: Throwable) {
            thrown = t
        }
        (thrown != null) shouldBe true
    }
})
