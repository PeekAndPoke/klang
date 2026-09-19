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
})
