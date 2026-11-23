/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.lights.GhostVisits

class AroundCourseSpec : StringSpec({

    /** The surrounding of the show of [seed] at every whole second up to [seconds]. */
    fun course(seed: Int, seconds: Int): List<AroundMoment> =
        (0..seconds).map { AroundCourse.at(t = it.toDouble(), seed = seed, look = AroundLook.Turns) }

    /** The turns of the show of [seed] up to [seconds]: when each lies, from the ghosts' visits. */
    fun turns(seed: Int, seconds: Double): List<Double> =
        (0..(seconds / GhostVisits.ROUND).toInt()).mapNotNull { AroundCourse.flipOf(round = it, seed = seed) }.filter { it <= seconds }

    "the address: forest, ruins, stones or off held, anything else by turns" {
        AroundLook.of(Switches.parse("?around=forest")) shouldBe AroundLook.Forest
        AroundLook.of(Switches.parse("?around=ruins")) shouldBe AroundLook.Ruins
        AroundLook.of(Switches.parse("?around=stones")) shouldBe AroundLook.Stones
        AroundLook.of(Switches.parse("?around=off")) shouldBe AroundLook.Off
        AroundLook.of(Switches.parse("?around=turns")) shouldBe AroundLook.Turns
        AroundLook.of(Switches.parse("?around=moor")) shouldBe AroundLook.Turns
        AroundLook.of(Switches.parse("")) shouldBe AroundLook.Turns
    }

    "the new look has them by turns, the look as made none; the address's switch wins over both" {
        LookVariants.of(Switches.parse("")).around shouldBe AroundLook.Off
        LookVariants.of(Switches.parse("?look=new")).around shouldBe AroundLook.Turns
        LookVariants.of(Switches.parse("?look=new&around=off")).around shouldBe AroundLook.Off
        LookVariants.of(Switches.parse("?around=stones")).around shouldBe AroundLook.Stones
    }

    "every show begins in the forest, and takes all of them in an order of its own, none twice in a row" {
        val orders = (1..40).map { AroundCourse.order(it) }

        for (order in orders) {
            order.first() shouldBe Surrounding.Forest
            order shouldContainExactlyInAnyOrder Surrounding.entries
        }

        // the seed decides the order after the forest: both orders come
        orders.map { it[1] }.toSet() shouldBe setOf(Surrounding.Ruins, Surrounding.Stones)
    }

    "by turns: the next one at each turn, in the show's order, round and round" {
        for (seed in listOf(1, 4, 5, 42)) {
            val order = AroundCourse.order(seed)
            val flips = turns(seed = seed, seconds = 1800.0)

            flips.size shouldBeGreaterThan 5
            AroundCourse.at(t = 0.0, seed = seed, look = AroundLook.Turns).surrounding shouldBe Surrounding.Forest

            fun at(t: Double): Surrounding? = AroundCourse.at(t = t, seed = seed, look = AroundLook.Turns).surrounding

            for ((n, flip) in flips.withIndex()) {
                at(flip - 0.01) shouldBe order[n % 3]
                at(flip + 0.01) shouldBe order[(n + 1) % 3]
            }

            // and nowhere else does it change, nor twice the same in a row
            val seen = course(seed = seed, seconds = 1800).map { it.surrounding }
            val changes = seen.zipWithNext().count { (a, b) -> a != b }

            changes shouldBe flips.size
        }
    }

    "a turn lies where the ghosts are fully there: it fades out after their turn in and is back before they go" {
        (AroundCourse.FLIP_AFTER - AroundCourse.FADE) shouldBeGreaterThan GhostVisits.TURN_SECONDS - 1e-9
        (AroundCourse.FLIP_AFTER + AroundCourse.FADE) shouldBeLessThan GhostVisits.HOLD + 1e-9

        for (seed in listOf(1, 4, 5, 42)) {
            for (flip in turns(seed = seed, seconds = 1800.0)) {
                GhostVisits.visiting(t = flip - AroundCourse.FADE, seed = seed) shouldBe true
                GhostVisits.visiting(t = flip + AroundCourse.FADE, seed = seed) shouldBe true
            }
        }
    }

    "it fades out before a turn and in after it, and is there in full otherwise" {
        val seed = 4
        val flip = turns(seed = seed, seconds = 600.0).first()

        fun presence(t: Double): Double = AroundCourse.at(t = t, seed = seed, look = AroundLook.Turns).presence

        presence(flip) shouldBe 0.0
        presence(flip - AroundCourse.FADE) shouldBe 1.0
        presence(flip + AroundCourse.FADE) shouldBe 1.0
        presence(flip - AroundCourse.FADE / 2) shouldBe 0.5
        presence(flip + AroundCourse.FADE / 2) shouldBe 0.5
        presence(flip - 1.0) shouldBeLessThan presence(flip - 2.0)
        presence(flip + 1.0) shouldBeLessThan presence(flip + 2.0)
        presence(flip - 30.0) shouldBe 1.0
        presence(-20.0) shouldBe 1.0
    }

    "held, it stays all through; off, there is none; no time, the forest" {
        for (t in listOf(0.0, 250.0, 900.0)) {
            AroundCourse.at(t = t, seed = 1, look = AroundLook.Ruins) shouldBe AroundMoment(Surrounding.Ruins, 1.0)
            AroundCourse.at(t = t, seed = 1, look = AroundLook.Stones) shouldBe AroundMoment(Surrounding.Stones, 1.0)
            AroundCourse.at(t = t, seed = 1, look = AroundLook.Forest) shouldBe AroundMoment(Surrounding.Forest, 1.0)
            AroundCourse.at(t = t, seed = 1, look = AroundLook.Off) shouldBe AroundMoment(null, 0.0)
        }

        AroundCourse.at(t = Double.NaN, seed = 1, look = AroundLook.Turns) shouldBe AroundMoment(Surrounding.Forest, 1.0)
        AroundCourse.at(t = Double.POSITIVE_INFINITY, seed = 1, look = AroundLook.Turns) shouldBe
            AroundMoment(Surrounding.Forest, 1.0)
    }
})
