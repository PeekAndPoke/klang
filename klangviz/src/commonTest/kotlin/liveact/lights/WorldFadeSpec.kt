/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.stage.GhostLight

class WorldFadeSpec : StringSpec({

    "it starts where it is told, at once" {
        WorldFade(start = WorldTime(on = 1.0, night = 1.0, ember = 0.0)).now shouldBe WorldTime(on = 1.0, night = 1.0, ember = 0.0)
    }

    "day turns into night in three seconds, at an even pace, never past it" {
        val fade = WorldFade(start = WorldTime(on = 1.0, night = 0.0, ember = 0.0))
        val night = WorldTime(on = 1.0, night = 1.0, ember = 0.0)

        fade.follow(target = night, dt = 1.5).night shouldBe (0.5 plusOrMinus 1e-12)
        fade.follow(target = night, dt = 1.0).night shouldBe (0.5 + 1.0 / 3 plusOrMinus 1e-12)
        fade.follow(target = night, dt = 5.0) shouldBe night
    }

    "the time comes and goes as a whole in a second and a half" {
        val fade = WorldFade(start = WorldTime.OFF)
        val day = WorldTime(on = 1.0, night = 0.0, ember = 0.0)

        fade.follow(target = day, dt = 0.75).on shouldBe (0.5 plusOrMinus 1e-12)
        fade.follow(target = day, dt = 0.75).on shouldBe (1.0 plusOrMinus 1e-12)
        fade.follow(target = WorldTime.OFF, dt = 1.5) shouldBe WorldTime.OFF
    }

    "a turn may be given its own pace for once; the whole time keeps its own, and a pace of no number is the usual" {
        val ember = WorldTime(on = 1.0, night = 1.0, ember = 1.0)
        val day = WorldTime(on = 1.0, night = 0.0, ember = 0.0)
        val slow = WorldFade(start = ember)

        slow.follow(target = day, dt = 2.0, turnSeconds = 8.0) shouldBe WorldTime(on = 1.0, night = 0.75, ember = 0.75)
        slow.follow(target = day, dt = 2.0) shouldBe WorldTime(on = 1.0, night = 0.75 - 2.0 / 3, ember = 0.75 - 2.0 / 3)

        WorldFade(start = WorldTime.OFF).follow(target = day, dt = 0.75, turnSeconds = 8.0).on shouldBe (0.5 plusOrMinus 1e-12)

        listOf(0.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { pace ->
            WorldFade(start = ember).follow(target = day, dt = 1.5, turnSeconds = pace) shouldBe
                WorldTime(on = 1.0, night = 0.5, ember = 0.5)
        }
    }

    "the turn to the ghosts goes at the turn's pace; their colour is taken at once" {
        val fade = WorldFade(start = WorldTime(on = 1.0, night = 1.0, ember = 0.0))
        val ghosts = WorldTime(on = 1.0, night = 1.0, ember = 0.0, ghosts = 1.0, ghostLight = GhostLight.Green)

        fade.follow(target = ghosts, dt = 1.5) shouldBe ghosts.copy(ghosts = 0.5)
        fade.follow(target = ghosts, dt = 2.0, turnSeconds = 8.0).ghosts shouldBe 0.75
        fade.follow(target = WorldTime(on = 1.0, night = 1.0, ember = 0.0), dt = 0.75).ghosts shouldBe 0.5
    }

    "no time, or a time below 0, moves nothing; a target of no number is 0" {
        val start = WorldTime(on = 1.0, night = 0.5, ember = 0.2)
        val fade = WorldFade(start = start)

        fade.follow(target = WorldTime.OFF, dt = Double.NaN) shouldBe start
        fade.follow(target = WorldTime.OFF, dt = -1.0) shouldBe start
        fade.follow(target = WorldTime(on = Double.NaN, night = Double.NaN, ember = Double.NaN), dt = 10.0) shouldBe WorldTime.OFF
    }
})
