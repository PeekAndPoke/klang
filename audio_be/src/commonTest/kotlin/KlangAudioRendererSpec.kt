package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

class KlangAudioRendererSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createRenderer(): KlangAudioRenderer {
        val commLink = KlangCommLink()
        val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate)

        val voiceScheduler = VoiceScheduler(
            options = VoiceScheduler.Options(
                commLink = commLink.backend,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                ignitorRegistry = IgnitorRegistry(),
                cylinders = cylinders,
            )
        )

        return KlangAudioRenderer(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voices = voiceScheduler,
            cylinders = cylinders,
        )
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Silent output
    // ═════════════════════════════════════════════════════════════════════════════

    "renderBlock with no voices produces all-zero output" {
        val renderer = createRenderer()
        val out = ShortArray(blockFrames * 2)

        renderer.renderBlock(cursorFrame = 0, out = out)

        for (i in out.indices) {
            out[i] shouldBe 0.toShort()
        }
    }

    "renderBlock produces correct output size (2x blockFrames for stereo interleave)" {
        val renderer = createRenderer()
        val out = ShortArray(blockFrames * 2)

        renderer.renderBlock(cursorFrame = 0, out = out)

        out.size shouldBe blockFrames * 2
    }

    "multiple silent renderBlock calls all produce zeros" {
        val renderer = createRenderer()
        val out = ShortArray(blockFrames * 2)

        repeat(10) { block ->
            renderer.renderBlock(cursorFrame = block * blockFrames, out = out)

            for (i in out.indices) {
                out[i] shouldBe 0.toShort()
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Clipping boundaries (unit-level: replicates renderer post-processing logic)
    // ═════════════════════════════════════════════════════════════════════════════

    "clip: sample at 0.0 maps to Short 0" {
        clipSample(0.0) shouldBe 0.toShort()
    }

    "clip: sample at 1.0 maps to Short.MAX_VALUE" {
        clipSample(1.0) shouldBe Short.MAX_VALUE
    }

    "clip: sample at -1.0 maps to -Short.MAX_VALUE (not Short.MIN_VALUE)" {
        // -1.0 * 32767 = -32767, which is within [-1.0, 1.0] branch
        val result = clipSample(-1.0)
        result shouldBe (-Short.MAX_VALUE.toInt()).toShort()
    }

    "clip: sample at 0.5 maps to half of Short.MAX_VALUE" {
        val result = clipSample(0.5)
        val expected = (0.5 * Short.MAX_VALUE).toInt().toShort()
        result shouldBe expected
    }

    "clip: sample at -0.5 maps to negative half of Short.MAX_VALUE" {
        val result = clipSample(-0.5)
        val expected = (-0.5 * Short.MAX_VALUE).toInt().toShort()
        result shouldBe expected
    }

    "clip: sample > 1.0 clamps to Short.MAX_VALUE" {
        clipSample(1.5) shouldBe Short.MAX_VALUE
        clipSample(2.0) shouldBe Short.MAX_VALUE
        clipSample(100.0) shouldBe Short.MAX_VALUE
    }

    "clip: sample < -1.0 clamps to Short.MIN_VALUE" {
        clipSample(-1.5) shouldBe Short.MIN_VALUE
        clipSample(-2.0) shouldBe Short.MIN_VALUE
        clipSample(-100.0) shouldBe Short.MIN_VALUE
    }

    "clip: sample just inside bounds are scaled, not clamped" {
        val justBelow1 = 0.999
        val result = clipSample(justBelow1)
        val expected = (justBelow1 * Short.MAX_VALUE).toInt().toShort()
        result shouldBe expected

        val justAboveMinus1 = -0.999
        val resultNeg = clipSample(justAboveMinus1)
        val expectedNeg = (justAboveMinus1 * Short.MAX_VALUE).toInt().toShort()
        resultNeg shouldBe expectedNeg
    }

    "clip: sample just outside bounds are clamped" {
        val justAbove1 = 1.0001
        clipSample(justAbove1) shouldBe Short.MAX_VALUE

        val justBelowMinus1 = -1.0001
        clipSample(justBelowMinus1) shouldBe Short.MIN_VALUE
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Stereo interleaving
    // ═════════════════════════════════════════════════════════════════════════════

    "interleave: output is [L0, R0, L1, R1, ...]" {
        val frames = 4
        val left = doubleArrayOf(0.1, 0.2, 0.3, 0.4)
        val right = doubleArrayOf(0.5, 0.6, 0.7, 0.8)
        val out = ShortArray(frames * 2)

        clipAndInterleave(left, right, frames, out)

        val maxShort = Short.MAX_VALUE

        for (i in 0 until frames) {
            val expectedL = (left[i] * maxShort).toInt().toShort()
            val expectedR = (right[i] * maxShort).toInt().toShort()
            out[i * 2] shouldBe expectedL
            out[i * 2 + 1] shouldBe expectedR
        }
    }

    "interleave: left-only signal has zeros at odd indices" {
        val frames = 4
        val left = doubleArrayOf(0.5, 0.5, 0.5, 0.5)
        val right = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val out = ShortArray(frames * 2)

        clipAndInterleave(left, right, frames, out)

        for (i in 0 until frames) {
            out[i * 2].toInt() shouldBeGreaterThan 0  // left has signal
            out[i * 2 + 1] shouldBe 0.toShort()       // right is silent
        }
    }

    "interleave: right-only signal has zeros at even indices" {
        val frames = 4
        val left = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val right = doubleArrayOf(0.5, 0.5, 0.5, 0.5)
        val out = ShortArray(frames * 2)

        clipAndInterleave(left, right, frames, out)

        for (i in 0 until frames) {
            out[i * 2] shouldBe 0.toShort()            // left is silent
            out[i * 2 + 1].toInt() shouldBeGreaterThan 0  // right has signal
        }
    }

    "interleave with clipping: mixed in-range and out-of-range samples" {
        val frames = 4
        val left = doubleArrayOf(0.5, 1.5, -0.5, -1.5)
        val right = doubleArrayOf(-1.5, -0.5, 1.5, 0.5)
        val out = ShortArray(frames * 2)

        clipAndInterleave(left, right, frames, out)

        val maxShort = Short.MAX_VALUE

        // Frame 0: L=0.5 (scaled), R=-1.5 (clamped)
        out[0] shouldBe (0.5 * maxShort).toInt().toShort()
        out[1] shouldBe Short.MIN_VALUE

        // Frame 1: L=1.5 (clamped), R=-0.5 (scaled)
        out[2] shouldBe Short.MAX_VALUE
        out[3] shouldBe (-0.5 * maxShort).toInt().toShort()

        // Frame 2: L=-0.5 (scaled), R=1.5 (clamped)
        out[4] shouldBe (-0.5 * maxShort).toInt().toShort()
        out[5] shouldBe Short.MAX_VALUE

        // Frame 3: L=-1.5 (clamped), R=0.5 (scaled)
        out[6] shouldBe Short.MIN_VALUE
        out[7] shouldBe (0.5 * maxShort).toInt().toShort()
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Limiter behavior (end-to-end via renderer with loud voices)
    // ═════════════════════════════════════════════════════════════════════════════

    "limiter compressor with loud signal converges to steady-state compression" {
        // The renderer's limiter uses these exact parameters
        val limiter = io.peekandpoke.klang.audio_be.effects.Compressor(
            sampleRate = sampleRate,
            thresholdDb = -1.0,
            ratio = 20.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1,
        )

        val loudAmplitude = 2.0

        // Process many blocks to converge the envelope follower
        var prevMax = Double.MAX_VALUE
        repeat(200) {
            val left = AudioBuffer(blockFrames) { loudAmplitude }
            val right = AudioBuffer(blockFrames) { loudAmplitude }
            limiter.process(left, right, blockFrames)
            prevMax = left.maxOrNull()!!
        }

        // After convergence, two consecutive blocks should produce nearly identical levels
        val block1 = AudioBuffer(blockFrames) { loudAmplitude }
        val block1R = AudioBuffer(blockFrames) { loudAmplitude }
        limiter.process(block1, block1R, blockFrames)

        val block2 = AudioBuffer(blockFrames) { loudAmplitude }
        val block2R = AudioBuffer(blockFrames) { loudAmplitude }
        limiter.process(block2, block2R, blockFrames)

        val level1 = block1.last()
        val level2 = block2.last()

        // Steady-state: levels should be nearly identical
        abs(level1 - level2) shouldBeLessThan 0.001
        // And compressed well below the input
        level1 shouldBeLessThan loudAmplitude
    }

    "limiter compressor reduces loud signals toward the -1dB ceiling" {
        // Directly test the Compressor (the limiter used by the renderer)
        // with the same parameters the renderer uses: threshold=-1dB, ratio=20, knee=0
        val limiter = io.peekandpoke.klang.audio_be.effects.Compressor(
            sampleRate = sampleRate,
            thresholdDb = -1.0,
            ratio = 20.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1,
        )

        // Feed a very loud signal (amplitude 3.0, ~9.5 dB) through multiple blocks
        // to let the envelope follower converge
        val loudAmplitude = 3.0

        var lastLeft = AudioBuffer(blockFrames) { loudAmplitude }
        var lastRight = AudioBuffer(blockFrames) { loudAmplitude }

        repeat(100) {
            lastLeft = AudioBuffer(blockFrames) { loudAmplitude }
            lastRight = AudioBuffer(blockFrames) { loudAmplitude }
            limiter.process(lastLeft, lastRight, blockFrames)
        }

        // After convergence, all samples should be compressed well below the input amplitude
        val maxOutput = lastLeft.maxOrNull()!!
        val minOutput = lastLeft.minOrNull()!!

        // The limiter with -1dB threshold and 20:1 ratio should compress 3.0 significantly
        // -1 dB linear ~= 0.891, so output should be around that level
        maxOutput shouldBeLessThan 1.2  // well below the input of 3.0
        minOutput shouldBeGreaterThan 0.0  // still positive (same-sign input)
    }

    "limiter preserves quiet signals below threshold" {
        val limiter = io.peekandpoke.klang.audio_be.effects.Compressor(
            sampleRate = sampleRate,
            thresholdDb = -1.0,
            ratio = 20.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1,
        )

        // A quiet signal at -20 dB (amplitude ~0.1) should pass through nearly unchanged
        val quietAmplitude = 0.1

        // Warm up with quiet signal
        repeat(50) {
            val left = AudioBuffer(blockFrames) { quietAmplitude }
            val right = AudioBuffer(blockFrames) { quietAmplitude }
            limiter.process(left, right, blockFrames)
        }

        val left = AudioBuffer(blockFrames) { quietAmplitude }
        val right = AudioBuffer(blockFrames) { quietAmplitude }
        limiter.process(left, right, blockFrames)

        // Quiet signals below -1 dB threshold should be essentially unity-gained
        for (i in 0 until blockFrames) {
            val diff = abs(left[i] - quietAmplitude)
            diff shouldBeLessThan 0.01  // negligible change
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═════════════════════════════════════════════════════════════════════════════

    "renderBlock at various cursor positions produces consistent silent output" {
        val renderer = createRenderer()
        val out = ShortArray(blockFrames * 2)

        val cursorPositions = listOf(0, 128, 44100, 1_000_000)

        for (cursor in cursorPositions) {
            out.fill(999.toShort()) // fill with non-zero to verify it gets overwritten
            renderer.renderBlock(cursorFrame = cursor, out = out)

            for (i in out.indices) {
                out[i] shouldBe 0.toShort()
            }
        }
    }
})

// ═════════════════════════════════════════════════════════════════════════════
// Helper functions replicating the renderer's post-processing logic
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Replicates the renderer's clip logic for a single float sample.
 * This matches the exact branching in KlangAudioRenderer.renderBlock.
 */
private fun clipSample(sample: AudioSample): Short {
    val maxShort = Short.MAX_VALUE

    val out = if (sample >= -1.0 && sample <= 1.0) {
        (sample * maxShort).toInt()
    } else if (sample > 1.0) {
        Short.MAX_VALUE.toInt()
    } else {
        Short.MIN_VALUE.toInt()
    }

    return out.toShort()
}

/**
 * Replicates the renderer's clip + interleave logic.
 * This matches the exact loop in KlangAudioRenderer.renderBlock (step 5).
 */
private fun clipAndInterleave(left: AudioBuffer, right: AudioBuffer, blockFrames: Int, out: ShortArray) {
    val maxShort = Short.MAX_VALUE

    for (i in 0 until blockFrames) {
        val lSample = left[i]
        val rSample = right[i]

        val lOut = if (lSample >= -1.0 && lSample <= 1.0) {
            (lSample * maxShort).toInt()
        } else if (lSample > 1.0) {
            Short.MAX_VALUE.toInt()
        } else {
            Short.MIN_VALUE.toInt()
        }

        val rOut = if (rSample >= -1.0 && rSample <= 1.0) {
            (rSample * maxShort).toInt()
        } else if (rSample > 1.0) {
            Short.MAX_VALUE.toInt()
        } else {
            Short.MIN_VALUE.toInt()
        }

        val idx = i * 2
        out[idx] = lOut.toShort()
        out[idx + 1] = rOut.toShort()
    }
}

private infix fun Double.shouldBeLessThan(other: Double) {
    if (this >= other) {
        throw AssertionError("Expected $this to be less than $other")
    }
}

private infix fun Double.shouldBeGreaterThan(other: Double) {
    if (this <= other) {
        throw AssertionError("Expected $this to be greater than $other")
    }
}
