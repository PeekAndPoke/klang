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
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * A delay or a reverb switched ON over a bus that has already run fades its feed in over the wet glide, the mirror
 * of every switch-off's fade (KLANG-00428, audit findings L7a-01 and L7a-02). Before, the wet SNAPPED out of Off on
 * every switch-on, so the unit was fed a step of the sounding mix, and the delay returned it one delay time later as
 * a click: kink 120.6 and a burst at -22.8 dB under a held tone (`disproof-sound.md`). A chain arriving through a
 * swap did the same from its first block. What still snaps: the first switch-on of a stage nothing has run through
 * since its build or its reset (a cylinder's first note), so a song whose sends are on from the start is unchanged,
 * and, since the decisions pass of 2026-11-11, any switch-on over a bus its cylinder judges SILENT (its last silence
 * visit found nothing and no voice rendered on it the block before), so a note that switches a delay on keeps its
 * echo's attack.
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

    // ── Only when the bus sounds (KLANG-00428, maintainer 2026-11-11: "fade in only when already sounding") ─────────

    /**
     * A cylinder driven as `Cylinders.processAndMix` drives it, one silence visit per block (it is the only one
     * rented): ten blocks of a held tone owned by a voice with no delay or room, then [quiet] blocks in which nothing
     * offers and the mix is [tailLevel] (0: silence; above it: a tail that rings with no voice), then [unvisited]
     * blocks of the tone's voice again with no silence visit (the visits are another cylinder's turn), then the note
     * whose owner switches [stage] on with a wet of 0.5. Returns the wet in force after that note's first block.
     */
    fun wetAfterSwitchOn(stage: String, quiet: Int, tailLevel: Double, unvisited: Int = 0): Double {
        // A grace far longer than the row: the cylinder stays active through its quiet blocks, never reset
        val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate, silentBlocksBeforeTailCheck = 1000)
        val first = VoiceTestHelpers.createSynthVoice(sampleRate = sampleRate, blockFrames = blockFrames)
        val on = when (stage) {
            "delay" -> klangMapOf("delay.wet" to 0.5, "delay.time" to 0.1, "delay.feedback" to 0.0)
            else -> klangMapOf("reverb.wet" to 0.5, "reverb.size" to 3.0)
        }
        val switchAt = 10 + quiet + unvisited
        val second = VoiceTestHelpers.createSynthVoice(
            startFrame = switchAt.toDouble() * blockFrames,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            katalystParams = on,
        )

        fun run(b: Int, level: Double, visit: Boolean = true) {
            cylinder.mixBuffer.left.fill(level)
            cylinder.mixBuffer.right.fill(level)
            cylinder.processEffects()

            if (visit) {
                cylinder.tryDeactivate(blockStart = b.toDouble() * blockFrames)
            }
        }

        for (b in 0 until 10) {
            cylinder.offerAndCommit(first, blockStart = b.toDouble() * blockFrames)
            run(b, level)
        }

        for (b in 10 until 10 + quiet) {
            cylinder.commitOwner()
            run(b, tailLevel)
        }

        for (b in 10 + quiet until switchAt) {
            cylinder.offerAndCommit(first, blockStart = b.toDouble() * blockFrames)
            run(b, level, visit = false)
        }

        withClue("the cylinder is still in use: not reset, so not fresh") {
            cylinder.isActive shouldBe true
        }

        cylinder.offerAndCommit(second, blockStart = switchAt.toDouble() * blockFrames)
        run(switchAt, level)

        return when (stage) {
            "delay" -> cylinder.delay.shouldNotBeNull().wetInForce
            else -> cylinder.reverb.shouldNotBeNull().wetInForce
        }
    }

    "a delay or a reverb switched on over a SILENT bus snaps: the note that switches it on keeps its attack" {
        for (stage in listOf("delay", "reverb")) {
            withClue("$stage after ten silent blocks") {
                wetAfterSwitchOn(stage = stage, quiet = 10, tailLevel = 0.0) shouldBe 0.5
            }
        }
    }

    "over a SOUNDING bus it still fades in: the tone a voice played the block before, or a tail with no voice" {
        for (stage in listOf("delay", "reverb")) {
            withClue("$stage, the tone's voice rendered the block before the switch-on") {
                wetAfterSwitchOn(stage = stage, quiet = 0, tailLevel = 0.0) shouldBe (0.5 / fadeBlocks plusOrMinus 1e-12)
            }

            withClue("$stage, no voice for ten blocks but the last silence visit found the mix sounding (a tail)") {
                wetAfterSwitchOn(stage = stage, quiet = 10, tailLevel = 0.1) shouldBe (0.5 / fadeBlocks plusOrMinus 1e-12)
            }

            withClue("$stage, the last visit found silence, but a voice rendered after it (another cylinder's visit turn)") {
                wetAfterSwitchOn(stage = stage, quiet = 10, tailLevel = 0.0, unvisited = 2) shouldBe (0.5 / fadeBlocks plusOrMinus 1e-12)
            }
        }
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
