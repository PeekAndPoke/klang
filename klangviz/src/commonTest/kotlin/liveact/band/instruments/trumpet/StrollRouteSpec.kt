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
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Kind
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import io.peekandpoke.klang.viz.liveact.camera.TOTAL_FROM
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.stage.StageEdge
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

/** The seeds the route is checked with in the world, over its first 60 rounds: few enough for the browser's run. */
private val WORLD_SEEDS = (-5..5) + listOf(42, 67, 1234, -1234)

/** The nearest to the camera, as the frontwoman's player works it out for Sirene (0.71 across, 5.94 ahead). */
private val CLOSEST = Offset(x = 0.71, z = 5.94)

/** The rounds sampled. */
private const val ROUNDS = 8

/** Sirene's place in Der Schmetterling's stage plot (`DER_SCHMETTERLING`), in metres, and her size in metres (her rig's scale). */
private const val PLACE_X = -0.7
private const val PLACE_Z = 2.2
private const val SIZE = 0.98

/** The others' places in Der Schmetterling's stage plot (`DER_SCHMETTERLING`), in metres. */
private val OTHERS: Map<Player, Pair<Double, Double>> = mapOf(
    Player.Beben to (-2.15 to 0.4),
    Player.Skalpell to (-3.0 to 1.7),
    Player.Rune to (1.7 to 0.4),
    Player.Klaue to (3.0 to 1.7),
    Player.Wucht to (0.1 to -2.1),
)

/** How far she stands from each one she visits, in metres. */
private val VISITED_APART: Map<Player, Double> = mapOf(
    Player.Skalpell to 1.35,
    Player.Beben to 1.57,
    Player.Rune to 1.2,
    Player.Klaue to 1.27,
    Player.Wucht to 2.12,
)

/** The others' places as the frontwoman's player hands them to her route: off her place, in her sizes (StrollGaitSpec too). */
internal val OTHERS_OFF: Map<Player, Offset> = OTHERS.mapValues { (_, at) ->
    Offset(x = (at.first - PLACE_X) / SIZE, z = (at.second - PLACE_Z) / SIZE)
}

/** The drummer's riser, in metres: from its left to its right, from its back to its front (as `Stage` stands it). */
private const val RISER_LEFT = -1.45
private const val RISER_RIGHT = 1.45
private const val RISER_BACK = -2.85
private const val RISER_FRONT = -0.65

/** How far [x], [z] lies outside the riser, in metres (0 on it or in it). */
private fun fromRiser(x: Double, z: Double): Double =
    hypot(max(0.0, max(RISER_LEFT - x, x - RISER_RIGHT)), max(0.0, max(RISER_BACK - z, z - RISER_FRONT)))

/** The marimba's side toward the audience, in metres: from its left end to its right (as `Marimba` stands it). */
private val MARIMBA_FRONT = (0.86 to 1.23) to (2.26 to 1.45)

/** How far [x], [z] lies from the line from [a] to [b], all in metres. */
private fun fromLine(x: Double, z: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
    val dx = b.first - a.first
    val dz = b.second - a.second
    val u = max(0.0, min(1.0, ((x - a.first) * dx + (z - a.second) * dz) / (dx * dx + dz * dz)))

    return hypot(x - a.first - u * dx, z - a.second - u * dz)
}

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
    Sampled(route = StrollRoute(seed = seed, restTurn = restTurn, closest = CLOSEST, others = OTHERS_OFF), restTurn = restTurn)

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
        // last of a short bend into it; some 5 degrees at most). Her way, that is: beside one she visits she then
        // turns to them while she stands, as the route says (see the visits' turn below), and back before she leaves
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

    "she stays in the front of the stage, where she strolls, but on the way to the camera" {
        // a plain round across from -0.7 to 1.35 of her size (since 2026-11-21: it was -0.55 to 1.1), toward the
        // audience from 0.2 behind her place to 0.9 before it (it was 0.7); an edge round, the big loop before the
        // band (since 2026-11-21: it was -0.55 to 1.1 and up to 0.9), from -3.05 to 4.05 across and from 0.5 behind
        // her place (its bends, wider at its pace) to 2.65 before it
        everyRoute { s, where ->
            for ((i, t) in s.times.withIndex()) {
                val p = s.at[i]

                when (s.kindAt(t)) {
                    Kind.Plain -> withClue("$where, $t s, a plain round: at $p") {
                        p.x shouldBeGreaterThan -0.7
                        p.x shouldBeLessThan 1.35
                        p.z shouldBeGreaterThan -0.2
                        p.z shouldBeLessThan 0.9
                    }

                    Kind.Edge -> withClue("$where, $t s, an edge round: at $p") {
                        p.x shouldBeGreaterThan -3.05
                        p.x shouldBeLessThan 4.05
                        p.z shouldBeGreaterThan -0.5
                        p.z shouldBeLessThan 2.65
                    }

                    // (a visit goes where the others are: the world's guard has it, below)
                    Kind.Camera, Kind.Close, Kind.Visit -> Unit
                }
            }

            for (round in 0 until ROUNDS) {
                val start = s.route.startOf(round)
                val near = s.times.filter { it >= start && it < start + s.route.lengthOf(round) }.maxOf { s.route.at(it).z }

                when (s.route.roundOf(round).kind) {
                    Kind.Camera -> withClue("$where, round $round goes to the camera") { near shouldBeGreaterThan 3.0 }
                    Kind.Close -> withClue("$where, round $round comes up close") { near shouldBeGreaterThan CLOSEST.z - 0.2 }
                    Kind.Plain, Kind.Edge, Kind.Visit -> Unit
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

            // (the maintainer, 2026-11-21: "She needs to step out of the lit area and walk much wider... something
            // like the picture attached maybe": it went across 1.2 of her size from 0.37 before her place, then 2.6
            // from 0.62, now 7 from 0.96)
            withClue("round $round: the loop goes across the whole band's front") {
                abs(walk.leg.last.x - walk.leg.first.x) shouldBeGreaterThan 6.5
                min(walk.leg.first.z, walk.leg.last.z) shouldBeGreaterThan 0.95
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

    "over many seeds and an hour, she stays on the stage, clear of the others, and walks the big loop in 3 rounds of 20" {
        // (the maintainer, 2026-11-21: "Sirene needs to walk further towrds the crowd a bit and take a wider angle
        // along the crowd") Every 0.2 s of the first 60 rounds, for 15 seeds (no more and no finer: the specs
        // also run in the browser, where 45 seeds every 0.1 s held it so long that the test run lost it), in
        // metres on the stage: she stays
        // 1.1 m inside the stage's edge or more (0.6 m and the mirror's fade), 0.95 m from the others' places
        // (some 1.01 m at the nearest, the guitarist to her left, as the maintainer's drawn loop passes him at
        // 1.02 m; it was 1.3 m before the big loop, 2026-11-21) and 1 m before the marimba (1.03 m).
        // The edge rounds go out to both ends of the loop, and walk it in 9 rounds of 60 (8 to 10 allowed).
        // On a visit (2026-11-21) she comes near the ones she visits and their instruments by design: 0.85 m from
        // the one she visits (0.93 m at the nearest, the bass player, as she turns to stand beside him), 0.6 m before
        // the marimba's front (0.66 m, beside its left end) and 0.25 m from the riser (0.32 m, before it); the others
        // 0.95 m as on every round. Off a visit she keeps 0.3 m from the riser too.
        for (seed in WORLD_SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST, others = OTHERS_OFF)
            val end = route.startOf(60)
            var left = 0.0
            var right = 0.0
            var t = 0.0

            while (t < end) {
                val p = route.at(t)
                val x = PLACE_X + SIZE * p.x
                val z = PLACE_Z + SIZE * p.z
                val round = route.roundOf(route.roundAt(t))
                val visit = round.kind == Kind.Visit

                withClue("seed $seed, $t s, a ${round.kind} round (visits ${round.visits}): at $x, $z m") {
                    StageEdge.distance(x = x, z = z) shouldBeGreaterThan 0.6 + StageEdge.FADE

                    for ((who, at) in OTHERS) {
                        hypot(x - at.first, z - at.second) shouldBeGreaterThan (if (who in round.visits) 0.85 else 0.95)
                    }

                    fromLine(x = x, z = z, a = MARIMBA_FRONT.first, b = MARIMBA_FRONT.second) shouldBeGreaterThan (if (visit) 0.6 else 1.0)
                    fromRiser(x = x, z = z) shouldBeGreaterThan (if (visit) 0.25 else 0.3)
                }

                if (route.roundOf(route.roundAt(t)).kind == Kind.Edge) {
                    left = min(left, p.x)
                    right = max(right, p.x)
                }

                t += 0.2
            }

            val edges = (0 until 60).count { route.roundOf(it).kind == Kind.Edge }

            withClue("seed $seed: the edge rounds of the first 60 ($edges), as far left ($left) and right ($right) as they go") {
                (edges in 8..10) shouldBe true
                left shouldBeLessThan StrollRoute.EDGE_LEFT + 0.05
                right shouldBeGreaterThan StrollRoute.EDGE_RIGHT - 0.05
            }
        }
    }

    "now and then she visits the others, every one of them, and stands beside them a while" {
        // (the maintainer, 2026-11-21: 'And Sirene should "visit" her band-members') Every one of them is visited
        // over the first 60 rounds of a few seeds, one or two in a round; at each stop she stands where the spot
        // says for the stay's time, 1.2 m to 1.57 m from the one she visits (2.12 m from the drummer on his riser).
        val visited = mutableSetOf<Player>()

        for (seed in SEEDS) {
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST, others = OTHERS_OFF)

            for (round in 0 until 60) {
                val worked = route.roundOf(round)

                if (worked.kind != Kind.Visit) {
                    continue
                }

                (worked.visits.size in 1..2) shouldBe true
                visited += worked.visits

                for ((i, who) in worked.visits.withIndex()) {
                    val spot = route.spots.getValue(who)
                    val arrives = route.startOf(round) + worked.walks[i].start + worked.walks[i].time
                    val (ox, oz) = OTHERS.getValue(who)
                    var t = arrives

                    withClue("seed $seed, round $round: beside $who, at $spot") {
                        // (a fifth farther from those on the floor than at first, 2026-11-27: "she get a little too
                        // close to the others")
                        hypot(PLACE_X + SIZE * spot.x - ox, PLACE_Z + SIZE * spot.z - oz) shouldBe
                            (VISITED_APART.getValue(who) plusOrMinus 0.01)
                        worked.walks[i + 1].start - (worked.walks[i].start + worked.walks[i].time) shouldBe
                            (StrollRoute.VISIT_STAY plusOrMinus 1e-9)
                    }

                    while (t <= arrives + StrollRoute.VISIT_STAY) {
                        withClue("seed $seed, round $round, $t s: standing beside $who") {
                            apart(route.at(t), spot) shouldBe (0.0 plusOrMinus 1e-9)
                        }

                        t += 0.25
                    }
                }
            }
        }

        visited shouldBe OTHERS.keys
    }

    "where she stands to visit one, she hides nobody from the total" {
        // (the spots' own word: "beside them as the total sees them, so she hides nobody there") Seen from where the
        // total stands, each spot lies 0.4 m or more off the line of sight to every one of the band behind her. In
        // plan: her head is about as high as such a line where she stands (the total at 1.85 m, the eyes at 1.6 m,
        // the drummer's 1.65 m on his riser).
        val route = StrollRoute(seed = 1, restTurn = 0.1, closest = CLOSEST, others = OTHERS_OFF)

        for ((who, spot) in route.spots) {
            val x = PLACE_X + SIZE * spot.x
            val z = PLACE_Z + SIZE * spot.z

            for ((other, at) in OTHERS) {
                if (at.second >= z) {
                    continue
                }

                withClue("beside $who, at $x, $z m: the line of sight to $other") {
                    fromLine(x = x, z = z, a = TOTAL_FROM.x to TOTAL_FROM.z, b = at) shouldBeGreaterThan 0.4
                }
            }
        }
    }

    "beside one she visits she turns to them and looks at them, eased in and out, 45 degrees at the most" {
        // (2026-11-21: a visit without a glance at the one she visits is no visit) Every 1/60 s round each stand of a
        // visit, for three seeds: the turn and the look's share never jump (a frame's step at most 0.025 rad and
        // 0.03: eased over a second, 45 degrees moves 0.0196 rad a frame at the most), the turn points toward the
        // one she visits and stays within the cap, and in the middle of her stay she looks at them all the way.
        // Off a stand there is no visit. (Kept light: the specs run in the browser too.)
        val frame = 1.0 / 60

        for (seed in SEEDS.take(2)) {
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST, others = OTHERS_OFF)

            for (round in 0 until 24) {
                val worked = route.roundOf(round)
                val start = route.startOf(round)

                if (worked.kind != Kind.Visit) {
                    route.visitAt(start + 0.5 * route.lengthOf(round)) shouldBe null
                    continue
                }

                for ((i, who) in worked.visits.withIndex()) {
                    val from = start + worked.walks[i].start + worked.walks[i].time
                    val to = start + worked.walks[i + 1].start
                    val spot = route.spots.getValue(who)
                    val place = OTHERS_OFF.getValue(who)
                    val toward = wrap(atan2(place.x - spot.x, place.z - spot.z) - 0.1)
                    var t = from - 0.5
                    var before = route.visitAt(t)

                    while (t < to + 0.5) {
                        t += frame

                        val now = route.visitAt(t)

                        withClue("seed $seed, round $round, $t s, beside $who (from $from to $to s)") {
                            abs((now?.turn ?: 0.0) - (before?.turn ?: 0.0)) shouldBeLessThan 0.025
                            abs((now?.share ?: 0.0) - (before?.share ?: 0.0)) shouldBeLessThan 0.03
                            abs(now?.turn ?: 0.0) shouldBeLessThanOrEqual StrollRoute.VISIT_TURN + 1e-12
                            (now == null || now.who == who) shouldBe true
                            (t > from && t < to || now == null || now.share < 1e-9) shouldBe true
                        }

                        before = now
                    }

                    val middle = route.visitAt(0.5 * (from + to))

                    withClue("seed $seed, round $round: in the middle of her stay beside $who (toward them $toward)") {
                        middle?.share shouldBe (1.0 plusOrMinus 1e-9)
                        (middle!!.turn * toward > 0) shouldBe true
                        abs(middle.turn) shouldBe (min(abs(toward), StrollRoute.VISIT_TURN) plusOrMinus 1e-9)
                    }
                }
            }
        }

    }

    "a stroll lets go of a visit from where it turns home, as smoothly, and is done with it by its end" {
        // one stroll that turns home from beside one she visits (kept short: the specs run in the browser too)
        val frame = 1.0 / 60
        val route = StrollRoute(seed = 1, restTurn = 0.1, closest = CLOSEST, others = OTHERS_OFF)
        var cut = 0
        var k = 0

        while (cut < 1 && k < 40) {
            val from = 100.0 * k

            k++

            // the first stand beside someone in the stroll's first two rounds, in the show's time
            val shift = StrollHome(route = route, from = from, to = from + 1).shift
            val first = route.roundAt(shift + 1e-9)
            val stand = (first..first + 1).firstNotNullOfOrNull { round ->
                val walk = route.roundOf(round).walks.first()

                if (route.roundOf(round).kind == Kind.Visit) route.startOf(round) + walk.start + walk.time else null
            } ?: continue
            val middle = from + stand - shift + 0.5 * StrollRoute.VISIT_STAY
            // an end a few seconds after the middle of the stand: she turns home from beside them
            val stroll = (1..12).map { late -> StrollHome(route = route, from = from, to = middle + late) }.firstOrNull {
                (route.visitAt(it.leftRoute + it.shift)?.share ?: 0.0) > 0.1
            } ?: continue
            var t = stroll.from
            var before = 0.0

            cut++

            while (t < stroll.to + 1) {
                val now = stroll.visitAt(t)?.turn ?: 0.0

                withClue("the stroll from ${stroll.from} s to ${stroll.to} s, at $t s") {
                    abs(now - before) shouldBeLessThan 0.025
                    (t < stroll.to || abs(now) < 1e-9) shouldBe true
                }

                before = now
                t += frame
            }
        }

        withClue("a stroll that turns home from beside one she visits") {
            cut shouldBe 1
        }
    }

    "a visit takes the place of a plain round only: the rounds to the camera and the edge's stay as they were" {
        for (seed in WORLD_SEEDS) {
            val alone = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST)
            val visiting = StrollRoute(seed = seed, restTurn = 0.1, closest = CLOSEST, others = OTHERS_OFF)
            val visits = (0 until 60).count { visiting.roundOf(it).kind == Kind.Visit }

            for (round in 0 until 60) {
                val was = alone.roundOf(round).kind
                val now = visiting.roundOf(round).kind

                withClue("seed $seed, round $round: a $was round alone, a $now round with the others to visit") {
                    (now == was || (was == Kind.Plain && now == Kind.Visit)) shouldBe true
                }
            }

            // about one round in five (5 to 17 of 60 over these seeds, 11.4 on average)
            withClue("seed $seed: the visits of the first 60 rounds ($visits)") {
                (visits in 4..18) shouldBe true
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
