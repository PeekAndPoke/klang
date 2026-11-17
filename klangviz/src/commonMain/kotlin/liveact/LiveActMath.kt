/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// The few numeric helpers the whole live act shares.

/** 0 below 0, 1 above 1, and a soft S between. */
fun smooth(x: Double): Double {
    val c = min(1.0, max(0.0, x))

    return c * c * (3 - 2 * c)
}

/** As [smooth], and softer still where it starts and ends: no jolt in the pace, for what a viewer rides along with. */
fun smoother(x: Double): Double {
    val c = min(1.0, max(0.0, x))

    return c * c * c * (c * (6 * c - 15) + 10)
}

/**
 * How far someone can turn their head to what stands at [angle] off straight ahead of them (radians, either side):
 * all the way (1) up to some 70 degrees, not at all (0) from some 95 degrees on, where it is behind them.
 */
fun inSight(angle: Double): Double = smooth((cos(angle) + 0.1) / 0.4)

/**
 * How far someone looks over their shoulder at one who stands at [angle] off straight ahead of them (radians,
 * either side): all the way (1) but for the last half radian, and not at all (0) at one straight behind them. There
 * the nearer shoulder changes sides, so a look let go of in between never jumps from one shoulder to the other.
 */
fun overShoulder(angle: Double): Double = smooth((PI - abs(angle)) / 0.5)

/**
 * How far a head takes a look at what stands at [angle] off straight ahead (radians, either side), 0 to 1: a look
 * that may go [behind] (at another member) goes [overShoulder], any other only as far as the place is [inSight].
 */
fun headReach(angle: Double, behind: Boolean): Double = if (behind) overShoulder(angle) else inSight(angle)

/**
 * How far [x] lies beyond [easy] on either side of 0, with its sign: 0 well within, and from [soft] before the edge
 * to [soft] past it a round bend into the straight line, so nothing starts with a jolt. [soft] 0: a sharp corner.
 */
fun beyond(x: Double, easy: Double, soft: Double): Double {
    val over = abs(x) - easy
    val far = when {
        over <= -soft -> 0.0
        over >= soft -> over
        else -> (over + soft) * (over + soft) / (4 * soft)
    }

    return sign(x) * far
}

/** A number from 0 to 1 that belongs to [arg], always the same one: the chance behind every seeded choice. */
fun hash01(arg: Double): Double {
    val x = sin(arg) * 43758.5453

    return x - floor(x)
}

/** Smooth noise along [x], 0 to 1: the value [at] gives at every whole number, eased between them. */
fun valueNoise(x: Double, at: (Int) -> Double): Double {
    val n = floor(x)
    val u = x - n
    val i = n.toInt()

    return at(i) + (at(i + 1) - at(i)) * (u * u * (3 - 2 * u))
}

/** Something that happens at a moment: [t] in seconds. */
interface Timed {
    val t: Double
}

/** The one before and the one after [t] in a list sorted by time; null where there is none. */
fun <T : Timed> List<T>.around(t: Double): Pair<T?, T?> {
    var lo = 0
    var hi = size

    while (lo < hi) {
        val mid = (lo + hi) shr 1

        if (this[mid].t <= t) {
            lo = mid + 1
        } else {
            hi = mid
        }
    }

    return getOrNull(lo - 1) to getOrNull(lo)
}

/** The index of the last one at or before [t] in a list sorted by time; -1 where there is none. */
fun <T : Timed> List<T>.lastIndexAt(t: Double): Int {
    var lo = 0
    var hi = size

    while (lo < hi) {
        val mid = (lo + hi) shr 1

        if (this[mid].t <= t) {
            lo = mid + 1
        } else {
            hi = mid
        }
    }

    return lo - 1
}
