/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

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

    "no time, or a time below 0, moves nothing; a target of no number is 0" {
        val start = WorldTime(on = 1.0, night = 0.5, ember = 0.2)
        val fade = WorldFade(start = start)

        fade.follow(target = WorldTime.OFF, dt = Double.NaN) shouldBe start
        fade.follow(target = WorldTime.OFF, dt = -1.0) shouldBe start
        fade.follow(target = WorldTime(on = Double.NaN, night = Double.NaN, ember = Double.NaN), dt = 10.0) shouldBe WorldTime.OFF
    }
})
