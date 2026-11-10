/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_bridge.constants.VOICE_CULL_SECONDS

/**
 * What the cull does with a voice whose output turns NaN, as the channel's comment states it (`measureVoicePeak`,
 * audit L6-07, whose comment claimed more than the code does): a NaN block measures as silent, so a HEARD voice that
 * blows up is culled, in its release or one cull window after its gate when it blew up in the gate; a voice NaN from
 * its FIRST sample is never heard, and a voice never heard is never culled: it renders to its end frame, like a voice
 * silent from its first sample. A check for the future of the documented rule.
 */
class VoiceCullNanSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val gateFrames = (0.05 * sampleRate).toInt()
    val endFrames = gateFrames + sampleRate
    val windowFrames = (VOICE_CULL_SECONDS * sampleRate).toInt()

    /** 0.5 for the first [cleanFrames] frames of the voice, then [after]. */
    class PoisonAfter(private val cleanFrames: Int, private val after: Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                val frame = ctx.voiceElapsedFrames + (i - ctx.offset)

                buffer[i] = if (frame < cleanFrames) 0.5 else after
            }
        }
    }

    /** Renders until the voice ends; the first frame of the block after its last one, and whether the cull ended it. */
    fun lifeOf(signal: Ignitor): Pair<Int, Boolean> {
        val voice = VoiceTestHelpers.createVoice(
            startFrame = 0.0,
            gateEndFrame = gateFrames.toDouble(),
            endFrame = endFrames.toDouble(),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            signal = signal,
        )
        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        var block = 0

        while (block * blockFrames < endFrames + 4 * blockFrames) {
            ctx.blockStart = (block * blockFrames).toDouble()

            val alive = voice.render(ctx)

            ctx.cylinders.clearAll()
            block++

            if (!alive) {
                break
            }
        }

        return Pair(block * blockFrames, voice.culled)
    }

    "a voice NaN from its first sample is never heard, so never culled: it renders to its end frame" {
        val (end, culled) = lifeOf(PoisonAfter(cleanFrames = 0, after = Double.NaN))

        culled shouldBe false
        (end >= endFrames) shouldBe true
    }

    "a heard voice that turns NaN in its gate is culled one cull window after the gate" {
        val (end, culled) = lifeOf(PoisonAfter(cleanFrames = gateFrames / 2, after = Double.NaN))

        culled shouldBe true

        withClue("ended at frame $end") {
            (end <= gateFrames + windowFrames + 2 * blockFrames) shouldBe true
        }
    }

    "a heard voice that turns NaN in its release is culled one cull window later" {
        val poisonAt = gateFrames + sampleRate / 10
        val (end, culled) = lifeOf(PoisonAfter(cleanFrames = poisonAt, after = Double.NaN))

        culled shouldBe true

        withClue("ended at frame $end") {
            (end <= poisonAt + windowFrames + 2 * blockFrames) shouldBe true
        }
    }
})
