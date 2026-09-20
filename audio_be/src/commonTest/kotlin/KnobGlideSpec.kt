/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs

/**
 * The contract of [KnobGlide] (`docs/plans/knob-glide.md` §3). Every oracle here is the straight
 * line from the start to the target over the glide time, written out by hand; none is read back
 * from the class.
 *
 * The glide lengths are the arithmetic of 50 ms at 128 frames, rounded to whole blocks:
 * 44.1 kHz is 2205 samples, 17.2 blocks, so 17; 48 kHz is 2400 samples, 18.75 blocks, so 19.
 */
class KnobGlideSpec : StringSpec({

    val frames = 128

    /** A glide that has consumed one block at [from], so the next retarget GLIDES instead of snapping. */
    fun settledAt(from: Double, sampleRate: Int = 44100): KnobGlide = KnobGlide(sampleRate, frames).apply {
        retarget(from)
        advance()
    }

    "a glide lands on the target bit for bit after exactly the glide time, and stays there" {
        val rates = listOf(44100 to 17, 48000 to 19)
        // Awkward pairs on purpose: an interpolation that is left to reach the end on its own
        // misses some of them by a rounding.
        val pairs = listOf(0.1 to 0.7, 0.2 to 0.9, 1.0 / 3.0 to 0.1, 0.05 to 1.0, 0.7 to 0.3)

        for ((sampleRate, blocks) in rates) {
            for ((from, to) in pairs) {
                withClue("$sampleRate Hz, $from -> $to") {
                    val glide = settledAt(from, sampleRate)

                    glide.retarget(to)

                    for (k in 1 until blocks) {
                        val value = glide.advance()
                        // The straight line, by hand.
                        val line = from + (to - from) * k / blocks

                        withClue("block $k") {
                            (abs(value - line) <= 1e-12) shouldBe true
                            value shouldNotBe to
                            glide.isGliding shouldBe true
                        }
                    }

                    glide.advance().toRawBits() shouldBe to.toRawBits()
                    glide.isGliding shouldBe false

                    repeat(40) {
                        glide.advance().toRawBits() shouldBe to.toRawBits()
                    }
                }
            }
        }
    }

    "a new target mid-glide restarts from the current value: no jump, and the whole glide time again" {
        val glide = settledAt(0.0)

        glide.retarget(1.0)

        repeat(8) {
            glide.advance()
        }

        val current = glide.value

        glide.retarget(0.0)

        // Nothing moves at the retarget itself...
        glide.value shouldBe current

        // ...and the first block after it is one seventeenth of the way from HERE to the new target.
        val first = glide.advance()

        (abs(first - (current - current / 17.0)) <= 1e-12) shouldBe true

        // The new glide takes its full 17 blocks, not the 9 the old one had left.
        repeat(15) {
            glide.advance()
        }

        glide.isGliding shouldBe true
        (glide.value > 0.0) shouldBe true

        glide.advance().toRawBits() shouldBe 0.0.toRawBits()
        glide.isGliding shouldBe false
    }

    "the first value snaps, after construction and after reset, until a block has consumed it" {
        val glide = KnobGlide(44100, frames)

        glide.retarget(0.7)
        glide.value shouldBe 0.7
        glide.isGliding shouldBe false

        // Still before any block: a second arrival is a starting point too, not a move.
        glide.retarget(0.2)
        glide.value shouldBe 0.2
        glide.isGliding shouldBe false

        glide.advance() shouldBe 0.2

        // A block has run at 0.2: from here on a new value is a move.
        glide.retarget(0.9)
        glide.isGliding shouldBe true
        glide.value shouldBe 0.2

        glide.reset()
        glide.retarget(0.4)
        glide.value shouldBe 0.4
        glide.isGliding shouldBe false
    }

    "a non-finite target can never get a glide stuck" {
        val glide = settledAt(0.0)

        glide.retarget(1.0)

        repeat(5) {
            glide.advance()
        }

        glide.retarget(Double.NaN)
        glide.retarget(Double.POSITIVE_INFINITY)
        glide.retarget(Double.NEGATIVE_INFINITY)

        glide.target shouldBe 1.0

        repeat(12) {
            glide.advance()
        }

        // 5 + 12 = 17 blocks: the glide landed where it was going, untouched by the garbage.
        glide.value.toRawBits() shouldBe 1.0.toRawBits()
        glide.isGliding shouldBe false

        // Before the first value, a non-finite arrival does not use up the snap either.
        val fresh = KnobGlide(44100, frames)

        fresh.retarget(Double.NaN)
        fresh.retarget(0.5)
        fresh.value shouldBe 0.5
        fresh.isGliding shouldBe false
    }

    // ── LEVEL: the per-sample half (Katalyst 5b-2) ───────────────────────────────────────────────

    /** A buffer of ones, so what [KnobGlide.advanceScaled] writes IS the level it applied. */
    fun ones(): StereoBuffer = StereoBuffer(frames).apply { fill(1.0) }

    "LEVEL: a glide ramps per SAMPLE along the straight line and lands on the target bit for bit" {
        val from = 0.2
        val to = 0.9
        val blocks = 17
        val glide = settledAt(from)
        val out = StereoBuffer(frames)
        // The straight line from `from` to `to` over the glide's 17 * 128 samples, by hand: sample
        // j of the glide (1-based) sits at j / (17 * 128) of the way.
        val total = blocks * frames

        glide.retarget(to)

        for (k in 1..blocks) {
            glide.advanceScaled(into = out, source = ones(), frames = frames)

            for (i in 0 until frames) {
                val j = (k - 1) * frames + i + 1
                val line = from + (to - from) * j / total

                withClue("block $k sample $i") {
                    (abs(out.left[i] - line) <= 1e-12) shouldBe true
                    out.right[i] shouldBe out.left[i]
                }
            }
        }

        withClue("the glide's last sample IS the target") {
            out.left[frames - 1].toRawBits() shouldBe to.toRawBits()
        }

        // Settled: every sample is the target, exactly, block after block.
        glide.advanceScaled(into = out, source = ones(), frames = frames)

        for (i in 0 until frames) {
            out.left[i].toRawBits() shouldBe to.toRawBits()
        }
    }

    "LEVEL: a settled or snapped knob multiplies by the value itself, one multiply per sample" {
        // The identity the whole step's "unchanged songs render the same" rests on: at a constant
        // wet the feed is the mix times that number, which is what a constant send was.
        val glide = KnobGlide(44100, frames)
        val source = StereoBuffer(frames)
        val out = StereoBuffer(frames)

        for (i in 0 until frames) {
            source.left[i] = 0.3 * i - 17.0
            source.right[i] = -0.7 * i + 5.0
        }

        glide.retarget(0.37)

        repeat(3) {
            glide.advanceScaled(into = out, source = source, frames = frames)

            for (i in 0 until frames) {
                out.left[i].toRawBits() shouldBe (source.left[i] * 0.37).toRawBits()
                out.right[i].toRawBits() shouldBe (source.right[i] * 0.37).toRawBits()
            }
        }
    }

    "LEVEL: a block shorter than blockFrames still starts where the last one ended and lands exactly" {
        val glide = settledAt(0.0)
        val out = StereoBuffer(frames)
        val short = 40

        glide.retarget(1.0)
        glide.advanceScaled(into = out, source = ones(), frames = frames)

        val ended = out.left[frames - 1]

        glide.advanceScaled(into = out, source = ones(), frames = short)

        val step = out.left[1] - out.left[0]

        withClue("continuous across the seam: the first sample is one step past where the block before ended") {
            (abs(out.left[0] - (ended + step)) <= 1e-12) shouldBe true
        }

        withClue("and the short block ends on this block's value, which the COEFFICIENT view reads too") {
            out.left[short - 1].toRawBits() shouldBe glide.value.toRawBits()
        }
    }

    "settleAt puts the knob there with nothing left to travel, and does NOT re-arm the snap" {
        val glide = settledAt(1.0)

        glide.retarget(0.0)
        glide.advance()

        withClue("a glide is running") {
            glide.isGliding shouldBe true
        }

        glide.settleAt(0.0)

        glide.value shouldBe 0.0
        glide.target shouldBe 0.0
        glide.isGliding shouldBe false

        // The snap window stays SPENT: the next target is travelled to, not jumped to. That is the
        // difference from `reset()`, and it is what keeps a stage's next switch-on a fade-in.
        glide.retarget(1.0)

        withClue("the next target glides, it does not snap") {
            glide.value shouldBe 0.0
            glide.isGliding shouldBe true
        }
    }

    "carryOver takes the other glide's position over, mid-glide, and spends the snap window" {
        val from = settledAt(1.0)

        from.retarget(0.0)
        from.advance()
        from.advance()

        val carried = from.value

        withClue("the source really is mid-glide") {
            (carried < 1.0 && carried > 0.0) shouldBe true
            from.isGliding shouldBe true
        }

        // A FRESH glide: left alone it would snap to whatever arrives next.
        val to = KnobGlide(sampleRate = 44100, blockFrames = frames)

        to.carryOver(from)

        to.value shouldBe carried
        to.target shouldBe 0.0
        to.isGliding shouldBe true

        // The glide's START comes too. Nothing proves that through a retarget, which overwrites
        // `start` anyway: it shows when the adopted glide simply CARRIES ON, which is what happens
        // on any block the arriving chain's writer does not run.
        val carriedOn = KnobGlide(sampleRate = 44100, blockFrames = frames)

        carriedOn.carryOver(from)

        val wantNext = from.advance()

        withClue("an adopted glide carries on down the SOURCE's line") {
            carriedOn.advance().toRawBits() shouldBe wantNext.toRawBits()
        }

        // The snap is spent, so the turn-around starts from where the knob stands.
        to.retarget(1.0)
        to.advance()

        val blocks = 17
        val expected = carried + (1.0 - carried) / blocks

        withClue("it turns around from the carried value, it does not snap to the new target") {
            (abs(to.value - expected) <= 1e-12) shouldBe true
        }
    }

    "carryOver of a RISING glide keeps its target: the swap case a falling one cannot see" {
        // Every falling glide's target is 0.0, which is also a fresh instance's default, so a
        // carryOver that forgot the target would look right. A rising one is the realistic swap:
        // a duck fading IN when the chain changes hands.
        val from = settledAt(0.0)

        from.retarget(1.0)
        from.advance()
        from.advance()
        from.advance()

        val carried = from.value

        withClue("the source is mid fade-in") {
            (carried > 0.0 && carried < 1.0) shouldBe true
            from.target shouldBe 1.0
            from.isGliding shouldBe true
        }

        val to = KnobGlide(sampleRate = 44100, blockFrames = frames)

        to.carryOver(from)

        to.value shouldBe carried
        to.target shouldBe 1.0
        to.isGliding shouldBe true

        // Carried on with no retarget it keeps climbing the SOURCE's line, target and all.
        val wantNext = from.advance()

        to.advance().toRawBits() shouldBe wantNext.toRawBits()
    }

    "carryOver clamps a countdown longer than this glide spans" {
        // The precondition is same-rate, same-block-size; the clamp is what keeps a shared helper
        // from driving a value outside [start, target] if a caller ever breaks it. 19 blocks at
        // 48 kHz into a 17-block glide at 44.1 kHz, falling from 1.0: uncoerced the first advance
        // reads 1.0588, above the value it started from.
        val wide = KnobGlide(sampleRate = 48000, blockFrames = frames)

        wide.retarget(1.0)
        wide.advance()
        wide.retarget(0.0)

        val narrow = settledAt(1.0)

        narrow.carryOver(wide)
        narrow.advance()

        withClue("the value stays inside the glide it was handed to") {
            (narrow.value <= 1.0 && narrow.value >= 0.0) shouldBe true
        }
    }
})
