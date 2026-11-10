/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * A delay or a reverb switched ON over a bus that has already run fades its feed in over the wet glide, the mirror
 * of every switch-off's fade (KLANG-00428, audit findings L7a-01 and L7a-02). Before, the wet SNAPPED out of Off on
 * every switch-on, so the unit was fed a step of the sounding mix, and the delay returned it one delay time later as
 * a click: kink 120.6 and a burst at -22.8 dB under a held tone (`disproof-sound.md`). A chain arriving through a
 * swap did the same from its first block. What still snaps: the first switch-on of a stage nothing has run through
 * since its build or its reset (a cylinder's first note), so a song whose sends are on from the start is unchanged.
 *
 * The oracle is the glide's own law: the feed rises linearly from 0 to the wet over [KNOB_GLIDE_SECONDS] in whole
 * blocks, so on a constant mix the delay's first echo can step by no more than one sample of that ramp.
 *
 * 44.1 kHz and 128 frames: one glide is 17 blocks.
 */
class KatalystSwitchOnFadeSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val fadeBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()
    val fadeSamples = fadeBlocks * blockFrames

    /** A held DC level on both sides: the step of a snapped feed is exactly `wet * level`. */
    val level = 0.5

    fun ctx() = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

    /** One block of [effect] on a held mix; returns what the stage added (the echo), left side. */
    fun block(effect: KatalystEffect, c: KatalystContext): DoubleArray {
        c.mixBuffer.left.fill(level)
        c.mixBuffer.right.fill(level)
        effect.process(c)

        return DoubleArray(blockFrames) { c.mixBuffer.left[it] - level }
    }

    fun delay() = KatalystDelayEffect(rings = SizedBuffers.forRings(sampleRate), sampleRate = sampleRate, blockFrames = blockFrames)

    fun reverb() = KatalystReverbEffect(units = ReverbUnits(sampleRate), blockFrames = blockFrames)

    /**
     * The echo of a delay switched on after [before] blocks of the held mix with the delay off, over enough blocks for
     * the whole first echo: time 0.02 s (882 whole samples, no interpolation), feedback 0, wet 0.8.
     */
    fun echoAfterSwitchOn(effect: KatalystDelayEffect, before: Int): DoubleArray {
        val c = ctx()

        repeat(before) {
            block(effect, c)
        }

        effect.configure(time = 0.02, feedback = 0.0, cap = 1.0, wet = 0.8)

        val blocks = fadeBlocks + 12
        val echo = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            block(effect, c).copyInto(destination = echo, destinationOffset = b * blockFrames)
        }

        return echo
    }

    fun largestStep(x: DoubleArray): Double {
        var m = 0.0

        for (i in 1 until x.size) {
            m = max(m, abs(x[i] - x[i - 1]))
        }

        return m
    }

    "a delay switched on over a held tone fades its feed in: the first echo rises by the glide's slope, it does not step" {
        val echo = echoAfterSwitchOn(delay(), before = 4)
        // One sample of the feed's ramp, times the held level: 0.5 * 0.8 / 2176.
        val slope = level * 0.8 / fadeSamples

        withClue("the echo arrives, and reaches the full wet once the ramp is through") {
            echo.max() shouldBe (level * 0.8 plusOrMinus 1e-12)
        }

        withClue("no step larger than the ramp's slope (a snapped feed steps by ${level * 0.8})") {
            largestStep(echo) shouldBeLessThan slope * 1.000001
        }
    }

    "the first switch-on of a fresh delay still snaps, and so does the first one after a reset: nothing had sounded" {
        withClue("fresh") {
            val echo = echoAfterSwitchOn(delay(), before = 0)

            largestStep(echo) shouldBe (level * 0.8 plusOrMinus 1e-12)
        }

        withClue("after a reset, the cylinder's new life") {
            val fx = delay()
            val c = ctx()

            repeat(4) { block(fx, c) }
            fx.reset()

            val echo = echoAfterSwitchOn(fx, before = 0)

            largestStep(echo) shouldBe (level * 0.8 plusOrMinus 1e-12)
        }
    }

    "a reverb switched on over a held tone glides its wet up from 0; a fresh one snaps" {
        val fx = reverb()
        val c = ctx()

        repeat(4) { block(fx, c) }
        fx.configure(size = 0.5, lowpass = null, wet = 0.8)
        block(fx, c)

        fx.wetInForce shouldBe (0.8 / fadeBlocks plusOrMinus 1e-12)

        repeat(fadeBlocks - 1) { block(fx, c) }

        fx.wetInForce shouldBe 0.8

        val fresh = reverb()

        fresh.configure(size = 0.5, lowpass = null, wet = 0.8)
        block(fresh, ctx())

        fresh.wetInForce shouldBe 0.8
    }

    "a chain swapped in over a sounding cylinder fades its delay's and its room's feed in from its first block" {
        for ((name, stage) in listOf(
            "delay" to KatalystStageDsl.Delay(
                wet = KatalystKnobDsl.Constant(0.5),
                time = KatalystKnobDsl.Constant(0.1),
                feedback = KatalystKnobDsl.Constant(0.0),
            ),
            "reverb" to KatalystStageDsl.Reverb(wet = KatalystKnobDsl.Constant(0.5), size = KatalystKnobDsl.Constant(3.0)),
        )) {
            withClue(name) {
                val rig = CylinderSwapRig(sampleRate = sampleRate)

                rig.registry.register("arriving", KatalystDsl.of(stage))
                rig.render(blocks = 20, level = 0.3)
                rig.cylinder.requestChain("arriving")
                rig.block(level = 0.3)

                val wet = when (name) {
                    "delay" -> rig.cylinder.delay.shouldNotBeNull().wetInForce
                    else -> rig.cylinder.reverb.shouldNotBeNull().wetInForce
                }

                // One block of the glide from 0, not the snapped 0.5 that fed the unit a step of the mix.
                wet shouldBe (0.5 / fadeBlocks plusOrMinus 1e-12)
            }
        }
    }
})
