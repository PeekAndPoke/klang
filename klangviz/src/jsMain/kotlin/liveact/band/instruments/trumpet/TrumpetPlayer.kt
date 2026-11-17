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
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.Look
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
 * sight.
 */
class TrumpetPlayer(
    private val show: Show,
    val rig: Rig,
    private val part: Part<Note>,
    traits: Traits,
    seed: Double = 3.0,
    private val looks: (Double) -> List<Look> = { emptyList() },
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

    /** Which way the stroll goes at one moment: how fast, how much it walks (0 to 1), and the turn into its way. */
    private class Facing(val speed: Double, val going: Double, val turn: Double)

    /**
     * A stretch in which she has nothing to play and strolls about the front of the stage, from [from] to [to]
     * seconds. Which way she faces is worked out once per stroll in tenths of a second, as far as it is asked for: the
     * direction of her way jumps where she turns round or nearly stands, so she follows it with a body's inertia, never
     * faster than a calm turn.
     */
    private inner class Stroll(val from: Double, val to: Double) {
        private val turns = mutableListOf<Double>()
        private var now = 0.0
        private var x = from
        private val end = min(to, from + 1500) + 0.2

        /** Which way she faces at [t], off the way she faces at her place. */
        fun turnAt(t: Double): Double {
            val at = max(0, floor((t - from) / 0.1).toInt())

            while (turns.size < at + 2 && x < end) {
                // by the shorter way round, and at most 57 degrees a second
                val want = facing(this, x).turn - now

                now += max(-0.10, min(0.10, atan2(sin(want), cos(want)) * 0.2))
                turns.add(now)
                x += 0.1
            }

            val i = min(turns.size - 2, at)
            val u = min(1.0, max(0.0, (t - from) / 0.1 - i))

            return turns[i] + (turns[i + 1] - turns[i]) * u
        }
    }

    private val mover = Mover(show = show, seed = seed, traits = traits)
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
        // the same stroll as before keeps what was worked out for it
        strolls = planned.strolls.map { stroll ->
            before.firstOrNull { it.from == stroll.from && it.to == stroll.to } ?: Stroll(from = stroll.from, to = stroll.to)
        }
    }

    /** Where the stroll [k] has her at [t], off her place: a slow wandering that leaves from her place and comes back to it. */
    private fun strollAt(k: Stroll, t: Double): Vector3 {
        val ease = ::smooth
        // a stroll is rounds: out from her place, about, back to it, and there she waits a while before the next round.
        // The rounds differ in length, so it is no clockwork
        // (under the show's seed: the rounds start elsewhere in their order, and her way is another one)
        val z = show.seed
        val rounds = listOf(listOf(47, 61, 53, 69), listOf(61, 53, 69, 47), listOf(53, 69, 47, 61), listOf(69, 47, 61, 53))[z % 4]
        var x = t - k.from
        var round = 0

        while (x > rounds[round % rounds.size]) {
            x -= rounds[round % rounds.size]
            round++
        }

        val length = rounds[round % rounds.size]
        // at her place she stands a good while (the maintainer, 2026-11-16: "stand still more between walking")
        val wait = 20
        val out = ease(x / 6) * ease((length - wait - x) / 6) * ease((k.to - t) / 7)
        // on her way she stops too: seven seconds of walking, then four of standing where she is, and on
        val leg = (t - k.from) / 11
        val tau = 7 * (floor(leg) + ease((leg - floor(leg)) * 11 / 7))
        // toward the audience: a little about, and in some rounds (about two in five, by the seed) she comes right
        // up to the camera in the round's middle, stays a moment and goes back
        // (the sum as a floating number, as the prototype has it: a large seed must not wrap round)
        val close = if (((round * 7.0 + z * 3.0) % 5) < 2) {
            3.4 * s * ease((x - 0.22 * length) / (0.2 * length)) * ease((0.72 * length - x) / (0.2 * length)) *
                ease((k.to - t) / 9)
        } else {
            0.0
        }

        return vec(
            out * (0.60 * sin(0.15 * tau + z * 1.7) + 0.18 * sin(0.34 * tau + 1.0 + z * 2.3) + 0.30) * s,
            0.0,
            out * (0.18 * sin(0.21 * tau + 2.0 + z * 0.9) + 0.30) * s + close,
        )
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
        val k = strolls.firstOrNull { t > it.from && t < it.to } ?: return Walk(at = home, turn = homeTurn, speed = 0.0)
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

    /** A [look] as far as she takes it ([share]), toward [yaw]: off the way she faces, as far as a head turns. */
    private class Turn(val look: Look, val share: Double, val yaw: Double)

    /**
     * The looks her skills ask for at [t], as far as she takes each: as far as she is free to look ([freeToLook]),
     * and as far as the place is in sight where she stands and faces ([walk]); a look at one behind her goes over
     * her shoulder, and lets go where they are straight behind her.
     */
    private fun looksAt(t: Double, walk: Walk): List<Turn> {
        val free = freeToLook(notes = notes, t = t)

        if (free <= 0) {
            return emptyList()
        }

        return looks(t).map { look ->
            val off = atan2(look.at.x - walk.at.x, look.at.z - walk.at.z) - walk.turn
            val yaw = atan2(sin(off), cos(off))
            val share = look.share * free * headReach(angle = yaw, behind = look.behind)

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
        // where her skills ask the head to look, each as far as she may: a stare calms the nod and the wander
        val turns = looksAt(t = t, walk = walk)
        val calm = turns.filter { it.look.still }.maxOfOrNull { it.share } ?: 0.0

        val beat = t * bpm / 60
        val nod = mover.nod(beat) * (1 - 0.7 * up) * (1 - 0.7 * calm)
        val body = nod + mover.burst()
        val parts = mover.parts
        val sway = sin(PI * beat / 2) * parts.sway
        val back = up * (0.08 + 0.16 * high) + 0.04 * breath

        rig.root.position.copy(walk.at)
        rig.root.rotation.y = walk.turn

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
        // between phrases the right arm hangs loose, nearly straight, and the trumpet hangs from the hand beside her
        // thigh, the bell down: nothing is held up
        val hip = rig.bone("hips").getWorldPosition(vec())
        val hang = rig.dir(-0.03, -0.97, 0.22).normalize()
        val shoulderR = rig.bone("elbowR").getWorldPosition(vec())
        val loose = shoulderR.clone()
            .addScaledVector(rig.dir(1.0, 0.0, 0.0), -0.11 * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), -0.93 * rig.armLength)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), (0.07 + 0.13 * going * swing) * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), 0.05 * s * going * abs(swing))
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
