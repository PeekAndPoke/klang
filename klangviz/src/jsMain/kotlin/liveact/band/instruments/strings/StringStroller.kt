/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.WalkIn
import io.peekandpoke.klang.viz.liveact.band.figure.blend
import io.peekandpoke.klang.viz.liveact.band.figure.capture
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute.Offset
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollTime
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.Stroller
import io.peekandpoke.klang.viz.liveact.camera.LOOK_BARS
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.song.Strum
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.getWorldPosition

/**
 * The walk with the guitar (`GuitarStroll`): a string player who strolls about the front of the stage while they
 * play, on the trumpet player's route ([Stroller]) at two thirds of her pace ([SLOW]), when [planStringStrolls] says.
 * [show]: the show, for the route's seed and the bar; [rig]: the player's figure; [part]: their strums, read on their
 * own (refreshed here, apart from the ones they play from); [place]: where they stand in the stage plot; [others]:
 * where the others stand, whom they visit on some rounds. The instrument stays on its strap and the player plays on:
 * only where the figure stands, which way it faces, how its legs step and how its hips move change. The hips move
 * as the kind of walk has them on the route ([hips], as the trumpet player's do), not as on the walk-in.
 */
class StringStroller(private val show: Show, private val rig: Rig, private val part: Part<Strum>, place: Place, others: Map<Player, Place>) {
    private companion object {
        /** How much slower than the trumpet player's the route's clock runs: two thirds of her pace (the maintainer, 2026-11-27). */
        const val SLOW = 1.5
    }

    /** How far ahead the song is known: a song played live as far as it is read ahead, one known whole all the way. */
    private val ahead: Double = if (part.grows) STROLL_AHEAD else Double.POSITIVE_INFINITY

    private val stroller: Stroller

    /** The last plan (see [planStringStrolls]): it goes on from what the song has forgotten. */
    private var planned: StringStrollPlan = StringStrollPlan()

    /** How far the figure has walked on the stroll it is on: what the legs step by. */
    private val way: WayWalked

    /**
     * The modifier of the kind of walk on the hips at the last [walk] (see `WalkKind`; the route's, as for the trumpet
     * player): null where the figure was left as its walk-in put it.
     */
    var hips: Double? = null
        private set

    /** The note times last asked of [seen], and what came back: asked every frame, worked out only when they change. */
    private var seenOf: Pair<List<Double>, List<StrollTime>>? = null
    private var seenAs: List<Double> = emptyList()

    init {
        val home = vec(place.x, place.y, place.z)

        stroller = Stroller(
            seed = show.seed,
            home = home,
            homeTurn = place.turn,
            s = rig.s,
            closest = Stroller.closestToCamera(home = home, chin = chinAt(place), s = rig.s, height = rig.height),
            others = others,
            slow = SLOW,
        )
        way = WayWalked { t -> stroller.walkAt(t).at.let { Offset(x = it.x, z = it.z) } }

        part.refresh()
        plan(now = Double.NEGATIVE_INFINITY)
    }

    /**
     * The height where the head's bone starts with the figure standing at rest at [place]: what the nearest place to
     * the camera goes by (see [Stroller.closestToCamera]). The figure is left where it was, held as it was.
     */
    private fun chinAt(place: Place): Double {
        val held = capture(rig)
        val stoodAt = rig.root.position.clone()
        val stoodTurned = rig.root.rotation.y

        rig.root.position.set(place.x, place.y, place.z)
        rig.root.rotation.y = place.turn
        rig.reset()
        rig.update()

        val chin = rig.bone("head").getWorldPosition(vec()).y

        rig.root.position.copy(stoodAt)
        rig.root.rotation.y = stoodTurned
        blend(rig = rig, a = held, b = held, w = 0.0)

        return chin
    }

    /** The strolls, from the strums as far as they are known at [now] (see [planStringStrolls]: one that has begun stays). */
    private fun plan(now: Double) {
        planned = planStringStrolls(strums = part.events, now = now, before = planned, ahead = ahead)
        stroller.plan(times = planned.strolls, now = now)
    }

    /**
     * The figure at [t] on a stroll: once its walk-in has arrived ([walkIn], from `enter`, which put it at its place),
     * the root goes where the stroll has it and turns into its way, and what comes back has the legs step by the way
     * walked on the stroll (for `stepLegs`; the hips by [hips]). At its place, or before it has arrived, the figure is
     * left as the walk-in put it, and [walkIn] comes back. Call it every frame, before the player is posed: the plan
     * is kept up to date here.
     */
    fun walk(t: Double, walkIn: WalkIn): WalkIn {
        if (part.refresh()) {
            plan(now = t)
        }

        val from = stroller.strollFrom(t)

        if (walkIn.since < 0 || from == null) {
            way.rest()
            hips = null

            return walkIn
        }

        val walk = stroller.walkAt(t)

        rig.root.position.copy(walk.at)
        rig.root.rotation.y = walk.turn
        hips = walk.hips

        return WalkIn(going = walk.going, far = way.at(from = from, t = t), since = walkIn.since)
    }

    /**
     * Of [times] (when the player begins a note), those the camera may choose a look on them by: none while they
     * stroll, nor a look before that would reach into a stroll (see [outsideStrolls]).
     */
    fun seen(times: List<Double>): List<Double> {
        val asked = times to planned.strolls
        val before = seenOf

        if (before == null || before.first !== asked.first || before.second !== asked.second) {
            seenOf = asked
            seenAs = outsideStrolls(times = times, strolls = planned.strolls, margin = LOOK_BARS * show.bar)
        }

        return seenAs
    }
}
