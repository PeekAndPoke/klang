/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.figure.Mover
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.TALL
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.camera.HOME
import io.peekandpoke.klang.viz.liveact.camera.TOTAL_DRIFT_Y
import io.peekandpoke.klang.viz.liveact.camera.TOTAL_DRIFT_Z
import io.peekandpoke.klang.viz.liveact.headReach
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

// The trumpet player, who plays a tune of held notes (which of a song's parts that is, the cast says): raises the
// trumpet to the lips before a phrase, fingers the valves on every note, points the bell higher for a high note and
// lets it sink between phrases. The bell lights up at each note and fades. All places are in the figure's own measures.

/** How far from the middle line each foot is set when she walks, in her own measure. */
private const val NARROW = 0.045

/**
 * The raised arm before a phrase: it starts to go up [FROM] s before the first note, takes [RISE] s to get there,
 * and is down again [UNTIL] s before the note, coming down over [FALL] s.
 */
private object Raise {
    const val FROM = 4.2
    const val RISE = 0.8
    const val UNTIL = 1.1
    const val FALL = 0.8
}

/**
 * The frontwoman with her trumpet. [part]: her notes, sorted by time, as far as the song is known; the beat for the nod is the [show]'s.
 * [traits] and [seed] make her [Mover] (the prototype's fallback seed is 3; the band gives her her own). [looks]:
 * where her skills ask the head to look at a time (see `lookAt`, the last in rank first; none unless given): the
 * head turns to each as far as it is asked, each over the ones before it, only as far as she is free to look (never
 * with the trumpet at her lips, see [freeToLook]) and, but for a look at one behind her, as far as the place is in
 * sight. [camera]: where the camera is (a live vector the show keeps, as her skills' looks at it have it; where it
 * is not given, where the total stands): on a stroll to it she stares at it (see [StrollHome.stareAt]), over all her
 * other looks.
 */
class TrumpetPlayer(
    private val show: Show,
    val rig: Rig,
    private val part: Part<Note>,
    traits: Traits,
    seed: Double = 3.0,
    private val looks: (Double) -> List<Look> = { emptyList() },
    private val camera: Vector3 = HOME.pos.clone(),
) {
    /**
     * Where she is and how she walks: [at] her place on the stage, [turn] which way she faces, [speed] how fast she
     * goes, [going] 0 standing to 1 walking. Put from outside by her walk-in, or her own (her place, her strolls).
     */
    class Walk(val at: Vector3, val turn: Double, val speed: Double, val going: Double = 0.0)

    /**
     * What sounds at a moment: the [note] (null without any), whether it [sounding] (fading shortly after it ends),
     * how [high] it is (0 her lowest to 1 her highest), and how hard it is blown ([blow]).
     */
    class Sound(val note: Note?, val sounding: Double, val high: Double, val blow: Double)

    private companion object {
        /**
         * Back at her place from a stroll, she turns to the front within this many seconds. A guard only: her way
         * home arrives facing the front (see [StrollHome]), and the body's inertia ([StrollTurn]) has caught up by
         * then, so there is next to nothing left to turn.
         */
        const val TURN_HOME = 3.0

        /** A look out of her sight on a stroll lets go over this many seconds, as her looks fall (`SIRENE`). */
        const val LOOK_LET_GO = 1.6

        /** The moments before that it is let go from, this many seconds apart. */
        const val LOOK_STEP = 0.1

        /** How far her face is before her middle, and the room left before the camera, in her sizes. */
        const val FACE = 0.1
        const val ROOM = 0.1
    }

    /** Which way the stroll goes at one moment: how fast, how much it walks (0 to 1), and the turn into its way. */
    private class Facing(val speed: Double, val going: Double, val turn: Double)

    /**
     * A stretch in which she has nothing to play and strolls about the front of the stage, from [from] to [to]
     * seconds, her end known since [known] (see [StrollHome]: her route, and a way home in time). Which way she
     * faces on it is worked out once per stroll (see [StrollTurn]): she follows her way with a body's inertia.
     * [before]: the stroll it replaces, where her end changed while she strolled (the same start): up to [known] she
     * goes and faces as she did on it. Only what is needed is taken over, so no line of replaced strolls is kept.
     */
    private inner class Stroll(val from: Double, val to: Double, known: Double, before: Stroll? = null) {
        /** Where the stroll has her, off her place, in her sizes. */
        val way: StrollHome = StrollHome(route = route, from = from, to = to, notBefore = known, before = before?.way)

        private val turn: StrollTurn = StrollTurn(from = from, to = to, handOver = before?.turn, at = known) { x -> facing(this, x).turn }

        /** Which way she faces at [t], off the way she faces at her place. */
        fun turnAt(t: Double): Double = turn.at(t)
    }

    private val mover = Mover(show = show, seed = seed, traits = traits)

    /** Her way on a stroll, the same for every stroll (see [StrollRoute]): a stroll only shifts its time. */
    private val route: StrollRoute
    private val bpm: Double = show.bpm
    private val s: Double = rig.s

    /**
     * The last plan (see [planTrumpet]): where the song has forgotten its past, the next one goes on from until when
     * she played and from her last note of it.
     */
    private var planned: TrumpetPlan = TrumpetPlan()

    /** Her notes, as far as the song is known: the plan's. */
    private val notes: List<Note> get() = planned.notes

    /** Her lowest and her highest note so far: of those known, and of those the song has forgotten. */
    private val lowest: Int get() = planned.lowest
    private val highest: Int get() = planned.highest

    /** The trumpet she plays (see [Trumpet]). */
    val trumpet: Trumpet = Trumpet(rig)

    private val headRest: Quaternion

    /** Where she stands to play. */
    val home: Vector3

    /** Which way she faces at her place. */
    val homeTurn: Double

    /** Where a phrase begins: a note after more than six seconds of silence (and her first note). */
    val phrases: List<Double> get() = planned.phrases

    // where she stands to play, and the stretches in which she has nothing to play and strolls about the front of the
    // stage: from a little after a phrase to well before the next, so she is back in her place in time
    private var strolls: List<Stroll> = emptyList()

    init {
        rig.update()
        headRest = rig.bone("head").getWorldQuaternion(quat())

        home = rig.root.position.clone()
        homeTurn = rig.root.rotation.y
        route = StrollRoute(
            seed = show.seed,
            restTurn = homeTurn,
            closest = closestToCamera(chin = rig.bone("head").getWorldPosition(vec()).y),
        )

        part.refresh()
        plan(now = Double.NEGATIVE_INFINITY)
    }

    /**
     * Her phrases and her strolls, from the notes as far as they are known at [now] (see [planTrumpet]: a stroll
     * that has begun stays).
     */
    private fun plan(now: Double) {
        val before = strolls

        planned = planTrumpet(notes = part.events, now = now, before = planned)
        // the same stroll as before keeps what was worked out for it; one whose end changed goes on from where the
        // one it replaces had her (see [StrollHome])
        strolls = planned.strolls.map { stroll ->
            val old = before.firstOrNull { it.from == stroll.from }

            if (old != null && old.to == stroll.to) {
                old
            } else {
                Stroll(from = stroll.from, to = stroll.to, known = now, before = old)
            }
        }
    }

    /**
     * Where the stroll [k] has her at [t], off her place: her way through the front of the stage (see
     * [StrollRoute]: out from her place, about, back to it, and there she waits a while before the next round), and
     * in time a planned way home, so she is at her place, facing the front, as the stroll ends (see [StrollHome]).
     */
    private fun strollAt(k: Stroll, t: Double): Vector3 {
        val at = k.way.at(t)

        return vec(at.x * s, 0.0, at.z * s)
    }

    /**
     * The nearest she may come to the total's camera on a stroll (see [StrollRoute]), off her place in her sizes:
     * right before the lens, as close as her head stays whole in the wide shot. The total drifts a little
     * ([TOTAL_DRIFT_Z] toward the stage and [TOTAL_DRIFT_Y] up at most), so its nearest and highest place counts.
     * The frame's lower edge falls from the lens at half the lens's angle plus the camera's tilt down to what it
     * looks at, the upper edge rises at half the angle less the tilt: her chin (where her head's bone starts) stays
     * above the one, the top of her head ([TALL]) below the other. Her face is [FACE] of her size before her
     * middle, and [ROOM] more is left.
     */
    private fun closestToCamera(chin: Double): StrollRoute.Offset {
        val lensY = HOME.pos.y + TOTAL_DRIFT_Y
        val lensZ = HOME.pos.z - TOTAL_DRIFT_Z
        val tilt = atan2(HOME.pos.y - HOME.at.y, HOME.pos.z - HOME.at.z)
        val half = HOME.fov / 2 * PI / 180
        val top = home.y + TALL * rig.height
        val below = if (chin < lensY) (lensY - chin) / tan(half + tilt) else 0.0
        val above = if (top > lensY) (top - lensY) / tan(half - tilt) else 0.0
        val stand = lensZ - max(below, above) - (FACE + ROOM) * s

        return StrollRoute.Offset(x = (HOME.pos.x - home.x) / s, z = (stand - home.z) / s)
    }

    private fun facing(k: Stroll, x: Double): Facing {
        val ahead = strollAt(k, x + 0.2).sub(strollAt(k, x - 0.2)).multiplyScalar(1 / 0.4)
        val speed = ahead.length()
        // she looks where she goes, the more the faster she goes; standing, she faces the audience. Going away from it
        // she turns round and walks, her back to it: held sideways she slid backwards (the maintainer, 2026-11-16)
        val going = smooth(min(1.0, speed / (0.05 * s)))
        var heading = atan2(ahead.x, ahead.z) - homeTurn

        heading = atan2(sin(heading), cos(heading))

        return Facing(speed = speed, going = going, turn = heading * going)
    }

    /** Her place on the stage at [t], which way she walks, how fast, and how far she has walked in this stroll. */
    fun walkAt(t: Double): Walk {
        val k = strolls.firstOrNull { t > it.from && t < it.to }

        if (k == null) {
            // back from a stroll she may still be turned the way she came (she follows her way with a body's
            // inertia): she turns to the front over the next seconds, by the shorter way round, and does not snap
            val ended = strolls.lastOrNull { t >= it.to && t < it.to + TURN_HOME } ?: return Walk(at = home, turn = homeTurn, speed = 0.0)
            val left = ended.turnAt(ended.to)
            val turn = atan2(sin(left), cos(left)) * (1 - smooth((t - ended.to) / TURN_HOME))

            return Walk(at = home, turn = homeTurn + turn, speed = 0.0)
        }

        val now = facing(k, t)
        val turn = k.turnAt(t)

        return Walk(at = home.clone().add(strollAt(k, t)), turn = homeTurn + turn, speed = now.speed, going = now.going)
    }

    /** What sounds at [t]: the note, whether it sounds (fading shortly after it ends), how high it is, how hard it is blown. */
    fun soundAt(t: Double): Sound {
        val (before, after) = notes.around(t)
        val sounding = when {
            before != null && t < before.t + before.d -> 1.0
            before != null -> exp(-(t - before.t - before.d) / 0.08)
            else -> 0.0
        }
        val note = if (before != null && (sounding > 0.05 || after == null)) before else after ?: before
        val high = if (note != null) (note.midi - lowest).toDouble() / max(1, highest - lowest) else 0.5

        return Sound(note = note, sounding = sounding, high = high, blow = if (before != null) before.v * sounding else 0.0)
    }

    /**
     * Her stare at the [camera] on a stroll to it at [t] (see [StrollHome.stareAt]), as a still look, last so that it
     * lies over all her other looks; also as it is let go after the stroll's end. Null where there is none.
     */
    private fun stareAt(t: Double): Look? {
        val k = strolls.lastOrNull { t > it.from && t < it.to + StrollHome.STARE_FALL } ?: return null
        val share = k.way.stareAt(t)

        return if (share > 0) Look(share = share, at = camera, still = true) else null
    }

    /** A [look] as far as she takes it ([share]), toward [yaw]: off the way she faces, as far as a head turns. */
    private class Turn(val look: Look, val share: Double, val yaw: Double)

    /**
     * The looks her skills ask for at [t], as far as she takes each: as far as she is free to look ([freeToLook]),
     * and as far as the place is in sight where she stands and faces ([walk]); a look at one behind her goes over
     * her shoulder, and lets go where they are straight behind her. Where she turns away from a look on a stroll,
     * the head lets go of it slowly, over [LOOK_LET_GO] seconds, as a look falls ([lookReach]: by where she stood
     * and faced over those seconds, from her own way, [walkAt]); not where she is put from outside ([put]), whose
     * way before is not hers to know. Straight behind her it lets go all the same.
     */
    private fun looksAt(t: Double, walk: Walk, put: Boolean): List<Turn> {
        val free = freeToLook(notes = notes, t = t)

        if (free <= 0) {
            return emptyList()
        }

        // where she stood and faced a moment ago, at fixed times (so the head lets go smoothly as t goes on): worked
        // out only where she has looks, and once for all of them
        val grid = floor(t / LOOK_STEP) * LOOK_STEP
        val past = HashMap<Int, Walk>()

        fun yawOf(look: Look, from: Walk): Double {
            val off = atan2(look.at.x - from.at.x, look.at.z - from.at.z) - from.turn

            return atan2(sin(off), cos(off))
        }

        return (looks(t) + listOfNotNull(stareAt(t))).map { look ->
            val yaw = yawOf(look = look, from = walk)
            val before: ((Int) -> Double)? = if (put) {
                null
            } else {
                { k -> yawOf(look = look, from = past.getOrPut(k) { walkAt(grid - k * LOOK_STEP) }) }
            }
            val reach = lookReach(
                t = t,
                yaw = yaw,
                reach = { angle -> headReach(angle = angle, behind = look.behind) },
                yawBefore = before,
                fall = LOOK_LET_GO,
                step = LOOK_STEP,
            )
            val share = look.share * free * reach

            Turn(look = look, share = share, yaw = max(-1.25, min(1.25, yaw)))
        }.filter { it.share > 0 }
    }

    /** [put]: where she is put from outside (her walk-in); else she is at her place, or strolling. */
    fun update(t: Double, put: Walk? = null) {
        if (part.refresh()) {
            plan(now = t)
        }

        val (before, after) = notes.around(t)

        // is a note sounding, how high is it, and is the trumpet up: up from a little before a phrase to a little after
        val now = soundAt(t)
        val sounding = now.sounding
        val note = now.note
        // the body goes with the tune as a whole, never with a single note: how high she plays and how hard she blows
        // are taken over a good half second round this moment, so a new note cannot jerk her
        var high = 0.0
        var breath = 0.0
        var weights = 0.0

        for (i in -6..6) {
            val weight = (7 - abs(i)).toDouble()
            val then = soundAt(t + i * 0.06)

            high += weight * then.high
            breath += weight * then.blow
            weights += weight
        }

        high /= weights
        breath /= weights

        val up = trumpetUp(notes = notes, t = t)
        val blow = now.blow

        // strolling: the whole figure goes along her way and turns into it; the legs step by the way made
        val walk = put ?: walkAt(t)
        // placed before the looks: a look may go by where she stands and faces (`LookPlaces.nowhere`)
        rig.root.position.copy(walk.at)
        rig.root.rotation.y = walk.turn

        // where her skills ask the head to look, each as far as she may: a stare calms the nod and the wander
        val turns = looksAt(t = t, walk = walk, put = put != null)
        val calm = turns.filter { it.look.still }.maxOfOrNull { it.share } ?: 0.0

        val beat = t * bpm / 60
        val nod = mover.nod(beat) * (1 - 0.7 * up) * (1 - 0.7 * calm)
        val body = nod + mover.burst()
        val parts = mover.parts
        val sway = sin(PI * beat / 2) * parts.sway
        val back = up * (0.08 + 0.16 * high) + 0.04 * breath

        val going = walk.going
        // she walks in time: a step every two beats, so two steps a bar; a step is as long as her pace asks for. And she
        // dances a little as she goes: the hips dip on every beat and sway onto the standing leg, the shoulders turn
        // against the hips, the arms swing wide
        val stride = beat / 4
        val step = min(0.5 * s, walk.speed * 0.6 * 4 * 60 / bpm)
        val swing = cos(2 * PI * stride)                        // 1 as the left foot lands ahead
        val dip = going * (0.5 + 0.5 * cos(2 * PI * beat))      // 1 on the beat

        rig.reset()
        rig.update()
        rig.moveHips(
            vec(
                0.025 * sway * s * (1 - going) + 0.035 * s * going * sin(2 * PI * stride),
                -(0.010 + 0.014 * body * parts.knees * (1 - 0.6 * going) + 0.008 * breath) * rig.pelvis.y - 0.010 * s * dip,
                -0.02 * back * s,
            )
        )
        rig.turn("hips", Y_AXIS, 0.10 * going * swing)
        rig.turn("chest", Y_AXIS, -0.26 * going * swing)
        rig.turn("chest", Z_AXIS, 0.05 * going * sin(2 * PI * stride))
        // the frontman stands tall: chest out, head up, hardly a bend in the knees
        rig.turn("belly", X_AXIS, -0.03 - 0.5 * back + 0.025 * body * parts.trunk)
        rig.turn("chest", X_AXIS, -0.35 * back)
        rig.turn("chest", Z_AXIS, -0.04 * sway)
        // she plays across the stage, not at the audience: the bell is beside her face, never before it
        rig.turn("chest", Y_AXIS, 0.30 * up)
        rig.turn("head", Y_AXIS, 0.38 * up)
        rig.turn("head", X_AXIS, -0.04 * (1 - up) + 0.09 * nod * parts.head - 0.45 * back)
        mover.apply(rig = rig, t = t, calm = 0.8 * calm)

        if (turns.isNotEmpty()) {
            // the chest turns a little with the looks, the face the rest of the way, each over the ones before
            var chest = 0.0

            for (turn in turns) {
                chest += (0.3 * turn.yaw - chest) * turn.share
            }

            rig.turn("chest", Y_AXIS, chest)

            for (turn in turns) {
                rig.faceTo(turn.look.at, turn.share)
            }
        }

        for (side in Side.entries) {
            val sign = side.sign
            val foot = rig.ankle.getValue(side).clone()

            // standing, the feet are a little apart; walking, they come close to one line, the more the faster she
            // goes: a narrow, elegant step (the maintainer, 2026-11-16: "legs closer together ... more elegant",
            // "standing in one place stays as is")
            foot.x += (sign * NARROW * s - foot.x) * going + 0.06 * sign * s * (1 - going)

            // a step: on the floor the foot goes back under her, then it lifts and swings ahead
            val phase = ((stride + (if (side == Side.L) 0.0 else 0.5)) % 1.0 + 1) % 1.0
            val air: Double? = if (phase < 0.6) null else (phase - 0.6) / 0.4

            foot.z += going * step * (if (air == null) 0.5 - phase / 0.6 else -0.5 + air)
            foot.y += going * (if (air == null) 0.0 else 0.07 * s * sin(PI * air))
            rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.3 * sign, 0.0, 1.0))
        }

        // the trumpet: at the lips, pointing where the face points; lowered before the chest between phrases
        val head = rig.bone("head")
        val turned = head.getWorldQuaternion(quat()).multiply(headRest.clone().invert())
        val lips = head.getWorldPosition(vec()).add(rig.dir(0.0, 0.045, 0.100).multiplyScalar(0.110 * s).applyQuaternion(turned))
        val ahead = rig.dir(0.0, -0.20, 1.0).applyQuaternion(turned)
        // between phrases, standing, the right arm hangs loose, nearly straight, and the trumpet hangs from the hand beside her
        // thigh, the bell down: nothing is held up
        val hip = rig.bone("hips").getWorldPosition(vec())
        val hang = rig.dir(-0.03, -0.97, 0.22).normalize()
        val shoulderR = rig.bone("elbowR").getWorldPosition(vec())
        val loose = shoulderR.clone()
            .addScaledVector(rig.dir(1.0, 0.0, 0.0), -0.11 * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), -0.93 * rig.armLength)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), (0.07 + 0.13 * going * swing) * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), 0.05 * s * going * abs(swing))
            // walking, the forearm comes up a little: only a little on this arm, it carries the trumpet
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), 0.045 * s * going)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), 0.04 * s * going)
        val hangUp = rig.dir(0.0, 0.22, 0.97)
        val hangY = hangUp.addScaledVector(hang, -hangUp.dot(hang)).normalize()
        val hangZ = vec().crossVectors(hang, hangY)
        val resting = loose.clone()
            .addScaledVector(hang, -0.17 * s)
            .addScaledVector(hangY, -0.035 * s)
            .addScaledVector(hangZ, -0.085 * s)
        val at = resting.lerp(lips, up)
        val axisX = hang.clone().lerp(ahead, up).normalize()
        val upward = rig.dir(0.0, 1.0, 0.0)
        val axisY = upward.addScaledVector(axisX, -upward.dot(axisX)).normalize()
        val axisZ = vec().crossVectors(axisX, axisY)

        trumpet.group.position.copy(at)
        trumpet.group.quaternion.setFromRotationMatrix(mat4().makeBasis(axisX, axisY, axisZ))

        fun place(x: Double, y: Double, z: Double): Vector3 =
            at.clone().addScaledVector(axisX, x * s).addScaledVector(axisY, y * s).addScaledVector(axisZ, z * s)

        // the right hand on the valves from the right, the left hand round them from the left and below; elbows out.
        // Z points to the player's right when the trumpet points ahead.
        val valvesTop = place(0.19, 0.05, 0.0)
        val wristR = place(0.17, 0.035, 0.085)

        rig.hand(Side.R, wristR, rig.dir(-0.9, -0.5, 0.1).lerp(rig.dir(-0.3, -0.3, -1.0), 1 - up))

        // playing, the hand turns onto the valves; hanging, it just follows the arm
        val forearm = wristR.clone().sub(rig.bone("wristR").getWorldPosition(vec())).normalize()

        rig.point("handR", forearm.lerp(valvesTop.clone().sub(wristR).normalize(), smooth(up)))

        // hanging, the trumpet is where the hand really is (the arm may not reach where it was meant to hang): its
        // valves lie in the palm, whatever her build
        if (up < 1) {
            rig.update()

            val hand = rig.bone("handR")
            val wrist = hand.getWorldPosition(vec())
            val along = vec(0.0, 1.0, 0.0).applyQuaternion(hand.getWorldQuaternion(quat()))
            val palm = wrist.addScaledVector(along, 0.05 * s)

            trumpet.group.position.add(palm.sub(place(0.19, 0.0, 0.03)).multiplyScalar(1 - smooth(up)))
        }

        val grip = place(0.20, -0.02, 0.0)
        // the left hand comes to the trumpet only to play; else that arm hangs
        val hanging = hip.clone()
            .addScaledVector(rig.dir(1.0, 0.0, 0.0), rig.shoulder.getValue(Side.L).x + 0.05 * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), -0.10 * s)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), (0.04 + 0.03 * sway * (1 - going) - 0.20 * going * swing) * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), 0.07 * s * going * abs(swing))
            .addScaledVector(rig.dir(1.0, 0.0, 0.0), 0.04 * s * going)
            // walking, the forearm comes up: more on this arm, the free one (the maintainer, 2026-11-18: "bend up
            // her forearms a little ... more on the arm that is free than on the one carrying something")
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), 0.11 * s * going)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), 0.09 * s * going)
        val wristL = hanging.clone().lerp(place(0.18, -0.05, -0.085), up)
        // before she starts to play again after a silence, she raises the left arm high for a moment: here I come
        // (a first pose asked of a body from outside, to try that out: the maintainer, 2026-11-16). It is up from
        // Raise.FROM seconds before the phrase's first note and down again in time for the hand to take the trumpet.
        val next = phrases.firstOrNull { start -> start > t - 0.2 }
        val toPhrase = if (next == null) 99.0 else next - t
        val raised = smooth((Raise.FROM - toPhrase) / Raise.RISE) * smooth((toPhrase - Raise.UNTIL) / Raise.FALL)
        val shoulderL = rig.bone("elbowL").getWorldPosition(vec())
        val aloft = shoulderL.clone().addScaledVector(rig.dir(0.22, 0.95, 0.16).normalize(), 0.97 * rig.armLength)

        wristL.lerp(aloft, raised)
        rig.hand(
            Side.L,
            wristL,
            rig.dir(0.9, -0.6, 0.1).lerp(rig.dir(0.3, -0.3, -1.0), 1 - up).lerp(rig.dir(1.0, -0.3, -0.4), raised),
        )

        when {
            raised > 0.5 -> rig.point("handL", aloft.clone().sub(shoulderL))
            up > 0.5 -> rig.point("handL", grip.clone().sub(wristL))
            else -> Unit
        }

        // the valves go down with the note's fingering
        trumpet.press(note = note, sounding = sounding)

        // the bell's light fades as the strings' does: an exponential fall from the note's start, a quicker one from
        // where it ends (the next note at the latest)
        var bell = 0.0

        if (before != null) {
            val age = t - before.t
            val held = min(before.d, if (after != null) after.t - before.t else 9.0)

            bell = max(0.6, before.v) * exp(-age / 0.25 - max(0.0, age - held) / 0.06)
        }

        trumpet.glow(bell)
        rig.eyes?.let { eyes -> eyes.emissiveIntensity = 2 + 14 * blow }
    }
}
