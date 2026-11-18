/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.figure.Mover
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.instruments.StickPose
import io.peekandpoke.klang.viz.liveact.band.instruments.Stroke
import io.peekandpoke.klang.viz.liveact.band.instruments.holdStick
import io.peekandpoke.klang.viz.liveact.band.instruments.strokeAt
import io.peekandpoke.klang.viz.liveact.band.skills.CrossedMallets
import io.peekandpoke.klang.viz.liveact.beyond
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.lengthSq
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.lerpVectors
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The marimba player: stands behind a marimba and plays notes with four mallets, two in each hand. Which bar is hit
// follows the note, so the player moves along the instrument with the tune. All places are in the figure's own
// measures.

/**
 * One blow of one hand as the figure plays it: the plan's [MalletBlow] with its places as the figure's vectors. [at] is
 * the place between the two heads and [half] the way from there to the outer mallet's head.
 */
private class MalletHit(
    override val t: Double,
    override val v: Double,
    override val at: Vector3,
    val half: Vector3,
) : Stroke

/**
 * The marimba player of [show]: the figure [rig] behind a marimba, playing [notes] (sorted by time) with four
 * mallets, two in each hand: a single note with a hand's inner mallet, notes that sound together with as many
 * mallets as they need (which hand plays which: [planMarimba]). She moves with the music by her [traits], her chance
 * drawn under [seed].
 *
 * [crossing]: where her sheet has the skill, she crosses her mallets below her head for a moment before a phrase and
 * after it (see [CrossedMallets]); without it, never. [arrives]: when she is at her place (her walk-in's end): before
 * her first note the mallets cross only from then on.
 */
class MarimbaPlayer(
    private val show: Show,
    private val rig: Rig,
    private val part: Part<Note>,
    traits: Traits,
    seed: Double = 2.0,
    private val crossing: CrossedMallets? = null,
    private val arrives: Double = Double.NEGATIVE_INFINITY,
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)
    private val s: Double = rig.s

    /** Her notes, sorted by time, as far as the song is known. */
    private var notes: List<Note> = emptyList()

    /** Which hand plays which note. */
    private var byHand: Map<Side, List<MalletHit>> = emptyMap()

    /**
     * The last plan of the hands (see [planMarimba]): where the song has forgotten its past, the next plan goes on from
     * what it carries on ([MarimbaPlan.carryOn]), so no note that is still known changes hands.
     */
    private var planned: MarimbaPlan? = null

    /** The marimba she plays, built to the range of her notes, and her mallets (see [Marimba]). */
    val marimba: Marimba

    /** Where a hand's mallets idle when it has nothing to play: the place between their two heads. */
    private val rests: Map<Side, Vector3>

    /** From there to the outer mallet's head, as the hand holds the two when both idle. */
    private val restHalves: Map<Side, Vector3> = Side.entries.associateWith { side -> vec(side.sign * MALLETS_APART / 2 * s, 0.0, 0.0) }

    init {
        part.refresh()
        notes = part.events
        marimba = Marimba(rig = rig, notes = notes, grows = part.grows)
        rests = mapOf(
            Side.L to vec(0.14 * s, marimba.height + 0.012 * s, marimba.near + 0.08 * s),
            Side.R to vec(-0.14 * s, marimba.height + 0.012 * s, marimba.near + 0.08 * s),
        )
        plan()
    }

    /** Which hand plays which note, from the notes as far as they are known. */
    private fun plan() {
        notes = part.events

        // what the song has forgotten since the last plan: the hands go on from the last of it
        val before = planned?.carryOn(from = notes.firstOrNull()?.t ?: Double.POSITIVE_INFINITY) ?: HandsBefore()
        val next = planMarimba(
            notes = notes,
            before = before,
            place = { midi -> marimba.barOf(midi).place },
            barWidth = marimba.width,
            s = s,
        )

        planned = next
        byHand = next.byHand.mapValues { (_, blows) ->
            blows.map { blow ->
                MalletHit(
                    t = blow.t,
                    v = blow.v,
                    at = vec(blow.at.x, blow.at.y, blow.at.z),
                    half = vec(blow.half.x, blow.half.y, blow.half.z),
                )
            }
        }
    }

    /**
     * Half the way between the two mallet heads of [side]'s hand at [t]: as its blow before asks, on the way to what
     * the next one asks, as the hand itself travels (see [strokeAt]).
     */
    private fun halfAt(side: Side, t: Double): Vector3 {
        val (before, after) = byHand.getValue(side).around(t)
        val from = before?.half ?: restHalves.getValue(side)
        val to = after?.half ?: from
        val gap = if (before != null && after != null) after.t - before.t else 1.0
        val until = if (after != null) after.t - t else 9.0

        return from.clone().lerp(to, smooth((gap - until) / max(0.05, gap * 0.8)))
    }

    /** The marimba player and her marimba at [t] (seconds). */
    fun update(t: Double) {
        if (part.refresh()) {
            plan()
        }

        // how much is played feeds the eyes
        var energy = 0.0
        val (last, coming) = notes.around(t)

        var i = notes.lastIndexAt(t)

        while (i >= 0 && notes[i].t > t - 1.2) {
            val n = notes[i]

            energy += n.v * exp(-(t - n.t) / 0.3)
            i--
        }

        // the bars just hit glow and fade; the marimba hovers, and the mallets follow it
        marimba.answer(t = t, notes = notes)

        val hover = marimba.hoverAt(t)

        // is she playing: up from well before a phrase to a little after it; between phrases she relaxes, the
        // arms hanging with the mallets down beside her, standing upright
        val until = if (coming != null) coming.t - t else 99.0
        val since = if (last != null) t - last.t else 99.0
        // into position a few cycles ahead: the arms start to rise three cycles before her first note and she stands
        // ready, mallets over the bars, a good cycle before it
        val cycle = 240 / show.bpm
        val up = max(smooth(1 - (until - READY * cycle) / (RISE * cycle)), 1 - smooth((since - DONE) / FALL))
        // where she has the skill, she crosses her mallets below her head for a moment before a phrase and after
        // it: they rise from there to play (as above, from three cycles before the note) and come down into it when
        // she is done (as above, all down 2.6 s after the last note). How far the crossed mallets show: as far as
        // they are crossed and she is not playing; 0 where they are not
        val crossed = crossing?.at(t = t, plays = notes, rises = (READY + RISE) * cycle, falls = DONE + FALL, arrives = arrives) ?: 0.0
        val bones = crossed * (1 - up)

        val hands = listOf(Side.L, Side.R).associateWith { side ->
            val shoulder = rig.shoulder.getValue(side)
            val pose = strokeAt(
                list = byHand.getValue(side),
                t = t,
                rest = rests.getValue(side),
                home = vec(shoulder.x, 0.0, 0.22 * s),
                height = 0.20 * s,
                windup = 0.26,
                hover = 0.25,
            )

            pose.tip.y += hover

            val down = vec(shoulder.x + 0.05 * side.sign * s, rig.pelvis.y - 0.34 * s, 0.14 * s)

            pose.tip.lerpVectors(down, pose.tip, up)
            pose.aim.x *= up

            StickPose(tip = pose.tip, bend = pose.bend * up, lift = pose.lift, aim = pose.aim)
        }
        // each hand's two mallets: wide as its blows ask while she plays, nearly closed while the arms hang
        val halves = listOf(Side.L, Side.R).associateWith { side ->
            val half = halfAt(side, t)

            half.set(half.x * (0.3 + 0.7 * up), half.y * up, half.z * up)
        }
        val left = hands.getValue(Side.L)
        val right = hands.getValue(Side.R)

        // the body and the arms are two things: the arms reach for the notes, and the body stays where it is as long
        // as they can. Only where the playing moves out of easy reach does the body follow, by what is beyond it, and
        // slowly: it goes by where the hands play over some five seconds, the nearer moments counting more, and not
        // by each note; and it starts and ends softly. (Over two seconds, and from a hard edge, the weight went from
        // leg to leg too hectically: the maintainer, 2026-11-17.)
        val middle = (left.aim.x + right.aim.x) / 2
        var region = 0.0
        var counted = 0.0

        for (step in -WEIGH_STEPS..WEIGH_STEPS) {
            val counts = 0.5 + 0.5 * cos(PI * step / (WEIGH_STEPS + 1))

            for (side in listOf(Side.L, Side.R)) {
                val rest = rests.getValue(side)

                region += counts * strokeAt(list = byHand.getValue(side), t = t + step * WEIGH_STEP, rest = rest, home = rest, height = 0.0).aim.x
                counted += counts
            }
        }

        region *= up / counted

        val beyond = beyond(x = region, easy = 0.20 * s, soft = 0.08 * s)
        val stance = (0.07 + 0.10 * up) * s
        val reach = stance + rig.ankle.getValue(Side.L).x
        val shift = max(-1.0, min(1.0, beyond / reach)) * reach
        // -1 on the right leg, 1 on the left
        val weight = shift / (stance + rig.ankle.getValue(Side.L).x)
        val nod = mover.nod(t * show.bpm / 60)
        val body = nod + mover.burst()

        rig.reset()
        rig.update()
        rig.moveHips(vec(shift, -(0.015 + 0.03 * up + 0.010 * body * mover.parts.knees - 0.02 * abs(weight)) * rig.pelvis.y, 0.0))
        rig.turn("hips", Z_AXIS, -0.05 * weight)
        rig.turn("belly", X_AXIS, 0.02 + 0.11 * up + 0.02 * body * mover.parts.trunk)
        rig.turn("belly", Z_AXIS, 0.04 * weight)
        rig.turn("chest", Y_AXIS, 0.22 * (middle - shift) / s)
        rig.turn("head", X_AXIS, 0.04 + 0.18 * up + 0.08 * nod * mover.parts.head)
        mover.apply(rig, t)
        rig.turn("head", Y_AXIS, 0.5 * (middle - shift) / s)

        if (bones > 0) {
            // the mallets crossed below her head: the head up, level and still, looking straight ahead
            rig.faceTo(rig.root.localToWorld(vec(0.0, rig.height + 0.10 * s, 4.0 * s)), bones)
        }

        for (side in listOf(Side.L, Side.R)) {
            val foot = rig.ankle.getValue(side).clone()

            foot.x += stance * side.sign
            // the leg without the weight rests on its toes a little
            foot.y += 0.025 * s * max(0.0, -weight * side.sign)
            rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.25 * side.sign, 0.0, 1.0))
        }

        for (side in listOf(Side.L, Side.R)) {
            val hand = hands.getValue(side)
            val half = halves.getValue(side)
            val held = marimba.malletLength * GRIP
            // the hand holds both: it reaches for the place between the two heads, each of them about a mallet's hold
            // away. Each mallet is then set from its head, so the head is where the blow asks whatever the angle
            // between the two; only where the hand holds the shaft gives a little
            val grip = holdStick(
                rig = rig,
                side = side,
                tip = hand.tip,
                bend = hand.bend,
                length = marimba.malletLength,
                pole = rig.dir(0.6 * side.sign, -1.0, -0.5),
                mesh = null,
                reach = sqrt(max(0.25 * held * held, held * held - half.lengthSq())),
            )
            val heads = listOf(1.0, -1.0).map { way -> rig.root.localToWorld(hand.tip.clone().addScaledVector(half, way)) }

            if (bones > 0) {
                crossMallets(side = side, grip = grip, heads = heads, share = bones)
            }

            for ((mallet, head) in marimba.mallets.getValue(side).zip(heads)) {
                val along = head.clone().sub(grip).normalize()

                mallet.position.copy(head).addScaledVector(along, -marimba.malletLength / 2)
                mallet.quaternion.setFromUnitVectors(Y_AXIS, along)
            }
        }

        val eyes = rig.eyes

        if (eyes != null) {
            eyes.emissiveIntensity = 2 + 12 * min(1.6, energy)
        }
    }

    /**
     * The hand of [side] takes its mallets [share] of the way (0 to 1) from where they are ([grip], the hand, and the
     * two [heads], all in the world; changed in place) to the crossed mallets below her head: the hand low before
     * the chest on its own side, its two mallets nearly closed so they read as one bone, pointing up across to the
     * other side, the heads out past the other shoulder; the other hand's the mirror, so the two bones cross in an X
     * right below her chin, the left one a little before the right. On the way there and back the heads swing up on
     * their own side first, before the body, so the two hands' never meet. The arm then reaches for the hand's place,
     * the elbow low on its own side.
     */
    private fun crossMallets(side: Side, grip: Vector3, heads: List<Vector3>, share: Double) {
        val shoulder = rig.shoulder.getValue(side)
        val ahead = shoulder.z + (SKULL_AHEAD + (if (side == Side.L) SKULL_APART else 0.0)) * s
        val hand = vec(side.sign * SKULL_HAND_X * s, shoulder.y - SKULL_HAND_DOWN * s, ahead)
        val bone = vec(-side.sign * SKULL_ACROSS, SKULL_RISE, -SKULL_BACK).normalize()
        val middle = hand.clone().addScaledVector(bone, marimba.malletLength * GRIP)
        // on the way: up on its own side, before the body
        val via = vec(side.sign * SKULL_VIA_X * s, shoulder.y - SKULL_VIA_DOWN * s, ahead + SKULL_VIA_AHEAD * s)
        val b = share
        val c = 1 - b

        for ((head, way) in heads.zip(listOf(1.0, -1.0))) {
            val crossed = rig.root.localToWorld(middle.clone().add(vec(0.0, 0.0, way * SKULL_CLOSED * s)))
            val swing = rig.root.localToWorld(via.clone())

            head.multiplyScalar(c * c).addScaledVector(swing, 2 * b * c).addScaledVector(crossed, b * b)
        }

        grip.lerp(rig.root.localToWorld(hand), b).addScaledVector(rig.dir(0.0, 0.0, 1.0), SKULL_GRIP_AHEAD * s * sin(PI * b))

        rig.hand(side, grip, rig.dir(0.6 * side.sign, -1.0, -0.5).lerp(rig.dir(side.sign * 1.0, -1.0, -0.2), b).normalize())
        rig.point("hand$side", heads[0].clone().add(heads[1]).multiplyScalar(0.5).sub(grip))
    }

    private companion object {
        /** Where along a mallet the hand holds it, from its head, as a share of its length. */
        const val GRIP = 0.80

        /**
         * When she is up to play: ready over the bars [READY] cycles before a note, rising to it over the [RISE]
         * cycles before that; after a note she stays up [DONE] seconds and comes down over [FALL] seconds.
         */
        const val READY = 1.2
        const val RISE = 1.8
        const val DONE = 1.4
        const val FALL = 1.2

        /** How the body weighs where the hands play: this many moments either side of now, this many seconds apart. */
        const val WEIGH_STEPS = 6
        const val WEIGH_STEP = 0.45

        /**
         * The crossed mallets (see [crossMallets]), in the figure's measure: the hand so far out from her middle and
         * down from the shoulder, so far ahead of the shoulder (the left one so much further, so the bones lie one
         * before the other); the bone so much across for so much up and so much back toward the shoulder; a hand's
         * two heads so far apart (before and behind each other: nearly closed). On the way the heads swing by a place
         * so far out, down from the shoulder and further ahead, and the hand goes so far ahead of the straight way.
         */
        const val SKULL_HAND_X = 0.09
        const val SKULL_HAND_DOWN = 0.06
        const val SKULL_AHEAD = 0.14
        const val SKULL_APART = 0.035
        const val SKULL_ACROSS = 0.85
        const val SKULL_RISE = 0.45
        const val SKULL_BACK = 0.15
        const val SKULL_CLOSED = 0.012
        const val SKULL_VIA_X = 0.26
        const val SKULL_VIA_DOWN = 0.02
        const val SKULL_VIA_AHEAD = 0.12
        const val SKULL_GRIP_AHEAD = 0.06
    }
}
