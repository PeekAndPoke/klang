package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Reverb

class KatalystReverbEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    fun createEffect(roomSize: Double = 0.5): KatalystReverbEffect {
        val rev = Reverb(sampleRate)
        rev.roomSize = roomSize
        return KatalystReverbEffect(rev)
    }

    "does nothing when room size is below threshold (< 0.01)" {
        val effect = createEffect(roomSize = 0.005)
        val ctx = createCtx()

        ctx.reverbSendBuffer.left[0] = 0.5f

        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.0f
    }

    "processes reverb when room size is above threshold" {
        val effect = createEffect(roomSize = 0.5)
        val ctx = createCtx()

        // Feed signal through multiple blocks — reverb comb filters need time to build up
        repeat(20) {
            ctx.reverbSendBuffer.left.fill(0.5f)
            ctx.reverbSendBuffer.right.fill(0.5f)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        // Reverb should add signal to mix buffer
        val hasSignal = ctx.mixBuffer.left.any { it != 0.0f } || ctx.mixBuffer.right.any { it != 0.0f }
        hasSignal shouldBe true
    }

    "reverb tail persists across blocks" {
        val effect = createEffect(roomSize = 0.8)
        val ctx = createCtx()

        // Feed signal through enough blocks for comb filters to fill
        repeat(20) {
            ctx.reverbSendBuffer.left.fill(0.5f)
            ctx.reverbSendBuffer.right.fill(0.5f)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        // Now process with silent input — reverb tail should still produce output
        ctx.reverbSendBuffer.clear()
        ctx.mixBuffer.clear()
        effect.process(ctx)

        val hasSignal = ctx.mixBuffer.left.any { it != 0.0f }
        hasSignal shouldBe true
    }

    "reverb parameters are accessible" {
        val effect = createEffect(roomSize = 0.7)

        effect.reverb.roomSize shouldBe 0.7
        effect.reverb.sampleRate shouldBe sampleRate
    }
})
