/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Kind
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The seeds and the ways she faces at rest the route is checked with (Sirene's own: 0.1). */
private val SEEDS = listOf(0, 1, 2, 3, 7, 42, 67, -5, 1234)
private val RESTS = listOf(0.1, -0.2)

/** The nearest to the camera, as the frontwoman's player works it out for Sirene (0.71 across, 5.94 ahead). */
private val CLOSEST = Offset(x = 0.71, z = 5.94)

/** The rounds sampled. */
private const val ROUNDS = 8

/** The step of the samples, in seconds. */
private const val DT = 0.05

/** A slow walk, in her sizes a second: the player has her walking all the way from this pace (`TrumpetPlayer.facing`). */
private const val SLOW = 0.05

/** The quintic ease's steepest slope (at its middle): 15/8. */
private const val EASE_PEAK = 15.0 / 8.0

private fun degrees(radians: Double): Double = radians * 180 / PI

/** [a] as the same turn within -pi to pi. */
private fun wrap(a: Double): Double = atan2(sin(a), cos(a))

private fun apart(a: Offset, b: Offset): Double = hypot(b.x - a.x, b.z - a.z)

/** A route sampled every [DT] over its first [ROUNDS] rounds: the times and where she is. */
private class Sampled(val route: StrollRoute, val restTurn: Double) {
    val end = route.startOf(ROUNDS)
    val times = (0..(end / DT).toInt()).map { it * DT }
    val at = times.map { route.at(it) }

    fun kindAt(t: Double): Kind = route.roundOf(route.roundAt(t)).kind
}

private fun sampled(seed: Int, restTurn: Double): Sampled =
    Sampled(route = StrollRoute(seed = seed, restTurn = restTurn, closest = CLOSEST), restTurn = restTurn)

private fun everyRoute(check: (Sampled, String) -> Unit) {
    for (seed in SEEDS) {
        for (rest in RESTS) {
            check(sampled(seed = seed, restTurn = rest), "seed $seed, facing $rest at rest")
        }
    }
}

/** The legs of [route]'s round [round] in the stroll's time: (from, to, the leg). */
private fun legsOf(route: StrollRoute, round: Int): List<Triple<Double, Double, StrollRoute.Walk>> {
    val start = route.startOf(round)

    return route.roundOf(round).walks.map { Triple(start + it.start, start + it.start + it.time, it) }
}

class StrollRouteSpec : StringSpec({

    "she is at her place as a round begins and for its last 20 seconds" {
        everyRoute { s, where ->
            for (round in 0 until ROUNDS) {
                val start = s.route.startOf(round)
                val end = start + s.route.lengthOf(round)

                withClue("$where, round $round begins at $start") {
                    apart(s.route.at(start), StrollRoute.HOME) shouldBe (0.0 plusOrMinus 1e-12)
                }

                var t = end - 20.0

                while (t <= end) {
                    withClue("$where, round $round, $t s (it ends at $end)") {
                        apart(s.route.at(t), StrollRoute.HOME) shouldBe (0.0 plusOrMinus 1e-12)
                    }

                    t += DT
                }
            }
        }
    }

    "no jump: a step is never longer than the leg's own pace allows, and she never walks faster than a calm walk" {
        // A leg of length l walked in time T, eased by the quintic, is fastest in its middle: 15/8 l / T. A sample
        // step may be 5 % longer than that (the length table is linear between its samples). Over all legs that
        // peak stays below 0.8 of her size a second, a calm walk.
        everyRoute { s, where ->
            var fastest = 0.0

            for (round in 0 until ROUNDS) {
                for ((from, to, walk) in legsOf(s.route, round)) {
                    val most = EASE_PEAK * walk.leg.length / walk.time

                    fastest = max(fastest, most)

                    var t = from

                    while (t + DT <= to) {
                        withClue("$where, round $round, $t s: a step of a leg ${walk.leg.length} long in ${walk.time} s") {
                            apart(s.route.at(t), s.route.at(t + DT)) shouldBeLessThanOrEqual most * DT * 1.05 + 1e-9
                        }

                        t += DT
                    }
                }
            }

            withClue("$where: the fastest leg's peak") {
                fastest shouldBeLessThan 0.8
            }

            // and between the legs she stands: two samples of the whole stroll are never further apart than the
            // fastest leg allows
            for (i in 0 until s.at.size - 1) {
                withClue("$where, ${s.times[i]} s") {
                    apart(s.at[i], s.at[i + 1]) shouldBeLessThanOrEqual fastest * DT * 1.05 + 1e-9
                }
            }
        }
    }

    "she arrives at every standstill already facing the way she stands, and leaves it walking forward" {
        // over the last and the first half second: within 6 degrees (half a second out she may still be on the
        // last of a short bend into it; some 5 degrees at most)
        everyRoute { s, where ->
            for (round in 0 until ROUNDS) {
                for ((from, to, _) in legsOf(s.route, round)) {
                    for ((a, b, what) in listOf(Triple(to - 0.5, to, "arriving"), Triple(from, from + 0.5, "leaving"))) {
                        var t = a

                        while (t < b - 1e-9) {
                            val p = s.route.at(t)
                            val q = s.route.at(min(b, t + 0.01))

                            if (apart(p, q) > 1e-12) {
                                val off = degrees(abs(wrap(atan2(q.x - p.x, q.z - p.z) - s.restTurn)))

                                withClue("$where, round $round, $what at $t s: the way she goes, off the way she stands") {
                                    off shouldBeLessThan 6.0
                                }
                            }

                            t += DT
                        }
                    }
                }
            }
        }
    }

    "she starts and stops softly: in the first and the last fifth of a second of a leg she is slower than a slow walk" {
        everyRoute { s, where ->
            for (round in 0 until ROUNDS) {
                for ((from, to, _) in legsOf(s.route, round)) {
                    for (t in listOf(from, from + 0.1, to - 0.3, to - 0.2)) {
                        withClue("$where, round $round, $t s (the leg from $from to $to)") {
                            apart(s.route.at(t), s.route.at(t + 0.1)) / 0.1 shouldBeLessThan SLOW
                        }
                    }
                }
            }
        }
    }

    "walking, the way she goes never turns faster than 120 degrees a second, a body walking a bend" {
        everyRoute { s, where ->
            var worst = 0.0
            var worstAt = 0.0

            for (i in 1 until s.at.size - 1) {
                val ax = (s.at[i].x - s.at[i - 1].x) / DT
                val az = (s.at[i].z - s.at[i - 1].z) / DT
                val bx = (s.at[i + 1].x - s.at[i].x) / DT
                val bz = (s.at[i + 1].z - s.at[i].z) / DT

                if (hypot(ax, az) > SLOW && hypot(bx, bz) > SLOW) {
                    val rate = degrees(abs(atan2(ax * bz - az * bx, ax * bx + az * bz))) / DT

                    if (rate > worst) {
                        worst = rate
                        worstAt = s.times[i]
                    }
                }
            }

            withClue("$where: the fastest turn, at $worstAt s (a ${s.kindAt(worstAt)} round)") {
                worst shouldBeLessThan 120.0
            }
        }
    }

    "she stays in the front of the stage, where she strolled before, but on the way to the camera" {
        // across: from -0.55 to 1.1 of her size (her old stroll: -0.48 to 1.08); toward the audience: from 0.2
        // behind her place to 0.7 before it, and on the front edge's arc up to 0.9
        everyRoute { s, where ->
            for ((i, t) in s.times.withIndex()) {
                val p = s.at[i]

                when (s.kindAt(t)) {
                    Kind.Plain, Kind.Edge -> withClue("$where, $t s, a ${s.kindAt(t)} round: at $p") {
                        p.x shouldBeGreaterThan -0.55
                        p.x shouldBeLessThan 1.1
                        p.z shouldBeGreaterThan -0.2
                        p.z shouldBeLessThan (if (s.kindAt(t) == Kind.Edge) 0.9 else 0.7)
                    }

                    Kind.Camera, Kind.Close -> Unit
                }
            }

            for (round in 0 until ROUNDS) {
                val start = s.route.startOf(round)
                val near = s.times.filter { it >= start && it < start + s.route.lengthOf(round) }.maxOf { s.route.at(it).z }

                when (s.route.roundOf(round).kind) {
                    Kind.Camera -> withClue("$where, round $round goes to the camera") { near shouldBeGreaterThan 3.0 }
                    Kind.Close -> withClue("$where, round $round comes up close") { near shouldBeGreaterThan CLOSEST.z - 0.2 }
                    Kind.Plain, Kind.Edge -> Unit
                }
            }
        }
    }

    "two rounds in five go to the camera, for every seed, a negative one too" {
        for (seed in listOf(-1234, -40, -5, 0, 7, 42)) {
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST)
            val camera = (0 until 10).count { route.roundOf(it).kind == Kind.Camera || route.roundOf(it).kind == Kind.Close }

            withClue("seed $seed: the camera rounds among the first ten") {
                camera shouldBe 4
            }
        }
    }

    "the same seed gives the same route, asked in any order; another seed another" {
        val one = StrollRoute(seed = 5, restTurn = 0.1, closest = CLOSEST)
        val again = StrollRoute(seed = 5, restTurn = 0.1, closest = CLOSEST)
        // (a seed 20 on has the same rounds in the same order, to the camera the same ones: only the stops differ)
        val other = StrollRoute(seed = 25, restTurn = 0.1, closest = CLOSEST)
        val times = (0..2000).map { it * 0.2 }

        times.map { one.at(it) } shouldBe times.reversed().map { again.at(it) }.reversed()

        // the stops of the first rounds, where she stands
        fun stops(route: StrollRoute): List<Offset> = (0 until 4).flatMap { round -> route.roundOf(round).walks.map { it.leg.last } }

        stops(one) shouldNotBe stops(other)
    }

    "in some rounds she walks the front edge in one long arc, and keeps walking on it from one end to the other" {
        val edges = SEEDS.flatMap { seed ->
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST)

            (0 until ROUNDS).filter { route.roundOf(it).kind == Kind.Edge }.map { route to it }
        }

        edges.shouldNotBeEmpty()

        for ((route, round) in edges) {
            val (from, to, walk) = legsOf(route, round)[1]

            withClue("round $round: the arc goes across the width she strolls in") {
                abs(walk.leg.last.x - walk.leg.first.x) shouldBeGreaterThan 1.0
                min(walk.leg.first.z, walk.leg.last.z) shouldBeGreaterThan 0.35
            }

            // all through its middle three fifths (the ease starts and ends it slowly) she walks
            var t = from + 0.2 * (to - from)

            while (t < to - 0.2 * (to - from)) {
                withClue("round $round, $t s: on the arc (from $from to $to) she keeps walking") {
                    apart(route.at(t), route.at(t + DT)) / DT shouldBeGreaterThan SLOW
                }

                t += DT
            }
        }
    }

    "in some camera rounds she comes up close, straight at the lens at the last, stands two to three seconds and goes back" {
        val close = SEEDS.flatMap { seed ->
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST)

            (0 until ROUNDS).filter { route.roundOf(it).kind == Kind.Close }.map { route to it }
        }

        close.shouldNotBeEmpty()

        for ((route, round) in close) {
            val (there, back) = legsOf(route, round)
            val stop = there.third.leg.last
            var nearest = Double.NEGATIVE_INFINITY
            var t = there.first

            while (t <= back.second) {
                nearest = max(nearest, route.at(t).z)
                t += 0.01
            }

            withClue("round $round: as near as $nearest, and never nearer than the closest (${CLOSEST.z})") {
                nearest shouldBeLessThanOrEqual CLOSEST.z + 1e-9
                nearest shouldBeGreaterThan CLOSEST.z - 0.2
            }

            withClue("round $round: she stands there") {
                (back.first - there.second) shouldBe (2.5 plusOrMinus 0.5)
            }

            // the last stretch: straight at the lens, the way she faces at rest
            t = there.second - 0.01

            while (apart(route.at(t), stop) < 0.9 * StrollRoute.RUN_IN) {
                val p = route.at(t)
                val q = route.at(t + 0.01)

                if (apart(p, q) > 1e-12) {
                    withClue("round $round, $t s: straight at the lens") {
                        degrees(abs(wrap(atan2(q.x - p.x, q.z - p.z) - 0.1))) shouldBeLessThan 1.0
                    }
                }

                t -= 0.05
            }
        }
    }

    "followed with a body's inertia (StrollTurn, as the player faces her way), walking she never goes sideways" {
        // as the frontwoman's player faces on a stroll (`TrumpetPlayer.facing`): the way she goes over 0.4 s round
        // the moment, off the way she faces at rest, the more the faster she goes
        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST)

            fun ahead(x: Double): Triple<Double, Double, Double> {
                val a = route.at(x + 0.2)
                val b = route.at(x - 0.2)
                val speed = apart(a, b) / 0.4
                val going = smooth(min(1.0, speed / SLOW))
                val heading = wrap(atan2(a.x - b.x, a.z - b.z) - 0.1)

                return Triple(heading, going, speed)
            }

            val end = route.startOf(ROUNDS)
            val turn = StrollTurn(from = 0.0, to = end) { x -> ahead(x).let { it.first * it.second } }
            var x = 0.0

            while (x < end) {
                val (heading, going, _) = ahead(x)

                if (going > 0.9) {
                    withClue("seed $seed, $x s: the way she faces, off the way she goes") {
                        degrees(abs(wrap(heading - turn.at(x)))) shouldBeLessThan 25.0
                    }
                }

                x += 0.1
            }
        }
    }
})
