/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

/** The seeds the kinds of walk are checked with, and the way she faces at rest (Sirene's). */
private val SEEDS = listOf(0, 1, 2, 7, 42, -5)
private const val REST = 0.1

/** As the frontwoman's player works it out for Sirene. */
private val CLOSEST = Offset(x = 0.71, z = 5.94)

/** Where strolls begin, and how long they last: their ends unrelated to the rounds. */
private val STARTS = listOf(15.0, 83.7, 211.3)
private val LENGTHS = listOf(14.0, 31.1, 58.2, 133.3, 251.9)

/** A frame, in seconds. */
private const val FRAME = 1.0 / 60

/** Faster than this (her sizes a second) she walks; slower, she stands or is about to. */
private const val WALKING = 0.01

/** The kinds' modifiers on her hips, as the table has them: the catwalk, the edge, a plain stroll, the way home. */
private const val CAMERA = 1.4
private const val EDGE = 1.15
private const val PLAIN = 1.0
private const val HOME = 0.85

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

/** The frames of [home] from a second before it to a second after it. */
private fun framesOf(home: StrollHome): List<Double> =
    (0..((home.to - home.from + 2.0) / FRAME).toInt()).map { home.from - 1.0 + it * FRAME }

/** How fast she goes at [t] on [home], her sizes a second. */
private fun speedAt(home: StrollHome, t: Double): Double {
    val a = home.at(t - 0.05)
    val b = home.at(t + 0.05)

    return hypot(b.x - a.x, b.z - a.z) / 0.1
}

class StrollGaitSpec : StringSpec({

    "on her route every leg walks as its kind has the hips: to the camera the catwalk, the edge's arc, a plain stroll" {
        val seen = mutableSetOf<StrollRoute.Kind>()

        for (seed in SEEDS) {
            // with the others to visit (see StrollRouteSpec), so every kind of round comes
            val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST, others = OTHERS_OFF)

            for (r in 0 until StrollRoute.FIRST_ROUNDS) {
                val round = route.roundOf(r)

                seen.add(round.kind)

                for ((i, walk) in round.walks.withIndex()) {
                    val expected = when (round.kind) {
                        StrollRoute.Kind.Plain, StrollRoute.Kind.Visit -> PLAIN
                        StrollRoute.Kind.Edge -> if (i == 1) EDGE else PLAIN
                        StrollRoute.Kind.Camera, StrollRoute.Kind.Close -> if (i == 0) CAMERA else PLAIN
                    }

                    for (f in listOf(0.0, 0.5, 1.0)) {
                        val x = route.startOf(r) + walk.start + f * walk.time

                        withClue("seed $seed, round $r (${round.kind}), leg $i, $f of the way") {
                            route.hipsAt(x) shouldBe (expected plusOrMinus 1e-12)
                        }
                    }
                }
            }
        }

        withClue("the kinds of round looked at") {
            seen shouldContainAll StrollRoute.Kind.entries
        }
    }

    "the kind's modifier on her hips never jumps: over every stroll, its step a frame at most 0.015" {
        // from the way home's 0.85 to the catwalk's 1.4, eased (the steepest 1.5 times the average) over a second
        var steepest = 0.0

        everyStroll { home, where ->
            val frames = framesOf(home)

            for (i in 1 until frames.size) {
                val step = abs(home.hipsAt(frames[i]) - home.hipsAt(frames[i - 1]))

                steepest = maxOf(steepest, step)

                withClue("$where, at ${frames[i]} s") {
                    step shouldBeLessThanOrEqual 0.015
                }
            }
        }

        // (and it does change somewhere)
        withClue("the steepest step a frame") {
            steepest shouldBeGreaterThan 0.005
        }
    }

    "while she walks her route the hips are one kind's, whole: a kind changes while she stands" {
        val seen = mutableSetOf<Double>()

        everyStroll { home, where ->
            for (t in framesOf(home)) {
                val x = t - home.from

                if (x <= 0.0 || x > home.leftRoute || speedAt(home, t) <= WALKING) {
                    continue
                }

                val hips = home.hipsAt(t)
                val kind = listOf(CAMERA, EDGE, PLAIN).minBy { abs(it - hips) }

                seen.add(kind)

                withClue("$where, at $t s, walking at ${speedAt(home, t)}") {
                    hips shouldBe (kind plusOrMinus 1e-12)
                }
            }
        }

        withClue("the kinds she was seen walking") {
            seen shouldContainAll listOf(CAMERA, EDGE, PLAIN)
        }
    }

    "on her way home the hips are the way home's, a second after she left her route" {
        var walkedHome = 0

        everyStroll { home, where ->
            if (home.way == null) {
                return@everyStroll
            }

            for (t in framesOf(home)) {
                val x = t - home.from

                if (x < home.leftRoute + StrollRoute.KIND_BLEND || t > home.to) {
                    continue
                }

                withClue("$where, at $t s (she left her route ${round(home.leftRoute * 100) / 100} s in)") {
                    home.hipsAt(t) shouldBe (HOME plusOrMinus 1e-12)
                }

                if (speedAt(home, t) > WALKING) {
                    walkedHome++
                }
            }
        }

        withClue("the frames she was seen walking home") {
            walkedHome shouldBeGreaterThan 100
        }
    }
})
