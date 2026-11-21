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
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.klang.viz.liveact.sound.RecordedMusic
import kotlin.math.abs

/** The kick's tag, as the plans here are given it. */
private const val KICK = "kick"

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

    "the front lamp stays dark for the first 45 seconds, and its first stretch then comes whole" {
        for (seed in 0 until 60) {
            val show = Show(seed = seed)
            val plan = LightPlan(show = show, kick = KICK)
            val levels = generateSequence(0.0) { it + 0.05 }.takeWhile { it < 45.0 + 45 * show.bar }
                .map { t -> t to plan.frontAt(t = t, waiting = false, trying = false).level }
                .toList()

            withClue("seed $seed") {
                levels.filter { it.first < LightPlan.FIRST_SHADOWS }.maxOf { it.second } shouldBe 0.0
                // it still comes, fully, within the next round of forty bars
                levels.maxOf { it.second } shouldBeGreaterThan 0.99
                // and rises from dark: no step up larger than a twentieth of a second of its slow rise allows
                levels.zipWithNext().maxOf { (a, b) -> b.second - a.second } shouldBeLessThan 0.02
            }
        }
    }

    "the front lamp comes up and goes down slowly" {
        val show = Show(seed = 3)
        val plan = LightPlan(show = show, kick = KICK)
        val levels = generateSequence(0.0) { it + 0.05 }.takeWhile { it < 45.0 + 81 * show.bar }
            .map { t -> t to plan.frontAt(t = t, waiting = false, trying = false).level }
            .toList()
        // the first stretch: from where the lamp leaves the dark to where it is dark again
        val on = levels.dropWhile { it.second == 0.0 }.takeWhile { it.second > 0.0 }
        val full = on.filter { it.second > 0.999 }
        val fade = minOf(LightPlan.SHADOW_FADE, 4 * show.bar)

        (full.first().first - on.first().first) shouldBe (fade plusOrMinus 0.4)
        (on.last().first - full.last().first) shouldBe (fade plusOrMinus 0.4)
        // and between the two it stands full for the rest of its eight bars
        (full.last().first - full.first().first) shouldBe (8 * show.bar - fade plusOrMinus 0.4)
    }

    "where the show opens, the front lamp lights the walk-in in red and goes down as the song begins" {
        val plan = LightPlan(show = Show(seed = 3, switches = Switches.parse("?opening=night")), kick = KICK)

        // before the click nothing; then full on and red all through the walk-in
        plan.frontAt(t = -15.0, waiting = true, trying = false).level shouldBe 0.0
        listOf(-15.0, -7.0, -0.01).forEach { t ->
            plan.frontAt(t = t, waiting = false, trying = false) shouldBe FrontLight(level = 1.0, strobing = false)
            plan.frontColor(t = t, tried = -1) shouldBe FrontColors.RED
        }

        // down over the fade as the song begins, with no step, still red
        val levels = generateSequence(0.0) { it + 0.05 }.takeWhile { it < 45.0 }
            .map { t -> plan.frontAt(t = t, waiting = false, trying = false).level }
            .toList()

        levels.first() shouldBe 1.0
        levels.zipWithNext().forEach { (a, b) -> (a - b) shouldBeLessThan 0.02 }
        levels.zipWithNext().all { (a, b) -> b <= a } shouldBe true
        plan.frontAt(t = LightPlan.OPENING_FADE / 2, waiting = false, trying = false).level shouldBe (0.5 plusOrMinus 1e-9)
        plan.frontColor(t = LightPlan.OPENING_FADE / 2, tried = -1) shouldBe FrontColors.RED
        // and then dark until the first shadows' time, as in a show that does not open
        levels.filterIndexed { i, _ -> i * 0.05 >= LightPlan.OPENING_FADE + 0.1 }.maxOf { it } shouldBe 0.0

        // a show that does not open keeps its walk-in dark
        LightPlan(show = Show(seed = 3), kick = KICK).frontAt(t = -7.0, waiting = false, trying = false).level shouldBe 0.0
        LightPlan(show = Show(seed = 3, switches = Switches.parse("?opening=off")), kick = KICK)
            .frontAt(t = -7.0, waiting = false, trying = false).level shouldBe 0.0
    }

    "the key f and the address's front lamp are not held back" {
        val plan = LightPlan(show = Show(seed = 3), kick = KICK)

        plan.frontAt(t = 5.0, waiting = false, trying = true).level shouldBe 1.0
        val asked = LightPlan(show = Show(seed = 3, switches = Switches.parse("?front=0.7")), kick = KICK)

        asked.frontAt(t = 5.0, waiting = false, trying = false).level shouldBe 0.7
    }

    "a stroboscope's run that begins before the first shadows is left out, a later one flashes" {
        val runs = listOf(Run(from = 30.0, to = 50.0), Run(from = 60.0, to = 62.0))

        LightPlan.strobes(runs = runs, t = 40.0) shouldBe false
        LightPlan.strobes(runs = runs, t = 48.0) shouldBe false
        LightPlan.strobes(runs = runs, t = 59.99) shouldBe true
        LightPlan.strobes(runs = runs, t = 61.0) shouldBe true
        LightPlan.strobes(runs = runs, t = 62.07) shouldBe true
        LightPlan.strobes(runs = runs, t = 62.09) shouldBe false
        LightPlan.strobes(runs = listOf(Run(from = 45.0, to = 46.0)), t = 45.5) shouldBe true
    }

    "a show opens in the first mood, and no mood follows itself" {
        for (seed in 0 until 60) {
            val plan = LightPlan(show = Show(seed = seed), kick = KICK)

            withClue("seed $seed") {
                plan.moodOf(0) shouldBe LightPlan.MOODS[0]

                for (k in 1 until 40) {
                    (plan.moodOf(k) == plan.moodOf(k - 1)) shouldBe false
                }
            }
        }
    }

    "a mood holds for its bars and goes over into the next in its round's last bars" {
        val show = Show(seed = 0)
        val plan = LightPlan(show = show, kick = KICK)
        val round = LightPlan.ROUND * show.bar
        val turn = plan.turnOf(0)

        plan.moodAt(0.0).share shouldBe 0.0
        plan.moodAt((LightPlan.ROUND - turn) * show.bar).share shouldBe 0.0
        plan.moodAt((LightPlan.ROUND - turn / 2.0) * show.bar).share shouldBe (0.5 plusOrMinus 1e-9)
        plan.moodAt(round + 0.01).from shouldBe plan.moodOf(1)
        plan.moodAt(round + 0.01).to shouldBe plan.moodOf(2)
        // before the song the first mood holds
        plan.moodAt(-10.0).from shouldBe LightPlan.MOODS[0]
    }

    "some moods change in four bars, some creep in over eight or sixteen; never with a jump" {
        for (seed in 0 until 20) {
            val show = Show(seed = seed)
            val plan = LightPlan(show = show, kick = KICK)
            val turns = (0 until 40).map { plan.turnOf(it) }

            withClue("seed $seed: the turns of the first 40 moods, $turns") {
                turns.toSet() shouldBe LightPlan.TURNS.toSet()
            }

            // the share over the first six rounds, every twentieth of a second: it rises with no step larger than
            // the quickest turn allows, and falls only where a round ends, from 1 to 0, as the next mood takes over
            val shares = generateSequence(0.0) { it + 0.05 }.takeWhile { it < 6 * LightPlan.ROUND * show.bar }
                .map { plan.moodAt(it).share }
                .toList()
            val quickest = 1.5 * 0.05 / (LightPlan.TURNS.min() * show.bar)

            shares.zipWithNext().forEach { (a, b) ->
                if (b < a) {
                    a shouldBeGreaterThan 0.99
                    b shouldBeLessThan 0.01
                } else {
                    (b - a) shouldBeLessThan quickest + 1e-9
                }
            }
        }
    }

    "the moods' order is the same under the same seed, and another under another" {
        fun order(seed: Int) = LightPlan(show = Show(seed = seed), kick = KICK).let { plan -> List(20) { plan.moodOf(it) } }

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
            val plan = LightPlan(show = Show(seed = seed), kick = KICK)
            val rigs = (0 until 60).map { plan.rigOf(it) }

            withClue("seed $seed") {
                rigs.first() shouldBe BackRig.ALL
                rigs.toSet() shouldBe setOf(BackRig.ALL, BackRig.OUTER, BackRig.INNER)
                rigs.zipWithNext().none { (a, b) -> a == b && a != BackRig.ALL } shouldBe true
            }
        }
    }

    "three rigs in five are all the lamps, one in five each the outer two and the inner one" {
        val rigs = (1..40).flatMap { seed -> LightPlan(show = Show(seed = seed), kick = KICK).let { plan -> (1..100).map { plan.rigOf(it) } } }
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
        val plan = LightPlan(show = show, kick = KICK)
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
        val inner = LightPlan(show = Show(seed = 3, switches = Switches.parse("?rig=inner")), kick = KICK)

        for (t in listOf(-5.0, 0.0, 31.0, 500.0)) {
            inner.rigAt(t).share(BackPlace.OUTER) shouldBe 0.0
            inner.rigAt(t).share(BackPlace.INNER) shouldBe 1.4
        }

        LightPlan(show = Show(seed = 3, switches = Switches.parse("?rig=none")), kick = KICK).rigAt(0.0).from shouldBe BackRig.ALL
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
        val plan = LightPlan(show = show, kick = KICK)
        val shares = (0 until 20000).map { plan.rigAt(it * 0.05) }

        shares.minOf { maxOf(it.share(BackPlace.OUTER), it.share(BackPlace.INNER)) } shouldBeGreaterThan 0.49
        shares.zipWithNext().maxOf { (a, b) -> abs(a.share(BackPlace.OUTER) - b.share(BackPlace.OUTER)) } shouldBeLessThan 0.03
    }

    "the stroboscope goes by the kick it is given: the song's tag the score names" {
        // a run of twelve kicks, a fifth of a second apart, tagged as this song names its kick
        val events = (0 until 12).map { RecordedEvent(t = 60.0 + it * 0.2, d = 0.1, tags = listOf("bd")) }
        val show = Show(seed = 3, music = RecordedMusic(song = Song(Recording(rpm = 30.0, events = events)), loudness = null))

        LightPlan(show = show, kick = "bd").runs shouldBe listOf(Run(from = events.first().t, to = events.last().t))
        LightPlan(show = show, kick = KICK).runs shouldBe emptyList()
    }
})
