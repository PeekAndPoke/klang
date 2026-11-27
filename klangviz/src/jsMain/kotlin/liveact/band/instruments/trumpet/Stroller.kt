/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.figure.TALL
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.camera.HOME
import io.peekandpoke.klang.viz.liveact.camera.TOTAL_DRIFT_Y
import io.peekandpoke.klang.viz.liveact.camera.TOTAL_DRIFT_Z
import io.peekandpoke.klang.viz.liveact.headReach
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The strolls of one who walks about the front of the stage: the trumpet player while her part rests (see
 * [TrumpetPlayer]), a string player while they play (see `StringStroller`). Their way is the same for every stroll
 * (see [StrollRoute]): out from their place, about, back to it, and there they wait a while before the next round;
 * in time a planned way home, so they are at their place, facing the front, as a stroll ends (see [StrollHome]).
 *
 * [home]: where they stand at their place, [homeTurn] which way they face there; [s]: their size. [seed]: the show's,
 * for the route; [closest]: the nearest they may come to the total's camera (see [closestToCamera]); [others]: where
 * the others stand in the stage plot, whom they visit on some rounds.
 *
 * [slow]: how much slower the route's clock runs than the show's. Their place at a time t is the route's at t / [slow],
 * so the rounds, the legs, the pauses and the wait at home all stretch by it, and every turn with them: 1 is the
 * trumpet player's pace, 1.5 two thirds of it.
 */
class Stroller(
    seed: Int,
    private val home: Vector3,
    private val homeTurn: Double,
    private val s: Double,
    closest: StrollRoute.Offset,
    others: Map<Player, Place>,
    private val slow: Double = 1.0,
) {
    companion object {
        /**
         * Back at the place from a stroll, the figure turns to the front within this many seconds of the route's clock. A
         * guard only: the way home arrives facing the front (see [StrollHome]), and the body's inertia ([StrollTurn])
         * has caught up by then, so there is next to nothing left to turn.
         */
        private const val TURN_HOME = 3.0

        /**
         * The nearest one who stands at [home], [s] their size, [height] their height, may come to the total's camera
         * on a stroll (see [StrollRoute]), off their place in their sizes: right before the lens, as close as their
         * head stays whole in the wide shot. The total drifts a little ([TOTAL_DRIFT_Z] toward the stage and
         * [TOTAL_DRIFT_Y] up at most), so its nearest and highest place counts. The frame's lower edge falls from the
         * lens at half the lens's angle plus the camera's tilt down to what it looks at, the upper edge rises at half
         * the angle less the tilt: their [chin] (the height where the head's bone starts, standing at their place)
         * stays above the one, the top of their head ([TALL]) below the other. The face is [FACE] of their size
         * before their middle, and [ROOM] more is left.
         */
        fun closestToCamera(home: Vector3, chin: Double, s: Double, height: Double): StrollRoute.Offset {
            val lensY = HOME.pos.y + TOTAL_DRIFT_Y
            val lensZ = HOME.pos.z - TOTAL_DRIFT_Z
            val tilt = atan2(HOME.pos.y - HOME.at.y, HOME.pos.z - HOME.at.z)
            val half = HOME.fov / 2 * PI / 180
            val top = home.y + TALL * height
            val below = if (chin < lensY) (lensY - chin) / tan(half + tilt) else 0.0
            val above = if (top > lensY) (top - lensY) / tan(half - tilt) else 0.0
            val stand = lensZ - max(below, above) - (FACE + ROOM) * s

            return StrollRoute.Offset(x = (HOME.pos.x - home.x) / s, z = (stand - home.z) / s)
        }

        /** How far the face is before the middle, and the room left before the camera, in the figure's sizes. */
        private const val FACE = 0.1
        private const val ROOM = 0.1

        /**
         * Where they look at one they visit (see [visitLook]): so high over their place, in metres, about at their
         * eyes; on the riser, over the riser, as the drummer sits at the kit.
         */
        private const val EYES = 1.6
        private const val SEATED_EYES = 1.25

        /**
         * A look out of sight on a stroll lets go over this many seconds, as Sirene's looks fall (`SIRENE`; the
         * maintainer, 2026-11-19: "One sudden movement is fine, but the way back then needs to be slower").
         */
        const val LOOK_LET_GO = 1.6

        /** The moments before that it is let go from, this many seconds apart. */
        const val LOOK_STEP = 0.1
    }

    /** Which way the stroll goes at one moment: how fast, how much it walks (0 to 1), and the turn into its way. */
    private class Facing(val speed: Double, val going: Double, val turn: Double)

    /**
     * A stretch in which they stroll, from [from] to [to] seconds of the route's clock, its end known since [known]
     * (see [StrollHome]: the route, and a way home in time). Which way they face on it is worked out once per stroll
     * (see [StrollTurn]): they follow their way with a body's inertia. [before]: the stroll it replaces, where its end
     * changed while they strolled (the same start): up to [known] they go and face as they did on it. Only what is
     * needed is taken over, so no line of replaced strolls is kept.
     */
    private inner class Stroll(val from: Double, val to: Double, known: Double, before: Stroll? = null) {
        /** Where the stroll has them, off their place, in their sizes. */
        val way: StrollHome = StrollHome(route = route, from = from, to = to, notBefore = known, before = before?.way)

        private val turn: StrollTurn = StrollTurn(from = from, to = to, handOver = before?.turn, at = known) { x -> facing(this, x).turn }

        /** Which way they face at [x] (the route's clock), off the way they face at their place. */
        fun turnAt(x: Double): Double = turn.at(x)
    }

    /** Their way on a stroll, the same for every stroll (see [StrollRoute]): a stroll only shifts its time. */
    private val route: StrollRoute = StrollRoute(
        seed = seed,
        restTurn = homeTurn,
        closest = closest,
        // the others' places in the stage plot, off theirs, in their sizes: they visit them on some rounds
        others = others.mapValues { (_, place) ->
            StrollRoute.Offset(x = (place.x - home.x) / s, z = (place.z - home.z) / s)
        },
    )

    /**
     * Where the others' eyes are, about, for the look at one they visit ([visitLook]): over their place in the stage
     * plot ([others]; the rigs of the others are not theirs to read), at [EYES], or [SEATED_EYES] over the riser.
     */
    private val heads: Map<Player, Vector3> = others.mapValues { (_, place) ->
        vec(place.x, place.y + (if (place.y > 0.0) SEATED_EYES else EYES), place.z)
    }

    /** The strolls as last planned ([plan]), on the route's clock. */
    private var strolls: List<Stroll> = emptyList()

    /**
     * The strolls [times] (seconds on the show's clock), as planned at [now]: the same stroll as before keeps what
     * was worked out for it; one whose end changed goes on from where the one it replaces had them (see [StrollHome]).
     */
    fun plan(times: List<StrollTime>, now: Double) {
        val before = strolls
        val known = now / slow

        strolls = times.map { time ->
            val from = time.from / slow
            val to = time.to / slow
            val old = before.firstOrNull { it.from == from }

            if (old != null && old.to == to) {
                old
            } else {
                Stroll(from = from, to = to, known = known, before = old)
            }
        }
    }

    /** Where the stroll [k] has them at [x] (the route's clock), off their place. */
    private fun strollAt(k: Stroll, x: Double): Vector3 {
        val at = k.way.at(x)

        return vec(at.x * s, 0.0, at.z * s)
    }

    /** How the stroll [k] goes at [x] (the route's clock); its speed in metres a second of that clock. */
    private fun facing(k: Stroll, x: Double): Facing {
        val ahead = strollAt(k, x + 0.2).sub(strollAt(k, x - 0.2)).multiplyScalar(1 / 0.4)
        val speed = ahead.length()
        // they look where they go, the more the faster they go; standing, they face the audience. Going away from it
        // they turn round and walk, the back to it: held sideways she slid backwards (the maintainer, 2026-11-16)
        val going = smooth(min(1.0, speed / (0.05 * s)))
        var heading = atan2(ahead.x, ahead.z) - homeTurn

        heading = atan2(sin(heading), cos(heading))

        // standing beside one they visit, they turn to them (see [StrollRoute.visitAt]; 0 where they walk)
        return Facing(speed = speed, going = going, turn = heading * going + (k.way.visitAt(x)?.turn ?: 0.0))
    }

    /**
     * Their place on the stage at [t] (seconds on the show's clock), which way they walk, how fast (metres a second
     * of the show's clock) and how much they walk (on the route's clock, which only stretches the way: a slow stroll
     * walks as much, slower).
     */
    fun walkAt(t: Double): TrumpetPlayer.Walk {
        val x = t / slow
        val k = strolls.firstOrNull { x > it.from && x < it.to }

        if (k == null) {
            // back from a stroll they may still be turned the way they came (they follow their way with a body's
            // inertia): they turn to the front over the next seconds, by the shorter way round, and do not snap
            val ended = strolls.lastOrNull { x >= it.to && x < it.to + TURN_HOME } ?: return TrumpetPlayer.Walk(at = home, turn = homeTurn, speed = 0.0)
            val left = ended.turnAt(ended.to)
            val turn = atan2(sin(left), cos(left)) * (1 - smooth((x - ended.to) / TURN_HOME))

            return TrumpetPlayer.Walk(at = home, turn = homeTurn + turn, speed = 0.0)
        }

        val now = facing(k, x)
        val turn = k.turnAt(x)

        return TrumpetPlayer.Walk(
            at = home.clone().add(strollAt(k, x)),
            turn = homeTurn + turn,
            speed = now.speed / slow,
            going = now.going,
            hips = k.way.hipsAt(x),
        )
    }

    /**
     * When the stroll that has them at [t] began (seconds on the show's clock), also while they turn to the front
     * after it at their place ([walkAt]); null where they stand at their place, turned to the front.
     */
    fun strollFrom(t: Double): Double? {
        val x = t / slow
        val k = strolls.firstOrNull { x > it.from && x < it.to } ?: strolls.lastOrNull { x >= it.to && x < it.to + TURN_HOME }

        return k?.let { it.from * slow }
    }

    /**
     * How far they stare at the camera at [t] on a stroll to it (see [StrollHome.stareAt]), 0 to 1, also as it is let
     * go after the stroll's end; 0 where there is none.
     */
    fun stareAt(t: Double): Double {
        val x = t / slow
        val k = strolls.lastOrNull { x > it.from && x < it.to + StrollHome.STARE_FALL } ?: return 0.0

        return k.way.stareAt(x)
    }

    /**
     * The stare at the [camera] on a stroll to it at [t] (see [stareAt]), as a still look, to lie over all the other
     * looks; also as it is let go after the stroll's end. Null where there is none.
     */
    fun stareLook(t: Double, camera: Vector3): Look? {
        val share = stareAt(t)

        return if (share > 0) Look(share = share, at = camera, still = true) else null
    }

    /**
     * The look at one they visit on a stroll at [t] (see [visitAt]): at their eyes, about, from their place in the
     * stage plot ([heads]); one behind they look at over the shoulder. Null where they visit nobody.
     */
    fun visitLook(t: Double): Look? {
        val visiting = visitAt(t) ?: return null

        return if (visiting.share > 0) Look(share = visiting.share, at = heads.getValue(visiting.who), behind = true) else null
    }

    /**
     * How far the head takes [look] at [t] (seconds on the show's clock) on a stroll, whose place lies at [yaw] off
     * the way the figure faces now (radians, either side): as far as a head takes a look there (`headReach`), and
     * where the stroll has turned the figure away from it, letting go of it slowly, over [LOOK_LET_GO] seconds, as a
     * look falls (see [lookReach]), by where the stroll had the figure and which way it faced at the moments
     * [LOOK_STEP] apart before [t] (see [walkAt]). [past]: those moments' walks by their number back, kept by the
     * caller for one frame, so that several looks share them. As the trumpet player's looks on her strolls go.
     */
    fun reach(look: Look, yaw: Double, t: Double, past: MutableMap<Int, TrumpetPlayer.Walk> = HashMap()): Double {
        val grid = floor(t / LOOK_STEP) * LOOK_STEP

        fun yawFrom(from: TrumpetPlayer.Walk): Double {
            val off = atan2(look.at.x - from.at.x, look.at.z - from.at.z) - from.turn

            return atan2(sin(off), cos(off))
        }

        return lookReach(
            t = t,
            yaw = yaw,
            reach = { angle -> headReach(angle = angle, behind = look.behind) },
            yawBefore = { k -> yawFrom(past.getOrPut(k) { walkAt(grid - k * LOOK_STEP) }) },
            fall = LOOK_LET_GO,
            step = LOOK_STEP,
        )
    }

    /** Whom they visit on a stroll at [t] (see [StrollHome.visitAt]); null where they visit nobody. */
    fun visitAt(t: Double): StrollRoute.Visiting? {
        val x = t / slow
        val k = strolls.firstOrNull { x > it.from && x < it.to } ?: return null

        return k.way.visitAt(x)
    }
}
