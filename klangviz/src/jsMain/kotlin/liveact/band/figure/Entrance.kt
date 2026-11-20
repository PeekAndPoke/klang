/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.band.Entrance
import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.distanceTo
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The walk-in before the song: each player comes from off the stage to their place. The clock runs from -INTRO to 0
// (the song's start); a player's entrance sets where the figure stands, and the legs then step by the way made,
// whatever the rest of the body does (a guitarist walks in holding the guitar, the frontwoman with her trumpet).

/**
 * How a figure walks in at a moment: [going] (0 standing to 1 walking), [far] (the way made), [since] (seconds since
 * it arrived, negative before).
 */
data class WalkIn(val going: Double, val far: Double, val since: Double)

/** The pose of a rig at one moment: where its hips are, and every bone's turn by the bone's name. */
class CapturedPose(val hips: Vector3, val turns: Map<String, Quaternion>)

/**
 * Puts [rig] where its [entrance] has it at [t]: from the entrance's start, off the stage, to its place [home]
 * between the entrance's two times; a place above the floor (the riser) is stepped up to near the end. Returns how
 * it walks.
 */
fun enter(rig: Rig, entrance: Entrance, home: Place, t: Double): WalkIn {
    val t0 = entrance.start
    val t1 = entrance.end
    val u = min(1.0, max(0.0, (t - t0) / (t1 - t0)))
    val k = smooth(u)
    val from = vec(entrance.fromX, 0.0, entrance.fromZ)
    val to = vec(home.x, 0.0, home.z)
    val way = from.distanceTo(to)

    rig.root.position.copy(from).lerp(to, k)
    rig.root.position.y = home.y * smooth((u - 0.70) / 0.18)

    // walking, the figure faces its way; arriving, it turns to how it stands
    val heading = atan2(to.x - from.x, to.z - from.z)
    var d = home.turn - heading

    d = atan2(sin(d), cos(d))
    rig.root.rotation.y = heading + d * smooth((u - 0.80) / 0.20)

    val speed = if (u <= 0 || u >= 1) 0.0 else way * 6 * u * (1 - u) / (t1 - t0)

    return WalkIn(going = smooth(speed / 0.45), far = way * k, since = t - t1)
}

/** How long a step of the walk-in is, in the figure's size: each leg steps this far. */
private const val STEP = 0.42

/**
 * The steps' phase on the walk-in ([walk] from [enter]): by the way made, a whole number at every step of the left
 * foot, which lands ahead there (see [stepLegs]).
 */
fun strideOf(rig: Rig, walk: WalkIn): Double = walk.far / (STEP * rig.s / 0.6)

/**
 * The hips' motion on the walk-in ([walk] from [enter]) of one who walks with [gait] (see [HipMotion]): as much as
 * the walk-in has them move ([WalkKind.WalkIn]), with the legs' steps.
 */
fun walkInHips(rig: Rig, walk: WalkIn, gait: Gait): HipMotion = HipMotion.at(
    stride = strideOf(rig, walk),
    going = walk.going,
    amount = gait.hipsOn(WalkKind.WalkIn),
    s = rig.s,
    hipHalf = rig.hipHalf,
)

/**
 * The hips' motion of a stroll at [stride] (see [HipMotion.at]) for [rig]: [going] how far it walks, [amount] how
 * much its hips move.
 */
fun strollHips(rig: Rig, stride: Double, going: Double, amount: Double): HipMotion =
    HipMotion.at(stride = stride, going = going, amount = amount, s = rig.s, hipHalf = rig.hipHalf)

/**
 * Moves the hips of [rig] by [motion] (see [HipMotion]), and the belly and the chest answer: called after the
 * player's own move of the hips, before anything reaches for a place on the body. [shoulders]: the shoulders' own
 * turn against the legs, where the walk has one (see [HipMotion.SHOULDERS]), turned with the chest's answer.
 */
fun walkHips(rig: Rig, motion: HipMotion, shoulders: Double = 0.0) {
    rig.moveHips(vec(motion.sway, -motion.lower, 0.0))
    rig.turn("hips", Y_AXIS, motion.yaw)
    rig.turn("hips", Z_AXIS, motion.roll)
    rig.turn("belly", Z_AXIS, motion.waist)
    rig.turn("chest", Y_AXIS, motion.chestYaw + shoulders)
    rig.turn("chest", Z_AXIS, motion.chestRoll)
}

/** The legs step by the way made ([walk] from [enter]); the feet the pose had are kept as far as it stands. */
fun stepLegs(rig: Rig, walk: WalkIn) {
    if (walk.going < 0.002) {
        return
    }

    val s = rig.s
    val step = STEP * s
    val stride = strideOf(rig, walk)

    rig.update()

    for (side in Side.entries) {
        val sign = side.sign
        val phase = ((stride + (if (side == Side.L) 0.0 else 0.5)) % 1.0 + 1) % 1.0
        val air: Double? = if (phase < 0.6) null else (phase - 0.6) / 0.4
        val foot = rig.ankle.getValue(side).clone()

        foot.z += step * (if (air == null) 0.5 - phase / 0.6 else -0.5 + air)
        foot.y += if (air == null) 0.0 else 0.08 * s * sin(PI * air)

        val stepping = rig.root.localToWorld(foot)
        val standing = rig.bone("toe$side").getWorldPosition(vec())

        rig.foot(side, standing.lerp(stepping, walk.going), rig.dir(0.2 * sign, 0.0, 1.0))
    }
}

/**
 * A plain walk for a figure with nothing to hold up, who walks with [gait]: upright, the hips moving as the gait
 * has them on the walk-in (see [walkInHips]), the arms hanging and swinging against the legs.
 */
fun walkPose(rig: Rig, walk: WalkIn, gait: Gait) {
    val s = rig.s
    val swing = cos(2 * PI * strideOf(rig, walk)) * walk.going

    rig.reset()
    rig.update()
    rig.moveHips(vec(0.0, -0.012 * rig.pelvis.y, 0.0))
    walkHips(rig = rig, motion = walkInHips(rig = rig, walk = walk, gait = gait), shoulders = -HipMotion.SHOULDERS * swing)

    for (side in Side.entries) {
        val sign = side.sign
        val sh = rig.shoulder.getValue(side)
        val hand = vec(sh.x + 0.05 * sign * s, sh.y - 0.94 * rig.armLength, 0.05 * s - 0.16 * s * swing * sign)

        rig.hand(side, rig.root.localToWorld(hand), rig.dir(0.3 * sign, -0.3, -1.0))
    }

    stepLegs(rig, walk.copy(going = 1.0))
}

/** The pose of [rig] as it is now, to blend with another. */
fun capture(rig: Rig): CapturedPose {
    return CapturedPose(
        hips = rig.bone("hips").position.clone(),
        turns = rig.bones.mapValues { (_, bone) -> bone.quaternion.clone() },
    )
}

/** Sets [rig] between the poses [a] and [b], [w] of the way to [b]. */
fun blend(rig: Rig, a: CapturedPose, b: CapturedPose, w: Double) {
    for ((name, bone) in rig.bones) {
        bone.quaternion.copy(a.turns.getValue(name)).slerp(b.turns.getValue(name), w)
    }

    rig.bone("hips").position.copy(a.hips).lerp(b.hips, w)
    rig.update()
}
