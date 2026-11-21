/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.viz.liveact.band.instruments.bass.Bass
import io.peekandpoke.klang.viz.liveact.band.instruments.guitar.Guitar

/** Every model the band has, by name. */
private val DESIGNS = listOf(Guitar.A, Guitar.B, Guitar.C, Bass.A, Bass.B)

/** Whether [p] lies inside the closed edge [edge] (crossings of a ray to the right). */
private fun inside(edge: List<Pair<Double, Double>>, p: Pair<Double, Double>): Boolean {
    var inside = false

    for ((a, b) in (edge + edge.first()).zipWithNext()) {
        if ((a.second > p.second) != (b.second > p.second)) {
            val x = a.first + (p.second - a.second) / (b.second - a.second) * (b.first - a.first)

            if (p.first < x) {
                inside = !inside
            }
        }
    }

    return inside
}

/** The models' outlines and what lies on them: a check for the future, for whoever draws a new model. */
class StringDesignSpec : StringSpec({

    "the edge of an outline goes through every point, so many points to a stretch" {
        val outline = BodyOutline(listOf(0.0 to 0.0, 1.0 to 1.0, 2.0 to 0.0, 3.0 to 1.0))
        val edge = outline.contour(steps = 3)

        edge.size shouldBe 10
        edge[0] shouldBe (0.0 to 0.0)
        edge[3].first shouldBe (1.0 plusOrMinus 1e-12)
        edge[3].second shouldBe (1.0 plusOrMinus 1e-12)
        edge[6].first shouldBe (2.0 plusOrMinus 1e-12)
        edge[9].first shouldBe (3.0 plusOrMinus 1e-12)
        edge[9].second shouldBe (1.0 plusOrMinus 1e-12)
    }

    "at a corner the edge turns sharply: the stretches on both sides of it are each a curve of their own" {
        // through four points, with a corner at the third: a curve through the first three, a straight line on
        val points = listOf(0.0 to 0.0, 1.0 to 0.5, 2.0 to 0.0, 3.0 to 1.0)
        val elsewhere = points.dropLast(1) + (3.0 to -1.0)
        val sharp = BodyOutline(points, corners = setOf(2.0 to 0.0)).contour(steps = 4)

        sharp.size shouldBe 10
        sharp[8].first shouldBe (2.0 plusOrMinus 1e-12)
        sharp[8].second shouldBe (0.0 plusOrMinus 1e-12)
        sharp[9] shouldBe (3.0 to 1.0)
        // the curve up to the corner does not know where the edge goes after it; without the corner it does
        BodyOutline(elsewhere, corners = setOf(2.0 to 0.0)).contour(steps = 4).take(9) shouldBe sharp.take(9)
        BodyOutline(elsewhere).contour(steps = 4)[7] shouldNotBe BodyOutline(points).contour(steps = 4)[7]
    }

    "every model has its own name, the letter after its kind" {
        DESIGNS.map { it.name } shouldBe listOf("E-Guitar A", "E-Guitar B", "E-Guitar C", "Bass A", "Bass B")
    }

    "every model's pickups, bridge and knobs lie on its body, and its quake's trace across it" {
        for (design in DESIGNS) {
            val body = design.outline.contour(steps = 3)
            val bridge = -0.10
            val onBody = design.pickups.map { it to 0.0 } + design.knobs + listOf(bridge to 0.0) +
                design.pickups.flatMap { x -> listOf(x to design.neck * 0.625, x to -design.neck * 0.625) }
            val trace = design.ornaments.quake?.let { q ->
                listOf(q.from to q.y + q.bow, q.to to q.y + q.bow, q.at to q.y + q.height, q.at to q.y - q.height)
            }.orEmpty()

            for (p in onBody + trace) {
                withClue("${design.name}: $p") {
                    inside(body, p) shouldBe true
                }
            }
        }
    }

    "every model's tuners stand along its head's lower edge, within the head's length" {
        for (design in DESIGNS) {
            val head = design.head.contour(steps = 3)

            for (i in 0 until design.strings) {
                val along = 0.03 + 0.14 * (i + 0.7) / (design.strings + 0.6)

                withClue("${design.name}: tuner $i") {
                    inside(head, along to -0.02) shouldBe true
                }
            }
        }
    }
})
