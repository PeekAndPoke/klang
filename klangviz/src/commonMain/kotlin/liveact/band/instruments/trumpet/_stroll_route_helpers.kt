/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollLeg.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// How the trumpet player gets from one pose to the next on her stroll (see StrollRoute): the shortest way that
// starts and ends in the given directions and bends no tighter than a given radius, made of a bend, a straight and
// a bend (the shortest such ways, after L. E. Dubins, 1957). Plain geometry, so it has a spec through the route.

/** A full turn. */
private const val TURN = 2 * PI

/** [a] in 0 until a full turn. */
private fun once(a: Double): Double = a - TURN * floor(a / TURN)

/**
 * A way of a bend, a straight and a bend: the first bend round [c0] by [arc0] radians ([s0] 1 to the left, -1 to the
 * right), the straight [straight] long in the direction [heading], the last bend round [c1] by [arc1] ([s1]).
 */
private class Bends(
    val s0: Double,
    val arc0: Double,
    val c0x: Double,
    val c0z: Double,
    val straight: Double,
    val heading: Double,
    val s1: Double,
    val arc1: Double,
    val c1x: Double,
    val c1z: Double,
    val radius: Double,
) {
    val length: Double get() = radius * (arc0 + arc1) + straight
}

/** The shortest way of a bend, a straight and a bend from [from] to [to], no bend tighter than [radius]. */
private fun shortestBends(from: Pose, to: Pose, radius: Double): Bends {
    var best: Bends? = null

    for (s0 in listOf(1.0, -1.0)) {
        for (s1 in listOf(1.0, -1.0)) {
            // the middle of a bend lies to the left of the way (-sin, cos) for a bend to the left, to the right else
            val c0x = from.x - s0 * radius * sin(from.heading)
            val c0z = from.z + s0 * radius * cos(from.heading)
            val c1x = to.x - s1 * radius * sin(to.heading)
            val c1z = to.z + s1 * radius * cos(to.heading)
            val apart = hypot(c1x - c0x, c1z - c0z)
            val toward = atan2(c1z - c0z, c1x - c0x)
            val heading: Double
            val straight: Double

            if (s0 == s1) {
                // two bends the same way: the straight runs parallel to the line between their middles
                heading = if (apart > 1e-9) toward else to.heading
                straight = apart
            } else {
                // two bends the opposite way: the straight crosses between them, which needs room for both
                if (apart < 2 * radius) {
                    continue
                }

                straight = sqrt(apart * apart - 4 * radius * radius)
                heading = toward + s0 * atan2(2 * radius, straight)
            }

            var arc0 = once(s0 * (heading - from.heading))
            var arc1 = once(s1 * (to.heading - heading))

            // (a bend of a whole turn is none: rounding can leave one where the poses already line up)
            if (arc0 > TURN - 1e-9) {
                arc0 = 0.0
            }

            if (arc1 > TURN - 1e-9) {
                arc1 = 0.0
            }

            val bends = Bends(
                s0 = s0, arc0 = arc0, c0x = c0x, c0z = c0z, straight = straight, heading = heading,
                s1 = s1, arc1 = arc1, c1x = c1x, c1z = c1z, radius = radius,
            )

            if (best == null || bends.length < best.length - 1e-12) {
                best = bends
            }
        }
    }

    // two bends the same way always fit
    return best!!
}

/** How long the shortest way from [from] to [to] is that bends no tighter than [radius] (see [bendsAndStraight]). */
internal fun bendsLength(from: Pose, to: Pose, radius: Double): Double = shortestBends(from = from, to = to, radius = radius).length

/**
 * The poses along the shortest way from [from] to [to] that bends no tighter than [radius] (see [shortestBends]),
 * after [from], up to and with [to]: on a bend every half radius at most, on the straight every [spacing]. The
 * straight swings to and fro in a gentle S of up to [sway] to the side (its sign: which side first), flat where
 * it meets the bends, and the gentler the shorter the straight, so the S never bends tighter than the bends do.
 */
internal fun bendsAndStraight(from: Pose, to: Pose, radius: Double, sway: Double, spacing: Double): List<Pose> {
    val way = shortestBends(from = from, to = to, radius = radius)
    val out = mutableListOf<Pose>()
    val step = min(spacing, 0.5 * radius)

    fun bend(cx: Double, cz: Double, s: Double, start: Double, arc: Double) {
        val n = ceil(radius * arc / step - 1e-9).toInt()

        for (k in 1..n) {
            val a = start + s * arc * k / n

            out.add(Pose(x = cx + s * radius * sin(a), z = cz - s * radius * cos(a), heading = a))
        }
    }

    bend(cx = way.c0x, cz = way.c0z, s = way.s0, start = from.heading, arc = way.arc0)

    val qx = way.c0x + way.s0 * radius * sin(way.heading)
    val qz = way.c0z - way.s0 * radius * cos(way.heading)
    val long = way.straight
    val n = ceil(long / spacing - 1e-9).toInt()
    val amplitude = if (long > 0.0) min(abs(sway), SWAY_CURVE * long * long) * sign(sway) else 0.0

    for (k in 1..n) {
        val f = k.toDouble() / n
        // sin(2 pi f) * sin(pi f): an S whose ends are flat
        val off = amplitude * sin(TURN * f) * sin(PI * f)
        val slope = amplitude / long * (TURN * cos(TURN * f) * sin(PI * f) + PI * sin(TURN * f) * cos(PI * f))

        out.add(
            Pose(
                x = qx + cos(way.heading) * long * f - sin(way.heading) * off,
                z = qz + sin(way.heading) * long * f + cos(way.heading) * off,
                heading = way.heading + atan(slope),
            )
        )
    }

    bend(cx = way.c1x, cz = way.c1z, s = way.s1, start = way.heading, arc = way.arc1)

    // the last one is the pose asked for, exactly; a pose that falls on the one before it is left out
    if (out.isEmpty()) {
        out.add(to)
    } else {
        out[out.size - 1] = to
    }

    val clean = mutableListOf<Pose>()
    var before = from

    for ((i, pose) in out.withIndex()) {
        if (hypot(pose.x - before.x, pose.z - before.z) > 1e-4 || i == out.size - 1) {
            clean.add(pose)
            before = pose
        }
    }

    return clean
}

/** How much a straight of length l may swing to the side in its S, at most: this many times l squared. */
private const val SWAY_CURVE = 0.02
