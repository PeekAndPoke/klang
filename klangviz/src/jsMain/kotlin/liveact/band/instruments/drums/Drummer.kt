/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.figure.Mover
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.instruments.Stroke
import io.peekandpoke.klang.viz.liveact.band.instruments.holdStick
import io.peekandpoke.klang.viz.liveact.band.instruments.strokeAt
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.song.Drum
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Limb
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The drummer: sits on the kit's stool and plays a list of hits. Every place is in the figure's own measures (its
// leg's length), so the kit fits whoever sits there.

/** One hit of one limb, with the place it lands at. */
private class KitHit(override val t: Double, override val at: Vector3, override val v: Double) : Stroke

/**
 * The drummer of [show]: the figure [rig] on a stool behind a kit, playing [hits] (sorted by time, as far as
 * the song is known). He moves with the music by his [traits], his chance drawn under [seed].
 */
class Drummer(
    private val show: Show,
    private val rig: Rig,
    private val hits: Part<DrumHit>,
    traits: Traits,
    seed: Double = 1.0,
    /** The band's name, written on the bass drum's front head. */
    name: String = "",
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)
    private val s: Double = rig.s
    private var all: List<DrumHit> = emptyList()

    /** The kit he plays, and the sticks he plays it with (see [Kit]). */
    val kit: Kit = Kit(rig = rig, name = name)

    /** Each limb's hits, with the places they land at. */
    private var byLimb: Map<Limb, List<Stroke>> = emptyMap()

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

        rig.reset()
        rig.update()
        // sitting: the pelvis onto the stool, the trunk leaning in, nodding with the kick and the snare
        val nod = mover.nod(t * show.bpm / 60)
        val body = nod + mover.burst()
        val parts = mover.parts
        val bob = 0.012 * s * body * parts.knees + 0.004 * s * snare

        rig.moveHips(vec(0.0, kit.seat + 0.085 * s - rig.pelvis.y - bob, -0.02 * s))
        rig.turn("belly", X_AXIS, 0.19 + 0.05 * body * parts.trunk + 0.015 * snare)
        rig.turn("chest", Y_AXIS, 0.35 * (left.aim.x + right.aim.x) / s)
        rig.turn("chest", X_AXIS, 0.06)
        rig.turn("neck", X_AXIS, 0.06 * nod * parts.head)
        rig.turn("head", X_AXIS, -0.12 + 0.24 * nod * parts.head + 0.02 * snare)
        mover.apply(rig, t)

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

            holdStick(
                rig = rig,
                side = side,
                tip = hand.tip,
                bend = hand.bend,
                length = kit.stickLength,
                pole = rig.dir(0.55 * side.sign, -1.0, -0.5),
                mesh = kit.sticks.getValue(side),
            )
        }

        val eyes = rig.eyes

        if (eyes != null) {
            eyes.emissiveIntensity = 2 + 14 * min(1.6, energy)
        }
    }

    private companion object {
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
