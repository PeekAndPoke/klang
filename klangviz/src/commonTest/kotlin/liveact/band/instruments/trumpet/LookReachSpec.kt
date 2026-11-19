/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.viz.liveact.inSight
import io.peekandpoke.klang.viz.liveact.overShoulder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** A frame, in seconds. */
private const val FRAME = 1.0 / 60

private fun wrap(a: Double): Double = atan2(sin(a), cos(a))

/**
 * The share of a look whose place turns round her at [rate] radians a second from [start] (a body turning away
 * from it on a stroll), every frame from 0 to [until] seconds: (the time, the place's yaw, the share).
 */
private fun sweep(start: Double, rate: Double, until: Double, reach: (Double) -> Double): List<Triple<Double, Double, Double>> {
    fun yawAt(t: Double): Double = wrap(start + rate * t)

    return (0..(until / FRAME).toInt()).map { i ->
        val t = i * FRAME
        val grid = floor(t / 0.1) * 0.1
        val share = lookReach(
            t = t,
            yaw = yawAt(t),
            reach = reach,
            yawBefore = { k -> yawAt(grid - k * 0.1) },
            fall = 1.6,
            step = 0.1,
        )

        Triple(t, yawAt(t), share)
    }
}

class LookReachSpec : StringSpec({

    val kinds = listOf("over the shoulder" to ::overShoulder, "in sight" to ::inSight)

    "a look that she turns away from, across behind her, never jumps from one frame to the next" {
        // the share follows a reach that changes at most 3.75 a radian (in sight: 1.5 / 0.4, over the shoulder: 1.5 / 0.5),
        // under a ceiling that does too, and lets go at most 1.5 / 1.6 a second: so a frame's step stays below
        // (4 rate + 1) / 60 at a turn of [rate] radians a second
        for ((kind, reach) in kinds) {
            for (rate in listOf(0.5, 1.0, 2.1)) {
                val frames = sweep(start = 1.0, rate = rate, until = (2 * PI - 1.0) / rate, reach = { reach(it) })

                for (i in 1 until frames.size) {
                    withClue("$kind, turning $rate rad/s, at ${frames[i].first} s (yaw ${frames[i].second})") {
                        abs(frames[i].third - frames[i - 1].third) shouldBeLessThan (4 * rate + 1) * FRAME
                    }
                }
            }
        }
    }

    "straight behind her nothing is held: the head lets go there, whichever shoulder it went over" {
        for ((kind, reach) in kinds) {
            for (rate in listOf(0.5, 1.0, 2.1)) {
                for ((t, yaw, share) in sweep(start = 1.0, rate = rate, until = (2 * PI - 1.0) / rate, reach = { reach(it) })) {
                    if (abs(yaw) > PI - 0.05) {
                        withClue("$kind, turning $rate rad/s, at $t s (yaw $yaw)") {
                            share shouldBeLessThan 0.05
                        }
                    }
                }
            }
        }
    }

    "a look in sight that she turns away from is let go slowly: half a second after it began to leave, most of it is held" {
        // turning at 1 rad/s from straight ahead: it begins to leave sight (some 70 degrees) after 1.22 s and is out
        // of it (some 95 degrees) after 1.66 s; half a second after the first, plain sight would be nothing
        val frames = sweep(start = 0.0, rate = 1.0, until = 3.0, reach = { inSight(it) })
        val (t, yaw, share) = frames.first { it.first >= 1.22 + 0.5 }

        withClue("at $t s (yaw $yaw): plain sight would be ${inSight(yaw)}") {
            share shouldBeGreaterThan 0.5
        }
    }
})
