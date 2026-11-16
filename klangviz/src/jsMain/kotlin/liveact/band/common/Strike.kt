/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Striking with a stick or a mallet: where its tip is between two hits, and the arm that holds it. Shared by
// everyone who hits something. And how a player moves with the music apart from playing: the nod and the wandering.

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
 */
fun holdStick(rig: Rig, side: Side, tip: Vector3, bend: Double, length: Double, pole: Vector3, mesh: Object3D, grip: Double = 0.72) {
    val tipWorld = rig.root.localToWorld(tip.clone())
    val held = length * grip
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
    mesh.position.copy(at).addScaledVector(d, length * (grip - 0.5))
    mesh.quaternion.setFromUnitVectors(Y_AXIS, d)
}

/**
 * A player's character: how they move with the music apart from playing. Each player has one of their own, so no
 * two move alike, and a little chance in everything, so nobody moves like a machine.
 */
data class Traits(
    /** How deep the nod is. */
    val depth: Double = 1.0,
    /** How much each of the bar's four beats counts. */
    val beats: List<Double> = listOf(1.0, 0.5, 0.8, 0.5),
    /** Late (lazy) or early (eager), in beats. */
    val lag: Double = 0.0,
    /** How round the nod is, in beats (more is smoother). */
    val soft: Double = 0.10,
    /** How much a nod's depth and timing vary. */
    val chance: Double = 0.3,
    /** How often a nod is left out, 0 to 1. */
    val skip: Double = 0.0,
    /** How far the head turns, the head tilts and the trunk leans as they wander, in radians. */
    val look: Double = 0.10,
    val tilt: Double = 0.05,
    val lean: Double = 0.02,
    /** How little the sound's energy changes the movement: 0 follows it fully, 1 is always the same. */
    val steady: Double = 0.0,
    /** The head goes all the way only in fits; null: always. */
    val fits: Fits? = null,
    /** Which parts of the body take the beat, each a factor. */
    val parts: Parts = Parts(),
) {
    /** [every]: how long a mood lasts, in beats. [share]: how much of the time is a fit, 0 to 1. [calm]: the head's share between fits. */
    data class Fits(val every: Double, val share: Double, val calm: Double)

    /** [head]: the nod itself. [trunk]: leaning into it. [knees]: giving on the beat. [sway]: the hips going from side to side. */
    data class Parts(val head: Double = 1.0, val trunk: Double = 1.0, val knees: Double = 1.0, val sway: Double = 1.0)
}

/**
 * How one player moves with the music, apart from playing: the nod and a slow wandering of head and trunk, by
 * their [traits], with the [show]'s chance under [seed] (each player's own).
 */
class Mover(private val show: Show, private val seed: Double, val traits: Traits = Traits()) {

    /** The parts' shares just now: the head's changes with the fits ([nod] sets it). */
    var parts: Traits.Parts = traits.parts
        private set

    /** One beat's nod at [beat]: round on both sides, down a little faster than up. */
    private fun one(index: Int, beat: Double): Double {
        val c = traits
        val mine = show.own(index, seed)

        if (show.own(index, seed + 7) < c.skip) {
            return 0.0
        }

        val weight = c.beats[((index % 4) + 4) % 4] * (1 - c.chance * 0.6 + c.chance * 1.2 * mine)
        val phase = beat - index - c.lag - (show.own(index, seed + 3) - 0.5) * 0.12 * c.chance

        return weight * exp(-(phase * phase) / (if (phase < 0) 0.030 else 0.110))
    }

    /**
     * The nod at [beat] (in beats), 0 to about 1, as strong as the sound's energy over the last second and a half:
     * it never follows a single loud moment.
     */
    fun nod(beat: Double): Double {
        val c = traits

        c.fits?.let { fits ->
            // a slow mood, wandering between -1 and 1: above its threshold it is a fit, eased in and out
            val mood = show.drift(beat / fits.every, seed + 21)
            val fit = min(1.0, max(0.0, (mood - (1 - 2 * fits.share)) / 0.3))

            parts = parts.copy(head = traits.parts.head * (fits.calm + (1 - fits.calm) * fit * fit * (3 - 2 * fit)))
        }

        // rounded: the mean over a short stretch round this moment, so nothing in it is abrupt
        var sum = 0.0

        for (k in -2..2) {
            val b = beat + k * c.soft / 2
            val index = floor(b).toInt()

            sum += (3 - abs(k)) * max(one(index - 1, b), max(one(index, b), one(index + 1, b)))
        }

        return c.depth * (sum / 9) * (0.12 + 1.05 * strength())
    }

    /** How strong the movement is right now: the sound's energy, or for a steady player mostly a fixed amount. */
    fun strength(): Double {
        val live = min(1.25, max(0.0, (show.energy.slow - 0.12) / 0.5))

        return live + (0.85 - live) * traits.steady
    }

    /**
     * What a sudden burst of sound adds, for the body only (trunk and knees): the head never jerks with it (the
     * maintainer, 2026-11-15: "the body can move like this but the nods are too much").
     */
    fun burst(): Double = traits.depth * 0.25 * show.energy.burst * strength() * (1 - traits.steady)

    /** The slow wandering at [t] (seconds): the head looks about and tilts, the trunk leans; applied on top of the pose. */
    fun apply(rig: Rig, t: Double) {
        val c = traits
        val alive = 0.5 + 0.5 * min(1.0, show.energy.slow / 0.6)

        rig.turn("head", Y_AXIS, c.look * alive * show.drift(t * 0.23, seed + 11))
        rig.turn("head", Z_AXIS, c.tilt * alive * show.drift(t * 0.31, seed + 12))
        rig.turn("chest", Z_AXIS, c.lean * alive * show.drift(t * 0.13, seed + 13))
        rig.turn("chest", X_AXIS, c.lean * 0.6 * alive * show.drift(t * 0.17, seed + 14))
    }
}
