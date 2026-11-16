/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Show

class LightPlanSpec : StringSpec({

    "eight fast bass drum hits in a row are a run, seven are none" {
        val seven = List(7) { 1.0 + it * 0.1 }
        val eight = List(8) { 1.0 + it * 0.1 }

        bassDrumRuns(seven) shouldBe emptyList()
        bassDrumRuns(eight) shouldBe listOf(Run(from = eight.first(), to = eight.last()))
    }

    "a quarter of a second between two hits ends a run" {
        val first = List(8) { 1.0 + it * 0.1 }
        val second = List(9) { first.last() + 0.25 + it * 0.1 }

        bassDrumRuns(first + second) shouldBe listOf(
            Run(from = first.first(), to = first.last()),
            Run(from = second.first(), to = second.last()),
        )
    }

    "a hit doubled at once counts once" {
        // four hits, each doubled a few thousandths later: eight events, four hits, no run
        val doubled = List(4) { 1.0 + it * 0.1 }.flatMap { listOf(it, it + 0.004) }

        bassDrumRuns(doubled) shouldBe emptyList()
        bassDrumRuns(emptyList()) shouldBe emptyList()
    }

    "a show opens in the first mood, and no mood follows itself" {
        for (seed in 0 until 60) {
            val plan = LightPlan(Show(seed = seed))

            withClue("seed $seed") {
                plan.moodOf(0) shouldBe LightPlan.MOODS[0]

                for (k in 1 until 40) {
                    (plan.moodOf(k) == plan.moodOf(k - 1)) shouldBe false
                }
            }
        }
    }

    "a mood holds for its bars and goes over into the next in the bars after" {
        val show = Show(seed = 0)
        val plan = LightPlan(show)
        val round = (LightPlan.HOLD + LightPlan.TURN) * show.bar

        plan.moodAt(0.0).share shouldBe 0.0
        plan.moodAt(LightPlan.HOLD * show.bar).share shouldBe 0.0
        plan.moodAt((LightPlan.HOLD + LightPlan.TURN / 2.0) * show.bar).share shouldBe 0.5
        plan.moodAt(round + 0.01).from shouldBe plan.moodOf(1)
        plan.moodAt(round + 0.01).to shouldBe plan.moodOf(2)
        // before the song the first mood holds
        plan.moodAt(-10.0).from shouldBe LightPlan.MOODS[0]
    }

    "the moods' order is the same under the same seed, and another under another" {
        fun order(seed: Int) = LightPlan(Show(seed = seed)).let { plan -> List(20) { plan.moodOf(it) } }

        order(5) shouldBe order(5)
        // twenty moods out of five each: two seeds do not give the same twenty
        (0 until 10).map { order(it) }.distinct().size shouldBe 10
    }
})
