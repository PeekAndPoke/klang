/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.viz.liveact.band.Player

class MeetHelpersSpec : StringSpec({

    "what each one plays is what the show's cast gives them" {
        playsIn(SHOW_SCORE.cast.drums.player) shouldBe "Drums"
        playsIn(SHOW_SCORE.cast.trumpet.player) shouldBe "Trumpet"
        playsIn(SHOW_SCORE.cast.marimba.player) shouldBe "Marimba"
        SHOW_SCORE.cast.strings.map { it.player } shouldBe listOf(Player.Beben, Player.Skalpell, Player.Klaue)
        playsIn(Player.Beben) shouldBe "Bass"
        playsIn(Player.Skalpell) shouldBe "Lead guitar"
        playsIn(Player.Klaue) shouldBe "Rhythm guitar"
    }

    "everyone in the band plays something in the show" {
        for (player in Player.entries) {
            playsIn(player) shouldNotBe ""
        }
    }

    "a flat swipe to the left is the next member, to the right the one before" {
        swipeStep(dx = -120.0, dy = 10.0) shouldBe 1
        swipeStep(dx = 120.0, dy = -10.0) shouldBe -1
        swipeStep(dx = -SWIPE_LEAST, dy = 0.0) shouldBe 1
    }

    "a short drag, a steep one or no number is no swipe" {
        swipeStep(dx = -SWIPE_LEAST + 1, dy = 0.0) shouldBe 0
        swipeStep(dx = -100.0, dy = 61.0) shouldBe 0
        swipeStep(dx = 100.0, dy = 60.0) shouldBe -1
        swipeStep(dx = Double.NaN, dy = 0.0) shouldBe 0
        swipeStep(dx = -100.0, dy = Double.NaN) shouldBe 0
    }
})
