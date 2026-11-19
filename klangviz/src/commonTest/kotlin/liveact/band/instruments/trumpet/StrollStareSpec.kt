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
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Kind
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import io.peekandpoke.klang.viz.liveact.inSight
import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val SEEDS = listOf(0, 1, 2, 7, 42, -5)
private val STARTS = listOf(15.0, 83.7)
private const val REST = 0.1
private val CLOSEST = Offset(x = 0.71, z = 5.94)

/** The rounds looked at, from where a stroll begins. */
private const val ROUNDS = 8

/** A frame, in seconds. */
private const val FRAME = 1.0 / 60

/** The stare rises in 0.8 s and is let go in 1.8 s, as her timed looks are (asked for, so not the code's own numbers). */
private const val RISE = 0.8
private const val FALL = 1.8

/** A slow walk, her sizes a second. */
private const val SLOW = 0.05

/**
 * The lens, off her place in her sizes, worked out on its own: the total's camera stands at x 0, z 9.4 on the stage,
 * her place at x -0.7, z 2.2 (the stage plot), and her size is about 0.986 (her figure), so it lies 0.7 / 0.986 = 0.71
 * across and 7.2 / 0.986 = 7.30 ahead of her place.
 */
private val LENS_AT = Offset(x = 0.71, z = 7.30)

/** How far off the lens her way may point on her way back while she still stares, in degrees. */
private const val AWAY = 15.0

private fun wrap(a: Double): Double = atan2(sin(a), cos(a))

private fun degrees(radians: Double): Double = radians * 180 / PI

/**
 * One round of a stroll in the show's time: what it does, when it begins and ends, its stare's window and in a round
 * to the camera her stand before it (else null).
 */
private class Seen(
    val kind: Kind,
    val start: Double,
    val end: Double,
    val stare: ClosedFloatingPointRange<Double>?,
    val stand: ClosedFloatingPointRange<Double>?,
    val back: Double?,
)

/** The first [ROUNDS] rounds of [home] (a stroll of an hour, all route), in the show's time. */
private fun roundsOf(route: StrollRoute, home: StrollHome): List<Seen> {
    val first = route.roundAt(home.shift + 1e-9)

    return (first until first + ROUNDS).map { round ->
        val start = home.from + route.startOf(round) - home.shift
        val worked = route.roundOf(round)
        val window = worked.stare
        val walks = worked.walks

        Seen(
            kind = worked.kind,
            start = start,
            end = start + route.lengthOf(round),
            stare = window?.let { (start + it.start)..(start + it.endInclusive) },
            stand = window?.let { (start + walks[0].start + walks[0].time)..(start + walks[1].start) },
            back = window?.let { start + walks[1].start },
        )
    }
}

/** How far off the lens the way she goes is at [t] (degrees), or null where she is slower than a slow walk. */
private fun offLens(home: StrollHome, t: Double): Double? {
    val a = home.at(t - 0.025)
    val b = home.at(t + 0.025)

    if (hypot(b.x - a.x, b.z - a.z) / 0.05 < SLOW) {
        return null
    }

    return degrees(abs(wrap(atan2(b.z - a.z, b.x - a.x) - atan2(LENS_AT.z - a.z, LENS_AT.x - a.x))))
}

private fun everyStroll(check: (StrollRoute, StrollHome, String) -> Unit) {
    for (seed in SEEDS) {
        val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

        for (from in STARTS) {
            check(route, StrollHome(route = route, from = from, to = from + 3600.0), "seed $seed, a stroll from $from s")
        }
    }
}

class StrollStareSpec : StringSpec({

    "on a round to the camera she stares at it from when she walks at it, through her stand, till she turns away" {
        var windows = 0

        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = REST, closest = CLOSEST)

            for (from in STARTS) {
                val home = StrollHome(route = route, from = from, to = from + 3600.0)

                for (seen in roundsOf(route = route, home = home)) {
                    val window = seen.stare ?: continue
                    val where = "seed $seed, a stroll from $from s, a ${seen.kind} round from ${seen.start} s, stare from " +
                        "${window.start} s to ${window.endInclusive} s"

                    windows++

                    // nothing before she walks at the camera; as she begins to, her way points at it
                    var t = seen.start

                    while (t < window.start - 1e-9) {
                        withClue("$where, at $t s: before it") {
                            home.stareAt(t) shouldBe 0.0
                        }

                        val off = offLens(home = home, t = t)

                        if (off != null && t < window.start - 0.1) {
                            withClue("$where, at $t s: walking, but not at the lens yet ($off degrees off it)") {
                                off shouldBeGreaterThan 23.0
                            }
                        }

                        t += 0.05
                    }

                    withClue("$where: walking at the lens as the stare begins") {
                        (offLens(home = home, t = window.start) ?: 180.0) shouldBeLessThan 27.0
                    }

                    // all there 0.8 s on, and so through her stand, till she turns away
                    t = window.start + RISE

                    while (t <= window.endInclusive) {
                        withClue("$where, at $t s: all there") {
                            home.stareAt(t) shouldBe (1.0 plusOrMinus 1e-9)
                        }

                        t += 0.05
                    }

                    // on her way back, till she turns away, wherever she walks her way still points near the lens
                    val back = seen.back ?: continue

                    t = back

                    while (t < window.endInclusive - 0.1) {
                        val off = offLens(home = home, t = t)

                        if (off != null) {
                            withClue("$where, at $t s: on her way back, not turned away yet ($off degrees off the lens)") {
                                off shouldBeLessThan AWAY + 2.0
                            }
                        }

                        t += 0.05
                    }

                    withClue("$where: turned away from the lens as it is let go") {
                        (offLens(home = home, t = window.endInclusive) ?: 0.0) shouldBeGreaterThan AWAY - 2.0
                    }

                    // let go slowly: still there 1.5 s after she turned away, gone 1.8 s after
                    withClue("$where: let go over at least 1.5 s") {
                        home.stareAt(window.endInclusive + 1.5) shouldBeGreaterThan 0.0
                        home.stareAt(window.endInclusive + FALL + 1e-6) shouldBe 0.0
                    }

                    // and her stand before the camera lies within it, all there
                    val stand = seen.stand ?: continue

                    withClue("$where: her stand (${stand.start} s to ${stand.endInclusive} s) is in the stare, all there") {
                        window.start + RISE shouldBeLessThan stand.start
                        window.endInclusive shouldBeGreaterThan stand.endInclusive
                    }
                }
            }
        }

        windows shouldBeGreaterThan 4
    }

    "she stares at the camera on no other round, and the stare never jumps from one frame to the next" {
        // the rise is steepest in its middle: 1.5 / 0.8 a second; the fall less so
        everyStroll { route, home, where ->
            for (seen in roundsOf(route = route, home = home)) {
                var t = seen.start
                var before = home.stareAt(t)

                while (t < seen.end) {
                    t += FRAME

                    val now = home.stareAt(t)

                    when (seen.kind) {
                        Kind.Plain, Kind.Edge -> withClue("$where, a ${seen.kind} round, at $t s") { now shouldBe 0.0 }
                        Kind.Camera, Kind.Close -> Unit
                    }

                    withClue("$where, a ${seen.kind} round, at $t s: a frame's step") {
                        abs(now - before) shouldBeLessThan 1.5 / RISE * FRAME + 1e-9
                    }

                    before = now
                }
            }
        }
    }

    "a stroll that ends while she stares lets go as it leaves the route, slowly; replaced, it stares as before" {
        var cut = 0

        everyStroll { route, long, where ->
            for (seen in roundsOf(route = route, home = long)) {
                val window = seen.stare ?: continue

                for (late in listOf(1.0, 4.0, 9.0)) {
                    val end = window.start + late + 3.0
                    val home = StrollHome(route = route, from = long.from, to = end)
                    val left = home.from + home.leftRoute

                    if (left < window.endInclusive) {
                        cut++
                    }

                    var t = window.start - 1.0
                    var before = home.stareAt(t)

                    while (t < end + FALL + 1.0) {
                        t += FRAME

                        val now = home.stareAt(t)

                        withClue("$where, a stroll to $end s (it leaves the route at $left s), at $t s") {
                            abs(now - before) shouldBeLessThan 1.5 / RISE * FRAME + 1e-9

                            if (t <= left) {
                                now shouldBe long.stareAt(t)
                            }

                            if (t >= left + FALL) {
                                now shouldBe 0.0
                            }
                        }

                        before = now
                    }

                    // replaced while she stares, to a later end: the same stare
                    val now = window.start + 0.5 * late
                    val replaced = StrollHome(route = route, from = long.from, to = end + 20.0, notBefore = now, before = home)

                    withClue("$where, a stroll to $end s replaced at $now s") {
                        replaced.stareAt(now) shouldBe (home.stareAt(now) plusOrMinus 1e-9)
                    }
                }
            }
        }

        withClue("strolls that leave the route while she stares") {
            cut shouldBeGreaterThan 0
        }
    }

    "through her turn away the stare she takes (her body's inertia, the camera in sight) never falls faster than in 1.5 s" {
        // as the frontwoman's player has it: she faces her way with a body's inertia (StrollTurn, as `TrumpetPlayer.facing`
        // asks it), and takes the stare as far as the lens is in her sight, let go slowly where she turns from it
        // (lookReach); a fall eased over 1.5 s is at its steepest 1.0 a second
        var fastest = 0.0
        var fastestAt = ""

        everyStroll { route, home, where ->
            fun ahead(x: Double): Double {
                val a = home.at(x + 0.2)
                val b = home.at(x - 0.2)
                val going = smooth(min(1.0, hypot(a.x - b.x, a.z - b.z) / 0.4 / SLOW))

                return wrap(atan2(a.x - b.x, a.z - b.z) - REST) * going
            }

            val turn = StrollTurn(from = home.from, to = home.to) { ahead(it) }

            fun yawAt(t: Double): Double {
                val p = home.at(t)

                return wrap(atan2(LENS_AT.x - p.x, LENS_AT.z - p.z) - (REST + turn.at(t)))
            }

            fun taken(t: Double): Double {
                val grid = floor(t / 0.1) * 0.1
                val reach = lookReach(
                    t = t,
                    yaw = yawAt(t),
                    reach = { inSight(it) },
                    yawBefore = { k -> yawAt(grid - k * 0.1) },
                    fall = 1.6,
                    step = 0.1,
                )

                return home.stareAt(t) * reach
            }

            for (seen in roundsOf(route = route, home = home)) {
                val window = seen.stare ?: continue
                var t = window.endInclusive - 1.0
                var before = taken(t)

                while (t < window.endInclusive + 5.0) {
                    t += FRAME

                    val now = taken(t)
                    val falls = (before - now) / FRAME

                    if (falls > fastest) {
                        fastest = falls
                        fastestAt = "$where, a ${seen.kind} round, at $t s (she turns away at ${window.endInclusive} s)"
                    }

                    before = now
                }
            }
        }

        withClue("the fastest fall of the stare she takes: $fastest a second, $fastestAt") {
            fastest shouldBeLessThan 1.0
        }
    }

    "where she faces aside at rest, the way to the camera begins with a bend, and she stares only after it" {
        // facing 0.6 rad to her right at rest she leaves her place walking that way, some 40 degrees off the lens,
        // and bends round toward it: the stare begins as she walks at the lens, not as she leaves
        var bent = 0

        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = -0.6, closest = CLOSEST)

            for (from in STARTS) {
                val home = StrollHome(route = route, from = from, to = from + 3600.0)

                for (seen in roundsOf(route = route, home = home)) {
                    val window = seen.stare ?: continue
                    var t = seen.start
                    var aside = false

                    while (t < window.start - 0.1) {
                        val off = offLens(home = home, t = t)

                        if (off != null && off > 30.0) {
                            aside = true
                        }

                        t += 0.05
                    }

                    if (aside) {
                        bent++
                    }

                    withClue("seed $seed, a stroll from $from s, a ${seen.kind} round: as the stare begins she walks at the lens") {
                        (offLens(home = home, t = window.start) ?: 180.0) shouldBeLessThan 27.0
                        home.stareAt(window.start - 0.05) shouldBe 0.0
                    }
                }
            }
        }

        withClue("rounds to the camera that begin with a bend away from it") {
            bent shouldBeGreaterThan 0
        }
    }
})
