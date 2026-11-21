/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

// What a strung instrument says of itself: its name, its shape, its strings and tuning, how it is played and how it
// hangs, and its ornaments. The guitars and the basses each give theirs (see Guitar, Bass); the cast says who plays
// which (see `StringSeat`).

/**
 * An outline, seen from the front with the neck to the right (x along the neck, y across, in the figure's measure),
 * as [points] a curve goes through: a body's from the neck pocket's lower corner round the body to its upper corner,
 * a head's from the nut round the head and back. The curve is smooth through every point except the [corners] (each
 * one of the [points]): there it turns sharply, to the tip of a horn or a blade. It is closed by a straight line from
 * the last point back to the first (under the neck, where a body's is). Each instrument has its own (none is a
 * maker's model).
 */
class BodyOutline(val points: List<Pair<Double, Double>>, val corners: Set<Pair<Double, Double>> = emptySet()) {
    init {
        require(points.size >= 3) { "An outline needs three points at least" }
        require(points.containsAll(corners)) { "A corner must be one of the outline's points: ${corners - points.toSet()}" }
    }

    /**
     * The outline as the points of its edge, from the first point to the last: [steps] points on each stretch between
     * two of its [points] (a smooth curve through them, as three.js's `SplineCurve` draws one, broken at each corner).
     */
    fun contour(steps: Int): List<Pair<Double, Double>> {
        val ends = listOf(0) + points.indices.filter { it in 1 until points.size - 1 && points[it] in corners } + (points.size - 1)
        val out = mutableListOf(points[0])

        for ((from, to) in ends.zipWithNext()) {
            val run = points.subList(from, to + 1)

            if (run.size == 2) {
                out.add(run[1])

                continue
            }

            val divisions = steps * (run.size - 1)

            for (d in 1..divisions) {
                out.add(splineAt(run = run, t = d.toDouble() / divisions))
            }
        }

        return out
    }

    private companion object {
        /** The point at [t] (0 to 1) along a smooth curve through [run]: uniform Catmull-Rom, as three.js's `SplineCurve`. */
        fun splineAt(run: List<Pair<Double, Double>>, t: Double): Pair<Double, Double> {
            val last = run.size - 1
            val p = last * t
            val i = minOf(p.toInt(), last - 1)
            val w = p - i
            val p0 = run[maxOf(i - 1, 0)]
            val p1 = run[i]
            val p2 = run[i + 1]
            val p3 = run[minOf(i + 2, last)]

            return Pair(
                catmullRom(w = w, a = p0.first, b = p1.first, c = p2.first, d = p3.first),
                catmullRom(w = w, a = p0.second, b = p1.second, c = p2.second, d = p3.second),
            )
        }

        fun catmullRom(w: Double, a: Double, b: Double, c: Double, d: Double): Double {
            val v0 = (c - a) * 0.5
            val v1 = (d - b) * 0.5
            val w2 = w * w

            return (2 * b - 2 * c + v0 + v1) * w2 * w + (-3 * b + 3 * c - 2 * v0 - v1) * w2 + v0 * w + b
        }
    }
}

/**
 * A strung instrument and how it is played. [name]: its model's name (`E-Guitar A`, `Bass B`); [scale]: from the nut
 * to the bridge; [bodyHalfLength] and [bodyHalfWidth]: the body's half length and half width; [strings]: how many;
 * [neck]: the neck's width; [tilt]: the neck's tilt; how the right hand plays: [swing] how far it goes through the
 * strings, [tau] how quickly (in seconds), [arm] the share of the swing the arm does (the rest is the wrist); [hang]:
 * how low it hangs from the trunk; [outline]: the body's outline; [pickups]: where the pickups sit along it; [knobs]:
 * where the two knobs sit on the body; [head]: the head's outline, from the nut; [ornaments]: its gold (none unless
 * given); [tuning]: its strings' notes, by which the left hand finds each note's string and fret.
 */
class StringDesign(
    val name: String,
    val scale: Double,
    val bodyHalfLength: Double,
    val bodyHalfWidth: Double,
    val strings: Int,
    val neck: Double,
    val tilt: Double,
    val swing: Double,
    val tau: Double,
    val hang: Double,
    val arm: Double,
    val outline: BodyOutline,
    val pickups: List<Double>,
    val knobs: List<Pair<Double, Double>>,
    val head: BodyOutline,
    val tuning: Tuning,
    val ornaments: Ornaments = Ornaments.NONE,
)
