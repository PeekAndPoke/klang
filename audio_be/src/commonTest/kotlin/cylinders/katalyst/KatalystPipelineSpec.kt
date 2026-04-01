package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import kotlin.math.abs

/**
 * Integration tests for the bus effect pipeline.
 * Verifies that effects chain correctly: Delay → Reverb → Phaser → Compressor.
 */
class BusPipelineSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    fun createPipeline(
        delayTime: Double = 0.0,
        reverbRoom: Double = 0.0,
        phaserDepth: Double = 0.0,
        compressorThreshold: Double? = null,
    ): List<KatalystEffect> {
        val delay = KatalystDelayEffect(DelayLine(10.0, sampleRate).apply { delayTimeSeconds = delayTime })
        val reverb = KatalystReverbEffect(Reverb(sampleRate).apply { roomSize = reverbRoom })
        val phaser = KatalystPhaserEffect(Phaser(sampleRate).apply {
            depth = phaserDepth
            rate = 2.0
            centerFreq = 1000.0
            sweepRange = 1000.0
            feedback = 0.5
        })
        val compressor = KatalystCompressorEffect().apply {
            if (compressorThreshold != null) {
                this.compressor = Compressor(
                    sampleRate = sampleRate,
                    thresholdDb = compressorThreshold,
                    ratio = 4.0,
                    kneeDb = 0.0,
                    attackSeconds = 0.0001,
                    releaseSeconds = 0.1,
                )
            }
        }
        return listOf(delay, reverb, phaser, compressor)
    }

    "empty pipeline (all effects inactive) passes signal through unchanged" {
        val pipeline = createPipeline()
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5f)
        ctx.mixBuffer.right.fill(0.3f)

        for (effect in pipeline) {
            effect.process(ctx)
        }

        ctx.mixBuffer.left[0] shouldBe (0.5f plusOrMinus 1e-6f)
        ctx.mixBuffer.right[0] shouldBe (0.3f plusOrMinus 1e-6f)
    }

    "delay-only pipeline adds delayed signal to mix" {
        val pipeline = createPipeline(delayTime = 0.05)
        val ctx = createCtx()

        // Feed signal through multiple blocks
        repeat(50) {
            ctx.delaySendBuffer.left.fill(0.5f)
            ctx.delaySendBuffer.right.fill(0.5f)
            ctx.mixBuffer.clear()

            for (effect in pipeline) {
                effect.process(ctx)
            }
        }

        // After enough blocks, delayed signal should appear
        val hasSignal = ctx.mixBuffer.left.any { it != 0.0f }
        hasSignal shouldBe true
    }

    "reverb-only pipeline adds reverb signal to mix" {
        val pipeline = createPipeline(reverbRoom = 0.5)
        val ctx = createCtx()

        // Reverb comb filters need time to build up signal
        repeat(20) {
            ctx.reverbSendBuffer.left.fill(0.5f)
            ctx.reverbSendBuffer.right.fill(0.5f)
            ctx.mixBuffer.clear()

            for (effect in pipeline) {
                effect.process(ctx)
            }
        }

        val hasSignal = ctx.mixBuffer.left.any { it != 0.0f }
        hasSignal shouldBe true
    }

    "compressor reduces loud signal at end of chain" {
        val pipeline = createPipeline(compressorThreshold = -20.0)
        val ctx = createCtx()

        // Process enough blocks for the compressor envelope to converge
        repeat(20) {
            ctx.mixBuffer.left.fill(0.9f)
            ctx.mixBuffer.right.fill(0.9f)
            for (effect in pipeline) {
                effect.process(ctx)
            }
        }

        // Signal should be compressed
        val outputLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        (outputLevel < 0.9f) shouldBe true
    }

    "full pipeline chains all effects: delay + reverb + phaser + compressor" {
        val pipeline = createPipeline(
            delayTime = 0.05,
            reverbRoom = 0.3,
            phaserDepth = 0.5,
            compressorThreshold = -10.0,
        )
        val ctx = createCtx()

        // Feed signal through all send buffers
        repeat(50) {
            ctx.mixBuffer.left.fill(0.5f)
            ctx.mixBuffer.right.fill(0.5f)
            ctx.delaySendBuffer.left.fill(0.3f)
            ctx.delaySendBuffer.right.fill(0.3f)
            ctx.reverbSendBuffer.left.fill(0.2f)
            ctx.reverbSendBuffer.right.fill(0.2f)

            for (effect in pipeline) {
                effect.process(ctx)
            }
        }

        // After processing, signal should be modified by the chain
        // (not exactly 0.5 due to delay/reverb/phaser/compressor processing)
        val isModified = ctx.mixBuffer.left.any { abs(it - 0.5f) > 0.001f }
        isModified shouldBe true
    }

    "pipeline effects are independent — disabling one doesn't affect others" {
        // Create pipeline with only phaser active
        val pipeline = createPipeline(phaserDepth = 0.8)
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5f)
        ctx.mixBuffer.right.fill(0.5f)

        for (effect in pipeline) {
            effect.process(ctx)
        }

        // Phaser should modify the signal
        val phaserModified = ctx.mixBuffer.left.any { it != 0.5f }
        phaserModified shouldBe true

        // But send buffers should be untouched (delay/reverb were inactive)
        ctx.delaySendBuffer.left[0] shouldBe 0.0f
        ctx.reverbSendBuffer.left[0] shouldBe 0.0f
    }
})
