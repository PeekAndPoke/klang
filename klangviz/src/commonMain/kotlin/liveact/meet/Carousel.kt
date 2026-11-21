/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The turn of the carousel in "Meet the Band" ([MeetLayout]), in radians about the vertical, as time goes: it starts
 * at [start] and stands there until a turn is chosen ([choose]); then it goes there, eased at both ends, the shortest
 * way round, in [pace] seconds for one step of the hexagon (more steps take longer, but not as many times as long).
 *
 * A choice made while it turns is taken up from where it is and as fast as it goes then: the way to the new turn
 * starts with the speed it had (a curve with the place and the speed given at both ends: there it stands still), so
 * nothing jumps, neither the place nor the speed. The turn is not wrapped: after many turns one way it is many rounds
 * from where it started, and every choice is made near it.
 *
 * The times are in seconds of whatever clock the caller keeps, and must not go back.
 */
class Carousel(start: Double, private val pace: Double = PACE) {

    init {
        require(pace > 0.0) { "A turn takes some time: $pace" }
    }

    /** Where the turn under way started, how fast it went then, and where it goes (radians, radians a second). */
    private var from = start
    private var speed = 0.0
    private var to = start

    /** When the turn under way began and how long it takes, in seconds (0: none under way). */
    private var began = 0.0
    private var takes = 0.0

    /** Where the carousel goes, or stands: the last choice, near where it was when chosen. */
    val target: Double
        get() = to

    /**
     * Turns the carousel toward [turn] (radians; any of its rounds: it goes to the nearest) from the time [now] on.
     * The same turn as the one it goes to already changes nothing. Halfway round, either way is as short: it keeps
     * the way it is turning, and from standing it turns to smaller angles (the next member comes from the right).
     */
    fun choose(turn: Double, now: Double) {
        if (abs(wrap(turn - to)) < SAME) {
            return
        }

        val here = at(now)
        val going = speedAt(now)
        var way = wrap(turn - here)

        // halfway round: the way it goes already, else to smaller angles
        if (abs(abs(way) - PI) < SAME) {
            way = if (going > 0.0) PI else -PI
        }

        from = here
        speed = going
        to = here + way
        began = now
        takes = max(pace * sqrt(abs(way) / MeetLayout.STEP), pace * SHORTEST)
    }

    /** Where the carousel is at [now], in radians. */
    fun at(now: Double): Double {
        val s = progress(now) ?: return to
        val s2 = s * s
        val s3 = s2 * s

        // the curve from the place and the speed at the start to the target, standing still there
        return (2 * s3 - 3 * s2 + 1) * from + (s3 - 2 * s2 + s) * takes * speed + (-2 * s3 + 3 * s2) * to
    }

    /** How fast the carousel turns at [now], in radians a second. */
    fun speedAt(now: Double): Double {
        val s = progress(now) ?: return 0.0
        val s2 = s * s

        return ((6 * s2 - 6 * s) * from + (3 * s2 - 4 * s + 1) * takes * speed + (-6 * s2 + 6 * s) * to) / takes
    }

    /** Whether a turn is under way at [now]. */
    fun turning(now: Double): Boolean = progress(now) != null

    /** How far the turn under way has come at [now], 0 to 1; null where none is. */
    private fun progress(now: Double): Double? {
        if (takes <= 0.0 || now >= began + takes) {
            return null
        }

        return max(0.0, (now - began) / takes)
    }

    companion object {
        /** One step of the hexagon takes this many seconds, by default: calm. */
        const val PACE: Double = 1.4

        /** The shortest a turn takes, as a share of [PACE]: a small correction is not a jerk. */
        const val SHORTEST: Double = 0.6

        /** Two turns closer than this (radians) are the same. */
        private const val SAME: Double = 1e-9

        /** [angle] brought within one half round either way: from -pi to just below pi. */
        internal fun wrap(angle: Double): Double {
            val round = 2 * PI

            return angle - round * floor((angle + PI) / round)
        }
    }
}
