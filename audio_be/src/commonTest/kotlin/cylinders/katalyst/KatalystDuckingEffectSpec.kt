package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Ducking
import kotlin.math.abs

class KatalystDuckingEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
        delaySendBuffer = StereoBuffer(blockFrames),
        reverbSendBuffer = StereoBuffer(blockFrames),
    )

    "does nothing when no ducking is configured" {
        val effect = KatalystDuckingEffect()
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5)
        ctx.mixBuffer.right.fill(0.5)

        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.5
        ctx.mixBuffer.right[0] shouldBe 0.5
    }

    "does nothing when sidechain buffer is null" {
        val effect = KatalystDuckingEffect()
        effect.duckCylinderId = 0
        effect.ducking = Ducking(sampleRate = sampleRate, attackSeconds = 0.1, depth = 1.0)

        val ctx = createCtx()
        ctx.mixBuffer.left.fill(0.5)
        ctx.sidechainBuffer = null

        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.5
    }

    "reduces signal when sidechain has signal" {
        val effect = KatalystDuckingEffect()
        effect.duckCylinderId = 0
        effect.ducking = Ducking(sampleRate = sampleRate, attackSeconds = 0.001, depth = 1.0)

        val ctx = createCtx()
        val inputLevel = 0.5
        ctx.mixBuffer.left.fill(inputLevel)
        ctx.mixBuffer.right.fill(inputLevel)

        // Create a loud sidechain signal
        val sidechain = StereoBuffer(blockFrames)
        sidechain.left.fill(0.9)
        sidechain.right.fill(0.9)
        ctx.sidechainBuffer = sidechain

        effect.process(ctx)

        // Signal should be reduced
        val outputLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        (outputLevel < inputLevel) shouldBe true
    }

    "no ducking when sidechain is silent" {
        val effect = KatalystDuckingEffect()
        effect.duckCylinderId = 0
        effect.ducking = Ducking(sampleRate = sampleRate, attackSeconds = 0.001, depth = 1.0)

        // First, let the ducking fully release by processing many silent sidechain blocks
        val ctx = createCtx()
        val sidechain = StereoBuffer(blockFrames)
        ctx.sidechainBuffer = sidechain

        repeat(100) {
            ctx.mixBuffer.left.fill(0.5)
            ctx.mixBuffer.right.fill(0.5)
            sidechain.clear()
            effect.process(ctx)
        }

        // After full release, signal should pass through unchanged
        ctx.mixBuffer.left.fill(0.5)
        ctx.mixBuffer.right.fill(0.5)
        sidechain.clear()
        effect.process(ctx)

        val outputLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        (abs(outputLevel - 0.5) < 0.01) shouldBe true
    }

    "duck orbit ID and ducking instance can be updated" {
        val effect = KatalystDuckingEffect()

        effect.duckCylinderId shouldBe null
        effect.ducking shouldBe null

        effect.duckCylinderId = 3
        effect.ducking = Ducking(sampleRate = sampleRate, attackSeconds = 0.05, depth = 0.8)

        effect.duckCylinderId shouldBe 3
        effect.ducking!!.depth shouldBe 0.8
    }

    "clear() removes ducking configuration" {
        val effect = KatalystDuckingEffect()
        effect.duckCylinderId = 2
        effect.ducking = Ducking(sampleRate = sampleRate, attackSeconds = 0.05, depth = 0.8)

        effect.clear()

        effect.duckCylinderId shouldBe null
        effect.ducking shouldBe null
    }

    "linked stereo: both channels get identical gain reduction" {
        val effect = KatalystDuckingEffect()
        effect.duckCylinderId = 0
        effect.ducking = Ducking(sampleRate = sampleRate, attackSeconds = 0.001, depth = 0.8)

        val ctx = createCtx()
        ctx.mixBuffer.left.fill(1.0)
        ctx.mixBuffer.right.fill(1.0)

        // Asymmetric sidechain — left is louder
        val sidechain = StereoBuffer(blockFrames)
        sidechain.left.fill(0.9)
        sidechain.right.fill(0.1)
        ctx.sidechainBuffer = sidechain

        effect.process(ctx)

        // Both channels should have identical levels (linked stereo detection)
        val leftLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        val rightLevel = abs(ctx.mixBuffer.right[blockFrames - 1])
        (abs(leftLevel - rightLevel) < 0.001) shouldBe true
    }
})
