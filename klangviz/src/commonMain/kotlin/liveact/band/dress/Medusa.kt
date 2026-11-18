/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A place in space: [x] to one side, [y] up, [z] ahead. A snake's shape is given in its head's own space, in head
 * radii from the head's middle; where the snakes are set on a stage the same three numbers are metres there.
 */
data class HairPoint(val x: Double, val y: Double, val z: Double)

/**
 * Hair like Medusa's: [snakes] of them rise from the upper half of a head and writhe, each a line of [joints] points
 * from its root on the scalp to its tip, [length] head radii long.
 *
 * Each snake stands out from the head where it is rooted, to all sides, and bends a little upward; a slow wave
 * travels along it from the root to the tip, in two directions at two paces, wider toward the tip, and no two
 * snakes are in step. The root stays where it is: well inside the head, so a snake comes out of the head and no
 * gap shows between the two, whatever the head's own shape. Everything goes by the time alone, so any moment looks the same whenever it is shown.
 */
class MedusaHair(val snakes: Int = 9, val joints: Int = 11, val length: Double = 3.3) {

    init {
        require(snakes >= 1) { "No snakes: $snakes" }
        require(joints >= 2) { "A snake has a root and a tip at least: $joints joints" }
    }

    /** Where the [joint]th point of the [snake] is at [t] (seconds); the joint 0 is its root inside the head. */
    fun at(snake: Int, joint: Int, t: Double): HairPoint {
        require(snake in 0 until snakes) { "No snake $snake of $snakes" }
        require(joint in 0 until joints) { "No joint $joint of $joints" }

        // the roots lie under the upper scalp, spread evenly: round the head by the golden angle, from near the crown
        // down to the ears
        val round = snake * GOLDEN
        val down = 0.35 + 1.10 * (snake + 0.5) / snakes
        val out = Triple(sin(down) * cos(round), cos(down), sin(down) * sin(round))
        // it leaves the head straight out and turns upward
        val along = joint.toDouble() / (joints - 1)
        val far = along * length
        val lift = 0.14 * far * along
        // two directions across it: round the head, and the one square to both
        val side = Triple(-sin(round), 0.0, cos(round))
        val other = Triple(
            out.second * side.third - out.third * side.second,
            out.third * side.first - out.first * side.third,
            out.first * side.second - out.second * side.first,
        )
        // the wave: nothing at the root, widest at the tip, travelling outward, unhurried (a fifth slower than at
        // first: the maintainer, 2026-11-17, "a little too fast ... just a little")
        val wide = WAVE * length * along.pow(1.3)
        val a = wide * sin(3.0 * PI * along - 1.35 * t + snake * 2.1)
        val b = 0.7 * wide * sin(2.1 * PI * along - 0.88 * t + snake * 3.7 + 1.0)

        return HairPoint(
            x = out.first * (ROOT + far) + side.first * a + other.first * b,
            y = out.second * (ROOT + far) + lift + side.second * a + other.second * b,
            z = out.third * (ROOT + far) + side.third * a + other.third * b,
        )
    }

    /**
     * How much of a snake's point goes with its own head and not with the head it grows on, 0 to 1: nothing at the
     * root, which sits in the skull, all at the tip, and more and more along the body between.
     */
    fun free(joint: Int): Double {
        require(joint in 0 until joints) { "No joint $joint of $joints" }

        val along = joint.toDouble() / (joints - 1)

        return along * along * (3 - 2 * along)
    }

    private companion object {
        /** The golden angle: roots spread by it never line up. */
        val GOLDEN = PI * (3 - sqrt(5.0))

        /** How far from the head's middle a snake is rooted, in head radii: inside the head. */
        const val ROOT = 0.5

        /** How wide the wave is at the tip, as a share of the snake's length. */
        const val WAVE = 0.24
    }
}

/**
 * Where a snake's head is a moment later. It has a mind of its own: it does not go with the head it grows on but
 * makes for the place it [wants], unhurried (to within a third of the way left in [ease] seconds), so when the
 * head below it nods or turns it stays where it was and finds its place again. Only its body holds it: it is never
 * further than [reach] from its [root], and where the root moves off faster than it follows, it is pulled along at
 * the end of its body, stretched straight.
 *
 * [head]: where it is now; [dt]: how much later, in seconds (0 and less: it stays, but for the body's pull). Two
 * short moments are as one long one, so it does not go by how fast the pictures come.
 */
fun pulledHead(head: HairPoint, wants: HairPoint, root: HairPoint, reach: Double, dt: Double, ease: Double = 0.4): HairPoint {
    val share = if (dt > 0) 1 - exp(-dt / ease) else 0.0
    val x = head.x + (wants.x - head.x) * share
    val y = head.y + (wants.y - head.y) * share
    val z = head.z + (wants.z - head.z) * share
    val far = sqrt((x - root.x) * (x - root.x) + (y - root.y) * (y - root.y) + (z - root.z) * (z - root.z))

    if (far <= reach) {
        return HairPoint(x = x, y = y, z = z)
    }

    val pull = reach / far

    return HairPoint(x = root.x + (x - root.x) * pull, y = root.y + (y - root.y) * pull, z = root.z + (z - root.z) * pull)
}

/**
 * How long ago the last moment was, in seconds, where this one ([t]) follows it; null where it does not: there was
 * none ([last] null), time went back (a jump in the song), or more than [most] seconds lie between (a jump ahead, a
 * page that slept). What goes on from moment to moment is then set anew. 0 where time stands (a still, a pause).
 */
fun sinceLast(last: Double?, t: Double, most: Double): Double? {
    val dt = t - (last ?: return null)

    return if (dt < 0 || dt > most) null else dt
}
