/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How a tail of hair moves (see [HangingTail]): heavy and calm, or light and lively.
 *
 * Each point of the tail is pulled toward where the tail's own shape has it, as by a spring; how strongly is given
 * as the spring's stiffness for a point of weight 1, in 1/s² (its square root is how fast the point swings back, in
 * radians a second). [hold] is that pull at the root, where the tail leaves the head, [keep] at the tip; between the
 * two it falls off evenly along the tail (geometrically). [calm]: how quickly a swing against the tail's shape dies
 * away, 1/s. [drag]: the air's drag on the tail's own motion, 1/s: it trails a little when its figure walks.
 * [weight]: how much of its weight the tail feels when the head tilts, 1 for all of it (at rest, with the head held
 * as it was made, the shape carries the weight). [swing]: how far a point may ever be from where the shape has it,
 * as a share of how far along the tail it is.
 */
data class TailFeel(
    val hold: Double,
    val keep: Double,
    val calm: Double,
    val drag: Double,
    val weight: Double,
    val swing: Double,
) {
    companion object {
        /**
         * A thick plait of lacquered hair (Rune's): it follows a turn of the head a little late, swings past once and
         * is still within a second or so; tilted with the head it hangs a little, as a stiff plait does. Tuned by eye
         * (2026-11-20).
         */
        val PLAIT: TailFeel = TailFeel(hold = 300.0, keep = 12.0, calm = 4.5, drag = 0.5, weight = 0.5, swing = 0.6)
    }
}

/**
 * A tail of hair as it is at rest, in its figure's rest space (x to the figure's left, y up, z ahead), in the
 * world's measure (metres).
 *
 * [points]: x, y and z of each point of the tail, from its root (fixed to the head) to its tip. [thick]: how far
 * the hair reaches round each point. [head]: the head as a ball, its middle's x, y, z and its radius; it moves with
 * the root. [body]: the body round which the tail hangs, as capsules (rounded sticks), each its two ends' x, y, z and
 * its radius, seven numbers to a capsule. Where a point lies nearer to the head or the body at rest than they and
 * its own thickness reach, it keeps only that far from them: the shape at rest is always allowed.
 */
class TailShape(
    val points: DoubleArray,
    val thick: DoubleArray,
    val head: DoubleArray,
    val body: DoubleArray,
) {
    /** How many points: the joints and the tip. */
    val count: Int = points.size / 3

    /** How many capsules the body is made of. */
    val capsules: Int = body.size / 7

    init {
        require(points.size % 3 == 0 && count >= 2) { "A tail has a root and a tip at least, three numbers each: ${points.size}" }
        require(thick.size == count) { "A thickness for each of the $count points: ${thick.size}" }
        require(head.size == 4) { "The head is its middle and its radius: ${head.size}" }
        require(body.size % 7 == 0) { "A capsule is seven numbers: ${body.size}" }
    }
}

/**
 * What a tail hangs on at a moment, in the world: [pin], where its root is; [turn], how the root is turned against
 * the tail's rest space (x, y, z, w of a rotation); [body], the body's capsules' two ends each (six numbers to a
 * capsule, as [TailShape.body] has them, without the radius). The world's y is up.
 */
class TailHold(capsules: Int) {
    val pin = DoubleArray(3)
    val turn = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
    val body = DoubleArray(6 * capsules)

    /** Becomes [other]. */
    fun copyFrom(other: TailHold) {
        other.pin.copyInto(destination = pin)
        other.turn.copyInto(destination = turn)
        other.body.copyInto(destination = body)
    }

    /** Becomes the hold [w] of the way from [a] to [b] (0 to 1). */
    fun blend(a: TailHold, b: TailHold, w: Double) {
        for (i in pin.indices) {
            pin[i] = a.pin[i] + (b.pin[i] - a.pin[i]) * w
        }

        for (i in body.indices) {
            body[i] = a.body[i] + (b.body[i] - a.body[i]) * w
        }

        // the turn: the shorter way round, made a rotation again
        val dot = a.turn[0] * b.turn[0] + a.turn[1] * b.turn[1] + a.turn[2] * b.turn[2] + a.turn[3] * b.turn[3]
        val sign = if (dot < 0) -1.0 else 1.0
        var length = 0.0

        for (i in 0 until 4) {
            turn[i] = a.turn[i] + (sign * b.turn[i] - a.turn[i]) * w
            length += turn[i] * turn[i]
        }

        val norm = sqrt(length)

        if (norm > 0) {
            for (i in 0 until 4) {
                turn[i] /= norm
            }
        } else {
            b.turn.copyInto(destination = turn)
        }
    }
}

/**
 * A tail of hair that hangs: a chain of points from a root that goes wherever the head takes it, the rest hanging
 * on it (the maintainer, 2026-11-20: "it must not simply follow the body immediately, must behave like something
 * hanging").
 *
 * Each point has its own inertia: when the head moves or turns, the tail lags, swings past and settles. A soft pull
 * keeps it near its [shape] as the head carries it, strongest at the root and weakest at the tip (a thick plait, not
 * a rope); the air slows it a little; tilted with the head it feels its weight (see [TailFeel]). The points keep their
 * distances from each other, and they keep out of the head and the body, so the tail lies on the back and slides
 * over it.
 *
 * It lives on the show's time ([follow]): steps of a fixed length ([STEP]) inside, so it moves the same at any
 * frame rate; a time that stands still holds it still; a jump (back, or ahead by more than [JUMP]) puts it at rest
 * at the new moment, never flung. It is shown one step late, between its last two steps ([x], [y], [z]): so every
 * frame shows it at its own moment, and a frame rate that is no whole share of the steps' (144 a second) does not
 * stutter. Nothing allocates once it is made.
 */
class HangingTail(private val shape: TailShape, private val feel: TailFeel = TailFeel.PLAIT) {

    companion object {
        /** One step, in seconds. */
        const val STEP: Double = 1.0 / 120

        /** Two moments further apart than this, in seconds, do not follow each other: the tail is put to rest anew. */
        const val JUMP: Double = 0.25

        /** How far after the moment a step may end and still be taken, in seconds: the sums' rounding. */
        private const val EARLY = 1e-9

        /** How long a tail put to rest settles where it hangs, in seconds. */
        const val SETTLE: Double = 1.0

        /** How much of its motion a settling tail keeps from one step to the next: it settles, it does not swing. */
        private const val SETTLING = 0.95

        /** The earth's pull, in m/s². */
        private const val G = 9.81

        /** How often the distances and the body are kept to in a step. */
        private const val PASSES = 2

        /** How near to the body a point may be at rest, against how near it is: a hair less, so it is never inside. */
        private const val SHY = 0.999
    }

    /** How many points: the joints and the tip. */
    val count: Int = shape.count

    private val n = count
    private val capsules = shape.capsules

    /** Where each point is, and where it was a step before (the motion is the difference). */
    private val p = DoubleArray(3 * n)
    private val before = DoubleArray(3 * n)

    /** Where the shape has each point, now and a step before. */
    private val target = DoubleArray(3 * n)
    private val targetBefore = DoubleArray(3 * n)

    /** The distance from each point to the next, and how far along the tail each point is. */
    private val lengths = DoubleArray(n - 1)
    private val along = DoubleArray(n)

    /** How strongly each point is pulled toward its place in the shape. */
    private val pull = DoubleArray(n)

    /** How far each point keeps from the head's middle, and from each capsule's axis. */
    private val clearHead = DoubleArray(n)
    private val clearBody = DoubleArray(n * capsules)

    /** The head's middle, now. */
    private val headAt = DoubleArray(3)

    /** A turned direction, while it is worked out. */
    private val turned = DoubleArray(3)

    /** Where each point is shown: one step late, between the last two steps. */
    private val shown = DoubleArray(3 * n)

    /** The hold at the last moment, and the one between that and the next for a step. */
    private val last = TailHold(capsules)
    private val mix = TailHold(capsules)

    /** The last moment it was told, and the moment its points are at; none before the first. */
    private var lastT: Double? = null
    private var at = 0.0

    init {
        val r = shape.points
        var far = 0.0

        for (i in 1 until n) {
            lengths[i - 1] = dist(r, i, r, i - 1)
            far += lengths[i - 1]
            along[i] = far
        }

        for (i in 0 until n) {
            val share = if (far > 0) along[i] / far else 0.0

            // from the root's pull to the tip's, evenly on a log scale
            pull[i] = feel.hold * exp(share * ln(feel.keep / feel.hold))
        }

        val h = shape.head

        for (i in 0 until n) {
            val dx = r[3 * i] - h[0]
            val dy = r[3 * i + 1] - h[1]
            val dz = r[3 * i + 2] - h[2]

            clearHead[i] = min(h[3] + shape.thick[i], SHY * sqrt(dx * dx + dy * dy + dz * dz))

            for (c in 0 until capsules) {
                val b = shape.body
                val o = 7 * c
                val d = toSegment(
                    px = r[3 * i], py = r[3 * i + 1], pz = r[3 * i + 2],
                    ax = b[o], ay = b[o + 1], az = b[o + 2],
                    bx = b[o + 3], by = b[o + 4], bz = b[o + 5],
                    out = turned,
                )

                clearBody[i * capsules + c] = min(b[o + 6] + shape.thick[i], SHY * d)
            }
        }

        r.copyInto(destination = p)
        r.copyInto(destination = before)
        r.copyInto(destination = target)
        r.copyInto(destination = targetBefore)
        r.copyInto(destination = shown)
    }

    /** Where the point [i] is shown, in the world: across, up, ahead (one [STEP] before the last moment). */
    fun x(i: Int): Double = shown[3 * i]

    fun y(i: Int): Double = shown[3 * i + 1]

    fun z(i: Int): Double = shown[3 * i + 2]

    /**
     * The tail at the moment [t] (seconds), hanging on [hold] as it is then. The first moment, and one that does not
     * follow the last (back in time, or more than [JUMP] ahead), puts it at rest; the same moment again leaves it
     * where it is; otherwise it moves on in steps of [STEP], what it hangs on going evenly from the last moment's
     * hold to this one.
     */
    fun follow(t: Double, hold: TailHold) {
        // NaN-guard: a moment that is no number leaves it where it is
        if (t != t) {
            return
        }

        val was = lastT

        if (was == null || t < was || t - was > JUMP) {
            settle(hold)
            last.copyFrom(hold)
            lastT = t
            at = t
            show(t)

            return
        }

        if (t == was) {
            return
        }

        // (a step that ends a hair after the moment, by the sums' rounding, is taken: at 60 or 120 frames a second
        // the steps end on the frames)
        while (at + STEP <= t + EARLY) {
            at += STEP
            mix.blend(a = last, b = hold, w = min(1.0, (at - was) / (t - was)))
            step(hold = mix, keep = 1.0)
        }

        last.copyFrom(hold)
        lastT = t
        show(t)
    }

    /** The points as shown at [t]: where they were a step before it, between the last two steps. */
    private fun show(t: Double) {
        val w = ((t - at) / STEP).coerceIn(0.0, 1.0)

        for (j in shown.indices) {
            shown[j] = before[j] + (p[j] - before[j]) * w
        }
    }

    /** Puts the tail where its shape has it on [hold], and lets it settle there for [SETTLE] seconds, at rest. */
    private fun settle(hold: TailHold) {
        targets(hold)
        target.copyInto(destination = p)
        target.copyInto(destination = before)
        target.copyInto(destination = targetBefore)
        keepTo(hold)

        var left = SETTLE

        while (left > 0) {
            step(hold = hold, keep = SETTLING)
            left -= STEP
        }

        p.copyInto(destination = before)
    }

    /** One step on [hold]; [keep] of the motion goes on (1 as it moves, less as it settles). */
    private fun step(hold: TailHold, keep: Double) {
        targets(hold)

        val h2 = STEP * STEP
        val air = exp(-feel.drag * STEP)
        val calm = 1 - exp(-feel.calm * STEP)
        val q = hold.turn

        // the weight the shape does not carry: the pull down less the pull down as the root is turned. Nothing at
        // all while the root is held upright, as the tail was made
        rotate(q = q, x = 0.0, y = -1.0, z = 0.0, out = turned)

        val wx = feel.weight * G * (0.0 - turned[0])
        val wy = feel.weight * G * (-1.0 - turned[1])
        val wz = feel.weight * G * (0.0 - turned[2])

        for (i in 1 until n) {
            for (k in 0 until 3) {
                val j = 3 * i + k
                val v = p[j] - before[j]
                val vt = target[j] - targetBefore[j]
                // the air slows the motion; the shape's calm slows only the swing against it
                val moved = keep * (v * air - (v - vt) * calm)
                val pulled = pull[i] * (target[j] - p[j]) + when (k) {
                    0 -> wx
                    1 -> wy
                    else -> wz
                }

                before[j] = p[j]
                p[j] += moved + pulled * h2
            }
        }

        p[0] = hold.pin[0]
        p[1] = hold.pin[1]
        p[2] = hold.pin[2]
        before[0] = p[0]
        before[1] = p[1]
        before[2] = p[2]

        keepTo(hold)
        target.copyInto(destination = targetBefore)
    }

    /** Where the shape has each point on [hold], and where the head's middle is. */
    private fun targets(hold: TailHold) {
        val r = shape.points
        val q = hold.turn

        for (i in 0 until n) {
            rotate(q = q, x = r[3 * i] - r[0], y = r[3 * i + 1] - r[1], z = r[3 * i + 2] - r[2], out = turned)
            target[3 * i] = hold.pin[0] + turned[0]
            target[3 * i + 1] = hold.pin[1] + turned[1]
            target[3 * i + 2] = hold.pin[2] + turned[2]
        }

        val h = shape.head

        rotate(q = q, x = h[0] - r[0], y = h[1] - r[1], z = h[2] - r[2], out = turned)
        headAt[0] = hold.pin[0] + turned[0]
        headAt[1] = hold.pin[1] + turned[1]
        headAt[2] = hold.pin[2] + turned[2]
    }

    /** No farther from where the shape has the point [i] than [TailFeel.swing] allows. */
    private fun limitSwing(i: Int) {
        val o = 3 * i
        val dx = p[o] - target[o]
        val dy = p[o + 1] - target[o + 1]
        val dz = p[o + 2] - target[o + 2]
        val off = sqrt(dx * dx + dy * dy + dz * dz)
        val most = feel.swing * along[i]

        if (off > most && off > 0) {
            val k = most / off

            p[o] = target[o] + dx * k
            p[o + 1] = target[o + 1] + dy * k
            p[o + 2] = target[o + 2] + dz * k
        }
    }

    /**
     * Every point at its distance from the one before, no farther from its place in the shape than it may swing,
     * and out of the head and the body (that last).
     */
    private fun keepTo(hold: TailHold) {
        for (pass in 0 until PASSES) {
            for (i in 1 until n) {
                keepLength(i)
                limitSwing(i)
                keepOut(i = i, hold = hold)
            }
        }
    }

    /** The point [i] at its distance from the one before, on the line between them. */
    private fun keepLength(i: Int) {
        val o = 3 * i
        val dx = p[o] - p[o - 3]
        val dy = p[o + 1] - p[o - 2]
        val dz = p[o + 2] - p[o - 1]
        val d = sqrt(dx * dx + dy * dy + dz * dz)

        if (d < 1e-12) {
            // on top of the one before: it goes the way the shape has it
            p[o] = p[o - 3] + target[o] - target[o - 3]
            p[o + 1] = p[o - 2] + target[o + 1] - target[o - 2]
            p[o + 2] = p[o - 1] + target[o + 2] - target[o - 1]

            return
        }

        val k = lengths[i - 1] / d

        p[o] = p[o - 3] + dx * k
        p[o + 1] = p[o - 2] + dy * k
        p[o + 2] = p[o - 1] + dz * k
    }

    /** The point [i] out of the head's ball and the body's capsules, each by the shortest way. */
    private fun keepOut(i: Int, hold: TailHold) {
        pushOut(i = i, cx = headAt[0], cy = headAt[1], cz = headAt[2], clear = clearHead[i])

        val b = hold.body

        for (c in 0 until capsules) {
            val o = 6 * c

            toSegment(
                px = p[3 * i], py = p[3 * i + 1], pz = p[3 * i + 2],
                ax = b[o], ay = b[o + 1], az = b[o + 2],
                bx = b[o + 3], by = b[o + 4], bz = b[o + 5],
                out = turned,
            )
            pushOut(i = i, cx = turned[0], cy = turned[1], cz = turned[2], clear = clearBody[i * capsules + c])
        }
    }

    /** The point [i] at least [clear] from (cx, cy, cz). */
    private fun pushOut(i: Int, cx: Double, cy: Double, cz: Double, clear: Double) {
        val o = 3 * i
        val dx = p[o] - cx
        val dy = p[o + 1] - cy
        val dz = p[o + 2] - cz
        val d = sqrt(dx * dx + dy * dy + dz * dz)

        if (d >= clear) {
            return
        }

        if (d < 1e-12) {
            // right in the middle: out the way the shape has it
            val tx = target[o] - cx
            val ty = target[o + 1] - cy
            val tz = target[o + 2] - cz
            val td = sqrt(tx * tx + ty * ty + tz * tz)

            if (td < 1e-12) {
                return
            }

            p[o] = cx + tx / td * clear
            p[o + 1] = cy + ty / td * clear
            p[o + 2] = cz + tz / td * clear

            return
        }

        val k = clear / d

        p[o] = cx + dx * k
        p[o + 1] = cy + dy * k
        p[o + 2] = cz + dz * k
    }
}

/** The distance between the point [i] of [a] and the point [j] of [b], three numbers to a point. */
private fun dist(a: DoubleArray, i: Int, b: DoubleArray, j: Int): Double {
    val dx = a[3 * i] - b[3 * j]
    val dy = a[3 * i + 1] - b[3 * j + 1]
    val dz = a[3 * i + 2] - b[3 * j + 2]

    return sqrt(dx * dx + dy * dy + dz * dz)
}

/** (x, y, z) turned by the rotation [q] (x, y, z, w), into [out]. */
private fun rotate(q: DoubleArray, x: Double, y: Double, z: Double, out: DoubleArray) {
    val qx = q[0]
    val qy = q[1]
    val qz = q[2]
    val qw = q[3]
    // t = 2 (q × v); v' = v + w t + q × t
    val tx = 2 * (qy * z - qz * y)
    val ty = 2 * (qz * x - qx * z)
    val tz = 2 * (qx * y - qy * x)

    out[0] = x + qw * tx + (qy * tz - qz * ty)
    out[1] = y + qw * ty + (qz * tx - qx * tz)
    out[2] = z + qw * tz + (qx * ty - qy * tx)
}
