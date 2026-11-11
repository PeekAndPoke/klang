/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs

/**
 * A playback's gain ramp ([PlaybackGain], KLANG-00406 step 5): it lands exactly on its target in the block its time
 * names, moves per sample without a step, follows its law, starts a new target from where it is, and takes a ramp of
 * 0 at once and a non-finite value not at all. Each block multiplies a bus of ones, so the bus is the gain.
 */
class PlaybackGainSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** A ramp of exactly [blocks] blocks. */
    fun rampOf(blocks: Int): Double = blocks * blockFrames.toDouble() / sampleRate

    /** The gain of one block, sample by sample. */
    fun PlaybackGain.block(): DoubleArray {
        val bus = StereoBuffer(blockFrames).also { it.fill(1.0) }

        applyTo(bus)

        return bus.left.copyOf()
    }

    "a ramp lands exactly on its target in the block its time names, and stays there" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)
        // A target the ramp's arithmetic misses (1 + (0.3 - 1) is 0.30000000000000004): only an exact landing hits it
        val target = 0.3

        gain.retarget(gain = target, rampSec = rampOf(10))

        val blocks = (0 until 12).map { gain.block() }

        withClue("still on its way in block 9") { blocks[8].last() shouldNotBe target }
        withClue("block 10 ends on the target, bit for bit") { blocks[9].last() shouldBe target }
        blocks[10].toList() shouldBe List(blockFrames) { target }
        gain.isUnity shouldBe false
    }

    "the ramp moves per sample, without a step between samples or blocks" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)

        gain.retarget(gain = 0.0, rampSec = rampOf(4))

        val samples = listOf(1.0) + (0 until 4).flatMap { gain.block().toList() }
        val largest = samples.zipWithNext { a, b -> abs(b - a) }.max()

        // From 1 to 0 over 512 samples, linear: each step is 1 / 512, about 0.002
        largest shouldBeLessThan 0.01
    }

    "the law: linear in amplitude, half way is half the gain" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)

        gain.retarget(gain = 0.0, rampSec = rampOf(2))

        // Half way from position 1 to position 0 is the end of the first block
        gain.block().last() shouldBe (0.5 plusOrMinus 1e-12)
    }

    "a new target mid-ramp starts from where the gain is" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)

        gain.retarget(gain = 0.0, rampSec = rampOf(10))
        val before = (0 until 5).map { gain.block() }.last().last()

        gain.retarget(gain = 1.0, rampSec = rampOf(10))
        val after = gain.block()

        withClue("no jump at the new target") { abs(after.first() - before) shouldBeLessThan 0.01 }
        withClue("on its way up from there") { (after.last() > before) shouldBe true }
        withClue("on the way down, not back at 1") { before shouldBeLessThan 0.75 }
    }

    "the same target asked again with a shorter time mid-ramp lands in the new time (audit L8-09)" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)

        // A fade out over 40 blocks, then, 10 blocks in, the same fade asked to end within 4
        gain.retarget(gain = 0.0, rampSec = rampOf(40))
        repeat(10) { gain.block() }
        gain.retarget(gain = 0.0, rampSec = rampOf(4))

        val blocks = (0 until 4).map { gain.block() }

        withClue("silent at the end of the new time, not 26 blocks later") { blocks[3].last() shouldBe 0.0 }
        withClue("from where it was, no jump") { (blocks[0].first() > 0.7) shouldBe true }
    }

    "the same target asked again once settled changes nothing" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)

        gain.retarget(gain = 0.5, rampSec = rampOf(2))
        repeat(2) { gain.block() }
        gain.retarget(gain = 0.5, rampSec = rampOf(8))

        gain.block().toList() shouldBe List(blockFrames) { 0.5 }
    }

    "a ramp of 0 sets the gain at once; back at 1 the gain is unity" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)

        gain.retarget(gain = 0.5, rampSec = 0.0)

        gain.block().toList() shouldBe List(blockFrames) { 0.5 }

        gain.retarget(gain = 1.0, rampSec = 0.0)

        gain.isUnity shouldBe true
    }

    "a non-finite gain or ramp is ignored: the gain that stands stays" {
        val gain = PlaybackGain(sampleRate = sampleRate, blockFrames = blockFrames)

        gain.retarget(gain = 0.5, rampSec = 0.0)
        gain.retarget(gain = Double.NaN, rampSec = 0.0)
        gain.retarget(gain = 0.0, rampSec = Double.NaN)
        gain.retarget(gain = Double.POSITIVE_INFINITY, rampSec = rampOf(2))

        gain.target shouldBe 0.5
        gain.block().toList() shouldBe List(blockFrames) { 0.5 }
    }
})
