/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The orbit's delay and reverb are INSERT-STYLE stages (Katalyst step 5b-2, decided with the
 * maintainer 2026-09-19, the signal-flow plan §7): each is fed from the orbit mix AT ITS POSITION in
 * the chain, scaled by the owner's one `wet`, and adds its return into that mix. No voice sends.
 *
 * Every oracle is built BY HAND from the bare DSP classes ([DelayLine], [Reverb]) and the test
 * signal's own definition: the stages before the one under test are replaced by the arithmetic
 * they are documented to do (a constant fader is one multiply), and the stage's feed is the mix at
 * that point times the wet. None of it is read back from a chain.
 */
class KatalystInsertFeedSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 40

    fun build(vararg stages: KatalystStageDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(*stages),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    ).also { it.applyParams(null) }

    fun c(value: Double) = IgnitorDsl.Constant(value)

    /** Two partials per channel: a signal whose every sample differs. */
    fun input(block: Int, i: Int, right: Boolean): Double {
        val n = (block * blockFrames + i).toDouble()

        return if (right) {
            0.3 * sin(2.0 * PI * 330.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 61.0 * n / sampleRate)
        } else {
            0.3 * sin(2.0 * PI * 440.0 * n / sampleRate) + 0.2 * sin(2.0 * PI * 97.0 * n / sampleRate)
        }
    }

    /** Renders [blocks] blocks of [input] (or of [impulse] alone) through [chain]; left and right interleaved. */
    fun render(chain: KatalystChain, impulse: Boolean = false): DoubleArray {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val out = DoubleArray(blocks * blockFrames * 2)

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = if (impulse) (if (b == 0 && i == 0) 0.5 else 0.0) else input(b, i, false)
                ctx.mixBuffer.right[i] = if (impulse) (if (b == 0 && i == 0) 0.5 else 0.0) else input(b, i, true)
            }

            chain.process(ctx)

            for (i in 0 until blockFrames) {
                out[(b * blockFrames + i) * 2] = ctx.mixBuffer.left[i]
                out[(b * blockFrames + i) * 2 + 1] = ctx.mixBuffer.right[i]
            }
        }

        return out
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    // ── The feed is the mix AT THE STAGE'S POSITION, times the wet ───────────────────────────────

    "the room is fed the mix at its position times the wet: a fader before it scales what it hears" {
        val fader = 0.5
        val wet = 0.3
        val chain = build(
            KatalystStageDsl.Gain(gain = c(fader)),
            KatalystStageDsl.Reverb(wet = c(wet), size = c(5.0)),
        )

        // By hand: the mix at the room's position is the input times the fader; the bare network
        // is fed that times the wet and adds its room to it.
        val room = Reverb(sampleRate).apply { size = Reverb.normalizeSize(5.0) }
        val feed = StereoBuffer(blockFrames)
        val mix = StereoBuffer(blockFrames)
        val expected = DoubleArray(blocks * blockFrames * 2)

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                mix.left[i] = input(b, i, false) * fader
                mix.right[i] = input(b, i, true) * fader
                feed.left[i] = mix.left[i] * wet
                feed.right[i] = mix.right[i] * wet
            }

            room.process(feed, mix, blockFrames)

            for (i in 0 until blockFrames) {
                expected[(b * blockFrames + i) * 2] = mix.left[i]
                expected[(b * blockFrames + i) * 2 + 1] = mix.right[i]
            }
        }

        val actual = render(chain)

        withClue("the room is really in the render: it differs from the faded dry alone") {
            (0 until actual.size / 2).any { abs(actual[it * 2] - input(it / blockFrames, it % blockFrames, false) * fader) > 1e-3 } shouldBe true
        }

        actual.bits() shouldBe expected.bits()
    }

    "the delay is fed the mix at its position times the wet, the same way" {
        val fader = 0.5
        val wet = 0.4
        val chain = build(
            KatalystStageDsl.Gain(gain = c(fader)),
            KatalystStageDsl.Delay(wet = c(wet), time = c(0.02), feedback = c(0.3), cap = c(1.0)),
        )

        val line = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate, time = 0.02, feedback = 0.3)
        val feed = StereoBuffer(blockFrames)
        val mix = StereoBuffer(blockFrames)
        val expected = DoubleArray(blocks * blockFrames * 2)

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                mix.left[i] = input(b, i, false) * fader
                mix.right[i] = input(b, i, true) * fader
                feed.left[i] = mix.left[i] * wet
                feed.right[i] = mix.right[i] * wet
            }

            line.process(feed, mix, blockFrames)

            for (i in 0 until blockFrames) {
                expected[(b * blockFrames + i) * 2] = mix.left[i]
                expected[(b * blockFrames + i) * 2 + 1] = mix.right[i]
            }
        }

        render(chain).bits() shouldBe expected.bits()
    }

    // ── The room hears the delay's echoes ────────────────────────────────────────────────────────

    "the reverb hears the delay's return: an echo that reaches the room is in the room" {
        // The classic order, delay then reverb, fed ONE impulse. The delay's echo arrives 0.02 s
        // later (the room's shortest comb answers it 1116 samples after that, inside the render); since the room is fed the mix after the delay, it rings on that echo as well.
        // Under the retired send model it heard the voices only, which is the control below.
        val delayWet = 1.0
        val roomWet = 0.4
        val chain = build(
            KatalystStageDsl.Delay(wet = c(delayWet), time = c(0.02), feedback = c(0.0), cap = c(1.0)),
            KatalystStageDsl.Reverb(wet = c(roomWet), size = c(3.0)),
        )

        fun byHand(roomHearsTheDelay: Boolean): DoubleArray {
            val line = DelayLine(maxDelaySeconds = 1.0, sampleRate = sampleRate, time = 0.02, feedback = 0.0)
            val room = Reverb(sampleRate).apply { size = Reverb.normalizeSize(3.0) }
            val mix = StereoBuffer(blockFrames)
            val lineFeed = StereoBuffer(blockFrames)
            val roomFeed = StereoBuffer(blockFrames)
            val out = DoubleArray(blocks * blockFrames * 2)

            for (b in 0 until blocks) {
                for (i in 0 until blockFrames) {
                    val x = if (b == 0 && i == 0) 0.5 else 0.0

                    mix.left[i] = x
                    mix.right[i] = x
                    lineFeed.left[i] = x * delayWet
                    lineFeed.right[i] = x * delayWet
                }

                line.process(lineFeed, mix, blockFrames)

                for (i in 0 until blockFrames) {
                    // The room's feed: the mix after the delay, or (the control) the dry input only.
                    val dry = if (b == 0 && i == 0) 0.5 else 0.0

                    roomFeed.left[i] = (if (roomHearsTheDelay) mix.left[i] else dry) * roomWet
                    roomFeed.right[i] = (if (roomHearsTheDelay) mix.right[i] else dry) * roomWet
                }

                room.process(roomFeed, mix, blockFrames)

                for (i in 0 until blockFrames) {
                    out[(b * blockFrames + i) * 2] = mix.left[i]
                    out[(b * blockFrames + i) * 2 + 1] = mix.right[i]
                }
            }

            return out
        }

        val actual = render(chain, impulse = true)
        val hears = byHand(roomHearsTheDelay = true)
        val deaf = byHand(roomHearsTheDelay = false)

        withClue("engagement: a room that hears the echo differs from one that does not, by an audible amount") {
            val gap = hears.indices.maxOf { abs(hears[it] - deaf[it]) }

            (gap > 1e-3) shouldBe true
        }

        actual.bits() shouldBe hears.bits()
    }

    // ── Nothing on the bus reads a voice's send amounts ─────────────────────────────────────────

    /** Renders [voice] through the real voice strip and the real cylinder; both channels' raw bits. */
    fun renderVoice(voice: Voice): Pair<List<Long>, RentedUnits> {
        val ctx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = mutableListOf<Long>()
        var orbit: RentedUnits? = null

        // 30 blocks: past the room's shortest comb (1116 samples) and the 0.05 s echo.
        for (b in 0 until 30) {
            ctx.blockStart = (b * blockFrames).toDouble()
            val cylinder = ctx.cylinders.getOrInit(voice.cylinderId, voice, ctx.blockStart)

            cylinder.clear()
            voice.render(ctx)
            cylinder.processEffects()

            out += cylinder.mixBuffer.left.map { it.toRawBits() }
            out += cylinder.mixBuffer.right.map { it.toRawBits() }
            orbit = RentedUnits(cylinder.delay?.delayLine, cylinder.reverb?.reverb)
        }

        return out to orbit!!
    }

    /** What `delay(0.3, 0.05, 0.5)` and `reverb(0.3, 6)` write into the orbit's slot state. */
    val slots = mapOf(
        "delay.wet" to 0.3, "delay.time" to 0.05, "delay.feedback" to 0.5, "delay.cap" to 1.0,
        "reverb.wet" to 0.3, "reverb.size" to 6.0,
    )

    "the slots alone reach the stages: the same voice without them renders dry" {
        // Through the real voice strip and cylinder: without the slots the orbit rents nothing and
        // sounds different. Until step 5b-3 this was the engagement control for a row showing that
        // the voice's delay and reverb FIELDS reached nothing; the fields left in that step.
        val (wet, _) = renderVoice(VoiceTestHelpers.createSynthVoice(blockFrames = blockFrames, katalystParams = slots))
        val (dry, orbit) = renderVoice(VoiceTestHelpers.createSynthVoice(blockFrames = blockFrames))

        withClue("no ring and no network without the slots") {
            orbit.line.shouldBeNull()
            orbit.room.shouldBeNull()
        }

        withClue("not two silences: the voice sounds") {
            dry.any { Double.fromBits(it) != 0.0 } shouldBe true
        }

        wet shouldNotBe dry
    }
})

/** What a render left rented on the orbit, for the row that asserts nothing was. */
private class RentedUnits(val line: DelayLine?, val room: Reverb?)
