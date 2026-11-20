/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the hips do at one moment of a walk, in the figure's space (x to its left, y up, z ahead), and how the body
 * above them answers so that the shoulders stay about level and the head about still. One place for both walks, the
 * walk-in (`walkInHips`) and Sirene's stroll.
 *
 * [sway]: how far the hips go sideways, onto the standing leg (to the left is positive), in the figure's measures;
 * [lower]: how far they come down, so that the standing leg's hip keeps its height while the other one drops (the
 * pelvis pivots on the standing hip); [yaw]: their turn about the up axis; [roll]: their tilt about the axis ahead,
 * the hip of the swinging leg dropping (positive lifts the left hip); [waist]: the belly's tilt, which takes the
 * [roll] back so the trunk above stands as it would without it; [chestYaw]: the chest's turn, which takes the [yaw]
 * back (the shoulders' own swing against the legs is the walk's, [SHOULDERS]: not the hips'); [chestRoll]: the
 * chest's tilt against the [sway], so the head stays over the middle.
 */
data class HipMotion(
    val sway: Double,
    val lower: Double,
    val yaw: Double,
    val roll: Double,
    val waist: Double,
    val chestYaw: Double,
    val chestRoll: Double,
) {
    companion object {
        /** How far the hips sway onto the standing leg, in the figure's size (the rig's `s`), where the hips move as 1. */
        const val SWAY = 0.035

        /** How far the hips turn with the legs, in radians, where the hips move as 1. */
        const val YAW = 0.10

        /**
         * How far the hip of the swinging leg drops, as a tilt in radians (about 3.4 degrees), where the hips move as
         * 1. More reads as a limp; it is what was missing before, and what reads as hips.
         */
        const val DROP = 0.06

        /** How far the chest tilts against the sway, in radians, where the hips move as 1. */
        const val TILT = 0.05

        /**
         * The shoulders' own swing against the legs, in radians: a walk's, whatever the hips do (the walk-in's plain
         * walk has it, and Sirene's stroll), so not in the motion. With the chest's answer to the hips' turn it
         * makes the chest's turn of Sirene's stroll as it was before there were gaits: -0.26 at 1.
         */
        const val SHOULDERS = 0.16

        /** No motion at all: a member who stands. */
        val NONE: HipMotion = HipMotion(sway = 0.0, lower = 0.0, yaw = 0.0, roll = 0.0, waist = 0.0, chestYaw = 0.0, chestRoll = 0.0)

        /**
         * The hips' motion at [stride] (the steps' phase, as the legs step it: the left foot lands ahead at every
         * whole number and the right one half a step later; each stays on the floor for 0.6 of a step), as far as
         * the figure walks ([going], 0 standing to 1 walking), as much as the hips move ([amount], see [Gait]; 1 as
         * Sirene's stroll had them), for a figure of the size [s] whose hip joints stand [hipHalf] from its middle.
         * Nothing where [going] or [amount] is 0; the same to the right half a step later as to the left.
         */
        fun at(stride: Double, going: Double, amount: Double, s: Double, hipHalf: Double): HipMotion {
            val k = going * amount
            // 1 with the weight on the left leg, -1 on the right; and 1 as the left foot lands ahead
            val onLeft = sin(2 * PI * stride)
            val swing = cos(2 * PI * stride)
            val roll = DROP * k * onLeft

            return HipMotion(
                sway = SWAY * s * k * onLeft,
                // the standing hip rises by the half width times the tilt: down by about as much, smoothly
                lower = hipHalf * DROP * k * onLeft * onLeft,
                yaw = YAW * k * swing,
                roll = roll,
                waist = -roll,
                chestYaw = -YAW * k * swing,
                chestRoll = TILT * k * onLeft,
            )
        }
    }
}
