/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import kotlin.math.sqrt

// How a hanging tail (HangingTail) measures the body it hangs on: from the figure's own skin, so a reshaped figure
// is measured anew.

/**
 * The point of the stick from (ax, ay, az) to (bx, by, bz) nearest to (px, py, pz), into [out] (three numbers);
 * returns how far the point is from it.
 */
internal fun toSegment(
    px: Double, py: Double, pz: Double,
    ax: Double, ay: Double, az: Double,
    bx: Double, by: Double, bz: Double,
    out: DoubleArray,
): Double {
    val abx = bx - ax
    val aby = by - ay
    val abz = bz - az
    val ab2 = abx * abx + aby * aby + abz * abz
    val u = if (ab2 > 0) ((px - ax) * abx + (py - ay) * aby + (pz - az) * abz) / ab2 else 0.0
    val k = u.coerceIn(0.0, 1.0)

    out[0] = ax + abx * k
    out[1] = ay + aby * k
    out[2] = az + abz * k

    val dx = px - out[0]
    val dy = py - out[1]
    val dz = pz - out[2]

    return sqrt(dx * dx + dy * dy + dz * dz)
}

/**
 * For each of the [points] (three numbers to a point), the stick of [sticks] (six numbers to a stick: its two ends)
 * it is nearest to, by the stick's index; the first of them where two are as near.
 */
fun nearestSticks(points: DoubleArray, sticks: DoubleArray): IntArray {
    val count = sticks.size / 6
    val out = DoubleArray(3)

    return IntArray(points.size / 3) { i ->
        var best = -1
        var bestD = Double.POSITIVE_INFINITY

        for (s in 0 until count) {
            val o = 6 * s
            val d = toSegment(
                px = points[3 * i], py = points[3 * i + 1], pz = points[3 * i + 2],
                ax = sticks[o], ay = sticks[o + 1], az = sticks[o + 2],
                bx = sticks[o + 3], by = sticks[o + 4], bz = sticks[o + 5],
                out = out,
            )

            if (d < bestD) {
                bestD = d
                best = s
            }
        }

        best
    }
}

/**
 * How far the [points] (three numbers to a point) reach round the stick from (ax, ay, az) to (bx, by, bz): the
 * distance from it that the share [share] (0 to 1) of them stay within. 0 where there are no points.
 */
fun reachRound(
    points: DoubleArray,
    ax: Double, ay: Double, az: Double,
    bx: Double, by: Double, bz: Double,
    share: Double,
): Double {
    val out = DoubleArray(3)
    val d = DoubleArray(points.size / 3) { i ->
        toSegment(
            px = points[3 * i], py = points[3 * i + 1], pz = points[3 * i + 2],
            ax = ax, ay = ay, az = az, bx = bx, by = by, bz = bz,
            out = out,
        )
    }

    if (d.isEmpty()) {
        return 0.0
    }

    d.sort()

    return d[((d.size - 1) * share.coerceIn(0.0, 1.0)).toInt()]
}
