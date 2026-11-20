/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.band.figure.WalkKind
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollLeg.Pose
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.smoother
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * One stroll of the trumpet player, from [from] to [to] seconds into the show: her [route] until she turns home, and
 * then a planned way home, so that she is at her place at [to], facing the way she faces at rest, as she arrives
 * from a round (a stroll's end has nothing to do with the rounds: it comes where her next phrase does).
 *
 * When she turns home ([turnBack], seconds into the stroll): the latest moment, on a grid of [GRID] seconds back
 * from [to], from which her way home is walked at [PACE] (her sizes a second, on average) or slower, and not so
 * much slower that she could not leave on it at the speed she goes ([LEAVE]). So she turns
 * back from wherever she is in time, and never goes further out than she can come back from at that pace: a round
 * to the camera that the stroll has no time for is turned back from on the way out, or not begun. Where she is at
 * her place at that moment she stays. Before it her place is the route's alone, whatever [to] is.
 *
 * The way home leaves where the route has her, the way she goes there (where she stands, the way she faces at
 * rest), as fast as she goes there, and bends round to her place no tighter than she walks a bend at that speed
 * (see [bendsAndStraight]); it is walked in the time left, slowing to a stand at her place. So nothing jumps, and
 * on the way home she is never faster than she was when she turned, or than [PACE] eased (15/8 of it at most).
 *
 * [notBefore]: the moment her end became known, where it changed while she strolled (a song read as it is played,
 * where a note may come, go or move while she strolls); [before]: the stroll this one replaces then, the same
 * start, another end. What she did before [notBefore] stays as it was: she turns home no sooner than that, and
 * where she had turned home already, she goes on from where she is:
 * - at her place already, she stays there;
 * - on her way home, with as much time as that way takes or more (the end came later, or not at all), she goes on
 *   along it and then stays at her place for the rest of the stroll: she never turns round on her way home;
 * - on her way home, with less time (the end came sooner), she goes home from where she is, leaving as fast as
 *   she goes.
 * From [notBefore] the way home may be faster than [PACE]: an end learned 9 s ahead (her next note is known some
 * 13 s ahead, and she is home 4 s before it) has her walk up to [LATE_FASTEST] of her size a second, more than her
 * step covers; an end only a moment ahead as fast as the way left asks (15/8 of its pace at most, or as fast as she
 * went). Where the end is known before the stroll begins (a song known in full) neither does anything.
 *
 * Which round the stroll begins with goes by [from] (one of [StrollRoute.FIRST_ROUNDS]), so two strolls of a show
 * begin differently. A stroll's start does not change once it has begun (see [planTrumpet]).
 */
class StrollHome(
    private val route: StrollRoute,
    val from: Double,
    val to: Double,
    notBefore: Double = Double.NEGATIVE_INFINITY,
    before: StrollHome? = null,
) {
    /**
     * A stretch of her way home: from [start] (seconds into the stroll) along [leg] for [time], leaving at [speed],
     * and then at her place; a null [leg]: at her place from [start] on.
     */
    internal class Way(val leg: StrollLeg?, val start: Double, val time: Double, val speed: Double) {
        /** How far along [leg] she is [x] seconds into the stroll: eased to a stand, leaving at [speed]. */
        fun along(x: Double): Double {
            val walked = leg ?: return 0.0
            val u = min(1.0, max(0.0, (x - start) / time))

            // the quintic ease, and its one with a start speed (0 at both ends, its slope 1 at the start)
            return walked.length * smoother(u) + speed * time * (u - 6 * u * u * u + 8 * u * u * u * u - 3 * u * u * u * u * u)
        }

        /** Where she is [x] seconds into the stroll. */
        fun at(x: Double): Offset = leg?.at(along(x)) ?: StrollRoute.HOME

        /** When she is at her place, seconds into the stroll. */
        val home: Double get() = if (leg == null) start else start + time
    }

    /** Where the route's time stands as the stroll begins: the start of its first round. */
    internal val shift: Double = route.startOf(
        min(StrollRoute.FIRST_ROUNDS - 1, floor(hash01(from * 0.7317 + 11.3) * StrollRoute.FIRST_ROUNDS).toInt())
    )

    /** Her place, the way she faces at rest. */
    private val homePose = Pose(x = 0.0, z = 0.0, heading = route.rest)

    /**
     * Her ways home, by when they begin: one of her own, and where she had turned home already in the stroll this
     * one replaces ([before]), the ones it had in its last [KEEP] seconds (older ones are never asked for: for
     * them she is on her route, so a line of replaced strolls is never kept).
     */
    internal val ways: List<Way>

    /** When she turns home, seconds into the stroll (see [StrollHome]). */
    internal val turnBack: Double get() = ways.first().start

    /** Her last way home where she walks one; null where she is at her place by then, and stays. */
    internal val way: Way? get() = ways.last().takeIf { it.leg != null }

    /**
     * When she left her route, seconds into the stroll: as she turned home the first time, also where this stroll
     * replaces one in which she had turned home already (whose ways may since be dropped, see [keep]). The stare at
     * the camera is let go from there ([stareAt]). Taken over, not read from the ways kept, it guards the case of a
     * [KEEP] shorter than the stare's fall ([STARE_FALL]): as long as [KEEP] is the longer, a way that is dropped
     * began so long ago that the stare had been let go by then, and the difference cannot be seen.
     */
    internal val leftRoute: Double

    init {
        val end = max(0.0, to - from)
        val least = max(0.0, notBefore - from)
        val kept = keep(ways = before?.ways?.filter { it.start <= least } ?: emptyList(), least = least)
        val turned = kept.lastOrNull()

        ways = when {
            // she followed her route at [least]: she turns home from it, in time
            turned == null -> listOf(fromRoute(end = end, least = least))
            // at her place already, or on her way home with as much time as that way takes or more: she goes on as she
            // went, and stays at her place for the rest of the stroll (she never turns round on her way home)
            turned.leg == null || least >= turned.home || end >= turned.home -> kept
            // on her way home, and her end came sooner: she goes home from where she is, as fast as she goes
            else -> kept + fromWay(ways = kept, end = end, least = least)
        }

        leftRoute = if (turned != null && before != null) before.leftRoute else ways.first().start
    }

    /**
     * How far she stares at the camera at [t] (seconds into the show), 0 to 1: in a round to the camera, while the
     * route has her walk at it and stand before it (see [StrollRoute.stareAround]), rising over [STARE_RISE] seconds
     * and let go over [STARE_FALL] as she turns away from it, as her timed looks fall. A stroll that leaves the route
     * before (its way home) lets go as it leaves it. It goes by the time, the route and when she left it alone, so a
     * replaced stroll stares as the one it replaces did.
     */
    fun stareAt(t: Double): Double {
        val x = t - from
        val window = route.stareAround(x + shift) ?: return 0.0
        val start = window.start - shift
        val end = min(window.endInclusive - shift, leftRoute)

        if (x <= start || start >= leftRoute) {
            return 0.0
        }

        return smooth((x - start) / STARE_RISE) * (1 - smooth((x - end) / STARE_FALL))
    }

    /**
     * The kind of walk's modifier on her hips at [t] (seconds into the show; see [WalkKind]): her route's, as its legs
     * have it (see [StrollRoute.hipsAt]), and from when she left it the way home's ([WalkKind.Home]), eased in over
     * [StrollRoute.KIND_BLEND] seconds, as she may leave it walking. It goes by the time, the route and when she left
     * it alone, so a replaced stroll walks as the one it replaces did.
     */
    fun hipsAt(t: Double): Double {
        val x = t - from
        val onRoute = route.hipsAt(x + shift)

        if (x <= leftRoute) {
            return onRoute
        }

        val home = smooth((x - leftRoute) / StrollRoute.KIND_BLEND)

        return onRoute + (WalkKind.Home.hips - onRoute) * home
    }

    /** Where she is at [t] (seconds into the show), off her place, in her sizes. */
    fun at(t: Double): Offset = placeAt(ways = ways, x = t - from)

    /** How far along her way home she is at [t] (seconds into the show); 0 before it and where she stands. */
    internal fun along(t: Double): Double = ways.lastOrNull { it.start <= t - from }?.along(t - from) ?: 0.0

    /** Where she is [x] seconds into the stroll, by [ways], and before them on her route. */
    private fun placeAt(ways: List<Way>, x: Double): Offset {
        val now = ways.lastOrNull { it.start <= x } ?: return onRoute(x)

        return now.at(x)
    }

    /**
     * Her way home from her route: the latest moment, on a grid of [GRID] seconds back from the stroll's [end] (seconds
     * into it) and no sooner than [least], from which the way home fits in the time left at [PACE] and is not so
     * slow that she could not leave on it as fast as she goes ([LEAVE]); where none fits, from [least].
     */
    private fun fromRoute(end: Double, least: Double): Way {
        var turn = least
        var k = floor(end / GRID).toInt()

        while (k >= 0 && k * GRID >= least) {
            val x = k * GRID
            val (at, speed) = poseOf(x = x) { onRoute(it) }
            val left = end - x

            if (hypot(at.x, at.z) < 1e-9) {
                turn = x
                break
            }

            val length = bendsLength(from = at, to = homePose, radius = radiusFor(speed))

            if (length <= PACE * left && speed * left <= LEAVE * length) {
                turn = x
                break
            }

            k--
        }

        turn = min(turn, end)

        val (start, speed) = poseOf(x = turn) { onRoute(it) }

        return wayFrom(start = start, speed = speed, at = turn, time = end - turn)
    }

    /** Her way home from where her [ways] have her at [least], as she goes there, to be home at [end]. */
    private fun fromWay(ways: List<Way>, end: Double, least: Double): Way {
        val (start, speed) = poseOf(x = least) { placeAt(ways = ways, x = it) }

        return wayFrom(start = start, speed = speed, at = least, time = end - least)
    }

    /** The way home from [start] at [speed], [at] seconds into the stroll, walked in [time]; standing where she is home. */
    private fun wayFrom(start: Pose, speed: Double, at: Double, time: Double): Way {
        if (hypot(start.x, start.z) < 1e-9 || time <= 0.0) {
            return Way(leg = null, start = at, time = 0.0, speed = 0.0)
        }

        val bends = bendsAndStraight(from = start, to = homePose, radius = radiusFor(speed), sway = 0.0, spacing = StrollRoute.SPACING)
        val leg = StrollLeg(listOf(start) + bends)

        // she leaves as fast as she goes, up to [LEAVE] times the way's average pace: up to there the ease with a
        // start speed only ever goes forward (it would stop and go back beyond 2.5)
        return Way(leg = leg, start = at, time = time, speed = min(speed, LEAVE * leg.length / time))
    }

    /** Of [ways] (by when they begin), the one she is on [KEEP] seconds before [least] and those after it. */
    private fun keep(ways: List<Way>, least: Double): List<Way> {
        val first = ways.indexOfLast { it.start <= least - KEEP }

        return if (first <= 0) ways else ways.subList(first, ways.size).toList()
    }

    /** Where the route has her [x] seconds into the stroll. */
    private fun onRoute(x: Double): Offset = if (x <= 0.0) StrollRoute.HOME else route.at(x + shift)

    /** Where [place] has her [x] seconds into the stroll, the way she goes there, and how fast. */
    private fun poseOf(x: Double, place: (Double) -> Offset): Pair<Pose, Double> {
        val at = place(x)
        val ahead = place(x + LOOK)
        val behind = place(x - LOOK)
        val dx = (ahead.x - behind.x) / (2 * LOOK)
        val dz = (ahead.z - behind.z) / (2 * LOOK)
        val speed = hypot(dx, dz)
        val heading = if (speed > 1e-6) atan2(dz, dx) else route.rest

        return Pose(x = at.x, z = at.z, heading = heading) to speed
    }

    /** The bends of a way home that leaves at [speed]: no faster a turn than [TURN_RATE], and never tighter than a round's. */
    private fun radiusFor(speed: Double): Double = max(StrollRoute.RADIUS, max(speed, EASE_PEAK * PACE) / TURN_RATE)

    companion object {
        /** The calm pace of the way home, in her sizes a second, on average: her legs walk 0.12 to 0.15. */
        internal const val PACE = 0.15

        /** The quintic ease's steepest slope: 15/8 of its average. */
        internal const val EASE_PEAK = 15.0 / 8.0

        /** The fastest turn on the way home, radians a second (about 115 degrees). */
        internal const val TURN_RATE = 2.0

        /** How much faster than the way home's average pace she may leave on it (see [at]). */
        internal const val LEAVE = 2.4

        /**
         * The fastest she walks home after an end learned 9 s ahead, her sizes a second: 1.40 measured (StrollHomeSpec),
         * some 3.3 times what her step covers at 120 bpm.
         */
        internal const val LATE_FASTEST = 1.45

        /** The stare at the camera rises over this many seconds (see [stareAt]). */
        internal const val STARE_RISE = 0.8

        /** The stare at the camera is let go over this many seconds, as her timed looks fall (`SIRENE`). */
        internal const val STARE_FALL = 1.8

        /** How long the ways home of a replaced stroll are kept, seconds back from when it was replaced. */
        internal const val KEEP = 2.0

        /** The moments she may turn home at, this many seconds apart. */
        internal const val GRID = 0.1

        /** How far round a moment the way she goes is taken, in seconds. */
        private const val LOOK = 0.01
    }
}
