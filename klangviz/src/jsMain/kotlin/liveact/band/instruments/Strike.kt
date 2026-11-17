/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyAxisAngle
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.distanceTo
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.lengthSq
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Striking with a stick or a mallet: where its tip is between two hits, and the arm that holds it. Shared by
// everyone who hits something.

/** One hit of one hand: when, where the tip lands (in the figure's space), and how hard (0 to 1). */
interface Stroke : Timed {
    val at: Vector3
    val v: Double
}

/** A stick at one moment: where its [tip] is, how the wrist is bent, how high it is lifted, and where it aims. */
class StickPose(val tip: Vector3, val bend: Double, val lift: Double, val aim: Vector3)

/**
 * A stick at [t], for one hand's hits [list] (sorted by time): where its tip is and how the wrist is bent. On the
 * head at each hit; after it a small bounce, idling low; before the next the wind-up, and on the way the tip
 * travels to the next place.
 *
 * [rest]: where the tip idles when there is no hit at all. [home]: a place in front of this arm's shoulder; the tip
 * is pulled back toward it as it rises. [height]: a full stroke's height. [windup]: how long before a hit it
 * starts, in seconds. [hover]: the idle height, as a share of [height].
 */
fun strokeAt(
    list: List<Stroke>,
    t: Double,
    rest: Vector3,
    home: Vector3,
    height: Double,
    windup: Double = 0.34,
    hover: Double = 0.16,
): StickPose {
    val (before, after) = list.around(t)
    val from = before?.at ?: rest
    val to = after?.at ?: from
    val gap = if (before != null && after != null) after.t - before.t else 1.0
    val since = if (before != null) t - before.t else 9.0
    val until = if (after != null) after.t - t else 9.0

    val move = if (from === to) 1.0 else smooth((gap - until) / max(0.05, gap * 0.8))
    val aim = from.clone().lerp(to, move)

    val window = min(gap, windup)
    val p = if (after != null && until < window) 1 - until / window else 0.0
    val strength = if (after != null) 0.45 + 0.55 * after.v else 0.0
    val idle = hover * (1 - exp(-since / 0.05))
    val lift = idle * (1 - p * p * p * p) + strength * min(1.0, window / windup) * sin(PI * p * p)

    val toward = vec(home.x - aim.x, 0.0, home.z - aim.z)

    if (toward.lengthSq() < 1e-6) {
        toward.set(0.0, 0.0, -1.0)
    }

    toward.normalize()

    val tip = aim.clone().add(vec(0.0, height * lift, 0.0)).addScaledVector(toward, 0.18 * height * lift)
    // the wrist's bend against the forearm: cocked up a little in the wind-up, snapped down a little into the hit
    val snap = max(exp(-since / 0.06), p.pow(8))
    val bend = 0.42 * lift - 0.14 * snap

    return StickPose(tip = tip, bend = bend, lift = lift, aim = aim)
}

/**
 * The arm [side] of [rig] holds a stick whose tip is at [tip] (the figure's space). The hand stays in line with
 * the forearm but for the wrist's [bend]: the forearm and the stick are one long bone from the elbow to the tip,
 * the upper arm and that bone reach for the tip, and the wrist lies on that bone. [mesh] is the stick, its length
 * [length] along its own y, held at [grip] of its length from the tip. [pole] (a world direction) is where the
 * elbow bends to.
 *
 * A hand with more than one stick (a marimba player's two mallets) gives no [mesh] and sets its sticks itself:
 * [tip] is then the place between their heads, [reach] how far that is from the hand, and what comes back is where
 * the hand holds them (in the world).
 */
fun holdStick(
    rig: Rig,
    side: Side,
    tip: Vector3,
    bend: Double,
    length: Double,
    pole: Vector3,
    mesh: Object3D?,
    grip: Double = 0.72,
    reach: Double = length * grip,
): Vector3 {
    val tipWorld = rig.root.localToWorld(tip.clone())
    val held = reach
    val shoulder = rig.at("elbow$side")
    val elbowNow = rig.at("wrist$side")
    val upper = shoulder.distanceTo(elbowNow)
    val fore = elbowNow.distanceTo(rig.at("hand$side"))
    val whole = sqrt(fore * fore + held * held + 2 * fore * held * cos(bend))
    val off = asin(min(1.0, max(-1.0, held * sin(bend) / whole)))

    val line = tipWorld.clone().sub(shoulder)
    val far = min(max(line.length(), abs(upper - whole) + 1e-3), (upper + whole) * 0.999)

    line.normalize()

    val cosine = (upper * upper + far * far - whole * whole) / (2 * upper * far)
    val out = pole.clone().addScaledVector(line, -pole.dot(line)).normalize()
    val elbow = shoulder.clone().addScaledVector(line, upper * cosine).addScaledVector(out, upper * sqrt(max(0.0, 1 - cosine * cosine)))
    val reached = shoulder.clone().addScaledVector(line, far)

    val along = reached.clone().sub(elbow).normalize()
    val axis = vec().crossVectors(along, Y_AXIS)

    if (axis.lengthSq() > 1e-6) {
        along.applyAxisAngle(axis.normalize(), -off)
    }

    rig.hand(side, elbow.clone().addScaledVector(along, fore), pole)

    val hand = rig.bone("hand$side")
    val at = hand.getWorldPosition(vec())
    val d = reached.clone().sub(at).normalize()
    val points = vec(0.0, 1.0, 0.0).applyQuaternion(hand.getWorldQuaternion(quat()))

    rig.turnWorld("hand$side", quat().setFromUnitVectors(points, d))

    if (mesh != null) {
        mesh.position.copy(at).addScaledVector(d, length * (grip - 0.5))
        mesh.quaternion.setFromUnitVectors(Y_AXIS, d)
    }

    return at
}
