/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

private const val EPS = 1e-9

/** How far the camera of [p] looks down, in radians: more is steeper, less of the wall behind the stage. */
private fun down(p: Placement): Double =
    atan2(p.pos.second - p.at.second, hypot(p.pos.first - p.at.first, p.pos.third - p.at.third))

/** How far [p] stands from the stage's middle, along the ground. */
private fun off(p: Placement): Double = hypot(p.pos.first, p.pos.third)

class CrowdArcSpec : StringSpec({

    val grid = (0..10).map { it / 10.0 }

    "a round's way stays on the half ring, goes either way, and as far as it should" {
        for (side in grid) {
            for (length in grid) {
                val way = CrowdArc.way(side = side, length = length, high = 0.5)
                val sweep = abs(way.end - way.start)

                withClue("side $side, length $length: $way") {
                    (abs(way.start) <= CrowdArc.WIDEST + EPS) shouldBe true
                    (abs(way.end) <= CrowdArc.WIDEST + EPS) shouldBe true
                    (sweep >= CrowdArc.LEAST - EPS && sweep <= CrowdArc.MOST + EPS) shouldBe true
                }
            }
        }

        withClue("from the left end to the right, and from the right end to the left") {
            val right = CrowdArc.way(side = 0.0, length = 1.0, high = 0.5)
            val left = CrowdArc.way(side = 0.5, length = 1.0, high = 0.5)

            (abs(right.start + CrowdArc.WIDEST) < EPS && right.end > right.start) shouldBe true
            (abs(left.start - CrowdArc.WIDEST) < EPS && left.end < left.start) shouldBe true
        }

        withClue("both ways reach the far end of the ring, and begin at many places, not only at its ends") {
            (abs(CrowdArc.way(side = 1.0, length = 0.5, high = 0.5).end + CrowdArc.WIDEST) < EPS) shouldBe true
            (CrowdArc.way(side = 0.49, length = 0.5, high = 0.5).end > CrowdArc.WIDEST - 0.1) shouldBe true
            (grid.filter { it < 0.5 }.map { CrowdArc.way(side = it, length = 0.5, high = 0.5).start }.toSet().size > 2) shouldBe true
            (grid.filter { it >= 0.5 }.map { CrowdArc.way(side = it, length = 0.5, high = 0.5).start }.toSet().size > 2) shouldBe true
        }

        withClue("different numbers, different ways") {
            (CrowdArc.way(side = 0.1, length = 0.3, high = 0.2) != CrowdArc.way(side = 0.8, length = 0.6, high = 0.9)) shouldBe true
        }
    }

    "lower and closer than the mixing desk, looking at the stage with more of the wall behind it" {
        for (side in grid) {
            for (high in grid) {
                val way = CrowdArc.way(side = side, length = 0.5, high = high)

                for (u in grid) {
                    val p = CrowdArc.at(way = way, u = u)

                    withClue("side $side, high $high, at $u: $p") {
                        (p.pos.second < STADIUM.pos.second) shouldBe true
                        (off(p) < off(STADIUM)) shouldBe true
                        (down(p) < down(STADIUM)) shouldBe true
                        p.at shouldBe CrowdArc.AT
                        // over the stage's middle, a little above its floor
                        (p.at.first == 0.0 && p.at.third == 0.0 && p.at.second > 0.5 && p.at.second < 2.5) shouldBe true
                        (abs(off(p) - CrowdArc.FAR) < EPS) shouldBe true
                        (abs(p.pos.second - CrowdArc.HIGH) <= CrowdArc.HIGH_SWAY + EPS) shouldBe true
                    }
                }
            }
        }
    }

    "behind the open ground before the stage, and well over the heads of the crowd there" {
        (CrowdArc.FAR > CrowdPlaces.FRONT) shouldBe true

        // the highest light held up near the arc, and some 0.2 m more as the arms move it (the crowd's shader)
        val lights = CrowdPlaces.all.filter { abs(hypot(it.x, it.z) - CrowdArc.FAR) < 2.0 }.maxOf { it.y } + 0.2

        withClue("the highest light near the arc at $lights m") {
            (CrowdArc.HIGH - CrowdArc.HIGH_SWAY - lights > 2.0) shouldBe true
        }
    }

    "along the way: from its start to its end, always on, never back; outside it clamped, no number its start" {
        val way = CrowdArc.way(side = 0.9, length = 1.0, high = 0.0)

        CrowdArc.at(way = way, u = 0.0).pos.first shouldBe CrowdArc.at(way = way, u = -1.0).pos.first
        CrowdArc.at(way = way, u = Double.NaN) shouldBe CrowdArc.at(way = way, u = 0.0)
        (abs(CrowdArc.at(way = way, u = 1.0).pos.first - CrowdArc.at(way = way, u = 2.0).pos.first) < EPS) shouldBe true

        val start = CrowdArc.at(way = way, u = 0.0)
        val end = CrowdArc.at(way = way, u = 1.0)

        (abs(atan2(start.pos.first, start.pos.third) - way.start) < 1e-6) shouldBe true
        (abs(atan2(end.pos.first, end.pos.third) - way.end) < 1e-6) shouldBe true
        (abs(start.pos.second - way.startHigh) < EPS) shouldBe true
        (abs(end.pos.second - way.endHigh) < EPS) shouldBe true

        // it began at the right and goes left: x falls all the way
        val xs = (0..100).map { CrowdArc.at(way = way, u = it / 100.0).pos.first }

        xs.zipWithNext().all { (a, b) -> b < a } shouldBe true
    }

    "it is shown after the round's early looks and within the round" {
        (CrowdArc.FROM >= opens(4) + 3) shouldBe true
        (CrowdArc.TO <= ROUND && CrowdArc.FROM < CrowdArc.TO) shouldBe true
    }

    "a way from numbers of no number is the middle's" {
        CrowdArc.way(side = Double.NaN, length = Double.NaN, high = Double.NaN) shouldBe
            CrowdArc.way(side = 0.5, length = 0.5, high = 0.5)
    }
})
