/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player
import kotlin.math.PI
import kotlin.math.abs

class CarouselSpec : StringSpec({

    val step = MeetLayout.STEP

    "it stands where it starts until a turn is chosen" {
        val carousel = Carousel(start = 0.4)

        carousel.at(0.0) shouldBe 0.4
        carousel.at(100.0) shouldBe 0.4
        carousel.speedAt(5.0) shouldBe 0.0
        carousel.turning(5.0) shouldBe false
    }

    "one step takes the pace, eased at both ends: still at the start and at the end, fastest halfway" {
        val carousel = Carousel(start = 0.0, pace = 1.4)

        carousel.choose(turn = -step, now = 10.0)

        carousel.at(10.0) shouldBe (0.0 plusOrMinus 1e-12)
        carousel.speedAt(10.0) shouldBe (0.0 plusOrMinus 1e-12)
        carousel.at(10.7) shouldBe (-step / 2 plusOrMinus 1e-12)
        abs(carousel.speedAt(10.7)) shouldBeGreaterThan abs(carousel.speedAt(10.2))
        carousel.turning(11.3) shouldBe true
        carousel.at(11.4) shouldBe (-step plusOrMinus 1e-12)
        carousel.speedAt(11.4) shouldBe 0.0
        carousel.turning(11.4) shouldBe false
    }

    "it goes the shortest way round: from Sirene to Klaue is one step back, not five on" {
        val carousel = Carousel(start = MeetLayout.turnFor(Player.Sirene))

        carousel.choose(turn = MeetLayout.turnFor(Player.Klaue), now = 0.0)

        carousel.target shouldBe (step plusOrMinus 1e-12)
        carousel.at(0.7) shouldBeGreaterThan 0.0
    }

    "after many turns it goes the shortest way from where it is, a round or more from where it started" {
        val carousel = Carousel(start = 0.0, pace = 1.0)
        var now = 0.0

        // seven steps on, one at a time: past a whole round
        for (i in 1..7) {
            carousel.choose(turn = -i * step, now = now)
            now += 2.0
        }

        carousel.at(now) shouldBe (-7 * step plusOrMinus 1e-9)

        // the place two on from the start is, from here, the next one on (not five back)
        carousel.choose(turn = -2 * step, now = now)

        carousel.target shouldBe (-8 * step plusOrMinus 1e-9)
    }

    "halfway round it turns to smaller angles from standing, and keeps its way while it turns" {
        val standing = Carousel(start = 0.0)

        standing.choose(turn = PI, now = 0.0)
        standing.target shouldBe (-PI plusOrMinus 1e-12)

        val turning = Carousel(start = 0.0, pace = 1.0)

        turning.choose(turn = step, now = 0.0)
        // halfway through that step it is at step / 2: a half round on from there
        turning.choose(turn = step / 2 + PI, now = 0.5)
        turning.target shouldBe (step / 2 + PI plusOrMinus 1e-12)
    }

    "the same choice again changes nothing, also while it turns" {
        val carousel = Carousel(start = 0.0, pace = 1.0)

        carousel.choose(turn = -step, now = 0.0)

        val halfway = carousel.at(0.5)

        carousel.choose(turn = -step + 2 * PI, now = 0.5)
        carousel.at(0.5) shouldBe halfway
        carousel.at(1.0) shouldBe (-step plusOrMinus 1e-12)
    }

    "a new choice while it turns is taken up smoothly: neither the place nor the speed jumps" {
        val carousel = Carousel(start = 0.0, pace = 1.4)

        carousel.choose(turn = -step, now = 0.0)

        val before = carousel.at(0.6)
        val speed = carousel.speedAt(0.6)

        carousel.choose(turn = -2 * step, now = 0.6)

        carousel.at(0.6) shouldBe (before plusOrMinus 1e-12)
        carousel.speedAt(0.6) shouldBe (speed plusOrMinus 1e-12)
        // a moment later it is close by and on its way to the new target
        abs(carousel.at(0.61) - before) shouldBeLessThan 0.02
        carousel.at(10.0) shouldBe (-2 * step plusOrMinus 1e-12)
    }

    "more steps take longer, but not as many times as long" {
        val one = Carousel(start = 0.0, pace = 1.0)
        val three = Carousel(start = 0.0, pace = 1.0)

        one.choose(turn = -step, now = 0.0)
        three.choose(turn = -3 * step, now = 0.0)

        one.turning(1.2) shouldBe false
        three.turning(1.2) shouldBe true
        three.turning(2.9) shouldBe false
    }
})
