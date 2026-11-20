/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A tail like Rune's of 2026-11-20 (`klangviz/figures/rune.py`, as the rig loads it), in metres: from the head's
 * middle over the back of the head and down the back, and the tip below the last joint.
 */
private val REST = doubleArrayOf(
    0.0, 1.6117, 0.0243,
    0.0, 1.5583, -0.1261,
    0.0, 1.3837, -0.1455,
    0.0, 1.2091, -0.1552,
    0.0, 1.0774, -0.1740,
)
private val THICK = doubleArrayOf(0.04, 0.041, 0.031, 0.02, 0.015)
private const val HEAD_R = 0.102
private val HEAD = doubleArrayOf(0.0, 1.6117, 0.0243, HEAD_R)

/** The body as capsules: the back, the neck, the left shoulder; two ends and a radius each. */
private val BODY = doubleArrayOf(
    0.0, 1.086, 0.0, 0.0, 1.358, 0.01, 0.097,
    0.0, 1.358, 0.01, 0.0, 1.505, 0.01, 0.074,
    0.0, 1.358, 0.01, 0.17, 1.40, 0.01, 0.077,
)
private const val CAPSULES = 3

/** Where the neck turns the head. */
private const val PIVOT_Y = 1.505
private const val PIVOT_Z = 0.0097

private fun tail(): HangingTail = HangingTail(TailShape(points = REST, thick = THICK, head = HEAD, body = BODY))

/**
 * The hold of a head turned [yaw] about the up axis and tilted [nod] forward (both radians, about the neck), and
 * moved [dx] to the side; the body stays where it is.
 */
private fun hold(yaw: Double = 0.0, nod: Double = 0.0, dx: Double = 0.0): TailHold {
    val h = TailHold(CAPSULES)
    // the turn about the up axis after the tilt about the axis across: q = yaw * nod
    val cy = cos(yaw / 2)
    val sy = sin(yaw / 2)
    val cn = cos(nod / 2)
    val sn = sin(nod / 2)
    val qx = cy * sn
    val qy = sy * cn
    val qz = -sy * sn
    val qw = cy * cn

    h.turn[0] = qx
    h.turn[1] = qy
    h.turn[2] = qz
    h.turn[3] = qw

    val r = turned(qx = qx, qy = qy, qz = qz, qw = qw, x = REST[0], y = REST[1] - PIVOT_Y, z = REST[2] - PIVOT_Z)

    h.pin[0] = r[0] + dx
    h.pin[1] = r[1] + PIVOT_Y
    h.pin[2] = r[2] + PIVOT_Z

    for (c in 0 until CAPSULES) {
        for (k in 0 until 6) {
            h.body[6 * c + k] = BODY[7 * c + k]
        }
    }

    return h
}

/** (x, y, z) turned by the rotation (qx, qy, qz, qw). */
private fun turned(qx: Double, qy: Double, qz: Double, qw: Double, x: Double, y: Double, z: Double): DoubleArray {
    val tx = 2 * (qy * z - qz * y)
    val ty = 2 * (qz * x - qx * z)
    val tz = 2 * (qx * y - qy * x)

    return doubleArrayOf(
        x + qw * tx + (qy * tz - qz * ty),
        y + qw * ty + (qz * tx - qx * tz),
        z + qw * tz + (qx * ty - qy * tx),
    )
}

/** Where the shape has the point [i] on [h]. */
private fun shapeAt(h: TailHold, i: Int): DoubleArray {
    val r = turned(
        qx = h.turn[0], qy = h.turn[1], qz = h.turn[2], qw = h.turn[3],
        x = REST[3 * i] - REST[0], y = REST[3 * i + 1] - REST[1], z = REST[3 * i + 2] - REST[2],
    )

    return doubleArrayOf(r[0] + h.pin[0], r[1] + h.pin[1], r[2] + h.pin[2])
}

private fun smooth(u: Double): Double {
    val k = u.coerceIn(0.0, 1.0)

    return k * k * (3 - 2 * k)
}

/** The head moves 0.2 m to the side over 0.2 s from 0.5 s on. */
private fun sideways(t: Double): TailHold = hold(dx = 0.2 * smooth((t - 0.5) / 0.2))

/** The head turns 0.7 rad over 0.35 s from 0.5 s on, and nods a little while it does. */
private fun headTurn(t: Double): TailHold {
    val u = smooth((t - 0.5) / 0.35)

    return hold(yaw = 0.7 * u, nod = 0.15 * sin(PI * u))
}

/** Runs [tail] from [from] to [to] seconds at [fps], held by [at]; [each] is told every frame. */
private fun run(
    tail: HangingTail,
    fps: Double,
    from: Double = 0.0,
    to: Double,
    at: (Double) -> TailHold,
    each: (t: Double, h: TailHold) -> Unit = { _, _ -> },
) {
    val frames = ((to - from) * fps + 0.5).toInt()

    for (f in 0..frames) {
        val t = from + f / fps
        val h = at(t)

        tail.follow(t, h)
        each(t, h)
    }
}

private fun distance(tail: HangingTail, i: Int, x: Double, y: Double, z: Double): Double {
    val dx = tail.x(i) - x
    val dy = tail.y(i) - y
    val dz = tail.z(i) - z

    return sqrt(dx * dx + dy * dy + dz * dz)
}

private fun toStick(tail: HangingTail, i: Int, s: DoubleArray, o: Int): Double =
    toSegment(
        px = tail.x(i), py = tail.y(i), pz = tail.z(i),
        ax = s[o], ay = s[o + 1], az = s[o + 2],
        bx = s[o + 3], by = s[o + 4], bz = s[o + 5],
        out = DoubleArray(3),
    )

class HangingTailSpec : StringSpec({

    "held still, it stays at rest, in its own shape" {
        val tail = tail()

        run(tail = tail, fps = 60.0, to = 3.0, at = { hold() }) { t, _ ->
            for (i in 0 until tail.count) {
                withClue("the point $i at $t s") {
                    distance(tail, i, REST[3 * i], REST[3 * i + 1], REST[3 * i + 2]) shouldBe (0.0 plusOrMinus 1e-9)
                }
            }
        }
    }

    "the head moved to the side: the tip lags, swings past the new place once and settles there" {
        val tail = tail()
        val tip = tail.count - 1
        var lagAtStop = 0.0
        var past = 0.0
        var back = 0.0
        var settled = 0.0

        run(tail = tail, fps = 60.0, to = 4.0, at = ::sideways) { t, h ->
            // how far the tip is beyond where the shape has it, the way the head went
            val beyond = tail.x(tip) - shapeAt(h, tip)[0]

            if (abs(t - 0.7) < 1e-9) {
                lagAtStop = -beyond
            }

            if (t in 0.7..1.6) {
                past = max(past, beyond)
            }

            if (t in 1.0..2.5) {
                back = min(back, beyond)
            }

            if (t >= 3.0) {
                settled = max(settled, abs(beyond))
            }
        }

        withClue("behind when the head stops") { lagAtStop shouldBeGreaterThan 0.02 }
        withClue("past the new place") { past shouldBeGreaterThan 0.005 }
        withClue("the swing back is smaller than the swing past") { abs(back) shouldBeLessThan past }
        withClue("still, three seconds on") { settled shouldBeLessThan 0.002 }
    }

    "the distances between the points hold" {
        val tail = tail()

        run(tail = tail, fps = 60.0, to = 3.0, at = ::headTurn) { t, _ ->
            for (i in 1 until tail.count) {
                val rest = sqrt(
                    (REST[3 * i] - REST[3 * i - 3]).let { it * it } +
                        (REST[3 * i + 1] - REST[3 * i - 2]).let { it * it } +
                        (REST[3 * i + 2] - REST[3 * i - 1]).let { it * it },
                )
                val now = distance(tail, i, tail.x(i - 1), tail.y(i - 1), tail.z(i - 1))

                withClue("from the point ${i - 1} to $i at $t s") {
                    now shouldBe (rest plusOrMinus 0.02 * rest)
                }
            }
        }
    }

    "30, 60, 120 and 144 frames a second end in the same place" {
        val ends = listOf(30.0, 60.0, 120.0, 144.0).map { fps ->
            val tail = tail()

            // ending mid-swing, where the frame rate would show most, on a frame of every rate (5/6 s)
            run(tail = tail, fps = fps, to = 5.0 / 6, at = ::headTurn)

            (0 until tail.count).map { i -> doubleArrayOf(tail.x(i), tail.y(i), tail.z(i)) }
        }

        for ((index, end) in ends.withIndex()) {
            for (i in end.indices) {
                withClue("the point $i, at the frame rate number $index against 120") {
                    val other = ends[2][i]
                    val d = sqrt((0 until 3).sumOf { k -> (end[i][k] - other[k]) * (end[i][k] - other[k]) })

                    d shouldBeLessThan 0.0005
                }
            }
        }
    }

    "at 144 frames a second, more frames than steps, it does not stutter: every frame moves it on" {
        val tail = tail()
        val tip = tail.count - 1
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var went = -1.0

        run(tail = tail, fps = 144.0, to = 0.8, at = ::headTurn) { t, _ ->
            val way = distance(tail, tip, x, y, z)

            // while the head turns, each frame goes on about as far as the one before: none stands still
            if (t > 0.6 && went > 0) {
                withClue("the tip's way in the frame at $t s, against the frame before") {
                    way shouldBeGreaterThan 0.5 * went
                }
            }

            went = way
            x = tail.x(tip)
            y = tail.y(tip)
            z = tail.z(tip)
        }
    }

    "a time that stands still holds it still, and the tail goes on from there as if nothing had been asked" {
        val tail = tail()
        val untold = tail()

        run(tail = tail, fps = 60.0, to = 0.75, at = ::headTurn)
        run(tail = untold, fps = 60.0, to = 0.75, at = ::headTurn)

        val now = (0 until tail.count).map { i -> doubleArrayOf(tail.x(i), tail.y(i), tail.z(i)) }

        // the same moment again, even told of another hold
        tail.follow(0.75, headTurn(2.0))

        for (i in 0 until tail.count) {
            withClue("the point $i") {
                distance(tail, i, now[i][0], now[i][1], now[i][2]) shouldBe (0.0 plusOrMinus 1e-12)
            }
        }

        // on from there: the same as the tail that was never asked
        run(tail = tail, fps = 60.0, from = 0.75 + 1 / 60.0, to = 1.2, at = ::headTurn)
        run(tail = untold, fps = 60.0, from = 0.75 + 1 / 60.0, to = 1.2, at = ::headTurn)

        for (i in 0 until tail.count) {
            withClue("the point $i, half a second on") {
                distance(tail, i, untold.x(i), untold.y(i), untold.z(i)) shouldBe (0.0 plusOrMinus 1e-12)
            }
        }
    }

    "a moment that is no number leaves it where it is, and it goes on from there" {
        val tail = tail()
        val untold = tail()

        run(tail = tail, fps = 60.0, to = 0.75, at = ::headTurn)
        run(tail = untold, fps = 60.0, to = 0.75, at = ::headTurn)

        tail.follow(Double.NaN, headTurn(2.0))

        run(tail = tail, fps = 60.0, from = 0.75 + 1 / 60.0, to = 1.2, at = ::headTurn)
        run(tail = untold, fps = 60.0, from = 0.75 + 1 / 60.0, to = 1.2, at = ::headTurn)

        for (i in 0 until tail.count) {
            withClue("the point $i, after a moment that was no number") {
                distance(tail, i, untold.x(i), untold.y(i), untold.z(i)) shouldBe (0.0 plusOrMinus 1e-12)
            }
        }
    }

    "a jump in time, ahead or back, puts it at rest where it is then: never flung" {
        for (jumpTo in listOf(5.0, 0.2)) {
            val tail = tail()

            // in mid-swing, then the jump: the head is turned and moved at once
            run(tail = tail, fps = 60.0, to = 0.8, at = ::headTurn)

            val there = hold(yaw = -0.5, dx = 0.3)

            tail.follow(jumpTo, there)

            val after = (0 until tail.count).map { i -> doubleArrayOf(tail.x(i), tail.y(i), tail.z(i)) }

            for (i in 0 until tail.count) {
                withClue("jumped to $jumpTo s: the point $i near its place in the shape") {
                    val s = shapeAt(there, i)

                    distance(tail, i, s[0], s[1], s[2]) shouldBeLessThan 0.01
                }
            }

            // a frame later it has hardly moved: no speed was brought along
            tail.follow(jumpTo + 1 / 60.0, there)

            for (i in 0 until tail.count) {
                withClue("jumped to $jumpTo s: the point $i a frame later") {
                    distance(tail, i, after[i][0], after[i][1], after[i][2]) shouldBeLessThan 0.0005
                }
            }
        }
    }

    "a frame more than a quarter second late is a jump; one just under it is not" {
        val late = tail()
        val onTime = tail()

        for (tail in listOf(late, onTime)) {
            run(tail = tail, fps = 60.0, to = 0.6, at = ::sideways)
        }

        // the head is moving: the tail late by a moment is put to rest, the other one swings on
        late.follow(0.6 + HangingTail.JUMP + 0.01, sideways(0.6 + HangingTail.JUMP + 0.01))
        onTime.follow(0.6 + HangingTail.JUMP - 0.01, sideways(0.6 + HangingTail.JUMP - 0.01))

        val tip = late.count - 1

        /** How far the tip of [tail] goes in the next tenth of a second, from [from] on. */
        fun goes(tail: HangingTail, from: Double): Double {
            var way = 0.0

            for (f in 1..6) {
                val x = tail.x(tip)

                tail.follow(from + f / 60.0, sideways(from + f / 60.0))
                way += abs(tail.x(tip) - x)
            }

            return way
        }

        goes(late, 0.6 + HangingTail.JUMP + 0.01) shouldBeLessThan 0.001
        goes(onTime, 0.6 + HangingTail.JUMP - 0.01) shouldBeGreaterThan 0.005
    }

    "no point ever ends inside the head, the neck, the back or the shoulder" {
        val tail = tail()

        // wild: the head turns far to the left and to the right, nods, and the head moves about
        val wild = { t: Double ->
            hold(
                yaw = 1.25 * sin(2 * PI * 0.7 * t),
                nod = -0.5 + 0.9 * (0.5 + 0.5 * sin(2 * PI * 1.1 * t)),
                dx = 0.05 * sin(2 * PI * 1.7 * t),
            )
        }

        run(tail = tail, fps = 60.0, to = 6.0, at = wild) { t, h ->
            val head = shapeAt(h, 0)

            for (i in 1 until tail.count) {
                withClue("the point $i at $t s, from the head's middle") {
                    distance(tail, i, head[0], head[1], head[2]) shouldBeGreaterThanOrEqual HEAD_R
                }

                for (c in 0 until CAPSULES) {
                    withClue("the point $i at $t s, from the capsule $c") {
                        toStick(tail, i, h.body, 6 * c) shouldBeGreaterThanOrEqual BODY[7 * c + 6]
                    }
                }
            }
        }
    }

    "the swing is never wider than the feel allows" {
        val tail = tail()
        val feel = TailFeel.PLAIT

        // the whole figure is thrown to the side, far and fast: the body goes with the head
        val thrown = { t: Double ->
            val dx = 0.6 * smooth((t - 0.5) / 0.1)

            hold(dx = dx).also { h ->
                for (k in 0 until 2 * CAPSULES) {
                    h.body[3 * k] += dx
                }
            }
        }

        run(tail = tail, fps = 60.0, to = 3.0, at = thrown) { t, h ->
            var along = 0.0

            for (i in 1 until tail.count) {
                along += sqrt((0 until 3).sumOf { k -> (REST[3 * i + k] - REST[3 * i - 3 + k]).let { it * it } })

                // the tail is shown a step late: at 60 frames a second half way from the frame before, where
                // what it hangs on went evenly
                val now = shapeAt(h, i)
                val before = shapeAt(thrown(t - 1 / 60.0), i)
                val s = DoubleArray(3) { k -> (now[k] + before[k]) / 2 }

                withClue("the point $i at $t s") {
                    // only keeping out of the body may move a point after its swing is limited: a hair more is allowed
                    distance(tail, i, s[0], s[1], s[2]) shouldBeLessThan feel.swing * along * 1.01
                }
            }
        }
    }
})
