/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.sin

// Where a free hand goes for an idle skill at the head (see IdleSkill), for any pose that has a hand free for it (so
// far the string players'). Which skills a pose shows, and with which hand, is the pose's own.

/** The scratch: how deep (in the figure's measure), how many times a second. */
private const val SCRATCH_DEPTH = 0.012
private const val SCRATCH_RATE = 4.0

/** The cap's peak, from the start of the head's bone: so far up and ahead (in the figure's measure), and the tug's way down. */
private const val PEAK_UP = 0.20
private const val PEAK_AHEAD = 0.135
private const val TUG_DOWN = 0.025

/** How long the tug at the cap takes, down and back; how long the swipe across the brow (seconds). */
private const val TUG = 0.7
private const val SWIPE = 0.8

/**
 * Where a hand is asked to go, in the world: the [wrist]'s place, which way the hand [points] (of length 1, whatever
 * it is given), where the elbow bends to ([pole]); and on the way there from where it was, and back, how far the
 * wrist goes round ([round], at the middle of the way) and how far the elbow is pulled down ([drop], added to the
 * pole at the middle of the way).
 */
class HandGoal(val wrist: Vector3, points: Vector3, val pole: Vector3, val round: Vector3, val drop: Vector3) {
    val points: Vector3 = points.clone().normalize()

    /**
     * Takes a hand [w] of the way (0 to 1) from where it is asked to be ([wrist], [pole], in the world; changed in
     * place) to this goal, by the way the goal gives ([round], [drop]).
     */
    fun blend(wrist: Vector3, pole: Vector3, w: Double) {
        wrist.lerp(this.wrist, w).addScaledVector(round, sin(PI * w))
        pole.lerp(this.pole, w).addScaledVector(drop, sin(PI * w)).normalize()
    }
}

/** The head of a rig as it is posed now: its middle, its size, and which ways are up, ahead and to its left. */
private class HeadNow(rig: Rig) {
    val up: Vector3 = rig.axisOf("head")
    val ahead: Vector3 = rig.faceAhead.clone().applyQuaternion(rig.bone("head").getWorldQuaternion(quat()))
    val left: Vector3 = vec().crossVectors(up, ahead).normalize()

    // the head's middle and its size (as the combed-back hair's pattern has them: see `Notstrom.hairOf`)
    val size: Double = rig.height / 1.60
    val middle: Vector3 = rig.bone("head").getWorldPosition(vec()).addScaledVector(up, 0.115 * size).addScaledVector(ahead, -0.012 * size)
    val radius: Double = 0.104 * size

    /** A place on the head's surface, so far [out] off it: toward [x] (to the left), [y] (up) and [z] (ahead). */
    fun on(x: Double, y: Double, z: Double, out: Double): Vector3 {
        val n = vec().addScaledVector(left, x).addScaledVector(up, y).addScaledVector(ahead, z).normalize()

        return middle.clone().addScaledVector(n, radius + out)
    }
}

/**
 * On the way to the head and back, the short way: the wrist a little in from the [side] and ahead, before the body,
 * and the elbow pulled down (not out to the side at the height of the shoulder).
 */
private fun before(rig: Rig, side: Side): Vector3 = rig.dir(-side.sign * 0.4, 0.0, 1.0).multiplyScalar(0.10 * rig.s)

/** See [before]: the elbow pulled down on the way. */
private fun elbowDown(rig: Rig): Vector3 = rig.dir(0.0, -1.0, -0.2).multiplyScalar(2.0)

/**
 * The hand of [side] scratching the head of [rig] (see [HeadScratch]), [since] seconds into the use: the fingers at
 * that side of the head, a little behind the ear, the wrist below them; they scratch with a small quick motion up and
 * down. It goes by the head as it is posed when it is asked: a pose that places the hand before the head is held at
 * last asks again then (see `StringPlayer.afterPose`).
 */
fun scratchHand(rig: Rig, side: Side, since: Double): HandGoal {
    val head = HeadNow(rig)
    val spot = head.on(x = side.sign * 0.8, y = 0.35, z = -0.45, out = 0.0)
    val scratch = SCRATCH_DEPTH * head.size * sin(2 * PI * SCRATCH_RATE * since)
    val wrist = head.on(x = side.sign * 0.95, y = -0.35, z = -0.35, out = 0.05 * head.size).addScaledVector(head.up, scratch)

    return HandGoal(
        wrist = wrist,
        points = spot.sub(wrist),
        pole = rig.dir(side.sign * 1.0, 0.1, 0.5),
        round = before(rig, side),
        drop = elbowDown(rig),
    )
}

/**
 * How far the tug at the peak of the cap has come down, 0 to 1 and back to 0, [since] seconds into a use that rises
 * over [rise] seconds (see [CapTug]): once, as soon as the hand is there.
 */
fun capTugAt(since: Double, rise: Double): Double = sin(PI * ((since - rise) / TUG).coerceIn(0.0, 1.0))

/**
 * The hand of [side] at the peak of the cap of [rig] (see [CapTug]), [since] seconds into a use that rises over [rise]
 * seconds: the fingers on the peak from below and before it, the wrist under them; once there, a tug down a little
 * and back (see [capTugAt]). It goes by the head as it is posed when it is asked (see [scratchHand]).
 */
fun capHand(rig: Rig, side: Side, since: Double, rise: Double): HandGoal {
    val head = HeadNow(rig)
    val tug = capTugAt(since = since, rise = rise)
    // the peak, as the figure script sets the cap and its brim on the head (`klangviz/figures/klaue.py`)
    val peak = rig.bone("head").getWorldPosition(vec())
        .addScaledVector(head.up, PEAK_UP * head.size)
        .addScaledVector(head.ahead, PEAK_AHEAD * head.size)
        .addScaledVector(head.up, -TUG_DOWN * head.size * tug)
    val wrist = peak.clone()
        .addScaledVector(head.ahead, 0.05 * head.size)
        .addScaledVector(head.up, -0.08 * head.size)
        .addScaledVector(head.left, side.sign * 0.03 * head.size)

    return HandGoal(
        wrist = wrist,
        points = peak.clone().sub(wrist),
        pole = rig.dir(side.sign * 1.0, -0.3, 0.3),
        round = before(rig, side),
        drop = elbowDown(rig),
    )
}

/**
 * How far the swipe across the brow has come, 0 at the other side of the brow to 1 past its own, [since] seconds into
 * a use that rises over [rise] seconds (see [BrowWipe]): once, beginning as the arm comes up.
 */
fun browSwipeAt(since: Double, rise: Double): Double = smooth((since - rise * 0.6) / SWIPE)

/**
 * The arm of [side] wiping the brow of [rig] (see [BrowWipe]), [since] seconds into a use that rises over [rise]
 * seconds: the forearm across the brow, the elbow out to its own side, the wrist sliding from the other side of the
 * brow past its own in one swipe (see [browSwipeAt]). It goes by the head as it is posed when it is asked (see
 * [scratchHand]).
 */
fun browHand(rig: Rig, side: Side, since: Double, rise: Double): HandGoal {
    val head = HeadNow(rig)
    val swipe = browSwipeAt(since = since, rise = rise)
    // from the other side of the brow to past its own
    val across = -side.sign * 0.5 + side.sign * 1.4 * swipe
    val wrist = head.on(x = across, y = 0.35, z = 0.9, out = 0.05 * head.size)

    return HandGoal(
        wrist = wrist,
        points = head.left.clone().multiplyScalar(-side.sign),
        pole = rig.dir(side.sign * 1.0, 0.1, 0.4),
        round = before(rig, side),
        drop = elbowDown(rig),
    )
}

