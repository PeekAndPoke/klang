/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switches
import kotlin.math.abs

class LightPlanSpec : StringSpec({

    "a dark lamp's shadow is drawn once and then no more, a lit lamp's always" {
        shadowDrawn(intensity = 0.0, made = false) shouldBe true
        shadowDrawn(intensity = 0.0, made = true) shouldBe false
        shadowDrawn(intensity = Double.NaN, made = true) shouldBe false
        shadowDrawn(intensity = 0.001, made = true) shouldBe true
        shadowDrawn(intensity = 260.0, made = false) shouldBe true
    }

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

    "a rig says which lamps behind are on: all, the outer two, or the inner one alone and turned up" {
        BackRig.ALL.share(BackPlace.OUTER) shouldBe 1.0
        BackRig.ALL.share(BackPlace.INNER) shouldBe 1.0
        BackRig.OUTER.share(BackPlace.OUTER) shouldBe 1.0
        BackRig.OUTER.share(BackPlace.INNER) shouldBe 0.0
        BackRig.INNER.share(BackPlace.OUTER) shouldBe 0.0
        BackRig.INNER.share(BackPlace.INNER) shouldBe 1.4
    }

    "a show opens with all the lamps, every rig comes, and the same few lamps never twice in a row" {
        for (seed in 1..20) {
            val plan = LightPlan(Show(seed = seed))
            val rigs = (0 until 60).map { plan.rigOf(it) }

            withClue("seed $seed") {
                rigs.first() shouldBe BackRig.ALL
                rigs.toSet() shouldBe setOf(BackRig.ALL, BackRig.OUTER, BackRig.INNER)
                rigs.zipWithNext().none { (a, b) -> a == b && a != BackRig.ALL } shouldBe true
            }
        }
    }

    "three rigs in five are all the lamps, one in five each the outer two and the inner one" {
        val rigs = (1..40).flatMap { seed -> LightPlan(Show(seed = seed)).let { plan -> (1..100).map { plan.rigOf(it) } } }
        val share = rigs.groupingBy { it }.eachCount().mapValues { it.value / rigs.size.toDouble() }

        // The chance picks all the lamps half the time and each of the others a quarter, and a pick that would
        // follow itself becomes all the lamps. So after the outer two come all the lamps (3 in 4) or the inner one
        // (1 in 4), and likewise after the inner one. With q the share of each of the two: q = (1 - 2q) / 4 + q / 4,
        // so q = 1/5, and all the lamps 3/5.
        share.getValue(BackRig.ALL) shouldBe (0.60 plusOrMinus 0.02)
        share.getValue(BackRig.OUTER) shouldBe (0.20 plusOrMinus 0.02)
        share.getValue(BackRig.INNER) shouldBe (0.20 plusOrMinus 0.02)
    }

    "a rig holds for its bars and goes over into the next in the two bars after; before the song it is the first" {
        val show = Show(seed = 3)
        val plan = LightPlan(show)
        val round = (LightPlan.RIG_HOLD + LightPlan.RIG_TURN) * show.bar

        plan.rigAt(-5.0).let {
            it.from shouldBe BackRig.ALL
            it.share shouldBe 0.0
        }

        for (k in 0 until 8) {
            val held = plan.rigAt(k * round + LightPlan.RIG_HOLD * show.bar - 0.01)
            val turning = plan.rigAt(k * round + (LightPlan.RIG_HOLD + 1) * show.bar)

            withClue("round $k") {
                held.from shouldBe plan.rigOf(k)
                held.share shouldBe 0.0
                turning.from shouldBe plan.rigOf(k)
                turning.to shouldBe plan.rigOf(k + 1)
                turning.share shouldBe (0.5 plusOrMinus 1e-9)
            }
        }
    }

    "?rig= holds one rig all through; a word that names none leaves it to the show" {
        val inner = LightPlan(Show(seed = 3, switches = Switches.parse("?rig=inner")))

        for (t in listOf(-5.0, 0.0, 31.0, 500.0)) {
            inner.rigAt(t).share(BackPlace.OUTER) shouldBe 0.0
            inner.rigAt(t).share(BackPlace.INNER) shouldBe 1.4
        }

        LightPlan(Show(seed = 3, switches = Switches.parse("?rig=none"))).rigAt(0.0).from shouldBe BackRig.ALL
    }

    "going from one rig to the next, the stage is never dark and never jumps" {
        val mix = { u: Double -> BackRigMix(from = BackRig.OUTER, to = BackRig.INNER, share = u) }

        mix(0.0).share(BackPlace.OUTER) shouldBe 1.0
        mix(0.0).share(BackPlace.INNER) shouldBe 0.0
        mix(0.5).share(BackPlace.OUTER) shouldBe (0.5 plusOrMinus 1e-12)
        mix(0.5).share(BackPlace.INNER) shouldBe (0.7 plusOrMinus 1e-12)
        mix(1.0).share(BackPlace.OUTER) shouldBe 0.0
        mix(1.0).share(BackPlace.INNER) shouldBe 1.4

        val show = Show(seed = 3)
        val plan = LightPlan(show)
        val shares = (0 until 20000).map { plan.rigAt(it * 0.05) }

        shares.minOf { maxOf(it.share(BackPlace.OUTER), it.share(BackPlace.INNER)) } shouldBeGreaterThan 0.49
        shares.zipWithNext().maxOf { (a, b) -> abs(a.share(BackPlace.OUTER) - b.share(BackPlace.OUTER)) } shouldBeLessThan 0.03
    }
})
