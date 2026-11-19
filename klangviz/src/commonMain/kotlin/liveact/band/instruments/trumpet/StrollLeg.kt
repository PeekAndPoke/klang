/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One leg of the trumpet player's stroll (see [StrollRoute]): a stretch of walking from one standstill to the next,
 * along a smooth way through her [poses], the first and the last where she stands. In her sizes, off her place.
 *
 * The way is a cubic Hermite spline: from one pose to the next, leaving the one and reaching the other along its
 * heading, each tangent as long as the chord between the two. So the way goes through every pose in the direction
 * the pose gives, and bends no more than the poses do.
 *
 * She goes along it by distance, not by the spline's parameter: a table of [length] (every segment in [SAMPLES]
 * steps) gives the place at a distance from the start ([at]).
 */
internal class StrollLeg(val poses: List<Pose>) {

    /**
     * A place on the way, [x] across the stage and [z] toward the audience, and the way she goes there: [heading]
     * in radians, from across the stage (+x) toward the audience (+z), so she goes along (cos, sin) of it.
     */
    class Pose(val x: Double, val z: Double, val heading: Double)

    private val distances = DoubleArray((poses.size - 1) * SAMPLES + 1)

    /** How long the way is, along the spline. */
    val length: Double

    /** Where she starts, off her place. */
    val first: StrollRoute.Offset = StrollRoute.Offset(x = poses.first().x, z = poses.first().z)

    /** Where she ends, off her place. */
    val last: StrollRoute.Offset = StrollRoute.Offset(x = poses.last().x, z = poses.last().z)

    init {
        require(poses.size >= 2) { "a leg goes from one pose to another: ${poses.size} poses" }

        var total = 0.0
        var px = poses.first().x
        var pz = poses.first().z

        for (i in 0 until poses.size - 1) {
            for (k in 1..SAMPLES) {
                val u = k.toDouble() / SAMPLES

                total += hypot(xOf(i, u) - px, zOf(i, u) - pz)
                px = xOf(i, u)
                pz = zOf(i, u)
                distances[i * SAMPLES + k] = total
            }
        }

        length = total
    }

    /** Where she is [d] along the way (0 to [length]; before and after it, at the ends). */
    fun at(d: Double): StrollRoute.Offset {
        if (d <= 0.0) {
            return first
        }

        if (d >= length) {
            return last
        }

        var lo = 0
        var hi = distances.size - 1

        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1

            if (distances[mid] <= d) {
                lo = mid
            } else {
                hi = mid
            }
        }

        val span = distances[hi] - distances[lo]
        val w = if (span > 0.0) (d - distances[lo]) / span else 0.0
        // the step's parameter, eased linearly between the table's two samples, on the spline itself
        val segment = (hi - 1) / SAMPLES
        val from = (lo - segment * SAMPLES).toDouble() / SAMPLES
        val to = (hi - segment * SAMPLES).toDouble() / SAMPLES
        val u = from + (to - from) * w

        return StrollRoute.Offset(x = xOf(segment, u), z = zOf(segment, u))
    }

    /** The spline's x on segment [i] at [u] (0 to 1). */
    private fun xOf(i: Int, u: Double): Double {
        val a = poses[i]
        val b = poses[i + 1]
        val chord = hypot(b.x - a.x, b.z - a.z)

        return hermite(p0 = a.x, m0 = cos(a.heading) * chord, p1 = b.x, m1 = cos(b.heading) * chord, u = u)
    }

    /** The spline's z on segment [i] at [u] (0 to 1). */
    private fun zOf(i: Int, u: Double): Double {
        val a = poses[i]
        val b = poses[i + 1]
        val chord = hypot(b.x - a.x, b.z - a.z)

        return hermite(p0 = a.z, m0 = sin(a.heading) * chord, p1 = b.z, m1 = sin(b.heading) * chord, u = u)
    }

    private companion object {
        /** The steps of a segment in the table of the way's length. */
        const val SAMPLES = 16

        /** The cubic Hermite curve from [p0] (tangent [m0]) to [p1] (tangent [m1]) at [u], 0 to 1. */
        fun hermite(p0: Double, m0: Double, p1: Double, m1: Double, u: Double): Double {
            val u2 = u * u
            val u3 = u2 * u

            return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * m0 + (-2 * u3 + 3 * u2) * p1 + (u3 - u2) * m1
        }
    }
}
