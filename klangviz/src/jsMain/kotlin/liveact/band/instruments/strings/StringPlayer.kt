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
import io.peekandpoke.klang.viz.liveact.band.figure.HipMotion
import io.peekandpoke.klang.viz.liveact.band.figure.Mover
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.members.Lap
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.figure.walkHips
import io.peekandpoke.klang.viz.liveact.band.members.Bearing
import io.peekandpoke.klang.viz.liveact.band.skills.BrowWipe
import io.peekandpoke.klang.viz.liveact.band.skills.CapTug
import io.peekandpoke.klang.viz.liveact.band.skills.HandGoal
import io.peekandpoke.klang.viz.liveact.band.skills.HeadScratch
import io.peekandpoke.klang.viz.liveact.band.skills.IdleInUse
import io.peekandpoke.klang.viz.liveact.band.skills.IdleSkill
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.band.skills.NeckScratch
import io.peekandpoke.klang.viz.liveact.band.skills.NeckStretch
import io.peekandpoke.klang.viz.liveact.band.skills.ShakeOut
import io.peekandpoke.klang.viz.liveact.band.skills.Skill
import io.peekandpoke.klang.viz.liveact.band.skills.Slouch
import io.peekandpoke.klang.viz.liveact.band.skills.StickTwirl
import io.peekandpoke.klang.viz.liveact.band.skills.Straighten
import io.peekandpoke.klang.viz.liveact.band.skills.TuneUp
import io.peekandpoke.klang.viz.liveact.band.skills.browHand
import io.peekandpoke.klang.viz.liveact.band.skills.capHand
import io.peekandpoke.klang.viz.liveact.band.skills.capTugAt
import io.peekandpoke.klang.viz.liveact.band.skills.scratchHand
import io.peekandpoke.klang.viz.liveact.headReach
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.song.Strum
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.angleTo
import io.peekandpoke.klang.workshop.threejs.applyAxisAngle
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.lerp
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

// The string players: a bass or a guitar on a strap. The fretting hand is where a player's would be for the note (the
// string and the fret of the instrument's real tuning, see Tuning), the strumming hand plucks (bass) or strums down
// and up (guitar) on every note; the strings light up when played. A right-handed player strums with the right hand,
// a left-handed one with the left, on a mirrored instrument.
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
    /** Where the strumming hand plays across the strings: the middle of those that sound. */
    val pick: Double,
    /** How wide the strumming hand goes through them: 1 for several strings, less for one. */
    val wide: Double,
    /** Which way the strumming hand goes through the strings: 1 down, -1 up. */
    val way: Double,
) : Timed

/**
 * The hands' places at one moment: the fingers [along] the neck and [across] it, the fretting hand's own place along
 * it ([hold]), where the strumming hand [pick]s and how [wide].
 */
private class Fret(val along: Double, val hold: Double, val across: Double, val pick: Double, val wide: Double)

/**
 * The strumming hand's idle skills in use in one frame ([idle]), and what it would do without them: the [wrist]'s
 * place, where the hand [points] and where the elbow bends to ([pole]), all in the world; and how the [arm]'s bones
 * were turned before the hand was placed (see `StringPlayer.arm`), to place it again from there.
 */
private class IdleHand(
    val idle: List<IdleInUse>,
    val wrist: Vector3,
    val points: Vector3,
    val pole: Vector3,
    val arm: List<Quaternion>,
)

/**
 * A string player: a strung instrument of the [design] (a bass, a guitar) on a strap, played by [rig].
 *
 * [notes]: sorted by time, one per pluck or strum; the nod goes by the [show]'s tempo. [traits]: how the player
 * moves with the music, with the show's chance under [seed] (the player's own; the prototype's fallback is 4, the
 * band gives each player one of their own). `update`'s `sits` (0 to 1) has the player sit cross-legged on the floor,
 * the instrument in the lap. [bearing]: how the player carries themselves over the instrument, the
 * member's own (see [Bearing]), or a plain one where none is given; [outline]: the body's
 * outline, the [design]'s own unless given; [looks]: where the member's skills ask the head to look at a time (see
 * `lookAt`, the last in rank first; none unless given): the head comes up from the instrument and turns to each as
 * far as it is asked, each over the ones before it;
 * [idles]: the member's idle skills in use at a time, in the order of rank (see `idlesAt`; none unless given): the
 * strumming hand leaves the strings for them (a scratch of the head, a tug at the cap, a wipe of the brow, a slouch),
 * the fretting hand leaves the neck (a shake out, a peg to tune), each hand's over the ones after it; a slouch
 * settles the body, the head leans in to tune, and dips into a wipe of the brow or with a tug at the cap;
 * [skills]: the member's skills, as their sheet equips them (none unless given): of them the pose itself acts the
 * straightening up from the instrument ([Straighten]; `?straight=1` holds everyone straight, to look at the pose);
 * [hand]: the hand that strums, the member's own (`Member.hand`; the right unless given), the other frets. A
 * left-handed player is the right-handed one's mirror: the instrument is built mirrored (see [StrungInstrument]), and
 * the pose goes by [mirror].
 */
class StringPlayer(
    private val show: Show,
    private val rig: Rig,
    private val design: StringDesign,
    private val part: Part<Strum>,
    traits: Traits,
    private val seed: Double = 4.0,
    bearing: Bearing? = null,
    private val outline: BodyOutline = design.outline,
    private val looks: (Double) -> List<Look> = { emptyList() },
    private val idles: (Double) -> List<IdleInUse> = { emptyList() },
    skills: List<Skill> = emptyList(),
    hand: Side = Side.R,
    private val lap: Lap = Lap.OnThigh,
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)

    /** The hand that strums (or plucks), and the one that frets. */
    private val strumming: Side = hand
    private val fretting: Side = hand.other

    /**
     * The pose's mirror: 1 for a right-handed player, -1 for a left-handed one. Every way to a side (x in the figure's
     * space) and every turn about an upright or a forward axis that goes with the hands is taken by it, so that a
     * left-handed player is the right-handed one's mirror. What goes with the beat (the sway, the step from leg to
     * leg) is no hand's, and stays as it is.
     */
    private val mirror: Double = fretting.sign

    /** The bones of the strumming arm, from the shoulder to the hand: what placing the hand turns. */
    private val arm: List<String> = listOf("elbow$strumming", "wrist$strumming", "hand$strumming")

    /** How the trunk leans (back is negative), how much the head goes, how far the hips dip on the beat. */
    private val lean: Double = bearing?.lean ?: 0.06
    private val bang: Double = bearing?.bang ?: 0.2
    private val bounce: Double = bearing?.bounce ?: 0.03
    private val step: Double = bearing?.step ?: 0.0

    /** When the player straightens up from the instrument, where their sheet has it; never without. */
    private val straighten: Straighten? = skills.filterIsInstance<Straighten>().firstOrNull()

    private val s = rig.s

    /** The instrument on the strap (see [StrungInstrument]). */
    val instrument: StrungInstrument = StrungInstrument(rig = rig, design = design, outline = outline, hand = strumming)

    private val bridge = instrument.bridge
    private val nut = instrument.nut

    private var notes: List<Pluck> = emptyList()

    /**
     * The last plan (see [planStrings]): what was played stays held as it was, and where the song has forgotten its
     * past, the plucks begin with the last one forgotten: the hands stay where it left them.
     */
    private var planned: StringPlan = StringPlan()

    /** This frame's strumming hand, where an idle skill at the head is in use: placed again in [afterPose]. */
    private var idleHand: IdleHand? = null

    init {
        part.refresh()
        notes = fingered(strums = part.events, now = Double.NEGATIVE_INFINITY)
    }

    /**
     * Where the hands are for each of [strums], as far as the song is known at [now]: how each is held and which
     * way the strumming hand goes is the plan's ([planStrings]), what was played staying as it was; where the hands are
     * for it follows from that.
     */
    private fun fingered(strums: List<Strum>, now: Double): List<Pluck> {
        val k = design

        planned = planStrings(strums = strums, now = now, before = planned, tuning = k.tuning, bar = show.bar)

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
            // the strumming hand plays at the middle of the strings that sound; one string is picked, several are strummed
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

    /** The fretting hand's place along the neck at [t]: it stays on a note and changes to the next just before it sounds. */
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

    /** The strumming hand's place across the strings at [t], -1 (above them) to 1 (below): through them at each note. */
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

    /**
     * The player at [t]: the strings' light, the body with the beat, the instrument on the trunk, both hands. [hips]:
     * how the hips move as the player walks (the walk-in, see `walkInHips`); the arms hold the instrument as ever.
     */
    fun update(t: Double, hips: HipMotion = HipMotion.NONE, sits: Double = 0.0) {
        // NaN-guard: a share of no number is standing
        val seated = if (sits != sits) 0.0 else sits.coerceIn(0.0, 1.0)
        val upright = 1 - seated

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

        // how busy the player is, for the body: by the notes of the last and the next moments, never by a single
        // stroke (see busyAt); the strings' light and the eyes go by each note as ever (energy)
        val playing = notes.busyAt(t) { it.v }
        // what the member does with the hands, the body and the head while the part pauses (see [strummingGoal],
        // [frettingGoal]), and how far a slouch settles the body
        val idle = idles(t)
        val settle = idle.filter { it.skill is Slouch }.maxOfOrNull { it.share } ?: 0.0
        // tuning up: how far the head leans in to listen
        val tune = idle.filter { it.skill is TuneUp }.maxOfOrNull { it.share } ?: 0.0

        // the body: knees giving on the beat, the trunk leaning, the head going with the beat
        val beat = t * show.bpm / 60
        // how much he is playing drives the body, less so for a steady player
        val live = 0.35 + 0.65 * playing
        val drive = live + (1 - live) * mover.traits.steady
        val nod = mover.nod(beat) * drive
        // (of a sudden burst of sound the body takes only a little: on a slow song the burst is the player's own
        // stroke, and the body would jerk with the strumming hand)
        val body = nod + BURST_SHARE * mover.burst()
        val parts = mover.parts
        val sway = sin(PI * beat / 2) * drive * parts.sway

        rig.reset()
        rig.update()
        // one who dances from leg to leg: the weight goes over one leg, then the other, two beats on each; it dwells
        // on a leg and crosses over quickly, the hips tilt with it, and the free leg comes up onto its toes
        val over = sin(PI * beat / 2)
        val weight = step * drive * over / (0.35 + 0.65 * abs(over))

        // (one who sits does not go from leg to leg, and no knee gives: what is left is the trunk's)
        rig.moveHips(
            vec(
                (0.03 * sway + 0.065 * weight) * s * upright,
                -(0.04 + bounce * body * parts.knees) * rig.pelvis.y * upright,
                0.0,
            )
        )

        if (seated > 0) {
            // sitting cross-legged on the floor: the pelvis comes down to just above it and a little back, where
            // the weight of one who sits is
            rig.moveHips(vec(0.0, -(rig.pelvis.y - SIT_HIGH * rig.height) * seated, -SIT_BACK * s * seated))
        }

        if (step > 0) {
            rig.turn("hips", Z_AXIS, -0.06 * weight * upright)
            rig.turn("belly", Z_AXIS, 0.05 * weight * upright)
        }

        if (settle > 0) {
            // a slouch: the weight settles, down and over the leg on the strumming side, the hips tilting with it
            rig.moveHips(vec(-SETTLE_OVER * s * settle * mirror, -SETTLE_DOWN * rig.pelvis.y * settle, 0.0))
            rig.turn("hips", Z_AXIS, SETTLE_TILT * settle * mirror)
            rig.turn("belly", Z_AXIS, -SETTLE_TILT * settle * mirror)
        }

        walkHips(rig = rig, motion = hips)

        // now and then the player straightens up from the instrument: the back comes up (a little past upright, and
        // not forward of where a player who leans back already is), the chest opens, the head lifts and looks out
        val up = show.switches.double(Switch.Straight)?.coerceIn(0.0, 1.0)
            ?: straighten?.at(t = t, roll = { index -> show.own(index, seed) })
            ?: 0.0
        val straight = min(lean, 0.0) - 0.05

        // (one who sits bends a little further over the instrument in the lap)
        rig.turn("belly", X_AXIS, lean + (straight - lean) * up + 0.04 * body * parts.trunk + SIT_BEND * seated)
        rig.turn("chest", X_AXIS, -0.06 * up)
        rig.turn("chest", Z_AXIS, -0.05 * sway)
        // the chest turned a little toward the neck
        rig.turn("chest", Y_AXIS, 0.18 * mirror)

        if (settle > 0) {
            // and the shoulders drop: the chest sinks a little forward
            rig.turn("chest", X_AXIS, SETTLE_SINK * settle)
        }

        // where the member's skills ask the head to look (a glance at the camera every now and then): the head comes
        // up from the instrument and turns to each look as far as it is asked, each over the ones before it, and
        // goes back. Without a look the head is turned toward the neck
        var look = 0.0
        var yaw = 0.35 * mirror
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

            yaw += (toward - 0.18 * mirror - yaw) * share     // the chest is turned 0.18 already
            pitch += (atan2(eye.y - from, hypot(eye.x, eye.z)) - pitch) * share
            look = max(look, share)
        }

        rig.turn("neck", X_AXIS, bang * 0.4 * nod * parts.head * (1 - 0.7 * look))
        rig.turn("head", X_AXIS, (0.12 - 0.18 * up + bang * nod * parts.head) * (1 - 0.8 * look) - pitch)

        if (look < 0.5) {
            mover.apply(rig, t)
        }

        rig.turn("head", Y_AXIS, yaw)

        val dip = headDipOf(idle)

        if (dip > 0) {
            // into the arm that wipes the brow, or with the tug at the cap
            rig.turn("head", X_AXIS, dip)
        }

        for (side in Side.entries) {
            val sign = side.sign
            val foot = rig.ankle.getValue(side).clone()

            foot.x += 0.07 * sign * s
            foot.y += 0.035 * s * max(0.0, -weight * sign) * upright

            if (seated > 0) {
                // the legs crossed before the body: each ankle a little past the middle, one before the other, just
                // above the floor; the knees lie out to the sides
                val crossed = vec(-SIT_CROSS * sign * s, SIT_ANKLE * s, (SIT_REACH + (if (side == Side.L) SIT_STAGGER else 0.0)) * s)

                foot.lerp(crossed, seated)
            }

            val knee = rig.dir(0.3 * sign + (SIT_KNEE_OUT - 0.3) * sign * seated, SIT_KNEE_UP * seated, 1.0 - (1.0 - SIT_KNEE_FORE) * seated)

            rig.foot(side, rig.root.localToWorld(foot), knee)
        }

        // the instrument hangs from the trunk: before the hip on the strumming side, its neck up and toward the
        // fretting hand
        val waist = rig.bone("chest").getWorldPosition(vec())
        // (in the lap of one who sits it lies flatter: the neck points less far up; the maintainer, 2026-11-23:
        // "they need to be held at a more natural angle... flatter angle")
        val lapTilt = when (lap) {
            Lap.OnThigh -> SIT_TILT
            Lap.Between -> SIT_TILT_BETWEEN
        }
        val tilt = k.tilt + (lapTilt - k.tilt) * seated
        val axisX = rig.dir(cos(tilt) * mirror, sin(tilt), 0.16).normalize()
        val face = rig.dir(0.0, 0.22, 1.0)
        val axisZ = face.addScaledVector(axisX, -face.dot(axisX)).normalize()
        val axisY = vec().crossVectors(axisZ, axisX)
        val at = waist.clone()
            .addScaledVector(rig.dir(mirror, 0.0, 0.0), -0.13 * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), k.hang * s)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), 0.17 * s)

        if (seated > 0) {
            when (lap) {
                // on the thigh of the strumming side, where the body is narrowest: the waist of its lower edge sits
                // on the leg (the maintainer, 2026-11-23: "the indentation at the bottom of the guitar has to rest on
                // the leg"). The waist: of the lower edge's points about the body's middle the one nearest the
                // strings (the outline in the instrument's own measure; a left-handed instrument is mirrored across
                // its neck)
                Lap.OnThigh -> {
                    val lower = outline.points.filter { (along, across) ->
                        across * mirror < 0 && abs(along) < SIT_WAIST_WITHIN * k.bodyHalfLength
                    }
                    val waist = lower.maxByOrNull { (_, across) -> across * mirror }

                    if (waist != null) {
                        val thigh = rig.root.localToWorld(vec(-SIT_THIGH_OUT * mirror * s, SIT_THIGH_HIGH * rig.height, SIT_THIGH_FORE * s))
                        val rested = thigh
                            .addScaledVector(axisX, -waist.first * s)
                            .addScaledVector(axisY, -waist.second * mirror * s)

                        at.lerp(rested, seated)
                    }
                }

                // between the legs: the body stands in the lap before the trunk, on its lowest point
                Lap.Between -> {
                    val lowest = outline.points.minOf { (along, across) -> axisX.y * along * s - abs(axisY.y * across * s) }
                    val rested = rig.root.localToWorld(vec(-SIT_LAP_OUT * mirror * s, SIT_LAP_HIGH * rig.height, SIT_LAP_FORE * s))

                    rested.y -= lowest
                    at.lerp(rested, seated)
                }
            }

            // in the lap of one who sits the instrument may lie on the legs and sink into them, but it never goes
            // into the floor (the maintainer, 2026-11-23: "they can touch the legs and even sink into them ... but
            // they must not enter the floor"): it is lifted by as much as its body's lowest point would lie below
            // a level a little over the floor (SIT_CLEAR). The body's outline in the instrument's own measure, either way up (a left-handed one is
            // mirrored across its neck), and half its depth
            val floor = rig.root.getWorldPosition(vec()).y + SIT_CLEAR * s
            var lowest = Double.POSITIVE_INFINITY

            for ((along, across) in outline.points) {
                val reach = axisX.y * along * s - abs(axisY.y * across * s) - abs(axisZ.y) * SIT_BODY_DEEP * s

                lowest = min(lowest, at.y + reach)
            }

            if (lowest < floor) {
                at.y += (floor - lowest) * seated
            }
        }

        instrument.group.position.copy(at)
        instrument.group.quaternion.setFromRotationMatrix(mat4().makeBasis(axisX, axisY, axisZ))
        // across the strings toward the lowest one, up as it is played: for a left-handed player the instrument is
        // mirrored across its neck (see [StrungInstrument]), its y the other way
        axisY.multiplyScalar(mirror)

        fun place(x: Double, y: Double, z: Double): Vector3 =
            at.clone().addScaledVector(axisX, x).addScaledVector(axisY, y).addScaledVector(axisZ, z)

        if (tune > 0) {
            // tuning up: the head leans in toward the head of the neck, and tilts as if listening
            rig.faceTo(place(nut + PEG * s, 0.0, 0.0), TUNE_LOOK * tune)
            rig.turn("head", Z_AXIS, TUNE_TILT * tune * mirror)
        }

        // the fretting hand: under the neck at the note's place, the fingers coming up over the strings
        val fret = fretAt(t)
        // for an upper (lower-sounding) string the fingers reach further round the neck: the wrist comes up and out
        // the fretting hand goes across the neck by the strings' true distance only, and the wrist hardly at all: on a
        // neck a string is a finger's reach away, not a move of the hand (the strumming hand's way stays drawn larger)
        val cross = fret.across / 2.5
        val onNeck = place(nut - fret.along, cross, 0.02 * s)
        // the hand stays in its position on the neck while the fingers go from fret to fret: the wrist is at the
        // hand's place, as far from the fingers as a hand allows
        val wristFret = place(nut - fret.along - max(-REACH * s, min(REACH * s, fret.hold - fret.along)), cross, 0.02 * s)
            .addScaledVector(axisY, -0.07 * s + 0.10 * cross)
            .addScaledVector(axisZ, -0.03 * s + 0.15 * cross)

        // the idle skills that take the fretting hand off the neck, each with how far
        val fretGoals = if (idle.isEmpty()) {
            emptyList()
        } else {
            val peg = place(nut + PEG * s, -0.04 * s, 0.0)

            idle.mapNotNull { used -> frettingGoal(used = used, peg = peg)?.let { goal -> goal to used.share } }
        }

        if (fretGoals.isEmpty()) {
            rig.hand(fretting, wristFret, rig.dir(0.4 * mirror, -1.0, -0.3))
            aim(fretting, onNeck.clone().sub(wristFret), 0.75)
        } else {
            // the fretting hand leaves the neck for what the idle skills ask, each over the ones after it on the sheet
            val wrist = wristFret.clone()
            val pole = rig.dir(0.4 * mirror, -1.0, -0.3)
            val points = onNeck.clone().sub(wristFret).normalize()

            for ((goal, w) in fretGoals.asReversed()) {
                goal.blend(wrist = wrist, pole = pole, w = w)
                points.lerp(goal.points, w).normalize()
            }

            rig.hand(fretting, wrist, pole)
            aim(fretting, points, 0.75)
        }

        // the strumming hand: over the strings between the bridge and the neck, through them on every note
        var strum = strumAt(t)

        if (tune > 0) {
            // tuning up: the strumming hand plucks a string softly now and then
            strum += pluckOf(idle)
        }

        val onStrings = place(bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * s, 0.03 * s)
        // a guitarist strums from the wrist: the arm goes only a little of the way, the hand turns for the rest
        val wristStrum = place(bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * k.arm * s, 0.03 * s)
            .addScaledVector(axisX, -0.07 * s)
            .addScaledVector(axisY, 0.05 * s)
            .addScaledVector(axisZ, 0.05 * s)

        idleHand = null

        if (idle.isEmpty()) {
            rig.hand(strumming, wristStrum, rig.dir(-0.9 * mirror, -0.1, -0.7))
            aim(strumming, onStrings.clone().sub(wristStrum), 0.80)
        } else {
            // the strumming hand leaves the strings for what the idle skills ask (see [placeIdleHand]); where one of
            // them is at the head, the hand is placed again once the head is held at last (see [afterPose])
            val hand = IdleHand(
                idle = idle,
                wrist = wristStrum,
                points = onStrings.clone().sub(wristStrum).normalize(),
                pole = rig.dir(-0.9 * mirror, -0.1, -0.7),
                arm = arm.map { name -> rig.bone(name).quaternion.clone() },
            )

            placeIdleHand(hand)

            if (idle.any { atHead(it.skill) }) {
                idleHand = hand
            }
        }

        rig.eyes?.let { eyes ->
            eyes.emissiveIntensity = 2 + 13 * min(1.6, energy)
        }
    }

    /**
     * Places the strumming hand again where an idle skill at the head is in use this frame, from the head as it is
     * held at last (eye contact turns it after the poses): call it once everything that turns a head has done so. The
     * arm is put back as it was before the hand was first placed, and placed anew, so the hand is where one placing
     * from this head would put it. Nothing where no idle skill at the head is in use.
     */
    fun afterPose() {
        val hand = idleHand ?: return

        for ((name, turn) in arm.zip(hand.arm)) {
            rig.bone(name).quaternion.copy(turn)
        }

        rig.update()
        placeIdleHand(hand)
    }

    /**
     * The strumming hand where the idle skills of [hand] ask, each over the ones after it on the sheet: the first in
     * rank has the hand where it is all there. A goal at the head is taken from the head as it is posed now.
     */
    private fun placeIdleHand(hand: IdleHand) {
        val wrist = hand.wrist.clone()
        val points = hand.points.clone()
        val pole = hand.pole.clone()

        for (used in hand.idle.asReversed()) {
            val goal = strummingGoal(used) ?: continue
            val w = used.share

            goal.blend(wrist = wrist, pole = pole, w = w)
            points.lerp(goal.points, w).normalize()
        }

        rig.hand(strumming, wrist, pole)
        aim(strumming, points, 0.80)
    }

    /**
     * A hand of [side] points toward [toward] (a world direction), but a wrist only bends so far: never more than
     * [most] radians off the line of its forearm (bent further it looked broken, the maintainer, 2026-11-16).
     */
    private fun aim(side: Side, toward: Vector3, most: Double) {
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

    /**
     * Where the strumming hand goes for the idle skill [used], at the time of its use: at the head (as the head is
     * posed now) for a scratch, a tug at the cap and a wipe of the brow; before the strings for a slouch. Null for a
     * skill that leaves the strumming hand where it is (or that a string player does not show).
     */
    private fun strummingGoal(used: IdleInUse): HandGoal? {
        return when (val skill = used.skill) {
            is HeadScratch -> scratchHand(rig = rig, side = strumming, since = used.since)

            is CapTug -> capHand(rig = rig, side = strumming, since = used.since, rise = skill.timing.rise)

            is BrowWipe -> browHand(rig = rig, side = strumming, since = used.since, rise = skill.timing.rise)

            is Slouch -> {
                // the hand off the strings, hanging limp before the body of the instrument
                val wrist = instrument.group.position.clone()
                    .addScaledVector(rig.dir(-mirror, 0.0, 0.0), SLOUCH_ASIDE * s)
                    .addScaledVector(rig.dir(0.0, 1.0, 0.0), SLOUCH_UP * s)
                    .addScaledVector(rig.dir(0.0, 0.0, 1.0), SLOUCH_AHEAD * s)

                HandGoal(
                    wrist = wrist,
                    points = rig.dir(0.0, -1.0, 0.25),
                    pole = rig.dir(-0.9 * mirror, -0.3, -0.5),
                    round = vec(),
                    drop = vec(),
                )
            }

            is ShakeOut, is TuneUp, is NeckStretch, is StickTwirl, is NeckScratch -> null
        }
    }

    /**
     * Where the fretting hand goes for the idle skill [used], at the time of its use: hanging out beside the neck and
     * shaken loosely for a shake out; at a tuning peg ([peg], in the world) for tuning. Null for a skill that leaves
     * the fretting hand on the neck (or that a string player does not show).
     */
    private fun frettingGoal(used: IdleInUse, peg: Vector3): HandGoal? {
        return when (used.skill) {
            is ShakeOut -> {
                // the hand hangs out beside the neck, below it, and shakes loosely from the wrist
                val shake = sin(2 * PI * SHAKE_RATE * used.since)
                val wrist = rig.at("elbow$fretting")
                    .addScaledVector(rig.dir(mirror, 0.0, 0.0), (SHAKE_OUT + SHAKE_WRIST * shake) * s)
                    .addScaledVector(rig.dir(0.0, 1.0, 0.0), -SHAKE_DOWN * s)
                    .addScaledVector(rig.dir(0.0, 0.0, 1.0), SHAKE_AHEAD * s)

                HandGoal(
                    wrist = wrist,
                    points = rig.dir(mirror * SHAKE_FLOP * sin(2 * PI * SHAKE_RATE * used.since + 1.0), -1.0, 0.2),
                    pole = rig.dir(0.8 * mirror, -0.5, -0.4),
                    round = rig.dir(0.3 * mirror, 0.0, 1.0).multiplyScalar(0.06 * s),
                    drop = vec(),
                )
            }

            is TuneUp -> {
                // the hand under the head of the neck, the fingers at a peg, turning it a little to and fro (across the
                // strings toward the lowest, and the turn, mirrored with the instrument)
                val along = vec(1.0, 0.0, 0.0).applyQuaternion(instrument.group.quaternion)
                val across = vec(0.0, 1.0, 0.0).applyQuaternion(instrument.group.quaternion).multiplyScalar(mirror)
                val wrist = peg.clone().addScaledVector(across, -0.07 * s).addScaledVector(along, -0.03 * s)
                val points = peg.clone().sub(wrist).applyAxisAngle(along, mirror * TURN * sin(2 * PI * TURN_RATE * used.since))

                HandGoal(
                    wrist = wrist,
                    points = points,
                    pole = rig.dir(0.7 * mirror, -1.0, -0.2),
                    round = rig.dir(0.0, 0.3, 1.0).multiplyScalar(0.06 * s),
                    drop = vec(),
                )
            }

            is HeadScratch, is Slouch, is CapTug, is BrowWipe, is NeckStretch, is StickTwirl, is NeckScratch -> null
        }
    }

    /** Whether the idle [skill] takes the strumming hand to the head: placed again from the head held at last. */
    private fun atHead(skill: IdleSkill): Boolean = skill is HeadScratch || skill is CapTug || skill is BrowWipe

    /** How far the head dips at the time of the idle skills [idle] (radians): into a wipe of the brow, with a tug at the cap. */
    private fun headDipOf(idle: List<IdleInUse>): Double {
        var dip = 0.0

        for (used in idle) {
            val skill = used.skill

            if (skill is BrowWipe) {
                dip = max(dip, BROW_DIP * used.share)
            }

            if (skill is CapTug) {
                dip = max(dip, CAP_DIP * used.share * capTugAt(since = used.since, rise = skill.timing.rise))
            }
        }

        return dip
    }

    /** The soft plucks while tuning (see [TuneUp]): where the strumming hand is across the strings, as by [strumAt]. */
    private fun pluckOf(idle: List<IdleInUse>): Double {
        var pluck = 0.0

        for (used in idle) {
            if (used.skill is TuneUp) {
                for (at in PLUCKS) {
                    val d = (used.since - at) / PLUCK_TIME

                    pluck += PLUCK * used.share * exp(-d * d)
                }
            }
        }

        return pluck
    }

    private companion object {
        /** How much of a sudden burst of sound goes into the body (the trunk and the knees), 0 to 1. */
        const val BURST_SHARE = 0.25

        /**
         * Sitting cross-legged on the floor (KLANG-00481; the maintainer, 2026-11-23: "ok cross-legged"), in the
         * figure's sizes: how high the pelvis is over the floor (a share of the figure's height) and how far back it
         * goes; how far the trunk bends over the instrument (radians); where the ankles lie (how far past the middle
         * each one crosses, how high, how far before the body, and how much further the left one); and where the
         * knees point (out to the side, up a little, and forward).
         */
        const val SIT_HIGH = 0.115
        const val SIT_BACK = 0.06
        const val SIT_BEND = 0.10
        const val SIT_CROSS = 0.05
        const val SIT_ANKLE = 0.07
        const val SIT_REACH = 0.50
        const val SIT_STAGGER = 0.07
        const val SIT_KNEE_OUT = 1.0
        const val SIT_KNEE_UP = 0.05
        const val SIT_KNEE_FORE = 0.35

        /**
         * Where an instrument in the lap rests: on the thigh of the strumming side, so far out from the body's
         * middle, so high over the floor (a share of the figure's height) and so far before the body, in the figure's
         * sizes; and how far from the body's middle along the neck its waist is looked for (a share of the body's
         * half length).
         */
        const val SIT_THIGH_OUT = 0.13
        const val SIT_THIGH_HIGH = 0.10
        const val SIT_THIGH_FORE = 0.10
        const val SIT_WAIST_WITHIN = 0.4

        /** How far up the neck of an instrument in the lap points, in radians (standing it is the design's own tilt). */
        const val SIT_TILT = 0.16

        /**
         * An instrument held between the legs ([Lap.Between]): how far up its neck points (radians), and where its
         * body stands in the lap: so far to the strumming side of the body's middle (less than nothing: a little
         * to the fretting side, where it leans on that leg; the maintainer, 2026-11-23: "a bit more in the center ...
         * and rest on their left leg slightly"), its lowest point so high over
         * the floor (a share of the figure's height), so far before the body.
         */
        const val SIT_TILT_BETWEEN = 0.62
        const val SIT_LAP_OUT = -0.05
        const val SIT_LAP_HIGH = 0.075
        const val SIT_LAP_FORE = 0.20

        /**
         * How far over the floor the instrument's body stays in the lap of one who sits (about half a thigh: it lies
         * on the legs and sinks into them a little, not through them; at 0.012 it went "a little too much through the
         * legs", the maintainer, 2026-11-23), and half the body's depth.
         */
        const val SIT_CLEAR = 0.075
        const val SIT_BODY_DEEP = 0.03

        /**
         * A slouch: how far the hips go over the leg on the strumming side and down (a share of their height), how
         * far they tilt, how far the chest sinks.
         */
        const val SETTLE_OVER = 0.03
        const val SETTLE_DOWN = 0.02
        const val SETTLE_TILT = 0.04
        const val SETTLE_SINK = 0.06

        /**
         * A slouch: where the hanging hand's wrist is, from the instrument's middle: to the strumming side, up and
         * ahead.
         */
        const val SLOUCH_ASIDE = 0.10
        const val SLOUCH_UP = 0.10
        const val SLOUCH_AHEAD = 0.10

        /** Tuning up: the peg, so far past the nut; how far the head turns to it and tilts; the peg turned so far, so often. */
        const val PEG = 0.10
        const val TUNE_LOOK = 0.5
        const val TUNE_TILT = 0.12
        const val TURN = 0.35
        const val TURN_RATE = 0.8

        /** Tuning up: the soft plucks, so many seconds into the use, each so long, so far across the strings. */
        val PLUCKS = listOf(1.6, 2.9)
        const val PLUCK_TIME = 0.12
        const val PLUCK = 0.5

        /**
         * The fretting hand shaken out: where it hangs, from the shoulder, so far out, down and ahead; how far the
         * wrist goes and the hand flops with each shake, and how many shakes a second.
         */
        const val SHAKE_OUT = 0.10
        const val SHAKE_DOWN = 0.40
        const val SHAKE_AHEAD = 0.20
        const val SHAKE_WRIST = 0.012
        const val SHAKE_FLOP = 0.6
        const val SHAKE_RATE = 5.0

        /** How far the head dips into a wipe of the brow, and with a tug at the cap (radians). */
        const val BROW_DIP = 0.12
        const val CAP_DIP = 0.08
    }
}
