package io.peekandpoke.klang.audio_be.orbits.bus

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor
import kotlin.math.abs

class BusCompressorEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = BusContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    "does nothing when no compressor is configured" {
        val effect = BusCompressorEffect()
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5f)
        ctx.mixBuffer.right.fill(0.5f)

        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.5f
        ctx.mixBuffer.right[0] shouldBe 0.5f
    }

    "compresses loud signal when compressor is configured" {
        val effect = BusCompressorEffect()
        effect.compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -20.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.0001,
            releaseSeconds = 0.1,
        )

        val ctx = createCtx()
        val loudSignal = 0.9f
        ctx.mixBuffer.left.fill(loudSignal)
        ctx.mixBuffer.right.fill(loudSignal)

        // Process enough blocks for envelope to converge
        repeat(20) {
            ctx.mixBuffer.left.fill(loudSignal)
            ctx.mixBuffer.right.fill(loudSignal)
            effect.process(ctx)
        }

        // Last sample should be compressed (quieter than input)
        val outputLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        (outputLevel < loudSignal) shouldBe true
    }

    "quiet signal passes through uncompressed" {
        val effect = BusCompressorEffect()
        effect.compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -6.0,
            ratio = 4.0,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1,
        )

        val ctx = createCtx()
        // Very quiet signal, well below threshold
        val quietSignal = 0.01f
        ctx.mixBuffer.left.fill(quietSignal)
        ctx.mixBuffer.right.fill(quietSignal)

        effect.process(ctx)

        // Should pass through essentially unchanged
        val outputLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        (abs(outputLevel - quietSignal) < 0.001f) shouldBe true
    }

    "compressor can be set to null to disable" {
        val effect = BusCompressorEffect()
        effect.compressor = Compressor(sampleRate = sampleRate)

        effect.compressor = null

        val ctx = createCtx()
        ctx.mixBuffer.left.fill(0.5f)
        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.5f
    }
})
