/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.figure.Mover
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.members.Bearing
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.headReach
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.song.Strum
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.angleTo
import io.peekandpoke.klang.workshop.threejs.applyAxisAngle
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.klang.workshop.threejs.worldToLocal
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

// The string players: a bass or a guitar on a strap. The left hand is where a player's would be for the note (the
// string and the fret of the instrument's real tuning, see Tuning), the right hand plucks (bass) or strums down and
// up (guitar) on every note; the strings light up when played.
// All places are in the figure's own measures; the instrument hangs from the trunk and moves with it.

// how a played string's light falls, in seconds to 1/e: while the note rings, and once it has ended
private const val RING = 0.25
private const val DAMP = 0.06

/** How far along the neck the fingers reach from the hand's own place, in the figure's measure. */
private const val REACH = 0.04

/** One pluck or strum, as this player plays it (see [planStrings]): where the hands go for it and which strings sound. */
private class Pluck(
    override val t: Double,
    val v: Double,
    val d: Double,
    /** The fingers' place along the neck, from the nut: at the frets they press. */
    val along: Double,
    /** The hand's own place along the neck: the middle of the four frets its fingers reach in its position. */
    val hold: Double,
    /** Where the fingers' strings lie across the instrument, drawn two and a half times as far apart as the strings are. */
    val across: Double,
    /** The strings that sound, the lowest is 0. */
    val strings: List<Int>,
    /** Where the right hand plays across the strings: the middle of those that sound. */
    val pick: Double,
    /** How wide the right hand goes through them: 1 for several strings, less for one. */
    val wide: Double,
    /** Which way the right hand goes through the strings: 1 down, -1 up. */
    val way: Double,
) : Timed

/**
 * The hands' places at one moment: the fingers [along] the neck and [across] it, the left hand's own place along it
 * ([hold]), where the right hand [pick]s and how [wide].
 */
private class Fret(val along: Double, val hold: Double, val across: Double, val pick: Double, val wide: Double)

/**
 * A string player: a strung instrument of the [design] (a bass, a guitar) on a strap, played by [rig].
 *
 * [notes]: sorted by time, one per pluck or strum; the nod goes by the [show]'s tempo. [traits]: how the player
 * moves with the music, with the show's chance under [seed] (the player's own; the prototype's fallback is 4, the
 * band gives each player one of their own). [bearing]: how the player carries themselves over the instrument, the
 * member's own (see [Bearing]), or a plain one where none is given; [outline]: the body's
 * outline, the [design]'s own unless given; [looks]: where the member's skills ask the head to look at a time (see
 * `lookAt`, the last in rank first; none unless given): the head comes up from the instrument and turns to each as
 * far as it is asked, each over the ones before it;
 * [straighten]: when the player straightens up from the instrument (null: never; `?straight=1` holds everyone
 * straight, to look at the pose).
 */
class StringPlayer(
    private val show: Show,
    private val rig: Rig,
    private val design: StringDesign,
    private val part: Part<Strum>,
    traits: Traits,
    private val seed: Double = 4.0,
    bearing: Bearing? = null,
    outline: BodyOutline = design.outline,
    private val looks: (Double) -> List<Look> = { emptyList() },
    private val straighten: Straighten? = Straighten(every = 21.0, chance = 0.55),
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)

    /** How the trunk leans (back is negative), how much the head goes, how far the hips dip on the beat. */
    private val lean: Double = bearing?.lean ?: 0.06
    private val bang: Double = bearing?.bang ?: 0.2
    private val bounce: Double = bearing?.bounce ?: 0.03
    private val step: Double = bearing?.step ?: 0.0

    /**
     * When this player's stretches of straightening up begin, in seconds into the song: each player's at another
     * moment, so they do not all rise together, and nobody in the song's first seconds.
     */
    private val straightenFrom: Double = straighten?.let { it.every * (0.3 + 0.7 * show.own(0, seed + 32.5)) } ?: 0.0
    private val s = rig.s

    /** The instrument on the strap (see [StrungInstrument]). */
    val instrument: StrungInstrument = StrungInstrument(rig = rig, design = design, outline = outline)

    private val bridge = instrument.bridge
    private val nut = instrument.nut

    private var notes: List<Pluck> = emptyList()

    /**
     * The last plan (see [planStrings]): what was played stays held as it was, and where the song has forgotten its
     * past, the plucks begin with the last one forgotten: the hands stay where it left them.
     */
    private var planned: StringPlan = StringPlan()

    init {
        part.refresh()
        notes = fingered(strums = part.events, now = Double.NEGATIVE_INFINITY)
    }

    /**
     * Where the hands are for each of [strums], as far as the song is known at [now]: how each is held and which
     * way the right hand goes is the plan's ([planStrings]), what was played staying as it was; where the hands are
     * for it follows from that.
     */
    private fun fingered(strums: List<Strum>, now: Double): List<Pluck> {
        val k = design

        planned = planStrings(strums = strums, now = now, before = planned, tuning = k.tuning)

        /** How far from the nut a finger lies that presses at [fret]: just behind the fret's wire. */
        fun alongOf(fret: Double): Double = k.scale * s * (1 - 2.0.pow(-max(0.0, fret - 0.4) / 12))

        /** Where the [string] lies across the instrument, drawn two and a half times as far apart as the strings are. */
        fun acrossOf(string: Double): Double = (0.5 - string / (k.strings - 1)) * k.neck * 0.8 * s * 2.5

        return planned.plucks.map { pluck ->
            val n = pluck.strum
            val grip = pluck.grip
            // the strings that sound: each note's own
            val sounding = grip.stops.map { it.string }.ifEmpty { listOf(0) }
            // the fingers lie on the strings that are pressed, at the middle of their frets; where every string is
            // open they stay over the neck where the hand is
            val pressed = grip.stops.filter { it.fret > 0 }
            val fret = if (pressed.isEmpty()) grip.position + 1.0 else pressed.sumOf { it.fret }.toDouble() / pressed.size
            val fingers = pressed.map { it.string }.ifEmpty { sounding }
            // the right hand plays at the middle of the strings that sound; one string is picked, several are strummed
            val mid = sounding.sum().toDouble() / sounding.size

            Pluck(
                t = n.t,
                v = n.v,
                d = n.d,
                along = alongOf(fret),
                hold = alongOf(grip.position + Tuning.SPAN / 2.0),
                across = acrossOf(fingers.sum().toDouble() / fingers.size),
                strings = sounding,
                pick = acrossOf(mid),
                wide = if (sounding.size > 1) 1.0 else 0.4,
                way = pluck.way,
            )
        }
    }

    /** The left hand's place along the neck at [t]: it stays on a note and changes to the next just before it sounds. */
    private fun fretAt(t: Double): Fret {
        val (before, after) = notes.around(t)
        val from = before ?: after
        val to = after ?: before

        if (from == null || to == null) {
            return Fret(along = 0.1 * s, hold = 0.1 * s, across = 0.0, pick = 0.0, wide = 1.0)
        }

        val gap = max(0.02, to.t - from.t)
        val window = min(gap * 0.9, 0.32)                             // unhurried: the hand glides to the next note
        val move = if (after != null && before != null) smooth(1 - (after.t - t) / window) else 1.0

        fun mix(a: Double, b: Double): Double = a + (b - a) * move

        return Fret(
            along = mix(from.along, to.along),
            hold = mix(from.hold, to.hold),
            across = mix(from.across, to.across),
            pick = mix(from.pick, to.pick),
            wide = mix(from.wide, to.wide),
        )
    }

    /** The right hand's place across the strings at [t], -1 (above them) to 1 (below): through them at each note. */
    private fun strumAt(t: Double): Double {
        val (before, after) = notes.around(t)
        val tau = design.tau

        fun one(n: Pluck): Double = n.way * (0.4 + 0.6 * n.v) * tanh((t - n.t) / tau)

        return when {
            before != null && after != null ->
                one(before) + (one(after) - one(before)) * smooth((t - before.t) / (after.t - before.t))
            before != null -> one(before)
            after != null -> one(after)
            else -> -0.5
        }
    }

    /** The player at [t]: the strings' light, the body with the beat, the instrument on the trunk, both hands. */
    fun update(t: Double) {
        if (part.refresh()) {
            notes = fingered(strums = part.events, now = t)
        }

        val k = design

        var energy = 0.0
        // a string's light fades as a plucked string's sound does: one exponential fall from the pluck (RING), and a
        // quicker one from where the note ends (the next one at the latest), as the hand damps it (DAMP)
        val lit = DoubleArray(k.strings)
        var i = notes.lastIndexAt(t)

        while (i >= 0 && notes[i].t > t - 4.0) {
            val n = notes[i]
            val age = t - n.t
            val next = notes.getOrNull(i + 1)
            val held = min(n.d, if (next != null) next.t - n.t else 9.0)
            val level = exp(-age / RING - max(0.0, age - held) / DAMP)

            energy += n.v * exp(-age / 0.25)

            for (string in n.strings) {
                lit[string] = max(lit[string], n.v * level)
            }

            i--
        }

        instrument.light(lit)

        val playing = min(1.0, energy)

        // the body: knees giving on the beat, the trunk leaning, the head going with the beat
        val beat = t * show.bpm / 60
        // how much he is playing drives the body, less so for a steady player
        val live = 0.35 + 0.65 * playing
        val drive = live + (1 - live) * mover.traits.steady
        val nod = mover.nod(beat) * drive
        val body = nod + mover.burst()
        val parts = mover.parts
        val sway = sin(PI * beat / 2) * drive * parts.sway

        rig.reset()
        rig.update()
        // one who dances from leg to leg: the weight goes over one leg, then the other, two beats on each; it dwells
        // on a leg and crosses over quickly, the hips tilt with it, and the free leg comes up onto its toes
        val over = sin(PI * beat / 2)
        val weight = step * drive * over / (0.35 + 0.65 * abs(over))

        rig.moveHips(vec((0.03 * sway + 0.065 * weight) * s, -(0.04 + bounce * body * parts.knees) * rig.pelvis.y, 0.0))

        if (step > 0) {
            rig.turn("hips", Z_AXIS, -0.06 * weight)
            rig.turn("belly", Z_AXIS, 0.05 * weight)
        }
        // now and then the player straightens up from the instrument: the back comes up (a little past upright, and
        // not forward of where a player who leans back already is), the chest opens, the head lifts and looks out
        val up = show.switches.double(Switch.Straight)?.coerceIn(0.0, 1.0)
            ?: straighten?.at(t - straightenFrom) { stretch, purpose -> show.own(stretch * 3 + purpose, seed + 31.5) }
            ?: 0.0
        val straight = min(lean, 0.0) - 0.05

        rig.turn("belly", X_AXIS, lean + (straight - lean) * up + 0.04 * body * parts.trunk)
        rig.turn("chest", X_AXIS, -0.06 * up)
        rig.turn("chest", Z_AXIS, -0.05 * sway)
        rig.turn("chest", Y_AXIS, 0.18)

        // where the member's skills ask the head to look (a glance at the camera every now and then): the head comes
        // up from the instrument and turns to each look as far as it is asked, each over the ones before it, and
        // goes back
        var look = 0.0
        var yaw = 0.35
        var pitch = 0.0

        for (asked in looks(t)) {
            val eye = rig.root.worldToLocal(asked.at.clone())
            val from = rig.height + 0.10 * s
            var toward = atan2(eye.x, eye.z)
            var share = asked.share

            // nobody looks at a camera behind their back: the head would have to turn right round. At one who stands
            // behind (another member) the look goes over the shoulder, and not at all at one straight behind
            share *= headReach(angle = toward, behind = asked.behind)

            if (asked.behind) {
                // the head turned as far as a head can (as `Rig.faceTo` does)
                toward = max(-1.25, min(1.25, toward))
            }

            yaw += (toward - 0.18 - yaw) * share     // the chest is turned 0.18 already
            pitch += (atan2(eye.y - from, hypot(eye.x, eye.z)) - pitch) * share
            look = max(look, share)
        }

        rig.turn("neck", X_AXIS, bang * 0.4 * nod * parts.head * (1 - 0.7 * look))
        rig.turn("head", X_AXIS, (0.12 - 0.18 * up + bang * nod * parts.head) * (1 - 0.8 * look) - pitch)

        if (look < 0.5) {
            mover.apply(rig, t)
        }

        rig.turn("head", Y_AXIS, yaw)

        for (side in Side.entries) {
            val sign = side.sign
            val foot = rig.ankle.getValue(side).clone()

            foot.x += 0.07 * sign * s
            foot.y += 0.035 * s * max(0.0, -weight * sign)
            rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.3 * sign, 0.0, 1.0))
        }

        // the instrument hangs from the trunk: before the right hip, its neck up and to the player's left
        val waist = rig.bone("chest").getWorldPosition(vec())
        val axisX = rig.dir(cos(k.tilt), sin(k.tilt), 0.16).normalize()
        val face = rig.dir(0.0, 0.22, 1.0)
        val axisZ = face.addScaledVector(axisX, -face.dot(axisX)).normalize()
        val axisY = vec().crossVectors(axisZ, axisX)
        val at = waist.clone()
            .addScaledVector(rig.dir(1.0, 0.0, 0.0), -0.13 * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), k.hang * s)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), 0.17 * s)

        instrument.group.position.copy(at)
        instrument.group.quaternion.setFromRotationMatrix(mat4().makeBasis(axisX, axisY, axisZ))

        fun place(x: Double, y: Double, z: Double): Vector3 =
            at.clone().addScaledVector(axisX, x).addScaledVector(axisY, y).addScaledVector(axisZ, z)

        // a hand points toward what it plays, but a wrist only bends so far: never more than [most] radians off the
        // line of its forearm (bent further it looked broken, the maintainer, 2026-11-16)
        fun aim(side: Side, toward: Vector3, most: Double) {
            val hand = rig.bone("hand$side").getWorldPosition(vec())
            val forearm = hand.sub(rig.bone("wrist$side").getWorldPosition(vec())).normalize()
            val want = toward.clone().normalize()
            val angle = forearm.angleTo(want)

            if (angle > most) {
                val axis = vec().crossVectors(forearm, want).normalize()

                want.copy(forearm).applyAxisAngle(axis, most)
            }

            rig.point("hand$side", want)
        }

        // the left hand: under the neck at the note's place, the fingers coming up over the strings
        val fret = fretAt(t)
        // for an upper (lower-sounding) string the fingers reach further round the neck: the wrist comes up and out
        // the left hand goes across the neck by the strings' true distance only, and the wrist hardly at all: on a neck
        // a string is a finger's reach away, not a move of the hand (the right hand's way stays drawn larger)
        val cross = fret.across / 2.5
        val onNeck = place(nut - fret.along, cross, 0.02 * s)
        // the hand stays in its position on the neck while the fingers go from fret to fret: the wrist is at the
        // hand's place, as far from the fingers as a hand allows
        val wristL = place(nut - fret.along - max(-REACH * s, min(REACH * s, fret.hold - fret.along)), cross, 0.02 * s)
            .addScaledVector(axisY, -0.07 * s + 0.10 * cross)
            .addScaledVector(axisZ, -0.03 * s + 0.15 * cross)

        rig.hand(Side.L, wristL, rig.dir(0.4, -1.0, -0.3))
        aim(Side.L, onNeck.clone().sub(wristL), 0.75)

        // the right hand: over the strings between the bridge and the neck, through them on every note
        val strum = strumAt(t)
        val onStrings = place(bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * s, 0.03 * s)
        // a guitarist strums from the wrist: the arm goes only a little of the way, the hand turns for the rest
        val wristR = place(bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * k.arm * s, 0.03 * s)
            .addScaledVector(axisX, -0.07 * s)
            .addScaledVector(axisY, 0.05 * s)
            .addScaledVector(axisZ, 0.05 * s)

        rig.hand(Side.R, wristR, rig.dir(-0.9, -0.1, -0.7))
        aim(Side.R, onStrings.clone().sub(wristR), 0.80)

        rig.eyes?.let { eyes ->
            eyes.emissiveIntensity = 2 + 13 * min(1.6, energy)
        }
    }
}
