/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The arc behind the crowd (KLANG-00489; the maintainer, 2026-11-26: "another angle that arcs around the back of the
 * crowd pointing at the stage. Different starting and end positions each time"): the camera goes slowly along the
 * audience's half ring, high over their heads, looking at the stage. It is cut to and cut away from, never flown
 * ("Cut to, no flight").
 *
 * Where (the maintainer, the same day): "a bit lower and closer than the FOH angle and a bit more of the wall behind
 * the stage visible (in height)". The mixing desk ([STADIUM]) stands 46 m back and 11 m up and looks down on the
 * ground before the stage; the arc runs [FAR] m from the stage's middle at about [HIGH] m and looks at the stage's
 * middle a little above its floor ([AT]), so the picture tilts up and shows more of the wall behind.
 */
object CrowdArc {
    /** How far from the stage's middle the arc runs, in metres: the crowd stands from 11.5 m to 62 m (`CrowdPlaces`). */
    const val FAR = 38.0

    /** How high the camera goes, in metres, and how far a round's start or end may lie above or below that. */
    const val HIGH = 8.0
    const val HIGH_SWAY = 0.8

    /** Where it looks: the stage's middle, this high. */
    val AT: Triple<Double, Double, Double> = Triple(0.0, 1.4, 0.0)

    /** Its lens, a little wider than the mixing desk's (27). */
    const val FOV = 28.0

    /** How far round the half ring it may go to either side, in radians from straight before the stage (60°). */
    const val WIDEST = PI / 3

    /** How far it goes along the ring in a round, at least and at most, in radians (20° to 40°). */
    const val LEAST = PI / 9
    const val MOST = 2 * PI / 9

    /** The bars of a round in which it is shown: cut to at the first, cut away at the second. */
    const val FROM = 30
    const val TO = 44

    /**
     * Where the arc of a round goes: from [start] to [end] (radians from straight before the stage, + to the stage's
     * right as the audience sees it, that is toward +x), at the heights [startHigh] and [endHigh].
     */
    data class Way(val start: Double, val end: Double, val startHigh: Double, val endHigh: Double)

    /**
     * The way of a round from three numbers from 0 to 1 its seed gives: [side] where it begins, [length] how far it
     * goes, [high] how high it begins and ends. It goes toward the middle from where it begins, so it never leaves
     * the ring ([WIDEST]). NaN-guard: a number of no number is 0.5.
     */
    fun way(side: Double, length: Double, high: Double): Way {
        val s = share(side)
        val l = share(length)
        val h = share(high)
        val start = WIDEST * (2 * s - 1)
        val sweep = LEAST + (MOST - LEAST) * l
        val end = if (start > 0) start - sweep else start + sweep

        return Way(
            start = start,
            end = end,
            startHigh = HIGH + HIGH_SWAY * (2 * h - 1),
            endHigh = HIGH - HIGH_SWAY * (2 * h - 1),
        )
    }

    /** The camera [u] of the way along [way] (0 to 1, clamped; no number: its start), in the world. */
    fun at(way: Way, u: Double): Placement {
        // NaN-guard: no number is the start
        val x = if (u != u) 0.0 else u.coerceIn(0.0, 1.0)
        // a little slower at both ends than in the middle, never still: it is cut to and from while moving
        val e = x - 0.15 * sin(2 * PI * x) / (2 * PI)
        val angle = way.start + (way.end - way.start) * e
        val high = way.startHigh + (way.endHigh - way.startHigh) * e

        return Placement(pos = Triple(FAR * sin(angle), high, FAR * cos(angle)), at = AT, fov = FOV)
    }

    /** [x] as a share, 0 to 1. NaN-guard: what is no number is the middle. */
    private fun share(x: Double): Double = if (x != x) 0.5 else x.coerceIn(0.0, 1.0)
}
