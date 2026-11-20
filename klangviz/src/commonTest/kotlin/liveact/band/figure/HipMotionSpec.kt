/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A figure's size, and how far its hip joints stand from its middle, for the checks that need one. */
private const val S = 1.07
private const val HIP_HALF = 0.099

/** The phases of a stride the motion is looked at in. */
private val STRIDES = (0 until 200).map { it / 200.0 }

private fun motion(stride: Double, going: Double = 1.0, amount: Double = 1.0): HipMotion =
    HipMotion.at(stride = stride, going = going, amount = amount, s = S, hipHalf = HIP_HALF)

private fun HipMotion.all(): List<Double> = listOf(sway, lower, yaw, roll, waist, chestYaw, chestRoll)

/**
 * A leg of the figures' skeleton (`klangviz/figures/figure.py`), at the figure's height [h] and its hips' width
 * [hips]: in the figure's space (x to its left, y up, z ahead), where the hip joint is, where the ankle is, and
 * how long the leg is from the one to the other through the knee.
 */
private class Leg(h: Double, hips: Double) {
    val hipX = 0.092 * hips * h
    val hipY = 0.92 * h
    val ankleX = 0.105 * hips * h
    val ankleY = 0.085 * h
    val ankleZ = -0.015 * h
    val length = dist(hipX, hipY, 0.0, 0.100 * hips * h, 0.52 * h, 0.01 * h) +
        dist(0.100 * hips * h, 0.52 * h, 0.01 * h, ankleX, ankleY, ankleZ)

    /** Where the hips' bone turns: it starts 0.10 below the hips' point (0.98). */
    val pivotY = 0.88 * h

    /** The figure's size, as the rig measures it: where the hips' bone ends against 0.92. */
    val s = 0.98 * h / 0.92

    companion object {
        fun dist(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double): Double =
            sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az))
    }
}

/**
 * How much further than the leg reaches the standing foot is from its hip, the worst over a stride, in the
 * figure's space: the hips lowered by [down] and moved by the motion at [amount], the feet set as the legs step
 * them ([step] long, [footX] from the middle; see `stepLegs` and Sirene's stroll). The leg reaches as far as the
 * rig lets it (0.999 of its length).
 */
private fun worstShortfall(leg: Leg, amount: Double, down: Double, step: Double, footX: Double): Double {
    var worst = Double.NEGATIVE_INFINITY

    for (stride in STRIDES) {
        val m = HipMotion.at(stride = stride, going = 1.0, amount = amount, s = leg.s, hipHalf = leg.hipX)

        for (sign in listOf(1.0, -1.0)) {
            val phase = ((stride + (if (sign > 0) 0.0 else 0.5)) % 1.0 + 1) % 1.0

            if (phase >= 0.6) {
                continue
            }

            // the hip joint, turned with the hips about their pivot: first the turn about the up axis, then the tilt
            val x0 = sign * leg.hipX
            val y0 = leg.hipY - leg.pivotY
            val xYaw = x0 * cos(m.yaw)
            val zYaw = -x0 * sin(m.yaw)
            val x = xYaw * cos(m.roll) - y0 * sin(m.roll) + m.sway
            val y = xYaw * sin(m.roll) + y0 * cos(m.roll) + leg.pivotY - down - m.lower
            val footZ = leg.ankleZ + step * (0.5 - phase / 0.6)
            val far = Leg.dist(x, y, zYaw, sign * footX, leg.ankleY, footZ)

            worst = max(worst, far - 0.999 * leg.length)
        }
    }

    return worst
}

class HipMotionSpec : StringSpec({

    "standing, or with hips that do not move, nothing moves" {
        for (stride in STRIDES) {
            for (m in listOf(motion(stride = stride, going = 0.0), motion(stride = stride, amount = 0.0))) {
                withClue("at the stride's $stride") {
                    m.all().maxOf { abs(it) } shouldBe (0.0 plusOrMinus 1e-15)
                }
            }
        }
    }

    "at 1 the sway and the turns are those of Sirene's stroll before there were gaits; the chest's turn with her shoulders' swing" {
        // her stroll had: the hips 0.035 of her size sideways and turned 0.10, the chest turned -0.26 and tilted 0.05
        for (stride in STRIDES) {
            val m = motion(stride = stride)
            val onLeft = sin(2 * PI * stride)
            val swing = cos(2 * PI * stride)

            withClue("at the stride's $stride") {
                m.sway shouldBe (0.035 * S * onLeft plusOrMinus 1e-12)
                m.yaw shouldBe (0.10 * swing plusOrMinus 1e-12)
                (m.chestYaw - HipMotion.SHOULDERS * swing) shouldBe (-0.26 * swing plusOrMinus 1e-12)
                m.chestRoll shouldBe (0.05 * onLeft plusOrMinus 1e-12)
            }
        }
    }

    "the same to the right half a step later as to the left: mirrored, as high" {
        for (stride in STRIDES) {
            val left = motion(stride = stride, going = 0.8, amount = 1.3)
            val right = motion(stride = stride + 0.5, going = 0.8, amount = 1.3)

            withClue("at the stride's $stride and half a step later") {
                right.sway shouldBe (-left.sway plusOrMinus 1e-12)
                right.yaw shouldBe (-left.yaw plusOrMinus 1e-12)
                right.roll shouldBe (-left.roll plusOrMinus 1e-12)
                right.waist shouldBe (-left.waist plusOrMinus 1e-12)
                right.chestYaw shouldBe (-left.chestYaw plusOrMinus 1e-12)
                right.chestRoll shouldBe (-left.chestRoll plusOrMinus 1e-12)
                right.lower shouldBe (left.lower plusOrMinus 1e-12)
            }
        }
    }

    "on the left leg the hips go over it and the right hip drops; the belly and the chest take it back" {
        // the left leg stands from the stride's 0 to 0.6, alone from 0.1 to 0.5
        val m = motion(stride = 0.25)

        m.sway shouldBeGreaterThan 0.0
        m.roll shouldBe (0.06 plusOrMinus 1e-12)
        m.waist shouldBe (-m.roll plusOrMinus 1e-15)
        m.yaw shouldBe (0.0 plusOrMinus 1e-12)
        m.chestYaw shouldBe (-m.yaw plusOrMinus 1e-15)
        m.chestRoll shouldBeGreaterThan 0.0

        // the standing hip keeps its height: it rises by the half width times the tilt, and the hips come down by that
        val standing = HIP_HALF * sin(m.roll) - m.lower
        val swinging = -HIP_HALF * sin(m.roll) - m.lower

        withClue("the standing hip's height, against the swinging one's drop $swinging") {
            abs(standing) shouldBeLessThan 0.05 * abs(swinging)
        }

        withClue("the swinging leg's hip drops by about the whole width times the tilt") {
            swinging shouldBe (-2 * HIP_HALF * 0.06 plusOrMinus 1e-3)
        }
    }

    "the motion goes smoothly with the stride: no step between two frames larger than the stride's own share of it" {
        // a stride takes four beats: at 180 bpm, 0.0125 of it a frame. The hips' largest move: the sway, 2 pi times
        // its reach a stride
        val frame = 0.0125
        val amount = 1.4

        for (stride in STRIDES) {
            val a = motion(stride = stride, amount = amount).all()
            val b = motion(stride = stride + frame, amount = amount).all()
            val step = a.zip(b).maxOf { (x, y) -> abs(y - x) }

            withClue("from the stride's $stride a frame on") {
                step shouldBeLessThanOrEqual 2 * PI * frame * amount * max(0.035 * S, 0.10) * 1.001
            }
        }
    }

    "the feet still land where the legs step them: the hips ask the standing leg for no more reach than a walk without them" {
        // the smallest figure (Sirene), the largest (Beben) and the one with the widest hips (Klaue); the walk-in
        // (steps 0.42 of the size, the feet as they stand, the hips down 1.2 % of their height, 0.92 of the size) and
        // Sirene's stroll (steps up to 0.5, the feet near one line, 0.045 from the middle, the hips down 1 %), each
        // as much as the hips may move
        val figures = mapOf("Sirene" to Leg(h = 0.92, hips = 1.08), "Beben" to Leg(h = 1.10, hips = 0.88), "Klaue" to Leg(h = 0.94, hips = 1.2))

        for ((name, leg) in figures) {
            val walks = mapOf(
                "the walk-in" to Triple(0.012 * 0.92 * leg.s, 0.42 * leg.s, leg.ankleX),
                "the stroll" to Triple(0.010 * 0.92 * leg.s, 0.5 * leg.s, 0.045 * leg.s),
            )

            for ((walk, how) in walks) {
                val (down, step, footX) = how
                val without = worstShortfall(leg = leg, amount = 0.0, down = down, step = step, footX = footX)

                for (amount in listOf(0.1, 0.2, 0.45, 0.7, 1.0, 1.15, 1.4)) {
                    withClue("$name on $walk, the hips moving $amount: the worst shortfall, against $without without") {
                        worstShortfall(leg = leg, amount = amount, down = down, step = step, footX = footX) shouldBeLessThanOrEqual
                            without + 1e-4 * leg.s
                    }
                }
            }
        }
    }
})
