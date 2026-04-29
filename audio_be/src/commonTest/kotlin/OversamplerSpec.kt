package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class OversamplerSpec : StringSpec({

    val blockFrames = 128

    "factorToStages converts correctly" {
        Oversampler.factorToStages(0) shouldBe 0
        Oversampler.factorToStages(1) shouldBe 0
        Oversampler.factorToStages(2) shouldBe 1
        Oversampler.factorToStages(3) shouldBe 1
        Oversampler.factorToStages(4) shouldBe 2
        Oversampler.factorToStages(5) shouldBe 2
        Oversampler.factorToStages(7) shouldBe 2
        Oversampler.factorToStages(8) shouldBe 3
        Oversampler.factorToStages(16) shouldBe 4
        Oversampler.factorToStages(-1) shouldBe 0
    }

    "identity transform preserves DC level" {
        val scratch = ScratchBuffers(blockFrames)
        val os = Oversampler(stages = 1) // 2x

        // DC signal — no phase issues, pure gain test
        val buffer = AudioBuffer(blockFrames) { 0.75 }
        os.process(buffer, 0, blockFrames, scratch) { it }

        // After filter warmup, DC should pass through at unity
        for (i in 16 until blockFrames) {
            buffer[i].toDouble() shouldBe (0.75 plusOrMinus 0.01)
        }
    }

    "identity transform preserves amplitude of low-freq sine" {
        val scratch = ScratchBuffers(blockFrames)
        val os = Oversampler(stages = 1) // 2x

        // Low-frequency sine — oversampling introduces ~4 sample group delay
        // but should preserve amplitude
        val freq = 100.0
        val sampleRate = 48000.0
        val buffer = AudioBuffer(blockFrames) { i ->
            sin(2.0 * PI * freq * i / sampleRate)
        }

        os.process(buffer, 0, blockFrames, scratch) { it }

        // Find peak amplitude in the stable region (after warmup)
        var maxAmp = 0.0
        for (i in 20 until blockFrames) {
            val a = abs(buffer[i].toDouble())
            if (a > maxAmp) maxAmp = a
        }

        // Peak should be close to 1.0 (the sine amplitude)
        // At 100Hz/48kHz, one full cycle = 480 samples, so 128 samples covers ~96 degrees
        // Max value in expected range: sin(96° * π/180) ≈ 0.995
        // After group delay, the peak might shift but amplitude should be preserved
        val expectedPeak = (0 until blockFrames).maxOf { abs(sin(2.0 * PI * freq * it / sampleRate)) }
        maxAmp shouldBe (expectedPeak plusOrMinus 0.02)
    }

    "2x oversampling reduces aliasing from hard clipping" {
        val scratch = ScratchBuffers(blockFrames)
        val sampleRate = 48000.0
        // Use a frequency high enough that clipping harmonics alias
        val freq = 8000.0

        // Generate sine at 8kHz
        val inputNoOs = AudioBuffer(blockFrames) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 0.8)
        }
        val inputOs = inputNoOs.copyOf()

        // Hard clip without oversampling
        for (i in inputNoOs.indices) {
            inputNoOs[i] = inputNoOs[i].coerceIn(-0.5, 0.5)
        }

        // Hard clip with 2x oversampling
        val os = Oversampler(stages = 1)
        os.process(inputOs, 0, blockFrames, scratch) { it.coerceIn(-0.5, 0.5) }

        // The oversampled version should have less high-frequency energy (less aliasing).
        // Measure by summing absolute differences between adjacent samples (rough HF proxy).
        var hfNoOs = 0.0
        var hfOs = 0.0
        for (i in 1 until blockFrames) {
            hfNoOs += abs(inputNoOs[i] - inputNoOs[i - 1])
            hfOs += abs(inputOs[i] - inputOs[i - 1])
        }

        // Oversampled should have less HF content
        (hfOs < hfNoOs) shouldBe true
    }

    "block boundary continuity - no discontinuity between blocks" {
        val scratch = ScratchBuffers(blockFrames)
        val os = Oversampler(stages = 1) // 2x
        val sampleRate = 48000.0
        val freq = 440.0

        // Process two consecutive blocks
        val block1 = AudioBuffer(blockFrames) { i ->
            sin(2.0 * PI * freq * i / sampleRate)
        }
        val block2 = AudioBuffer(blockFrames) { i ->
            sin(2.0 * PI * freq * (i + blockFrames) / sampleRate)
        }

        os.process(block1, 0, blockFrames, scratch) { it * 0.5 }
        os.process(block2, 0, blockFrames, scratch) { it * 0.5 }

        // Check continuity at the boundary: last sample of block1 should be close to first of block2
        val diff = abs(block2[0] - block1[blockFrames - 1])
        // Adjacent samples at 440Hz / 48kHz differ by at most ~0.057 per sample
        (diff < 0.1) shouldBe true
    }

    "4x oversampling works (stages=2)" {
        val scratch = ScratchBuffers(blockFrames)
        val os = Oversampler(stages = 2) // 4x

        os.factor shouldBe 4

        val buffer = AudioBuffer(blockFrames) { 0.7 }
        os.process(buffer, 0, blockFrames, scratch) { it * 0.5 }

        // DC signal * 0.5 should come through as ~0.35
        for (i in HALF_LEN_WARMUP until blockFrames) {
            buffer[i].toDouble() shouldBe (0.35 plusOrMinus 0.05)
        }
    }

    "offset is respected" {
        val scratch = ScratchBuffers(blockFrames)
        val os = Oversampler(stages = 1) // 2x

        val buffer = AudioBuffer(blockFrames) { 0.0 }
        val offset = 32
        val length = 64

        // Fill region with 1.0
        for (i in offset until offset + length) buffer[i] = 1.0

        os.process(buffer, offset, length, scratch) { it * 0.5 }

        // Before offset should be untouched
        for (i in 0 until offset) {
            buffer[i] shouldBe 0.0
        }
        // After offset+length should be untouched
        for (i in offset + length until blockFrames) {
            buffer[i] shouldBe 0.0
        }
    }
}) {
    companion object {
        // Allow warmup samples for filter group delay
        const val HALF_LEN_WARMUP = 15
    }
}
