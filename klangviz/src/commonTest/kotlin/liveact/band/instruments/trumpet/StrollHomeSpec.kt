/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random
import kotlin.math.sin

/** The seeds the way home is checked with, and the way she faces at rest (Sirene's). */
private val SEEDS = listOf(0, 1, 2, 7, 42, -5)
private const val REST = 0.1

/** As the frontwoman's player works it out for Sirene. */
private val CLOSEST = Offset(x = 0.71, z = 5.94)

/** Where strolls begin, and how long they last: from 14 s up, their ends unrelated to the rounds. */
private val STARTS = listOf(15.0, 83.7, 211.3)
private val LENGTHS = listOf(14.0, 17.3, 23.9, 31.1, 44.4, 58.2, 76.5, 97.2, 133.3, 190.6, 251.9)

/** The step of the samples, in seconds. */
private const val DT = 0.05

/** A frame, in seconds. */
private const val FRAME = 1.0 / 60

/** How far ahead a song read as it is played tells her end: her next note 13 s ahead, home 4 s before it. */
private const val LEARNED_AHEAD = 9.0

/** Her step covers this much, in her sizes a second, before her feet slide (0.5 of her size a step, at 120 bpm). */
private const val STEP_COVERS = 0.42

private fun apart(a: Offset, b: Offset): Double = hypot(b.x - a.x, b.z - a.z)

private fun degrees(radians: Double): Double = radians * 180 / PI

private fun wrap(a: Double): Double = atan2(sin(a), cos(a))

private fun everyStroll(check: (StrollHome, String) -> Unit) {
    for (seed in SEEDS) {
        val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

        for (from in STARTS) {
            for (length in LENGTHS) {
                check(StrollHome(route = route, from = from, to = from + length), "seed $seed, a stroll from $from s for $length s")
            }
        }
    }
}

/** The samples of [home] from its start to its end, every [DT] seconds, and its end. */
private fun timesOf(home: StrollHome): List<Double> =
    ((0..((home.to - home.from) / DT).toInt()).map { home.from + it * DT }.filter { it < home.to - 1e-6 }) + home.to

class StrollHomeSpec : StringSpec({

    "she is at her place as the stroll ends, whenever it ends" {
        everyStroll { home, where ->
            withClue(where) {
                apart(home.at(home.to), StrollRoute.HOME) shouldBe (0.0 plusOrMinus 1e-9)
            }
        }
    }

    "before she turns home her place is the route's, whatever the end: a stroll that ends later went the same way till then" {
        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

            for (from in STARTS) {
                val long = StrollHome(route = route, from = from, to = from + 3600.0)

                for (length in LENGTHS) {
                    val home = StrollHome(route = route, from = from, to = from + length)
                    var t = from

                    while (t <= from + home.turnBack) {
                        withClue("seed $seed, a stroll from $from s for $length s, at $t s (she turns home at ${home.turnBack} s in)") {
                            apart(home.at(t), long.at(t)) shouldBe (0.0 plusOrMinus 1e-12)
                        }

                        t += DT
                    }
                }
            }
        }
    }

    "her way home is calm: at 0.15 of her size a second on average, from wherever she turns" {
        everyStroll { home, where ->
            val way = home.way
            val leg = way?.leg

            if (way != null && leg != null) {
                withClue("$where: a way home ${leg.length} long in ${way.time} s") {
                    leg.length / way.time shouldBeLessThanOrEqual StrollHome.PACE + 0.01
                }
            }
        }
    }

    "no jump as she turns home, and on the way home never faster than she went as she turned, or than her step covers" {
        // a step of the samples: no longer than the route's own fastest leg allows (0.8 of her size a second, see
        // StrollRouteSpec); on the way home: no faster than she went as she turned (she leaves at that speed), or
        // than her step covers ([STEP_COVERS]), with 5 % for the table
        everyStroll { home, where ->
            val times = timesOf(home)
            val turn = home.from + home.turnBack
            // how fast she went just before she turned, as measured
            val leaving = if (home.turnBack > 0.01) apart(home.at(turn - 0.01), home.at(turn)) / 0.01 else 0.0

            // and she turns home as fast as she went: the speed over the hundredth of a second before the turn and the
            // one after it are about the same
            if (home.way != null && home.turnBack > DT) {
                val before = leaving
                val after = apart(home.at(turn), home.at(turn + 0.01)) / 0.01

                withClue("$where: turning home at $turn s, from $before to $after of her size a second") {
                    abs(after - before) shouldBeLessThan 0.03
                }
            }

            for (i in 0 until times.size - 1) {
                val step = apart(home.at(times[i]), home.at(times[i + 1])) / (times[i + 1] - times[i])

                withClue("$where, at ${times[i]} s (she turns home at $turn s)") {
                    step shouldBeLessThan 0.8 * 1.05

                    if (times[i] >= turn) {
                        step shouldBeLessThan max(leaving, STEP_COVERS) * 1.05 + 1e-9
                    }
                }
            }
        }
    }

    "she arrives home facing the way she stands, as from a round" {
        everyStroll { home, where ->
            if (home.way != null) {
                var t = home.to - 0.5

                while (t < home.to - 1e-9) {
                    val p = home.at(t)
                    val q = home.at(t + 0.01)

                    if (apart(p, q) > 1e-12) {
                        withClue("$where, at $t s: the way she goes, off the way she stands") {
                            degrees(abs(wrap(atan2(q.x - p.x, q.z - p.z) - REST))) shouldBeLessThan 6.0
                        }
                    }

                    t += DT
                }
            }
        }
    }

    "an end learned late (9 s ahead, a song read as it is played) changes nothing before; then she hurries home" {
        // her next note is known some 13 s ahead, and she is home 4 s before it (see planTrumpet): the end is learned
        // 9 s ahead. From there she walks home no faster than [StrollHome.LATE_FASTEST]: more than her step covers
        var farOut = 0
        var fastest = 0.0
        var fastestAt = ""

        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

            for (from in STARTS) {
                val long = StrollHome(route = route, from = from, to = from + 3600.0)

                for (length in LENGTHS) {
                    val learned = from + length - LEARNED_AHEAD
                    val home = StrollHome(route = route, from = from, to = from + length, notBefore = learned)
                    val where = "seed $seed, a stroll from $from s for $length s, its end learned at $learned s"

                    if (apart(long.at(learned), StrollRoute.HOME) > 2.0) {
                        farOut++
                    }

                    withClue(where) {
                        home.from + home.turnBack shouldBeGreaterThan learned - 1e-9
                        apart(home.at(learned), long.at(learned)) shouldBe (0.0 plusOrMinus 1e-12)
                        apart(home.at(home.to), StrollRoute.HOME) shouldBe (0.0 plusOrMinus 1e-9)
                    }

                    // every frame from a little before the end was learned: no jump, and no faster than the bound
                    var t = learned - 0.5

                    while (t < home.to) {
                        val step = apart(home.at(t), home.at(t + FRAME)) / FRAME

                        if (step > fastest) {
                            fastest = step
                            fastestAt = "$where, at $t s"
                        }

                        t += FRAME
                    }
                }
            }
        }

        withClue("strolls that are more than 2 of her sizes out when their end is learned") {
            farOut shouldBeGreaterThan 0
        }

        withClue("the fastest frame after an end learned late: $fastest of her size a second, $fastestAt") {
            fastest shouldBeLessThan StrollHome.LATE_FASTEST
        }
    }

    "on her way home she only ever goes forward, also from an end learned late" {
        // where she leaves faster than 2.5 times the way's average pace, the eased way would overshoot her place and
        // come back: she leaves no faster than [StrollHome.LEAVE] times it. Ends learned 9 s and 4 s ahead (and see
        // the replaced strolls below, where it matters most)

        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

            for (from in STARTS) {
                val long = StrollHome(route = route, from = from, to = from + 3600.0)

                for (length in LENGTHS) {
                    for (ahead in listOf(LEARNED_AHEAD, 4.0)) {
                        val learned = from + length - ahead
                        val home = StrollHome(route = route, from = from, to = from + length, notBefore = learned)
                        val way = home.way ?: continue
                        var t = home.from + way.start
                        var before = home.along(t)

                        while (t < home.to) {
                            t += FRAME

                            val now = home.along(t)

                            withClue("seed $seed, a stroll from $from s for $length s, its end learned at $learned s, at $t s") {
                                now shouldBeGreaterThan before - 1e-9
                            }

                            before = now
                        }
                    }
                }
            }
        }
    }

    "a stroll replaced after she turned home (its end changed again) goes on from where she is, and gets her home" {
        // replaced at moments after she turned home, to a later end, to the placeholder an hour on, to an earlier
        // end: the same place at that moment; after it no step faster than she went then, than
        // [StrollHome.LATE_FASTEST], or than the eased peak of the new way (15/8 of its average pace: an end only a
        // moment ahead asks for that much, whatever it is); home at the new end; and on the new way home only ever
        // forward: near the end of a way home she goes some three times as fast as what is left of it in the time
        // left, so an end a little earlier has her leave faster than 2.5 times the new way's pace, where the eased
        // way would overshoot her place and come back (see [StrollHome.LEAVE])
        var cases = 0
        var fastest = 0.0
        var fastestAt = ""
        var leavingFast = 0

        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

            for (from in STARTS) {
                for (length in LENGTHS) {
                    for (learned in listOf(Double.NEGATIVE_INFINITY, from + length - LEARNED_AHEAD)) {
                        val old = StrollHome(route = route, from = from, to = from + length, notBefore = learned)
                        var now = from + old.turnBack + 0.25

                        while (now < old.to - 0.25) {
                            for (end in listOf(old.to + 20.0, from + 3600.0, old.to - 0.2)) {
                                val new = StrollHome(route = route, from = from, to = end, notBefore = now, before = old)
                                val where = "seed $seed, a stroll from $from s for $length s (learned at $learned s), " +
                                    "replaced at $now s to end at $end s (she turned home at ${from + old.turnBack} s)"

                                cases++

                                withClue(where) {
                                    apart(new.at(now), old.at(now)) shouldBeLessThan 1e-6
                                    apart(new.at(end), StrollRoute.HOME) shouldBeLessThan 1e-9
                                }

                                // with as much time as her way home takes or more, she goes on along it, and stays home
                                if (end >= old.to) {
                                    var t = now

                                    while (t <= old.to) {
                                        withClue("$where, at $t s: she goes on along her way home") {
                                            apart(new.at(t), old.at(t)) shouldBeLessThan 1e-9
                                        }

                                        t += 0.25
                                    }
                                }

                                val going = apart(old.at(now - 0.01), old.at(now)) / 0.01
                                val newWay = new.ways.last()
                                val leg = newWay.leg

                                if (newWay.start >= now - from - 1e-9 && leg != null && going > 2.5 * leg.length / newWay.time) {
                                    leavingFast++
                                }

                                val pace = if (newWay.start >= now - from - 1e-9 && leg != null) leg.length / newWay.time else 0.0
                                val allowed = maxOf(going * 1.05, StrollHome.LATE_FASTEST, 2.0 * pace)
                                var t = now
                                var along = new.along(t)

                                while (t < minOf(end, now + 3.0)) {
                                    val step = apart(new.at(t), new.at(t + FRAME)) / FRAME

                                    if (step / allowed > fastest) {
                                        fastest = step / allowed
                                        fastestAt = "$where, at $t s: $step of her size a second, $allowed allowed"
                                    }

                                    t += FRAME

                                    val next = new.along(t)

                                    withClue("$where, at $t s: along her way home") {
                                        next shouldBeGreaterThan along - 1e-9
                                    }

                                    along = next
                                }
                            }

                            now += 0.5
                        }
                    }
                }
            }
        }

        cases shouldBeGreaterThan 1000

        withClue("replaced to an earlier end where she leaves faster than 2.5 times the new way's pace") {
            leavingFast shouldBeGreaterThan 0
        }

        withClue("the fastest frame after a stroll is replaced, against what is allowed: $fastest, $fastestAt") {
            fastest shouldBeLessThan 1.0
        }
    }

    "a stroll replaced again and again keeps what she did over the last seconds, gets her home, and keeps few ways" {
        // re-planned up to twelve times in a row, each on the one before, 0.5, 1.9, 2.5 and 7 s apart (below and
        // above [StrollHome.KEEP]). At every re-plan: over the last 1.9 s she is where the stroll then in effect had
        // her; home at the last end; and the ways kept are no more than the re-plans within [StrollHome.KEEP]
        // seconds before (this one with them), plus one
        val spacings = listOf(0.5, 1.9, 2.5, 7.0)
        val random = Random(11)
        var mostWays = 0

        fun chain(label: String, route: StrollRoute, first: StrollHome, start: Double, endAfter: (StrollHome, Double) -> Double) {
            var current = first
            val shown = mutableListOf(Double.NEGATIVE_INFINITY to current)
            var now = start

            for (step in 0 until 12) {
                if (now >= current.to - 0.5) {
                    break
                }

                val end = endAfter(current, now)
                val next = StrollHome(route = route, from = first.from, to = end, notBefore = now, before = current)
                val where = "$label, re-planned (step $step) at $now s to end at $end s"

                shown.add(now to next)

                var t = now - 1.9

                while (t <= now + 1e-9) {
                    val then = shown.last { it.first <= t }.second

                    withClue("$where, at $t s: where the stroll then in effect had her") {
                        apart(next.at(t), then.at(t)) shouldBeLessThan 1e-6
                    }

                    t += DT
                }

                val recent = shown.count { it.first > now - StrollHome.KEEP && it.first <= now }

                withClue("$where: the ways kept (${next.ways.size}), re-plans within the last ${StrollHome.KEEP} s: $recent") {
                    next.ways.size shouldBeLessThanOrEqual recent + 1
                }

                mostWays = maxOf(mostWays, next.ways.size)
                current = next
                now += spacings[step % spacings.size]
            }

            withClue("$label: home at its last end") {
                apart(current.at(current.to), StrollRoute.HOME) shouldBeLessThan 1e-9
            }
        }

        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

            for (from in STARTS) {
                // the end moving every way: later, to the placeholder, sooner
                for (n in 0 until 6) {
                    chain(
                        label = "seed $seed, a stroll from $from s, mixed chain $n",
                        route = route,
                        first = StrollHome(route = route, from = from, to = from + 3600.0),
                        start = from + 15.0 + 20.0 * random.nextDouble(),
                    ) { current, now ->
                        when (random.nextInt(4)) {
                            0 -> current.to + 1.0 + 20.0 * random.nextDouble()
                            1 -> from + 3600.0
                            2 -> max(now + 0.5, current.to - 10.0 * random.nextDouble())
                            else -> now + 0.5 + 12.0 * random.nextDouble()
                        }
                    }
                }

                // on a long way home, the end a little sooner each time: every re-plan gives her a new way
                for (length in LENGTHS) {
                    val first = StrollHome(route = route, from = from, to = from + length)

                    if ((first.way?.time ?: 0.0) >= 12.0) {
                        chain(
                            label = "seed $seed, a stroll from $from s for $length s, ends ever sooner",
                            route = route,
                            first = first,
                            start = from + first.turnBack + 0.3,
                        ) { current, _ -> current.to - 0.3 }
                    }
                }
            }
        }

        withClue("the most ways a stroll kept") {
            mostWays shouldBeGreaterThan 2
        }
    }

    "two strolls of one show that begin at different times do not walk the same first round" {
        val route = StrollRoute(seed = 3, restTurn = REST, closest = CLOSEST)
        val firsts = STARTS.map { from ->
            val home = StrollHome(route = route, from = from, to = from + 3600.0)

            (1..60).map { home.at(from + it * 1.0) }
        }

        withClue("the first minute of strolls from ${STARTS.joinToString()} s") {
            firsts[0] shouldNotBe firsts[1]
            firsts[1] shouldNotBe firsts[2]
            firsts[0] shouldNotBe firsts[2]
        }
    }
})
