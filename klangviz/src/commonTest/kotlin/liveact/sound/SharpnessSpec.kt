/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.sound

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/** Tells [sharpness] the same frame rate [times] times; whether any of them changed the ratio. */
private fun tell(sharpness: Sharpness, fps: Double, times: Int): Boolean =
    (1..times).map { sharpness.judge(fps) }.any { it }

class SharpnessSpec : StringSpec({

    "it starts at full and stays there while the frames come fast enough" {
        val sharpness = Sharpness(full = 2.0)

        sharpness.ratio shouldBe 2.0
        tell(sharpness, fps = 60.0, times = 40) shouldBe false
        tell(sharpness, fps = 47.0, times = 40) shouldBe false
        // a screen of 50 pictures a second, measured a hair below
        tell(sharpness, fps = 49.9, times = 40) shouldBe false
        sharpness.ratio shouldBe 2.0
    }

    "it takes a step down only at the fourth slow stretch in a row" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 46.9, times = 3) shouldBe false
        sharpness.ratio shouldBe 2.0
        sharpness.judge(46.9) shouldBe true
        sharpness.ratio shouldBe (1.68 plusOrMinus 1e-12)
    }

    "a fast stretch between slow ones starts the count again" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 22.0, times = 3) shouldBe false
        sharpness.judge(60.0) shouldBe false
        tell(sharpness, fps = 22.0, times = 3) shouldBe false
        sharpness.ratio shouldBe 2.0
    }

    "it goes on down while fewer pixels help, and stops where the frames are fast enough" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 22.0, times = 4) shouldBe true
        tell(sharpness, fps = 30.0, times = 4) shouldBe true
        tell(sharpness, fps = 41.0, times = 4) shouldBe true
        sharpness.ratio shouldBe (2.0 * 0.84 * 0.84 * 0.84 plusOrMinus 1e-12)
        tell(sharpness, fps = 48.0, times = 40) shouldBe false
        sharpness.ratio shouldBe (2.0 * 0.84 * 0.84 * 0.84 plusOrMinus 1e-12)
    }

    "it never goes below a plain screen's pixels, and reports no change once it is there" {
        val sharpness = Sharpness(full = 1.3)

        tell(sharpness, fps = 22.0, times = 4) shouldBe true
        sharpness.ratio shouldBe (1.092 plusOrMinus 1e-12)
        tell(sharpness, fps = 30.0, times = 4) shouldBe true
        sharpness.ratio shouldBe 1.0
        tell(sharpness, fps = 40.0, times = 4) shouldBe false
        sharpness.ratio shouldBe 1.0
    }

    "a plain screen has nothing to give: it stays at full" {
        val sharpness = Sharpness(full = 1.0)

        tell(sharpness, fps = 10.0, times = 40) shouldBe false
        sharpness.ratio shouldBe 1.0
    }

    "where two steps did not make the frames faster it goes back to full and stays" {
        val sharpness = Sharpness(full = 2.0)

        // a browser saving power: 30 frames a second whatever is drawn
        tell(sharpness, fps = 30.0, times = 8) shouldBe true
        sharpness.ratio shouldBe (2.0 * 0.84 * 0.84 plusOrMinus 1e-12)
        tell(sharpness, fps = 33.0, times = 4) shouldBe true
        sharpness.ratio shouldBe 2.0
        tell(sharpness, fps = 30.0, times = 40) shouldBe false
        sharpness.ratio shouldBe 2.0
    }

    "faster by an eighth after two steps counts as helped" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 30.0, times = 8) shouldBe true
        tell(sharpness, fps = 34.0, times = 4) shouldBe true
        sharpness.ratio shouldBe (2.0 * 0.84 * 0.84 * 0.84 plusOrMinus 1e-12)
    }

    "reset brings it back to full and lets it judge again" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 30.0, times = 12) shouldBe true
        sharpness.ratio shouldBe 2.0
        sharpness.reset() shouldBe false

        tell(sharpness, fps = 22.0, times = 4) shouldBe true
        sharpness.reset() shouldBe true
        sharpness.ratio shouldBe 2.0
        tell(sharpness, fps = 22.0, times = 3) shouldBe false
        sharpness.judge(22.0) shouldBe true
        sharpness.ratio shouldBe (1.68 plusOrMinus 1e-12)
    }

    "once fewer pixels helped, a later slow stretch is a step and never the way back to full" {
        val sharpness = Sharpness(full = 3.0)

        tell(sharpness, fps = 22.0, times = 4) shouldBe true
        tell(sharpness, fps = 30.0, times = 4) shouldBe true
        tell(sharpness, fps = 41.0, times = 4) shouldBe true
        // a heavier scene: slower than at the start, with fewer pixels
        tell(sharpness, fps = 23.0, times = 4) shouldBe true
        sharpness.ratio shouldBe (3.0 * 0.84 * 0.84 * 0.84 * 0.84 plusOrMinus 1e-12)
    }

    "frames that came fast enough after a step say that fewer pixels help" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 44.0, times = 4) shouldBe true
        sharpness.judge(60.0) shouldBe false
        tell(sharpness, fps = 45.0, times = 4) shouldBe true
        sharpness.judge(60.0) shouldBe false
        tell(sharpness, fps = 46.0, times = 4) shouldBe true
        sharpness.ratio shouldBe (2.0 * 0.84 * 0.84 * 0.84 plusOrMinus 1e-12)
    }

    "one stretch that is fast enough by a hair proves nothing" {
        val sharpness = Sharpness(full = 2.0)

        // held back by something else than pixels, near the line
        tell(sharpness, fps = 45.0, times = 4) shouldBe true
        sharpness.judge(48.0) shouldBe false
        tell(sharpness, fps = 45.0, times = 8) shouldBe true
        sharpness.ratio shouldBe 2.0
    }

    "fast frames before any step prove nothing" {
        val sharpness = Sharpness(full = 2.0)

        sharpness.judge(60.0) shouldBe false
        tell(sharpness, fps = 30.0, times = 12) shouldBe true
        sharpness.ratio shouldBe 2.0
    }

    "reset starts the count of slow stretches again" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 22.0, times = 3) shouldBe false
        sharpness.reset() shouldBe false
        tell(sharpness, fps = 22.0, times = 3) shouldBe false
        sharpness.ratio shouldBe 2.0
    }

    "reset forgets that fewer pixels helped" {
        val sharpness = Sharpness(full = 2.0)

        tell(sharpness, fps = 44.0, times = 4) shouldBe true
        sharpness.judge(60.0) shouldBe false
        sharpness.reset() shouldBe true
        tell(sharpness, fps = 30.0, times = 12) shouldBe true
        sharpness.ratio shouldBe 2.0
    }

    "reset takes the screen's ratio as it is now, and the floor follows it" {
        val sharpness = Sharpness(full = 2.0)

        sharpness.reset(full = 0.9) shouldBe true
        sharpness.ratio shouldBe 0.9
        sharpness.least shouldBe 0.9
        tell(sharpness, fps = 22.0, times = 8) shouldBe false
        sharpness.reset(full = 1.5) shouldBe true
        sharpness.least shouldBe 1.0
        tell(sharpness, fps = 22.0, times = 4) shouldBe true
        sharpness.ratio shouldBe (1.26 plusOrMinus 1e-12)
    }
})
