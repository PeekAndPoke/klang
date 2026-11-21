/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.stage.GhostLight

class GhostVisitsSpec : StringSpec({

    /** The visits of a show over [seconds], as (from, to) in whole seconds. */
    fun visits(seed: Int, seconds: Int): List<Pair<Int, Int>> {
        val found = mutableListOf<Pair<Int, Int>>()
        var from: Int? = null

        for (s in 0..seconds) {
            val on = GhostVisits.visiting(t = s.toDouble(), seed = seed)

            if (on && from == null) {
                from = s
            }

            if (!on && from != null) {
                found.add(from to s)
                from = null
            }
        }

        return found
    }

    "none before the song, none in its first minute, none at no time" {
        for (seed in 1..20) {
            GhostVisits.visiting(t = -5.0, seed = seed) shouldBe false
            GhostVisits.visiting(t = 59.0, seed = seed) shouldBe false
            GhostVisits.visiting(t = Double.NaN, seed = seed) shouldBe false
        }
    }

    "they come, but not often: a visit stays its time, and well apart from the next" {
        for (seed in listOf(1, 7, 42)) {
            val found = visits(seed = seed, seconds = 1800)

            found.size shouldBeGreaterThan 5

            for ((from, to) in found) {
                (to - from).toDouble() shouldBeGreaterThan GhostVisits.HOLD - 2
                (to - from).toDouble() shouldBeLessThan GhostVisits.HOLD + 2
            }

            for ((a, b) in found.zipWithNext()) {
                (b.first - a.second).toDouble() shouldBeGreaterThan GhostVisits.TURN_SECONDS
            }

            // not too often: the ghosts are there less than a quarter of the show
            found.sumOf { it.second - it.first }.toDouble() shouldBeLessThan 0.25 * 1800
        }
    }

    "each visit takes the next of the three colours, and the colour stays until the next visit begins" {
        for (seed in listOf(1, 7, 42)) {
            val found = visits(seed = seed, seconds = 1800)
            val colours = found.map { GhostVisits.light(t = it.first + 1.0, seed = seed) }

            colours.take(3) shouldContainAll GhostLight.entries

            for ((i, visit) in found.withIndex()) {
                // through the visit, and after it while the stage turns back
                GhostVisits.light(t = visit.second - 1.0, seed = seed) shouldBe colours[i]
                GhostVisits.light(t = visit.second + GhostVisits.TURN_SECONDS, seed = seed) shouldBe colours[i]
            }

            // before the first visit: the colour it will have
            GhostVisits.light(t = 0.0, seed = seed) shouldBe colours.first()
        }
    }
})
