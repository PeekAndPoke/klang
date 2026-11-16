/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.createCircleGeometry
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Vector2
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// The trumpet player (the song's `trommel` part, the "Gummieimer", which sounds like a held melody): raises the
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

/** Which of the three valves are down, by the note: not a real fingering, but it changes as one does. */
private val FINGERS: List<List<Int>> = listOf(
    listOf(0, 0, 0), listOf(0, 1, 0), listOf(1, 0, 0), listOf(1, 1, 0), listOf(0, 1, 1), listOf(1, 0, 1), listOf(1, 1, 1),
)

// TYPING: belongs in the workshop
private var MaterialParameters.emissive: Color
    get() = asDynamic().emissive.unsafeCast<Color>()
    set(value) {
        asDynamic().emissive = value
    }

// TYPING: belongs in the workshop
private var MaterialParameters.emissiveIntensity: Double
    get() = asDynamic().emissiveIntensity.unsafeCast<Double>()
    set(value) {
        asDynamic().emissiveIntensity = value
    }

/**
 * The frontwoman with her trumpet. [notes]: her notes, sorted by time; the beat for the nod is the [show]'s.
 * [traits] and [seed] make her [Mover] (the prototype's fallback seed is 3; the band gives her her own).
 */
class TrumpetPlayer(
    private val show: Show,
    val rig: Rig,
    val notes: List<Note>,
    traits: Traits,
    seed: Double = 3.0,
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
     * A stretch in which he has nothing to play and strolls about the front of the stage, from [from] to [to]
     * seconds. Which way he faces, worked out once per stroll (here when it is made) in tenths of a second: the
     * direction of his way jumps where he turns round or nearly stands, so he follows it with a body's inertia, never
     * faster than a calm turn.
     */
    private inner class Stroll(val from: Double, val to: Double) {
        val turns: List<Double> = buildList {
            var now = 0.0
            var x = from
            val end = min(to, from + 1500) + 0.2

            while (x < end) {
                // by the shorter way round, and at most 57 degrees a second
                val want = facing(this@Stroll, x).turn - now

                now += max(-0.10, min(0.10, atan2(sin(want), cos(want)) * 0.2))
                add(now)
                x += 0.1
            }
        }
    }

    private val mover = Mover(show = show, seed = seed, traits = traits)
    private val bpm: Double = show.bpm
    private val s: Double = rig.s

    /** Her lowest and her highest note. */
    private val lowest: Int = notes.minOfOrNull { it.midi } ?: 60
    private val highest: Int = notes.maxOfOrNull { it.midi } ?: 72

    private val brass: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x3a2c12
            roughness = 0.28
            metalness = 0.95
            side = MaterialSide.DoubleSide
        }
    )

    private val valves = mutableListOf<Mesh>()

    // the bell's light has its own colour, a turquoise (kept under full strength: brighter, it washes out to white)
    private val mouth: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x000000
            emissive = Three.addon.createColor(0x30ffd0)
            emissiveIntensity = 0.0
            side = MaterialSide.DoubleSide
        }
    )

    /** The trumpet, in its own space: x from the mouthpiece to the bell, y up. It stands in the figure's scene. */
    val trumpet: Group = buildTrumpet()

    private val headRest: Quaternion

    /** Where she stands to play. */
    val home: Vector3

    /** Which way she faces at her place. */
    val homeTurn: Double

    /** Where a phrase begins: a note after six seconds of silence or more (and her first note). */
    val phrases: List<Double>

    // where he stands to play, and the stretches in which he has nothing to play and strolls about the front of the
    // stage: from a little after a phrase to well before the next, so he is back in his place in time
    private val strolls: List<Stroll>

    init {
        rig.update()
        headRest = rig.bone("head").getWorldQuaternion(quat())

        home = rig.root.position.clone()
        homeTurn = rig.root.rotation.y

        phrases = notes.filterIndexed { i, n -> i == 0 || n.t - (notes[i - 1].t + notes[i - 1].d) > 6 }.map { it.t }

        val found = mutableListOf<Stroll>()
        var free = 3.0

        for (i in 0..notes.size) {
            val next = if (i < notes.size) notes[i].t else 1e9

            // he stands a good while first (the maintainer, 2026-11-16): twelve seconds after his last note, or after the start
            if (next - free > 30) {
                found.add(Stroll(from = free + 12, to = min(next - 4, free + 12 + 3600)))
            }

            if (i < notes.size) {
                free = max(free, notes[i].t + notes[i].d)
            }
        }

        strolls = found
    }

    private fun buildTrumpet(): Group {
        val g = Three.addon.createGroup()

        fun tube(from: Double, to: Double, r: Double): Mesh {
            val m = Three.addon.createMesh(Three.addon.createCylinderGeometry(r * s, r * s, (to - from) * s, 10), brass)

            m.rotation.z = -PI / 2
            m.position.x = (from + to) / 2 * s

            return m
        }

        g.add(tube(from = 0.0, to = 0.05, r = 0.010))
        g.add(tube(from = 0.03, to = 0.38, r = 0.0065))

        val profile = mutableListOf<Vector2>()

        for (i in 0..10) {
            val u = i / 10.0

            profile.add(Three.addon.createVector2((0.007 + 0.058 * u.pow(2.6)) * s, u * 0.14 * s))
        }

        val lathe = Three.addon.create<BufferGeometry>("LatheGeometry", profile.toTypedArray(), 24)
        val bell = Three.addon.createMesh(lathe, brass)

        bell.rotation.z = -PI / 2
        bell.position.x = 0.36 * s
        g.add(bell)

        // the loop under the tube, and the three valves
        val loop = Three.addon.createMesh(Three.addon.createTorusGeometry(0.035 * s, 0.006 * s, 8, 20), brass)

        loop.scale.set(2.6, 1.0, 1.0)
        loop.position.set(0.20 * s, -0.04 * s, 0.0)
        g.add(loop)

        for (i in 0 until 3) {
            val casing = Three.addon.createMesh(Three.addon.createCylinderGeometry(0.011 * s, 0.011 * s, 0.085 * s, 10), brass)

            casing.position.set((0.165 + i * 0.027) * s, -0.02 * s, 0.0)

            val cap = Three.addon.createMesh(Three.addon.createCylinderGeometry(0.008 * s, 0.008 * s, 0.03 * s, 8), brass)

            cap.position.set((0.165 + i * 0.027) * s, 0.035 * s, 0.0)
            g.add(casing)
            g.add(cap)
            valves.add(cap)
        }

        val disc = Three.addon.createMesh(Three.addon.createCircleGeometry(0.034 * s, 24), mouth)

        disc.rotation.y = PI / 2
        disc.position.x = 0.465 * s
        g.add(disc)

        g.traverse { o ->
            if (o.isMesh) {
                o.castShadow = true
            }
        }

        val scene = rig.root.parent ?: error("The figure must stand in a scene before it is given its trumpet")

        scene.add(g)

        return g
    }

    /** Where the stroll [k] has him at [t], off his place: a slow wandering that leaves from his place and comes back to it. */
    private fun strollAt(k: Stroll, t: Double): Vector3 {
        val ease = ::smooth
        // a stroll is rounds: out from his place, about, back to it, and there he waits a while before the next round.
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

    /** His place on the stage at [t], which way he walks, how fast, and how far he has walked in this stroll. */
    fun walkAt(t: Double): Walk {
        val k = strolls.firstOrNull { t > it.from && t < it.to } ?: return Walk(at = home, turn = homeTurn, speed = 0.0)
        val now = facing(k, t)
        val i = min(k.turns.size - 2, max(0, floor((t - k.from) / 0.1).toInt()))
        val u = min(1.0, max(0.0, (t - k.from) / 0.1 - i))
        val turn = k.turns[i] + (k.turns[i + 1] - k.turns[i]) * u

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

    /** [put]: where she is put from outside (her walk-in); else she is at her place, or strolling. */
    fun update(t: Double, put: Walk? = null) {
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

        val until = if (after != null) after.t - t else 99.0
        val since = if (before != null) t - before.t - before.d else 99.0
        val up = max(smooth(1 - (until - 0.15) / 0.7), 1 - smooth((since - 0.9) / 0.8))
        val blow = now.blow

        val beat = t * bpm / 60
        val nod = mover.nod(beat) * (1 - 0.7 * up)
        val body = nod + mover.burst()
        val parts = mover.parts
        val sway = sin(PI * beat / 2) * parts.sway
        val back = up * (0.08 + 0.16 * high) + 0.04 * breath

        // strolling: the whole figure goes along his way and turns into it; the legs step by the way made
        val walk = put ?: walkAt(t)

        rig.root.position.copy(walk.at)
        rig.root.rotation.y = walk.turn

        val going = walk.going
        // he walks in time: a step every two beats, so two steps a bar; a step is as long as his pace asks for. And he
        // dances a little as he goes: the hips dip on every beat and sway onto the standing leg, the shoulders turn
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
        // he plays across the stage, not at the audience: the bell is beside his face, never before it
        rig.turn("chest", Y_AXIS, 0.30 * up)
        rig.turn("head", Y_AXIS, 0.38 * up)
        rig.turn("head", X_AXIS, -0.04 * (1 - up) + 0.09 * nod * parts.head - 0.45 * back)
        mover.apply(rig, t)

        for (side in Side.entries) {
            val sign = side.sign
            val foot = rig.ankle.getValue(side).clone()

            // standing, the feet are a little apart; walking, they come close to one line, the more the faster she
            // goes: a narrow, elegant step (the maintainer, 2026-11-16: "legs closer together ... more elegant",
            // "standing in one place stays as is")
            foot.x += (sign * NARROW * s - foot.x) * going + 0.06 * sign * s * (1 - going)

            // a step: on the floor the foot goes back under him, then it lifts and swings ahead
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
        // between phrases the right arm hangs loose, nearly straight, and the trumpet hangs from the hand beside his
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

        trumpet.position.copy(at)
        trumpet.quaternion.setFromRotationMatrix(mat4().makeBasis(axisX, axisY, axisZ))

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

            trumpet.position.add(palm.sub(place(0.19, 0.0, 0.03)).multiplyScalar(1 - smooth(up)))
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
        val fingers = if (note != null && sounding > 0.05) FINGERS[((note.midi % 7) + 7) % 7] else listOf(0, 0, 0)

        valves.forEachIndexed { i, cap -> cap.position.y = (0.035 - 0.014 * fingers[i]) * s }

        // the bell's light fades as the strings' does: an exponential fall from the note's start, a quicker one from
        // where it ends (the next note at the latest)
        var bell = 0.0

        if (before != null) {
            val age = t - before.t
            val held = min(before.d, if (after != null) after.t - before.t else 9.0)

            bell = max(0.6, before.v) * exp(-age / 0.25 - max(0.0, age - held) / 0.06)
        }

        mouth.emissiveIntensity = 1.6 * bell
        rig.eyes?.let { eyes -> eyes.emissiveIntensity = 2 + 14 * blow }
    }
}
