/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private val GAZE = CrowdGaze(
    timing = SkillTiming(
        every = 9.0, chance = 0.75, least = 4.0, most = 6.0, earliest = 0.5, reserve = 7.5, rise = 0.9, fall = 1.0,
    ),
    spotLeast = 1.5,
    spotMost = 2.5,
    move = 0.8,
)

/** One spot held: from when the head leaves for it ([from]) to when it leaves for the next ([until]). */
private class Held(val spot: Int, val came: Int, val from: Double, val until: Double)

/** The spots one after another from [since] to [until] seconds, looked at every hundredth of a second. */
private fun spots(gaze: CrowdGaze, roll: (Double) -> Double, count: Int, since: Double, until: Double): List<Held> {
    val out = mutableListOf<Held>()
    var now: SpotMove? = null
    var from = since

    for (i in (since * 100).toInt()..(until * 100).toInt()) {
        val t = i / 100.0
        val move = gaze.spotAt(t = t, roll = roll, spots = count)
        val before = now

        // a new spot: another one, or the same one again (the move to it begins anew)
        if (before != null && (move.to != before.to || move.share < before.share)) {
            out.add(Held(spot = before.to, came = before.from, from = from, until = t))
            from = t
        }

        now = move
    }

    return out.drop(1)
}

class CrowdGazeSpec : StringSpec({

    val show = Show(seed = 0)
    val roll = { i: Double -> show.own(index = i, salt = 67.0) }

    "a spot is held from its least to its most, and the head moves on from where it rested" {
        val held = spots(gaze = GAZE, roll = roll, count = 18, since = -20.0, until = 900.0)

        withClue("how long a spot is held") {
            // (looked at every hundredth: a hundredth either way)
            held.minOf { it.until - it.from } shouldBeGreaterThanOrEqual 1.5 - 0.011
            held.maxOf { it.until - it.from } shouldBeLessThanOrEqual 2.5 + 0.011
            held.map { it.until - it.from }.average() shouldBe (2.0 plusOrMinus 0.05)
        }

        withClue("the head leaves from the spot it rested on") {
            held.zipWithNext().count { (a, b) -> b.came != a.spot } shouldBe 0
        }

        held.size shouldBeGreaterThan 400
    }

    "on the way the head goes over eased, from the spot before to the next, and rests once there" {
        // where a new spot begins, the head is still at the one before, and it is there the move's time later
        var changes = 0

        for (i in -2000..90000) {
            val t = i / 100.0
            val now = GAZE.spotAt(t = t, roll = roll, spots = 18)
            val next = GAZE.spotAt(t = t + 0.01, roll = roll, spots = 18)

            if (next.to != now.to) {
                changes++

                withClue("leaving at $t s") {
                    now.share shouldBe 1.0
                    next.from shouldBe now.to
                    next.share shouldBeLessThan 0.01
                }

                // the move: the share eased from 0 to 1 over 0.8 s, by the time since it began (between t and a
                // hundredth later), toward the same spot all the way
                val along = (1..82).map { k -> GAZE.spotAt(t = t + k * 0.01, roll = roll, spots = 18) }
                val shares = along.map { it.share }

                withClue("the move after $t s") {
                    along.map { it.to }.toSet() shouldBe setOf(next.to)
                    shares shouldBe shares.sorted()
                    shares[39] shouldBe (smooth(0.395 / 0.8) plusOrMinus 0.02)
                    shares[76] shouldBeLessThan 1.0
                    shares[81] shouldBe 1.0
                }
            }
        }

        changes shouldBeGreaterThan 400
    }

    "the spots are drawn from all of them, and the head nearly always moves on to another" {
        val held = spots(gaze = GAZE, roll = roll, count = 18, since = 0.0, until = 4000.0)
        val used = held.groupingBy { it.spot }.eachCount()

        used.keys shouldBe (0 until 18).toSet()
        used.values.min() shouldBeGreaterThan held.size / 18 / 2
        used.values.max() shouldBeLessThan held.size / 18 * 2

        // the same spot twice in a row only where three drew the same one: seldom
        withClue("the same spot again") {
            held.count { it.spot == it.came } shouldBeLessThan held.size / 50
        }
    }

    "the same moment always gives the same spot, whatever was asked before" {
        val times = (0 until 5000).map { it * 0.173 - 30.0 }
        val forward = times.map { GAZE.spotAt(t = it, roll = roll, spots = 18) }
        val backward = times.reversed().map { GAZE.spotAt(t = it, roll = roll, spots = 18) }.reversed()

        backward shouldBe forward
    }

    "the spots of the crowd: eighteen real lights, from the audience's left to its right, near to far" {
        val lights = CrowdPlaces.all
        val spots = crowdSpots(lights)

        spots.size shouldBe 18

        for (spot in spots) {
            (spot in lights) shouldBe true
        }

        for ((i, spot) in spots.withIndex()) {
            val degrees = listOf(-60.0, -36.0, -12.0, 12.0, 36.0, 60.0)[i / 3]
            val off = listOf(17.0, 30.0, 48.0)[i % 3]

            withClue("spot $i") {
                atan2(spot.x, spot.z) * 180 / PI shouldBe (degrees plusOrMinus 2.5)
                sqrt(spot.x * spot.x + spot.z * spot.z) shouldBe (off plusOrMinus 0.8)
            }
        }

        // the far rows stand higher (the ground climbs)
        spots[2].y shouldBeGreaterThan spots[0].y + 3.0
        abs(spots[0].y - spots[15].y) shouldBeLessThan 1.0

        crowdSpots(emptyList()) shouldBe emptyList()
    }
})
