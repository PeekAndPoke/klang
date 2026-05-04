package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Phaser

class KatalystPhaserEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    fun createEffect(depth: Double = 0.5, rate: Double = 1.0): KatalystPhaserEffect {
        val ph = Phaser(sampleRate)
        ph.depth = depth
        ph.rate = rate
        ph.center = 1000.0
        ph.sweep = 1000.0
        ph.feedback = 0.5
        return KatalystPhaserEffect(ph)
    }

    "does nothing when depth is below threshold (< 0.01)" {
        val effect = createEffect(depth = 0.005)
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5)
        ctx.mixBuffer.right.fill(0.5)

        effect.process(ctx)

        // Buffer should be unchanged
        ctx.mixBuffer.left[0] shouldBe 0.5
        ctx.mixBuffer.right[0] shouldBe 0.5
    }

    "modifies mix buffer in-place when active" {
        val effect = createEffect(depth = 0.8, rate = 5.0)
        val ctx = createCtx()

        // Fill with constant signal
        val originalValue = 0.5
        ctx.mixBuffer.left.fill(originalValue)
        ctx.mixBuffer.right.fill(originalValue)

        effect.process(ctx)

        // At least some samples should be modified by the phaser
        val modified = ctx.mixBuffer.left.any { it != originalValue }
        modified shouldBe true
    }

    "phaser is an insert effect — reads and writes mix buffer only" {
        val effect = createEffect(depth = 0.5)
        val ctx = createCtx()

        // Only put signal in mix buffer (not send buffers)
        ctx.mixBuffer.left.fill(0.5)

        effect.process(ctx)

        // Send buffers should still be zero
        ctx.delaySendBuffer.left[0] shouldBe 0.0
        ctx.reverbSendBuffer.left[0] shouldBe 0.0
    }
})
