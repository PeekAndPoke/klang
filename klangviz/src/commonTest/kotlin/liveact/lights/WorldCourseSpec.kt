/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.stage.GhostLight
import io.peekandpoke.klang.viz.liveact.stage.WorldLook

class WorldCourseSpec : StringSpec({

    val ember = WorldTime(on = 1.0, night = 1.0, ember = 1.0)
    val night = WorldTime(on = 1.0, night = 1.0, ember = 0.0)
    val day = WorldTime(on = 1.0, night = 0.0, ember = 0.0)

    "without an opening the stage starts where the look asks at the song's start, at once, and follows it" {
        val course = WorldCourse(opening = WorldLook.Off)

        course.follow(t = -15.0, look = WorldLook.Night, dt = 0.0) shouldBe night
        course.opens shouldBe false
        course.follow(t = -14.0, look = WorldLook.Day, dt = 1.5).night shouldBe (0.5 plusOrMinus 1e-12)

        // the drift a quarter round before the song starts at the song's own start, the day, as it always did
        WorldCourse(opening = WorldLook.Off, period = 30.0).follow(t = -7.5, look = WorldLook.Drift, dt = 0.0) shouldBe day
    }

    "with an opening the stage stands in its world from the first frame before the song, whatever the look asks" {
        val course = WorldCourse(opening = WorldLook.Ember)

        course.follow(t = -15.0, look = WorldLook.Drift, dt = 0.0) shouldBe ember
        course.opens shouldBe true
        course.follow(t = -5.0, look = WorldLook.Drift, dt = 10.0) shouldBe ember
        course.opens shouldBe true
    }

    "when the song begins it turns to the look's own in twenty seconds; then it is over" {
        val course = WorldCourse(opening = WorldLook.Ember)
        val turn = WorldCourse.TURN_SECONDS

        turn shouldBe 20.0
        course.follow(t = -0.5, look = WorldLook.Day, dt = 0.0) shouldBe ember
        course.follow(t = 0.0, look = WorldLook.Day, dt = 0.5) shouldBe
            WorldTime(on = 1.0, night = 1.0 - 0.5 / turn, ember = 1.0 - 0.5 / turn)
        course.opens shouldBe true
        course.follow(t = 10.0, look = WorldLook.Day, dt = 9.5) shouldBe WorldTime(on = 1.0, night = 0.5, ember = 0.5)
        course.opens shouldBe true
        course.follow(t = 20.0, look = WorldLook.Day, dt = 10.0) shouldBe day
        course.opens shouldBe false
    }

    "once it has arrived, the usual paces: three seconds from day to night, also after a seek back before the song" {
        val course = WorldCourse(opening = WorldLook.Ember)

        course.follow(t = -1.0, look = WorldLook.Day, dt = 0.0)
        course.follow(t = 1.0, look = WorldLook.Day, dt = 20.0) shouldBe day
        course.follow(t = 2.0, look = WorldLook.Night, dt = 1.5) shouldBe WorldTime(on = 1.0, night = 0.5, ember = 0.0)
        course.follow(t = -5.0, look = WorldLook.Night, dt = 1.5) shouldBe night
        course.opens shouldBe false
    }

    "the drift's own round is followed once the opening is over" {
        val course = WorldCourse(opening = WorldLook.Ember, period = 30.0)

        course.follow(t = -1.0, look = WorldLook.Drift, dt = 0.0)
        course.follow(t = 15.0, look = WorldLook.Drift, dt = 20.0).night shouldBe (1.0 plusOrMinus 1e-12)
        course.follow(t = 15.0, look = WorldLook.Drift, dt = 20.0).ember shouldBe 0.0
    }

    "where the look's world is off, the opening does nothing" {
        val course = WorldCourse(opening = WorldLook.Ember)

        course.follow(t = -15.0, look = WorldLook.Off, dt = 0.0) shouldBe WorldTime.OFF
        course.opens shouldBe false
        course.follow(t = -14.0, look = WorldLook.Off, dt = 1.0) shouldBe WorldTime.OFF
    }

    "a show first seen at its song's start or later has no opening: it starts where the look asks, at once" {
        listOf(0.0, 62.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { first ->
            val course = WorldCourse(opening = WorldLook.Ember)

            course.follow(t = first, look = WorldLook.Night, dt = 0.0) shouldBe night
            course.opens shouldBe false
        }
    }

    "a show first seen in the middle of its song is at that moment's time of day at once" {
        val course = WorldCourse(opening = WorldLook.Ember)
        val half = WorldLight.DRIFT_PERIOD / 2

        course.follow(t = half, look = WorldLook.Drift, dt = 0.0) shouldBe WorldLook.Drift.target(t = half)
        (WorldLook.Drift.target(t = half) == WorldLook.Drift.target(t = 0.0)) shouldBe false
        // a moment without an end is none: the song's start
        WorldCourse(opening = WorldLook.Ember).follow(t = Double.POSITIVE_INFINITY, look = WorldLook.Drift, dt = 0.0) shouldBe
            WorldLook.Drift.target(t = 0.0)
    }

    "a world chosen by hand during the opening wins at once, at the usual pace" {
        val course = WorldCourse(opening = WorldLook.Ember)

        course.follow(t = -10.0, look = WorldLook.Drift, dt = 0.0) shouldBe ember
        course.chosen()
        course.opens shouldBe false
        course.follow(t = -9.0, look = WorldLook.Night, dt = 1.5) shouldBe WorldTime(on = 1.0, night = 1.0, ember = 0.5)
    }

    "a world chosen by hand before the first frame: no opening at all" {
        val course = WorldCourse(opening = WorldLook.Ember)

        course.chosen()
        course.follow(t = -10.0, look = WorldLook.Day, dt = 0.0) shouldBe day
        course.opens shouldBe false
    }

    "the opening's world is the address's: another than ember opens in that one" {
        val course = WorldCourse(opening = WorldLook.Night)

        course.follow(t = -3.0, look = WorldLook.Day, dt = 0.0) shouldBe night
    }

    "the ghosts' colour goes with the stage, in the opening and after it" {
        val course = WorldCourse(opening = WorldLook.Ghosts)

        course.follow(t = -3.0, look = WorldLook.Day, dt = 0.0, ghost = GhostLight.Red) shouldBe
            WorldTime(on = 1.0, night = 1.0, ember = 0.0, ghosts = 1.0, ghostLight = GhostLight.Red)
        course.follow(t = 21.0, look = WorldLook.Day, dt = 21.0, ghost = GhostLight.Red) shouldBe day.copy(ghostLight = GhostLight.Red)
        course.opens shouldBe false
    }
})
