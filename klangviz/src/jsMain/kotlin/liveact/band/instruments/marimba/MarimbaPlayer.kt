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
import kotlin.math.sqrt

// The marimba player: stands behind a marimba and plays notes with four mallets, two in each hand. Which bar is hit
// follows the note, so the player moves along the instrument with the tune. All places are in the figure's own
// measures.

/**
 * One blow of one hand, with its two mallets: when, how hard, and where. [at] is the place between the two heads
 * and [half] the way from there to the outer mallet's head (the inner one's is as far the other way). Where the
 * hand plays one note, its inner mallet lands on the bar and the outer one rides beside it, above the bars.
 */
private class MalletHit(
    override val t: Double,
    override val v: Double,
    override val at: Vector3,
    val half: Vector3,
    val side: Side,
) : Stroke

/**
 * The marimba player of [show]: the figure [rig] behind a marimba, playing [notes] (sorted by time) with four
 * mallets, two in each hand: a single note with a hand's inner mallet, notes that sound together with as many
 * mallets as they need (see [malletShare]). She moves with the music by her [traits], her chance drawn under [seed].
 */
class MarimbaPlayer(
    private val show: Show,
    private val rig: Rig,
    private val part: Part<Note>,
    traits: Traits,
    seed: Double = 2.0,
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)
    private val s: Double = rig.s

    /** Her notes, sorted by time, as far as the song is known. */
    private var notes: List<Note> = emptyList()

    /** Which hand plays which note. */
    private var byHand: Map<Side, List<MalletHit>> = emptyMap()

    /** The notes known as they are played, in their order. */
    private var played: List<MalletHit> = emptyList()

    /**
     * Where the hands were before the first note known, where the song has forgotten its past: the hand that played
     * last, and each hand's last hit. The hands go on from there, so no note that is still known changes hands, and
     * each hand's list begins with its last hit forgotten: the mallet stays where that hit left it.
     */
    private var lastBefore: Side = Side.R
    private val hitsBefore = mutableMapOf<Side, MalletHit>()

    /** The marimba she plays, built to the range of her notes, and her mallets (see [Marimba]). */
    val marimba: Marimba

    /** Where a hand's mallets idle when it has nothing to play: the place between their two heads. */
    private val rests: Map<Side, Vector3>

    /** From there to the outer mallet's head, as the hand holds the two when both idle. */
    private val restHalves: Map<Side, Vector3> = Side.entries.associateWith { side -> vec(side.sign * APART / 2 * s, 0.0, 0.0) }

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
        val from = notes.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
        val gone = played.filter { it.t < from }

        gone.lastOrNull()?.let { lastBefore = it.side }

        for (side in Side.entries) {
            gone.lastOrNull { it.side == side }?.let { hitsBefore[side] = it }
        }

        val hands = Side.entries.associateWith { side -> listOfNotNull(hitsBefore[side]).toMutableList() }
        val all = mutableListOf<MalletHit>()
        var last = lastBefore
        val where = hitsBefore.toMutableMap()

        /** The [hand] plays the [struck] bars (one or two) at [t], as hard as [v]. */
        fun blow(hand: Side, struck: List<MarimbaBar>, t: Double, v: Double) {
            val half = when (struck.size) {
                // one note: the inner mallet plays it, the outer one rides beside it, further out and above the bars
                1 -> vec(hand.sign * APART / 2 * s, RIDE / 2 * s, 0.0)
                // two: the outer mallet takes the bar further out (the left hand's lower one, the right hand's higher)
                else -> {
                    val (inner, outer) = struck.sortedBy { it.at.x * hand.sign }

                    vec().addScaledVector(outer.at, 0.5).addScaledVector(inner.at, -0.5)
                }
            }
            val inner = struck.minBy { it.at.x * hand.sign }
            val hit = MalletHit(t = t, v = v, at = inner.at.clone().add(half), half = half, side = hand)

            all.add(hit)
            hands.getValue(hand).add(hit)
            where[hand] = hit
            last = hand
        }

        var i = 0

        while (i < notes.size) {
            // what sounds together is one moment
            var end = i + 1

            while (end < notes.size && notes[end].t - notes[i].t < TOGETHER) {
                end++
            }

            val together = notes.subList(i, end)
            val t = together.first().t

            i = end

            // (notes that lie on one bar, as a note doubled an octave beyond the bars does, are one note)
            val struck = together.map { marimba.barOf(it.midi) }.distinct()
            val v = together.maxOf { it.v }

            if (struck.size == 1) {
                // one note: the hands play in turn, but never crossing over each other
                val at = struck.single().at
                var hand = last.other
                val other = hand.other
                val there = where[other]
                // where the other hand's inner mallet is, the one nearest this hand (its blow's middle less the way
                // to its outer one)
                val recent = if (there != null && t - there.t < 0.6) there.at.x - there.half.x else null
                val slack = marimba.width / 2

                // the left hand is on the side of the low notes (x > 0): it never plays to the right of the right
                // hand's mallets, nor between them
                if (recent != null && ((hand == Side.L && at.x < recent - slack) || (hand == Side.R && at.x > recent + slack))) {
                    hand = other
                }

                blow(hand = hand, struck = struck, t = t, v = v)

                continue
            }

            // several: shared between the hands, the low ones for the left (the low notes lie at x > 0)
            val share = malletShare(places = struck.map { -it.at.x }, reach = REACH * s)

            for ((hand, mine) in listOf(Side.L to share.low, Side.R to share.high)) {
                if (mine.isNotEmpty()) {
                    blow(hand = hand, struck = mine.map { struck[it] }, t = t, v = v)
                }
            }
        }

        byHand = hands
        played = all
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
        val up = max(smooth(1 - (until - 1.2 * cycle) / (1.8 * cycle)), 1 - smooth((since - 1.4) / 1.2))

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

            for ((mallet, way) in marimba.mallets.getValue(side).zip(listOf(1.0, -1.0))) {
                val head = rig.root.localToWorld(hand.tip.clone().addScaledVector(half, way))
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


    private companion object {
        /** Notes that begin within this many seconds of each other sound together: one blow. */
        const val TOGETHER = 0.03

        /** How far apart a hand's two mallet heads are when it plays one note or none, in the figure's measure: two bars or so. */
        const val APART = 0.11

        /** How high above the bars the mallet rides that does not play. */
        const val RIDE = 0.045

        /** How wide a hand's two mallets open at most: an octave in the middle of the instrument. */
        const val REACH = 0.36

        /** Where along a mallet the hand holds it, from its head, as a share of its length. */
        const val GRIP = 0.80

        /** How the body weighs where the hands play: this many moments either side of now, this many seconds apart. */
        const val WEIGH_STEPS = 6
        const val WEIGH_STEP = 0.45
    }
}
