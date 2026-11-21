/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.figure.WalkKind
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollLeg.Pose
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.smoother
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the trumpet player's stroll takes her, [at] a time: a planned way through points, walked in legs with a
 * standstill after each (the maintainer, 2026-11-19: "she should 'plan' a route through certain points and then
 * 'bezier-curve' her way ... the distances between the points can be small, but many of them", and the strolling
 * should appear "more 'deliberate'"). Everything goes by the time and the [seed] alone, so any moment can be asked
 * for in any order.
 *
 * A stroll is rounds of 47, 61, 53 and 69 seconds (by the seed they start elsewhere in that order), and every round
 * ends with [WAIT] seconds standing at her place. In the time before, she walks legs, each ending in a standstill
 * of [PAUSE] seconds, the last one at her place. She arrives at every standstill already facing the way she faces
 * at rest ([restTurn], radians, as her turn on the stage: she faces (sin, cos) of it in x and z), and leaves it
 * walking forward: the way bends into it and out of it, no tighter than a turn a walking body makes (see
 * [bendsAndStraight]), so she never turns on the spot. Along a leg she goes by distance, eased in and out
 * ([smoother]: no speed and no push at either end), all legs of a round at the same pace.
 *
 * What a round does (see [Kind]): most go from point to point in the front of the stage; some walk along its front
 * edge in one long arc (the maintainer, 2026-11-19: "a long arc along the edge of the stage near the audience"),
 * since 2026-11-21 one big loop before the whole band, out past the guitarist on either side and some 1.5 m toward
 * the audience in its middle, out of the lit area on purpose (the maintainer, 2026-11-21: "Sirene needs to walk
 * further towrds the crowd a bit and take a wider angle along the crowd", and, the first step far too small, with
 * a loop drawn over the stage: "She needs to step out of the lit area and walk much wider... something like the
 * picture attached maybe", see [EDGE_LEFT]); in about two of five she walks up to the camera, stands there a moment
 * and goes back, and in some of those she comes on until her face nearly fills the wide shot, as far as [closest]
 * (the maintainer, 2026-11-19: "'frighteningly close' to the total camera sometimes").
 *
 * Now and then, instead of a plain round, she visits the others (see [Kind.Visit]; the maintainer, 2026-11-21:
 * 'And Sirene should "visit" her band-members').
 *
 * Places are off her place, in her sizes: x across the stage, z toward the audience. [closest]: the nearest she may
 * come to the camera, off her place in her sizes (who knows the camera works it out); she never passes it. [others]:
 * where the others stand, off her place, in her sizes (who knows the stage plot hands them in); she visits those
 * given, beside them (see [VISIT_BESIDE]), and none where none are given.
 *
 * A round is worked out once, when it is first asked for, and kept: a stroll begins with one of the first
 * [FIRST_ROUNDS] rounds (see [StrollHome]) and an hour holds some 63 of them, so the route keeps at most some 80,
 * and every stroll shares them.
 */
class StrollRoute(
    private val seed: Int,
    private val restTurn: Double,
    private val closest: Offset,
    private val others: Map<Player, Offset> = emptyMap(),
) {

    /** Where she is, off her place: [x] across the stage, [z] toward the audience, in her sizes. */
    data class Offset(val x: Double, val z: Double)

    /** What a round does. */
    internal enum class Kind {
        /** From point to point in the front of the stage, each a little nearer the audience, and home. */
        Plain,

        /** Out to one end of the front edge, along it in one long shallow arc, and home. */
        Edge,

        /** Up toward the camera, as far as [CAMERA_Z], and back. */
        Camera,

        /** Up to the camera as far as she may come ([closest]), slowly and straight for the last stretch, and back. */
        Close,

        /**
         * To one of the others, a while beside them ([VISIT_STAY]), on to one next to them where the round has the
         * time for it ([VISIT_PAIRS]), and home (the maintainer, 2026-11-21: 'And Sirene should "visit" her
         * band-members'). She stands there facing the audience, as at every standstill.
         */
        Visit,
    }

    /**
     * Whom she visits at a moment ([who]), how far she looks at them ([share], 0 to 1) and how far she has turned
     * to them ([turn], radians off the way she faces at rest, toward them): see [visitAt].
     */
    class Visiting(val who: Player, val share: Double, val turn: Double)

    /** One leg in the round's time: walked from [start] for [time] seconds (seconds into the round). */
    internal class Walk(val leg: StrollLeg, val start: Double, val time: Double)

    /**
     * A round worked out: what it does ([kind]), its legs in order, and in a round to the camera when she stares at
     * it ([stare], seconds into the round: see [stareOf]).
     */
    internal class Round(
        val kind: Kind,
        val walks: List<Walk>,
        val stare: ClosedFloatingPointRange<Double>? = null,
        val visits: List<Player> = emptyList(),
    )

    private val rounds: List<Int> = LENGTHS.indices.map { i -> LENGTHS[(i + seed.mod(LENGTHS.size)) % LENGTHS.size] }

    /** The way she faces at rest, as a pose's heading (see [Pose]). */
    internal val rest: Double = atan2(cos(restTurn), sin(restTurn))
    private val restX: Double = sin(restTurn)
    private val restZ: Double = cos(restTurn)

    private val worked = HashMap<Int, Round>()

    /** Where she stands to visit each of the others given, off her place, in her sizes (see [VISIT_BESIDE]). */
    internal val spots: Map<Player, Offset> = VISIT_BESIDE.filterKeys { it in others }.mapValues { (who, beside) ->
        val place = others.getValue(who)

        Offset(x = place.x + beside.x, z = place.z + beside.z)
    }

    /**
     * How far she turns to each one she visits, standing at their spot: toward their place, off the way she faces at
     * rest, at the most by [VISIT_TURN] to either side.
     */
    private val turns: Map<Player, Double> = spots.mapValues { (who, spot) ->
        val place = others.getValue(who)

        max(-VISIT_TURN, min(VISIT_TURN, wrap(atan2(place.x - spot.x, place.z - spot.z) - restTurn)))
    }

    /** Where she is [x] seconds after the stroll began, off her place, in her sizes. */
    fun at(x: Double): Offset {
        if (x <= 0.0) {
            return HOME
        }

        val round = roundAt(x)
        val into = x - startOf(round)

        for (walk in roundOf(round).walks) {
            if (into < walk.start) {
                return walk.leg.first
            }

            if (into <= walk.start + walk.time) {
                return walk.leg.at(walk.leg.length * smoother((into - walk.start) / walk.time))
            }
        }

        return HOME
    }

    /**
     * The kind of walk's modifier on her hips [x] seconds after the stroll began (see [WalkKind]): each leg's kind
     * ([walkKindOf]), eased from the one before over [KIND_BLEND] seconds that end as the leg begins. Every leg
     * begins from a standstill of [PAUSE] seconds or two at least (or [WAIT], at her place), so the change is made
     * while she stands, and the hips never jump. Before the stroll, the first leg's.
     */
    internal fun hipsAt(x: Double): Double {
        // the legs from the round before to the one a blend ahead, each with when the change to its kind begins; the
        // round before ends a [WAIT] at her place, so its last change lies more than a blend back
        val now = max(0.0, x)
        val changes = mutableListOf<Pair<Double, Double>>()

        for (round in max(0, roundAt(now) - 1)..roundAt(now + KIND_BLEND) + 1) {
            for ((i, walk) in roundOf(round).walks.withIndex()) {
                changes.add((startOf(round) + walk.start - KIND_BLEND) to walkKindOf(round = round, leg = i).hips)
            }
        }

        // the kind of the last change a whole blend back (or the first), and each change since eased in
        val settled = max(0, changes.indexOfLast { (at, _) -> at <= x - KIND_BLEND })
        var hips = changes[settled].second

        for (k in settled + 1 until changes.size) {
            val (at, kind) = changes[k]

            if (at > x) {
                break
            }

            hips += (kind - changes[k - 1].second) * smooth((x - at) / KIND_BLEND)
        }

        return hips
    }

    /**
     * The kind of walk of the leg [leg] of the round [round]: on the way to the camera the catwalk ([WalkKind.Camera],
     * up close too), and back from it a plain stroll; on an edge round the arc along the edge ([WalkKind.Edge]), and
     * the ways to it and from it a plain stroll; on a plain round and a visit, a plain stroll.
     */
    internal fun walkKindOf(round: Int, leg: Int): WalkKind = when (roundOf(round).kind) {
        Kind.Plain, Kind.Visit -> WalkKind.Stroll
        Kind.Edge -> if (leg == 1) WalkKind.Edge else WalkKind.Stroll
        Kind.Camera, Kind.Close -> if (leg == 0) WalkKind.Camera else WalkKind.Stroll
    }

    /** Which round [x] seconds after the stroll began lies in (the first is 0). */
    internal fun roundAt(x: Double): Int {
        val cycles = floor(x / CYCLE).toInt()
        var into = x - cycles * CYCLE
        var i = 0

        while (i < rounds.size - 1 && into > rounds[i]) {
            into -= rounds[i]
            i++
        }

        return cycles * rounds.size + i
    }

    /** When the round [round] begins, seconds after the stroll began. */
    internal fun startOf(round: Int): Double {
        val cycles = round / rounds.size

        return cycles * CYCLE + (0 until round % rounds.size).sumOf { rounds[it] }.toDouble()
    }

    /** How long the round [round] is, in seconds. */
    internal fun lengthOf(round: Int): Double = rounds[round % rounds.size].toDouble()

    /**
     * When she stares at the camera around [x] seconds after the stroll began (see [stareOf]), seconds after it
     * began: the window of the round [x] lies in; null where it goes to no camera.
     */
    internal fun stareAround(x: Double): ClosedFloatingPointRange<Double>? {
        if (x <= 0.0) {
            return null
        }

        val round = roundAt(x)
        val window = roundOf(round).stare ?: return null
        val start = startOf(round)

        return (start + window.start)..(start + window.endInclusive)
    }

    /**
     * Whom she visits [x] seconds after the stroll began, and how far she looks and turns to them (see [Visiting]);
     * null but while she stands beside one (since 2026-11-21: a visit without a glance at the one she visits is no
     * visit). Over her stay she turns to them and back, eased in over [VISIT_EASE] seconds as she has come
     * to a stand and out over as many before she walks on, so she arrives and leaves facing the audience as at every
     * standstill; at the most by [VISIT_TURN], so she stays open to the audience, and toward the one she visits
     * (where they stand behind her, the most to the side they are on).
     */
    internal fun visitAt(x: Double): Visiting? {
        if (x <= 0.0) {
            return null
        }

        val round = roundAt(x)
        val worked = roundOf(round)
        val into = x - startOf(round)

        for ((i, who) in worked.visits.withIndex()) {
            val start = worked.walks[i].start + worked.walks[i].time
            val end = worked.walks[i + 1].start

            if (into >= start && into <= end) {
                val share = smooth((into - start) / VISIT_EASE) * smooth((end - into) / VISIT_EASE)

                return Visiting(who = who, share = share, turn = share * turns.getValue(who))
            }
        }

        return null
    }

    /** The round [round] worked out (once, then kept). */
    internal fun roundOf(round: Int): Round = worked.getOrPut(round) { build(round = round, kind = wanted(round)) }

    /** A number from 0 to 1 for the round [round], its leg [leg] and the [k]th choice there: the same every time. */
    private fun roll(round: Int, leg: Int, k: Int): Double = hash01(seed * 12.9898 + round * 78.233 + leg * 37.719 + k * 4.581)

    /**
     * What the round [round] is meant to do (an edge round with no time for its way becomes a plain one or a visit,
     * a visit with no time for its way a plain one, see [build]).
     */
    private fun wanted(round: Int): Kind {
        // about two rounds in five go to the camera (the sum as a floating number: a large seed must not wrap round;
        // and taken modulo by floor, so a negative seed does not make every round one)
        if ((round * 7.0 + seed * 3.0).mod(5.0) < 2) {
            // and some of those come right up to it, where the round has time for the long way at a calm pace
            val window = lengthOf(round) - WAIT
            val far = 2 * hypot(closest.x, closest.z) + 1.5

            return if (roll(round = round, leg = 97, k = 0) < CLOSE_SHARE && far / (window - 3) <= CLOSE_PACE) {
                Kind.Close
            } else {
                Kind.Camera
            }
        }

        return if (roll(round = round, leg = 99, k = 0) < EDGE_SHARE) Kind.Edge else plainOr(round)
    }

    /** A plain round or, in about [VISIT_SHARE] of them, a visit (where there is anyone to visit). */
    private fun plainOr(round: Int): Kind =
        if (spots.isNotEmpty() && roll(round = round, leg = 96, k = 0) < VISIT_SHARE) Kind.Visit else Kind.Plain

    /**
     * Whom she visits in the round [round]: one of the others (the roll says who), and where she may go on from
     * them to another ([VISIT_PAIRS]), at most [most] in all.
     */
    private fun visitsOf(round: Int, most: Int): List<Player> {
        val order = VISIT_ORDER.filter { it in spots }
        val first = order[min(order.size - 1, floor(roll(round = round, leg = 95, k = 0) * order.size).toInt())]
        val next = VISIT_PAIRS.mapNotNull { (a, b) ->
            when (first) {
                a -> b
                b -> a
                else -> null
            }
        }.filter { it in spots }

        if (most < 2 || next.isEmpty()) {
            return listOf(first)
        }

        return listOf(first, next[min(next.size - 1, floor(roll(round = round, leg = 95, k = 1) * next.size).toInt())])
    }

    /** The round [round] worked out as a round of the [kind]; a visit to at most [most] of the others. */
    private fun build(round: Int, kind: Kind, most: Int = 2): Round {
        val window = lengthOf(round) - WAIT
        val visits = if (kind == Kind.Visit) visitsOf(round = round, most = most) else emptyList()
        // the standstills between the legs: at the camera up close a moment only, two to three seconds
        val pauses: (Int) -> List<Double> = { legs ->
            when (kind) {
                Kind.Close -> listOf(2.0 + roll(round = round, leg = 0, k = 71))
                Kind.Visit -> List(legs - 1) { VISIT_STAY }
                Kind.Plain, Kind.Edge, Kind.Camera -> List(legs - 1) { PAUSE }
            }
        }
        val ways: (Double) -> List<List<Pose>> = { radius ->
            when (kind) {
                Kind.Plain -> plainWays(round = round, window = window, radius = radius)
                Kind.Edge -> edgeWays(round = round, radius = radius)
                Kind.Camera, Kind.Close -> cameraWays(round = round, close = kind == Kind.Close, radius = radius)
                Kind.Visit -> visitWays(round = round, visits = visits, radius = radius)
            }
        }

        // the round's pace sets how tight she may bend: on the long way to the camera, round the band's front
        // (since 2026-11-21, when the edge's loop grew, see [EDGE_LEFT]) and on a visit she walks faster, and bends
        // wider. Worked out three times over, as the bends make the way longer.
        var radius = RADIUS
        var legs = emptyList<StrollLeg>()
        var walking = window
        var total = 0.0
        val widens = kind != Kind.Plain
        val passes = if (widens) 3 else 1

        repeat(passes) {
            legs = ways(radius).map { StrollLeg(it) }
            walking = window - pauses(legs.size).sum()
            total = legs.sumOf { it.length }

            if (widens) {
                radius = min(RADIUS_MOST, max(RADIUS, RADIUS_PER_PACE * total / walking))
            }
        }

        if (kind == Kind.Edge && total / walking > EDGE_PACE) {
            return build(round = round, kind = plainOr(round))
        }

        // a visit with no time for its way visits one fewer, or none
        if (kind == Kind.Visit && total / walking > VISIT_PACE) {
            return if (visits.size > 1) build(round = round, kind = Kind.Visit, most = 1) else build(round = round, kind = Kind.Plain)
        }

        val stands = pauses(legs.size)
        val walks = mutableListOf<Walk>()
        var t = 0.0

        for ((i, leg) in legs.withIndex()) {
            // every leg at the round's pace: its share of the walking time is its share of the way
            val time = walking * leg.length / total

            walks.add(Walk(leg = leg, start = t, time = time))
            t += time + (stands.getOrNull(i) ?: 0.0)
        }

        val stare = when (kind) {
            Kind.Camera, Kind.Close -> stareOf(there = walks[0], back = walks[1])
            Kind.Plain, Kind.Edge, Kind.Visit -> null
        }

        return Round(kind = kind, walks = walks, stare = stare, visits = visits)
    }

    /**
     * When she stares at the camera in a round to it, seconds into the round (the maintainer, 2026-11-19: "when she
     * comes close to the total camera she should also stare at the camera, already while walking towards it"). One
     * rule for both rounds to the camera, the one up close and the one as far as [CAMERA_Z]: from the moment she has
     * turned onto her way [there], walking at the lens (her way points at it within [APPROACH]; not where the leg
     * begins with a bend away from it), through her stand before it, until she begins to turn away from it on her
     * way [back] (her way more than [AWAY] off it): so it is let go while the camera is still in her sight, before her
     * body has turned from it. The lens lies [LENS] of her size straight on from [closest], toward
     * the audience (the camera stands across the stage where [closest] does). Where she never walks at the lens, from
     * the end of the way there.
     */
    private fun stareOf(there: Walk, back: Walk): ClosedFloatingPointRange<Double> {
        val lensX = closest.x
        val lensZ = closest.z + LENS

        // how far off the lens the way she goes is at [t] of [walk] (radians); null where she is slower than a slow walk
        fun offLens(walk: Walk, t: Double): Double? {
            fun placeAt(x: Double): Offset = walk.leg.at(walk.leg.length * smoother((x - walk.start) / walk.time))

            val a = placeAt(t - 0.5 * SCAN)
            val b = placeAt(t + 0.5 * SCAN)

            if (hypot(b.x - a.x, b.z - a.z) / SCAN < SLOW_WALK) {
                return null
            }

            return abs(wrap(atan2(b.z - a.z, b.x - a.x) - atan2(lensZ - a.z, lensX - a.x)))
        }

        var start = there.start + there.time
        var t = there.start

        while (t <= there.start + there.time) {
            val off = offLens(walk = there, t = t)

            if (off != null && off <= APPROACH) {
                start = t
                break
            }

            t += SCAN
        }

        var end = back.start + back.time

        t = back.start

        while (t <= back.start + back.time) {
            val off = offLens(walk = back, t = t)

            if (off != null && off > AWAY) {
                end = t
                break
            }

            t += SCAN
        }

        return start..max(start, end)
    }

    /** The way from [from] to [to], both standing, the way she faces at rest; [leg] and [round] choose its S. */
    private fun way(from: Offset, to: Offset, round: Int, leg: Int, radius: Double): List<Pose> {
        val start = Pose(x = from.x, z = from.z, heading = rest)

        return listOf(start) + bendsAndStraight(
            from = start,
            to = Pose(x = to.x, z = to.z, heading = rest),
            radius = radius,
            sway = SWAY * (2 * roll(round = round, leg = leg, k = 50) - 1),
            spacing = SPACING,
        )
    }

    /**
     * A plain round's ways: about one leg for every [LEG] seconds of the [window] (at least two), from her place
     * from stop to stop and home. Each stop is a little nearer the audience than the one before and [STEP_LEAST]
     * to [STEP_MOST] from it, so the way between two bends out of the one and into the other in an S.
     */
    private fun plainWays(round: Int, window: Double, radius: Double): List<List<Pose>> {
        val n = max(2, (window / LEG).roundToInt())
        val stops = mutableListOf(HOME)

        for (j in 0 until n - 1) {
            val before = stops.last()
            val z = AREA_NEAR + (AREA_FAR - AREA_NEAR) * (j + 0.2 + 0.6 * roll(round = round, leg = j, k = 0)) / (n - 1)
            val dz = z - before.z
            val d = STEP_LEAST + (STEP_MOST - STEP_LEAST) * roll(round = round, leg = j, k = 1)
            val dx = sqrt(max(0.0, d * d - dz * dz))
            val side = if (roll(round = round, leg = j, k = 2) < 0.5) 1.0 else -1.0
            var x = before.x + side * dx

            if (x < AREA_LEFT || x > AREA_RIGHT) {
                x = before.x - side * dx
            }

            stops.add(Offset(x = min(AREA_RIGHT, max(AREA_LEFT, x)), z = z))
        }

        stops.add(HOME)

        return (0 until n).map { j -> way(from = stops[j], to = stops[j + 1], round = round, leg = j, radius = radius) }
    }

    /**
     * An edge round's ways: out to one end of the front edge (which end, the roll says), along the whole width in
     * one long arc that bulges toward the audience ([BULGE]), with no standstill on it, and home. She stands at
     * each end facing the audience: at the first a quarter turn before the arc, at the last a quarter turn after it.
     */
    private fun edgeWays(round: Int, radius: Double): List<List<Pose>> {
        val toward = if (roll(round = round, leg = 98, k = 0) < 0.5) 1.0 else -1.0
        val q = EDGE_TURN
        val ze = EDGE_NEAR + EDGE_SPREAD * roll(round = round, leg = 98, k = 1)
        val xa = if (toward > 0) EDGE_LEFT else EDGE_RIGHT
        val xb = if (toward > 0) EDGE_RIGHT else EDGE_LEFT
        // where the arc begins and ends, and the way she goes there: along the edge
        val ax = xa + toward * q
        val bx = xb - toward * q
        val along = if (toward > 0) 0.0 else PI
        // the stands at its ends: a quarter turn of radius q from facing the audience into the arc, and out of it
        val sa = if (wrap(along - rest) > 0) 1.0 else -1.0
        val sb = if (wrap(rest - along) > 0) 1.0 else -1.0
        val caX = ax - sa * q * sin(along)
        val caZ = ze + sa * q * cos(along)
        val e1 = Offset(x = caX + sa * q * sin(rest), z = caZ - sa * q * cos(rest))
        val cbX = bx - sb * q * sin(along)
        val cbZ = ze + sb * q * cos(along)
        val e2 = Offset(x = cbX + sb * q * sin(rest), z = cbZ - sb * q * cos(rest))
        val k = max(2, ceil(abs(bx - ax) / EDGE_EVERY).toInt())
        val arcStart = Pose(x = ax, z = ze, heading = along)
        val arcEnd = Pose(x = bx, z = ze, heading = along)
        val first = Pose(x = e1.x, z = e1.z, heading = rest)
        val arc = mutableListOf(first)

        arc += bendsAndStraight(from = first, to = arcStart, radius = q, sway = 0.0, spacing = SPACING)

        for (i in 1 until k) {
            val f = i.toDouble() / k
            // a bulge of sin squared: flat where it meets the quarter turns
            val z = ze + BULGE * sin(PI * f) * sin(PI * f)
            val heading = atan2(BULGE * PI * sin(2 * PI * f), bx - ax)

            arc.add(Pose(x = ax + (bx - ax) * f, z = z, heading = heading))
        }

        arc.add(arcEnd)
        arc += bendsAndStraight(from = arcEnd, to = Pose(x = e2.x, z = e2.z, heading = rest), radius = q, sway = 0.0, spacing = SPACING)

        return listOf(
            way(from = HOME, to = e1, round = round, leg = 0, radius = radius),
            arc,
            way(from = e2, to = HOME, round = round, leg = 2, radius = radius),
        )
    }

    /** A visit's ways: from her place to each of the [visits] in turn, beside them ([spots]), and home. */
    private fun visitWays(round: Int, visits: List<Player>, radius: Double): List<List<Pose>> {
        val stops = listOf(HOME) + visits.map { spots.getValue(it) } + HOME

        return (0 until stops.size - 1).map { j -> way(from = stops[j], to = stops[j + 1], round = round, leg = j, radius = radius) }
    }

    /**
     * A camera round's ways: up to a stop before the camera and back. Up close ([close]) the last [RUN_IN] of the
     * way there is straight at the lens, and she turns back from the stop in the tightest bend ([RADIUS], while
     * she is still slow): the stop lies that bend's width short of [closest], so that leaving it, forward and
     * round, she comes no nearer than that.
     */
    private fun cameraWays(round: Int, close: Boolean, radius: Double): List<List<Pose>> {
        if (!close) {
            val stop = Offset(x = 0.3 * (roll(round = round, leg = 0, k = 70) - 0.5), z = CAMERA_Z)

            return listOf(
                way(from = HOME, to = stop, round = round, leg = 0, radius = radius),
                way(from = stop, to = HOME, round = round, leg = 1, radius = radius),
            )
        }

        val stop = Offset(x = closest.x - restX * 1.2 * RADIUS, z = closest.z - restZ * 1.2 * RADIUS)
        val before = Offset(x = stop.x - restX * RUN_IN, z = stop.z - restZ * RUN_IN)
        val there = way(from = HOME, to = before, round = round, leg = 0, radius = radius).toMutableList()
        val n = ceil(RUN_IN / SPACING).toInt()

        for (i in 1..n) {
            val f = i.toDouble() / n

            there.add(Pose(x = before.x + restX * RUN_IN * f, z = before.z + restZ * RUN_IN * f, heading = rest))
        }

        return listOf(there, way(from = stop, to = HOME, round = round, leg = 1, radius = RADIUS))
    }

    companion object {
        /** Her place. */
        val HOME: Offset = Offset(x = 0.0, z = 0.0)

        /** A stroll begins with one of this many rounds (see [StrollHome]). */
        internal const val FIRST_ROUNDS = 16

        /** The lengths of the rounds, in seconds, in their order. */
        private val LENGTHS = listOf(47, 61, 53, 69)

        /** All four rounds, one after the other. */
        private val CYCLE: Double = LENGTHS.sum().toDouble()

        /**
         * The seconds she stands at her place at the end of every round (the maintainer, 2026-11-16: "stand still
         * more between walking").
         */
        internal const val WAIT = 20.0

        /**
         * The kind of walk's modifier on her hips eases from one kind to the next over so many seconds (see
         * [hipsAt]): less than the shortest standstill before a leg (two seconds, at the camera up close).
         */
        internal const val KIND_BLEND = 1.0

        /** The seconds she stands at the end of a leg, but at the camera up close. */
        internal const val PAUSE = 4.0

        /** About one leg of a plain round for so many seconds of its walking window. */
        internal const val LEG = 12.0

        /**
         * The area of a plain round's stops: across the stage, and toward the audience (the maintainer, 2026-11-21:
         * "Sirene needs to walk further towrds the crowd a bit and take a wider angle along the crowd"; it was -0.25
         * to 0.95 across and 0.1 to 0.5 toward the audience). She stands at a stop, so its sides end where she would
         * stand before someone in the total's sight: to the left before the bass player, to the right before the
         * marimba player (her place is 0.7 m left of the stage's middle, one of her sizes some 0.98 m).
         */
        internal const val AREA_LEFT = -0.45
        internal const val AREA_RIGHT = 1.3
        internal const val AREA_NEAR = 0.2
        internal const val AREA_FAR = 0.75

        /** How far a plain round's stop lies from the one before. */
        internal const val STEP_LEAST = 0.55
        internal const val STEP_MOST = 0.9

        /**
         * The tightest bend: its radius. Bends of a round with a faster pace are wider, [RADIUS_PER_PACE] times
         * the pace (up to [RADIUS_MOST]), so she never turns faster than a body walking a bend.
         */
        internal const val RADIUS = 0.13
        internal const val RADIUS_PER_PACE = 1.0
        internal const val RADIUS_MOST = 0.6

        /** The poses of a straight, this far apart. */
        internal const val SPACING = 0.12

        /** The most a straight swings to the side, in its S. */
        internal const val SWAY = 0.05

        /** The step she is looked at in, to find when she stares at the camera (see [stareOf]), in seconds. */
        internal const val SCAN = 0.05

        /** A slow walk, in her sizes a second: slower, the way she goes says nothing (see [stareOf]). */
        internal const val SLOW_WALK = 0.05

        /** How far off the lens her way may point when she walks at it (see [stareOf]), in radians: 25 degrees. */
        internal const val APPROACH = 25 * PI / 180

        /**
         * How far off the lens her way may point on her way back while she still stares (see [stareOf]), in radians:
         * 15 degrees. Let go later (at a right angle, 60 or 40 degrees), the camera went out of her sight while the
         * stare still fell, and what she took of it fell 1.37, 1.14 or 1.17 a second, faster than over 1.5 s; from 15
         * degrees it falls 0.83 a second at most, as the stare itself does (StrollStareSpec, 2026-11-19).
         */
        internal const val AWAY = 15 * PI / 180

        /** Where the lens lies, straight on from [closest] toward the audience, in her sizes (see [stareOf]). */
        internal const val LENS = 1.5

        /** How far toward the camera she comes in a camera round. */
        internal const val CAMERA_Z = 3.4

        /** The share of camera rounds that come right up close, where the round has time for it at [CLOSE_PACE]. */
        internal const val CLOSE_SHARE = 2.0 / 3.0

        /** The fastest pace (in her sizes a second, on average) a round up close may ask of her. */
        internal const val CLOSE_PACE = 0.4

        /** The straight walk at the lens before the stop up close. */
        internal const val RUN_IN = 1.0

        /**
         * The share of the other rounds that walk the edge, where the round has time for it at [EDGE_PACE]. Since
         * the edge became the big loop (2026-11-21, see [EDGE_LEFT]) only the rounds of 69 seconds have that time,
         * and every one of them that goes to no camera walks it: some 3 rounds in 20 (it was 0.55 of the rounds
         * that had the time, and 0.7 for the first, smaller widening the same day, about one in five).
         */
        internal const val EDGE_SHARE = 1.0

        /**
         * The fastest pace an edge round may ask of her; a round that would need more is a plain one. 0.15 until
         * 2026-11-21, then 0.2 for a first widening; since the big loop (see [EDGE_LEFT]) the way out to one end,
         * along the loop and home is some 16.5 of her sizes, which a round of 69 seconds walks at 0.40 to 0.41 of
         * her size a second, 0.76 at the most: a relaxed walk, faster than a stroll, as the walks to the camera. A
         * round of 61 seconds would need 0.5, beyond a calm walk.
         */
        internal const val EDGE_PACE = 0.41

        /**
         * The ends of the front edge's loop, where she stands before it and after it (the maintainer, 2026-11-21:
         * "Sirene needs to walk further towrds the crowd a bit and take a wider angle along the crowd", and then, with
         * a loop drawn over the stage: "She needs to step out of the lit area and walk much wider... something like
         * the picture attached maybe"). They were -0.25 and 0.95, within the widths of a plain round, and -1.3 and
         * 1.3 for a first widening the same day, which was far too small. Now the loop goes out past the guitarists
         * on both sides: she stands some 3.64 m left of the stage's middle and 3.22 m right of it, 3.2 to 3.4 m
         * before it, beyond both guitarists in the total's sight, so she hides neither. At the left end she stands
         * outside the total's picture (at 16:9 one shoulder at its edge), at the right end inside it at 16:9 (half
         * out at 16:10, out at 4:3).
         */
        internal const val EDGE_LEFT = -3.0
        internal const val EDGE_RIGHT = 4.0

        /**
         * How near the audience the edge's loop runs at its ends: from [EDGE_NEAR], up to [EDGE_SPREAD] more (since
         * 2026-11-21, see [EDGE_LEFT]: it was from 0.5, then 0.75). Its middle comes [BULGE] nearer still.
         */
        internal const val EDGE_NEAR = 1.1
        internal const val EDGE_SPREAD = 0.1

        /**
         * The share of the plain rounds that are visits instead (see [Kind.Visit]): about one round in five (the
         * maintainer, 2026-11-21: 'And Sirene should "visit" her band-members').
         */
        internal const val VISIT_SHARE = 0.5

        /** How long she stays beside one she visits, in seconds. */
        internal const val VISIT_STAY = 5.0

        /** She turns to the one she visits and looks at them over this many seconds, and away again (see [visitAt]). */
        internal const val VISIT_EASE = 1.0

        /** The most she turns to the one she visits, in radians: 45 degrees, open to the audience (see [visitAt]). */
        internal const val VISIT_TURN = PI / 4

        /**
         * The fastest pace a visit may ask of her: where the round has no time for two at it, she visits one, and
         * where not even for one, the round is a plain one. Rounds of 47, 53 and 61 seconds have the time.
         */
        internal const val VISIT_PACE = 0.3

        /**
         * Where she stands to visit each of the others: off their place, in her sizes (some 0.98 m each), each
         * spot chosen on the stage plot of 2026-11-21 (`StagePlot`). Beside them as the total sees them, so she hides
         * nobody there, about a metre from them and clear of their instruments: in front of the guitarists, on the
         * side toward the middle (some 1.1 m from each, 0.66 m and 0.86 m from their guitars' heads); before the bass
         * player on the side toward the middle (1.31 m from him, 0.7 m from his bass's head, which reaches out that
         * way); left of the marimba (1.2 m from its player, 0.48 m from the marimba's end); and before the riser,
         * right of its middle (0.55 m before it, 2.1 m from the drummer up on it, 1.03 m from the marimba player).
         */
        internal val VISIT_BESIDE: Map<Player, Offset> = mapOf(
            Player.Skalpell to Offset(x = 0.87, z = 0.77),
            Player.Beben to Offset(x = 0.92, z = 0.97),
            Player.Wucht to Offset(x = 0.71, z = 2.04),
            Player.Rune to Offset(x = -1.22, z = 0.1),
            Player.Klaue to Offset(x = -0.82, z = 0.71),
        )

        /** The others in the order the roll picks the first of a visit from. */
        internal val VISIT_ORDER: List<Player> = listOf(Player.Klaue, Player.Skalpell, Player.Beben, Player.Wucht, Player.Rune)

        /**
         * Whom she may go on to from whom on a visit: the ways between their spots are clear of the others and their
         * instruments (between the bass player's and the drummer's the bass's head stands in the way, and between the
         * marimba's and the right guitarist's the marimba).
         */
        internal val VISIT_PAIRS: List<Pair<Player, Player>> = listOf(
            Player.Skalpell to Player.Beben,
            Player.Skalpell to Player.Klaue,
            Player.Wucht to Player.Rune,
        )

        /** The quarter turns into the edge's arc and out of it: their radius. */
        internal const val EDGE_TURN = 0.12

        /**
         * How far the edge's loop bulges toward the audience in its middle (since 2026-11-21, see [EDGE_LEFT]: it was
         * 0.08): she comes to some 4.75 m before the stage's middle, as the maintainer drew it, where the total's
         * picture still has her feet, and some 8.7 m short of the stage's front edge (`StageEdge`, 13.5 m before it).
         */
        internal const val BULGE = 1.4

        /** The arc's points, about this far apart. */
        internal const val EDGE_EVERY = 0.25

        /** [a] as the same turn within -pi to pi. */
        private fun wrap(a: Double): Double = atan2(sin(a), cos(a))
    }
}
