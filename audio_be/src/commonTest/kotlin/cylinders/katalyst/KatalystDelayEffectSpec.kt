package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine

class KatalystDelayEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    fun createEffect(delayTime: Double = 0.5, feedback: Double = 0.0): KatalystDelayEffect {
        val dl = DelayLine(maxDelaySeconds = 10.0, sampleRate = sampleRate)
        dl.delayTimeSeconds = delayTime
        dl.feedback = feedback
        return KatalystDelayEffect(dl)
    }

    "does nothing when delay time is below threshold (< 0.01s)" {
        val effect = createEffect(delayTime = 0.005)
        val ctx = createCtx()

        // Put signal in send buffer
        ctx.delaySendBuffer.left[0] = 0.5

        effect.process(ctx)

        // Mix buffer should be untouched
        ctx.mixBuffer.left[0] shouldBe 0.0
    }

    "processes delay when delay time is above threshold" {
        val effect = createEffect(delayTime = 0.05)
        val ctx = createCtx()

        // Put signal in send buffer
        for (i in 0 until blockFrames) {
            ctx.delaySendBuffer.left[i] = 0.5
            ctx.delaySendBuffer.right[i] = 0.3
        }

        // Process multiple blocks to allow delay to fill
        repeat(50) {
            ctx.delaySendBuffer.left.fill(0.5)
            ctx.delaySendBuffer.right.fill(0.3)
            ctx.mixBuffer.clear()
            effect.process(ctx)
        }

        // After enough blocks, delayed signal should appear in mix buffer
        val hasSignalL = ctx.mixBuffer.left.any { it != 0.0 }
        val hasSignalR = ctx.mixBuffer.right.any { it != 0.0 }

        hasSignalL shouldBe true
        hasSignalR shouldBe true
    }

    "delay line parameters are accessible and writable" {
        val effect = createEffect(delayTime = 0.5, feedback = 0.3)

        effect.delayLine.delayTimeSeconds shouldBe 0.5
        effect.delayLine.feedback shouldBe 0.3

        effect.delayLine.delayTimeSeconds = 1.0
        effect.delayLine.delayTimeSeconds shouldBe 1.0
    }
})
