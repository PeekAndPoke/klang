package io.peekandpoke.klang.audio_be.orbits.bus

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Phaser

class BusPhaserEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = BusContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    fun createEffect(depth: Double = 0.5, rate: Double = 1.0): BusPhaserEffect {
        val ph = Phaser(sampleRate)
        ph.depth = depth
        ph.rate = rate
        ph.centerFreq = 1000.0
        ph.sweepRange = 1000.0
        ph.feedback = 0.5
        return BusPhaserEffect(ph)
    }

    "does nothing when depth is below threshold (< 0.01)" {
        val effect = createEffect(depth = 0.005)
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5f)
        ctx.mixBuffer.right.fill(0.5f)

        effect.process(ctx)

        // Buffer should be unchanged
        ctx.mixBuffer.left[0] shouldBe 0.5f
        ctx.mixBuffer.right[0] shouldBe 0.5f
    }

    "modifies mix buffer in-place when active" {
        val effect = createEffect(depth = 0.8, rate = 5.0)
        val ctx = createCtx()

        // Fill with constant signal
        val originalValue = 0.5f
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
        ctx.mixBuffer.left.fill(0.5f)

        effect.process(ctx)

        // Send buffers should still be zero
        ctx.delaySendBuffer.left[0] shouldBe 0.0f
        ctx.reverbSendBuffer.left[0] shouldBe 0.0f
    }
})
