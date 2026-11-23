/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.figure.Mover
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.instruments.Stroke
import io.peekandpoke.klang.viz.liveact.band.instruments.holdStick
import io.peekandpoke.klang.viz.liveact.band.instruments.strokeAt
import io.peekandpoke.klang.viz.liveact.band.skills.BrowWipe
import io.peekandpoke.klang.viz.liveact.band.skills.CapTug
import io.peekandpoke.klang.viz.liveact.band.skills.HeadScratch
import io.peekandpoke.klang.viz.liveact.band.skills.IdleInUse
import io.peekandpoke.klang.viz.liveact.band.skills.NeckScratch
import io.peekandpoke.klang.viz.liveact.band.skills.NeckStretch
import io.peekandpoke.klang.viz.liveact.band.skills.ShakeOut
import io.peekandpoke.klang.viz.liveact.band.skills.Slouch
import io.peekandpoke.klang.viz.liveact.band.skills.StickTwirl
import io.peekandpoke.klang.viz.liveact.band.skills.TuneUp
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.song.Drum
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Limb
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyAxisAngle
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

// The drummer: sits on the kit's stool and plays a list of hits. Every place is in the figure's own measures (its
// leg's length), so the kit fits whoever sits there.

/** One hit of one limb, with the place it lands at. */
private class KitHit(override val t: Double, override val at: Vector3, override val v: Double) : Stroke

/**
 * The hand of [side] of the drummer as it holds its stick before an idle skill takes it, in the world: where the hand
 * is ([wrist]), which way the stick points from its butt to its tip ([along]), where the elbow bends to ([pole]); and
 * how the arm's bones were turned then ([arm]), to place the hand again from there.
 */
private class HeldStick(
    val side: Side,
    val wrist: Vector3,
    val along: Vector3,
    val pole: Vector3,
    val arm: List<Quaternion>,
)

/**
 * The drummer of [show]: the figure [rig] on a stool behind a kit, playing [hits] (sorted by time, as far as
 * the song is known). He moves with the music by his [traits], his chance drawn under [seed]. [idles]: the member's
 * idle skills in use at a time (see `idlesAt`; none unless given): of them a drummer shows a stick twirled in the
 * right hand ([StickTwirl]) and the neck scratched with the butt of the left stick ([NeckScratch]).
 */
class Drummer(
    private val show: Show,
    private val rig: Rig,
    private val hits: Part<DrumHit>,
    traits: Traits,
    seed: Double = 1.0,
    /** The band's name, written on the bass drum's front head. */
    name: String = "",
    private val idles: (Double) -> List<IdleInUse> = { emptyList() },
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)
    private val s: Double = rig.s
    private var all: List<DrumHit> = emptyList()

    /** The kit he plays, and the sticks he plays it with (see [Kit]). */
    val kit: Kit = Kit(rig = rig, name = name)

    /** Each limb's hits, with the places they land at. */
    private var byLimb: Map<Limb, List<Stroke>> = emptyMap()

    /** This frame's left hand where it scratches the neck, and the use: placed again in [afterPose]. */
    private var scratching: Pair<HeldStick, IdleInUse>? = null

    /** The last plan (see [planDrums]): where the song has forgotten its past, the next one goes on from it. */
    private var planned: DrumPlan? = null

    init {
        hits.refresh()
        plan()
    }

    /**
     * Which limb hits what and when, from the hits as far as they are known. Where the song has forgotten its past,
     * each limb's list begins with its last hit forgotten: the stick stays where that hit left it, as it would with
     * the whole song.
     */
    private fun plan() {
        val from = hits.events.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
        val next = planDrums(hits = hits.events, before = planned?.carryOn(from = from) ?: emptyMap())

        all = hits.events
        planned = next
        byLimb = next.byLimb.mapValues { (_, list) -> list.map { h -> KitHit(t = h.t, at = kit.at(h.drum), v = h.v) } }
    }

    /** The drummer and his kit at [t] (seconds). */
    fun update(t: Double) {
        if (hits.refresh()) {
            plan()
        }

        // what was hit: a cymbal swings, a drum dips (the kit answers), and it all feeds the body
        kit.answer(t = t, hits = all)

        var energy = 0.0
        var snare = 0.0
        var i = all.lastIndexAt(t)

        while (i >= 0 && all[i].t > t - 1.5) {
            val h = all[i]
            val age = t - h.t
            energy += h.v * exp(-age / 0.35) * (if (h.drum == Drum.Hat) 0.25 else 1.0)

            if (h.drum == Drum.Snare || h.drum == Drum.Kick) {
                snare = max(snare, h.v * exp(-age / 0.12))
            }

            i--
        }

        val hands = listOf(Side.L, Side.R).associateWith { side ->
            strokeAt(
                list = byLimb.getValue(side.hand),
                t = t,
                rest = kit.at(if (side == Side.L) Drum.Snare else Drum.Hat),
                home = vec(rig.shoulder.getValue(side).x, 0.0, 0.30 * s),
                height = 0.40 * s,
            )
        }
        val left = hands.getValue(Side.L)
        val right = hands.getValue(Side.R)
        // what he does with the sticks while the part pauses, and how far the head bows to a scratch of the neck
        val idle = idles(t)
        val bow = idle.filter { it.skill is NeckScratch }.maxOfOrNull { it.share } ?: 0.0

        rig.reset()
        rig.update()
        // sitting: the pelvis onto the stool, the trunk leaning in, nodding with the kick and the snare
        val nod = mover.nod(show.beatAt(t))
        val body = nod + mover.burst()
        val parts = mover.parts
        val bob = 0.012 * s * body * parts.knees + 0.004 * s * snare

        rig.moveHips(vec(0.0, kit.seat + 0.085 * s - rig.pelvis.y - bob, -0.02 * s))
        rig.turn("belly", X_AXIS, 0.19 + 0.05 * body * parts.trunk + 0.015 * snare)
        rig.turn("chest", Y_AXIS, 0.35 * (left.aim.x + right.aim.x) / s)
        rig.turn("chest", X_AXIS, 0.06)
        rig.turn("neck", X_AXIS, 0.06 * nod * parts.head)
        rig.turn("head", X_AXIS, -0.12 + 0.24 * nod * parts.head + 0.02 * snare)

        // at rest (a long silence in his part): the back comes up from the kit, and the head bows a little
        val (last, coming) = all.around(t)
        val rest = restShare(
            since = if (last != null) t - last.t else Double.POSITIVE_INFINITY,
            until = if (coming != null) coming.t - t else Double.POSITIVE_INFINITY,
        )

        if (rest > 0) {
            rig.turn("belly", X_AXIS, -REST_UPRIGHT * rest)
            rig.turn("head", X_AXIS, REST_BOW * rest)
        }
        mover.apply(rig, t)

        if (bow > 0) {
            // the neck scratched: the head bows forward and a little aside
            rig.turn("head", X_AXIS, NECK_BOW * bow)
            rig.turn("head", Z_AXIS, -NECK_ASIDE * bow)
        }

        // the feet on their pedals; the right one stamps the kick
        for (side in listOf(Side.L, Side.R)) {
            val foot = kit.feet.getValue(side).clone()
            // each foot stamps its own kicks: up just before, down at the hit
            val (_, next) = byLimb.getValue(side.kick).around(t)
            val until = if (next != null) next.t - t else 9.0

            foot.y += 0.05 * s * (if (until < 0.16) sin(PI * (1 - until / 0.16)) else 0.0)
            rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.5 * side.sign, 0.6, 1.0))
        }

        // the hands, and the sticks in them
        for (side in listOf(Side.L, Side.R)) {
            val hand = hands.getValue(side)

            // at rest the stick lies along the thigh, its tip past the knee, the hand on the thigh
            val resting = vec(REST_OUT * side.sign * s, kit.seat + REST_HIGH * s, REST_FORE * s)

            holdStick(
                rig = rig,
                side = side,
                tip = hand.tip.clone().lerp(resting, rest),
                bend = hand.bend * (1 - rest),
                length = kit.stickLength,
                pole = rig.dir(0.55 * side.sign, -1.0, -0.5),
                mesh = kit.sticks.getValue(side),
            )
        }

        scratching = null

        for (used in idle) {
            when (used.skill) {
                is StickTwirl -> placeStick(held = heldStick(Side.R), used = used)

                is NeckScratch -> {
                    val held = heldStick(Side.L)

                    placeStick(held = held, used = used)
                    scratching = held to used
                }

                is HeadScratch, is Slouch, is NeckStretch, is ShakeOut, is TuneUp, is CapTug, is BrowWipe -> Unit
            }
        }

        val eyes = rig.eyes

        if (eyes != null) {
            eyes.emissiveIntensity = 2 + 14 * min(1.6, energy)
        }
    }

    /**
     * Places the left hand and its stick again where the neck is scratched this frame, from the neck and the head as
     * they are held at last (eye contact turns them after the poses): call it once everything that turns a head has
     * done so. The arm is put back as it was before the scratch took it, and placed anew, so the stick is where one
     * placing from this head would put it. Nothing where no scratch is in use.
     */
    fun afterPose() {
        val (held, used) = scratching ?: return

        for ((name, turn) in armOf(held.side).zip(held.arm)) {
            rig.bone(name).quaternion.copy(turn)
        }

        rig.update()
        placeStick(held = held, used = used)
    }

    /** The hand of [side] and its stick as they are now, before an idle skill takes them (see [HeldStick]). */
    private fun heldStick(side: Side): HeldStick {
        val stick = kit.sticks.getValue(side)

        return HeldStick(
            side = side,
            wrist = rig.at("hand$side"),
            along = vec(0.0, 1.0, 0.0).applyQuaternion(stick.quaternion),
            pole = rig.dir(0.55 * side.sign, -1.0, -0.5),
            arm = armOf(side).map { name -> rig.bone(name).quaternion.clone() },
        )
    }

    /**
     * The hand and stick of [held] taken as far as the idle skill [used] asks (its share of the way): the right hand
     * held up beside him, the stick spinning in the fingers, for a twirl; the butt of the left stick at the back of
     * the neck, scratching, for a scratch of the neck (as the head and the neck are posed now).
     */
    private fun placeStick(held: HeldStick, used: IdleInUse) {
        val side = held.side
        val w = used.share
        val length = kit.stickLength
        val wrist: Vector3
        val along: Vector3
        val points: Vector3
        val pole: Vector3
        // where the hand holds the stick, from its middle toward the butt (the hand's own grip, or the middle)
        val grip: Double

        if (used.skill is StickTwirl) {
            // the hand up beside him, a little ahead; the stick spins about the line across the palm, past the hand's
            // outer side, so it never meets the forearm
            val shoulder = rig.at("elbow$side")
            val spin = twirlAt(used)

            wrist = shoulder
                .addScaledVector(rig.dir(side.sign, 0.0, 0.0), TWIRL_OUT * s)
                .addScaledVector(rig.dir(0.0, 1.0, 0.0), TWIRL_UP * s)
                .addScaledVector(rig.dir(0.0, 0.0, 1.0), TWIRL_AHEAD * s)
            along = rig.dir(0.0, 1.0, 0.0).applyAxisAngle(rig.dir(side.sign, 0.0, 0.0), spin)
            points = rig.dir(0.0, 1.0, 0.3)
            pole = rig.dir(side.sign * 0.8, -1.0, -0.2)
            grip = 0.0
        } else {
            // the butt of the stick at the back of the neck, going up and down a little; the stick out to the side
            val neck = rig.at("neck")
            val ahead = rig.faceAhead.clone().applyQuaternion(rig.bone("head").getWorldQuaternion(quat()))
            val scratch = SCRATCH_DEPTH * s * sin(2 * PI * SCRATCH_RATE * used.since)
            val butt = neck
                .addScaledVector(rig.axisOf("neck"), (BUTT_UP * s + scratch))
                .addScaledVector(ahead, -BUTT_BEHIND * s)

            along = rig.dir(side.sign * 1.0, 0.2, 0.25).normalize()
            points = along
            wrist = butt.addScaledVector(along, length * (1 - GRIP))
            pole = rig.dir(side.sign * 1.0, 0.2, 0.5)
            grip = GRIP - 0.5
        }

        // the hand from where it was, the elbow pulled down on the way there and back, the stick turning with it
        val hand = held.wrist.clone().lerp(wrist, w)
        val bent = held.pole.clone().lerp(pole, w).addScaledVector(rig.dir(0.0, -1.0, 0.0), 2 * sin(PI * w)).normalize()
        val turned = held.along.clone().lerp(along, w).normalize()

        rig.hand(side, hand, bent)
        rig.point("hand$side", held.along.clone().lerp(points, w).normalize())

        val stick = kit.sticks.getValue(side)
        val atGrip = rig.at("hand$side").addScaledVector(turned, length * (GRIP - 0.5))
        val atTwirl = rig.at("hand$side").addScaledVector(turned, length * grip)
            .addScaledVector(rig.dir(side.sign, 0.0, 0.0), if (used.skill is StickTwirl) TWIRL_PAST * s else 0.0)

        stick.position.copy(atGrip.lerp(atTwirl, w))
        stick.quaternion.setFromUnitVectors(Y_AXIS, turned)
    }

    /**
     * How far the stick of the twirl [used] has turned (radians), at the time of the use: it starts as the hand comes
     * up, spins evenly, and comes to rest over the fall, upright, at whole turns (the speed set for each use so they
     * come out whole, near [TWIRL_RATE]). So it never whips round as it goes back to the playing grip.
     */
    private fun twirlAt(used: IdleInUse): Double {
        val timing = used.skill.timing
        val start = timing.rise / 2
        val falls = used.length - timing.fall
        // spinning evenly from the start to the fall, then slowing to rest over the fall (half as far as even)
        val way = falls - start + timing.fall / 2
        val rate = 2 * PI * max(1.0, round(TWIRL_RATE * way)) / way
        val slowing = ((used.since - falls) / timing.fall).coerceIn(0.0, 1.0)

        return when {
            used.since < start -> 0.0
            used.since < falls -> rate * (used.since - start)
            else -> rate * (falls - start) + rate * timing.fall / 2 * (1 - (1 - slowing) * (1 - slowing))
        }
    }

    private companion object {
        /**
         * At rest (see `restShare`): how far the back comes up and the head bows (radians), and where a stick's tip
         * lies, in the figure's sizes: out to the side, over the stool's height, and before the body (past the
         * knee, the stick along the thigh and the hand on it).
         */
        const val REST_UPRIGHT = 0.12
        const val REST_BOW = 0.16
        const val REST_OUT = 0.17
        const val REST_HIGH = 0.07
        const val REST_FORE = 0.50

        /** Where along a stick the hand holds it, from its tip, as a share of its length (as in `holdStick`). */
        const val GRIP = 0.72

        /**
         * The stick twirled: the hand from the shoulder so far out, up and ahead (the forearm up, the elbow low); so
         * many turns a second; the stick spinning so far past the hand's outer side.
         */
        const val TWIRL_OUT = 0.16
        const val TWIRL_UP = 0.04
        const val TWIRL_AHEAD = 0.14
        const val TWIRL_RATE = 1.6
        const val TWIRL_PAST = 0.05

        /**
         * The neck scratched: the butt so far up the neck from its start and behind it; the scratch so deep, so many
         * times a second; the head bowed so far forward and aside (radians).
         */
        const val BUTT_UP = 0.05
        const val BUTT_BEHIND = 0.07
        const val SCRATCH_DEPTH = 0.015
        const val SCRATCH_RATE = 3.0
        const val NECK_BOW = 0.25
        const val NECK_ASIDE = 0.10

        /** The bones of the arm of a side, from the shoulder to the hand: what placing the hand turns. */
        fun armOf(side: Side): List<String> = listOf("elbow$side", "wrist$side", "hand$side")

        /** The hand of a side. */
        val Side.hand: Limb
            get() = when (this) {
                Side.L -> Limb.L
                Side.R -> Limb.R
            }

        /** The foot of a side, on the kick's double pedal. */
        val Side.kick: Limb
            get() = when (this) {
                Side.L -> Limb.KickL
                Side.R -> Limb.KickR
            }
    }
}
