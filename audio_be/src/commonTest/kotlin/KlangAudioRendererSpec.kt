/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.DoorAdsr
import io.peekandpoke.klang.audio_be.voices.DoorFields
import io.peekandpoke.klang.audio_be.voices.withClassicSlots
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

class KlangAudioRendererSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createRenderer(): KlangAudioRenderer =
        KlangAudioRenderer.create(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            commLink = KlangCommLink().backend,
        )

    // ═════════════════════════════════════════════════════════════════════════════
    // Silent output
    // ═════════════════════════════════════════════════════════════════════════════

    "renderBlock leaves the clock on the NEXT block — the same convention as the live dispatcher" {
        // Block-framing B1: between renders the clock is the next block to be rendered. The live
        // dispatcher and this offline renderer must agree, or a voice scheduled between two offline
        // renders would land one block off from where the same schedule lands live. Review round 1
        // noted this line had no spec: deleting it was green because offline schedules everything
        // before block 0 — which is exactly why the two hosts could drift apart unnoticed.
        val renderer = createRenderer()
        renderer.clockForTest.cursorFrame shouldBe 0.0

        renderer.renderBlock(cursorFrame = 0.0, out = StereoBuffer(blockFrames))

        renderer.clockForTest.cursorFrame shouldBe blockFrames.toDouble()
    }

    "with no voices every block is written with exact zeros, and one voice is heard" {
        // Every sample of both channels is pre-filled with a non-zero, so a block that leaves any
        // of `out` unwritten goes red, on consecutive blocks and after cursor jumps.
        val renderer = createRenderer()
        val out = StereoBuffer(blockFrames)
        val cursors = List(10) { it * blockFrames } + listOf(44100, 1_000_000)

        for (cursor in cursors) {
            out.fill(0.999)
            renderer.renderBlock(cursorFrame = cursor.toDouble(), out = out)

            withClue("cursor $cursor") {
                out.isExactlySilent() shouldBe true
            }
        }

        // The engagement: a renderer that always writes zeros passes everything above. One sine
        // voice, scheduled before the first render as the offline renderer does, must come out
        // within the master limiter's lookahead plus a few blocks.
        val playing = createRenderer()

        playing.setBackendStartTime(0.0)
        playing.voices.scheduleVoice(
            ScheduledVoice(
                playbackId = "p",
                startTime = 0.0,
                gateEndTime = 1.0,
                data = VoiceData.empty.copy(sound = "sine", freqHz = 440.0).withClassicSlots(
                    DoorFields(adsr = DoorAdsr(attack = 0.01, decay = 0.0, sustain = 1.0, release = 0.01))
                ),
                playbackStartTime = 0.0,
            )
        )

        var heard = false

        repeat(8) { block ->
            playing.renderBlock(cursorFrame = (block * blockFrames).toDouble(), out = out)
            heard = heard || !out.isExactlySilent()
        }

        withClue("a scheduled voice reaches the output") { heard shouldBe true }
    }

    // The clip's boundary table is OutputClipSpec; the 16-bit edge is Pcm16EdgeSpec.

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
            limiter.process(left = left, right = right, blockSize = blockFrames)
            prevMax = left.maxOrNull()!!
        }

        // After convergence, two consecutive blocks should produce nearly identical levels
        val block1 = AudioBuffer(blockFrames) { loudAmplitude }
        val block1R = AudioBuffer(blockFrames) { loudAmplitude }
        limiter.process(left = block1, right = block1R, blockSize = blockFrames)

        val block2 = AudioBuffer(blockFrames) { loudAmplitude }
        val block2R = AudioBuffer(blockFrames) { loudAmplitude }
        limiter.process(left = block2, right = block2R, blockSize = blockFrames)

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
            limiter.process(left = lastLeft, right = lastRight, blockSize = blockFrames)
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
            limiter.process(left = left, right = right, blockSize = blockFrames)
        }

        val left = AudioBuffer(blockFrames) { quietAmplitude }
        val right = AudioBuffer(blockFrames) { quietAmplitude }
        limiter.process(left = left, right = right, blockSize = blockFrames)

        // Quiet signals below -1 dB threshold should be essentially unity-gained
        for (i in 0 until blockFrames) {
            val diff = abs(left[i] - quietAmplitude)
            diff shouldBeLessThan 0.01  // negligible change
        }
    }
})

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
