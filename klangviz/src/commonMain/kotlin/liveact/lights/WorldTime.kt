/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.stage.GhostLight
import kotlin.math.max
import kotlin.math.min

/**
 * The time of day of the two worlds (KLANG-00478, [io.peekandpoke.klang.viz.liveact.stage.WorldLook]), four shares
 * from 0 to 1: how much the time of day counts at all ([on]; 0 is the stage as it was made, whatever the others say),
 * from day to night ([night]), how far the night has turned to ember, its red side ([ember]), and how far the time
 * has gone over to the ghosts ([ghosts]: the stage dark, all the light behind the fence), in the colour [ghostLight].
 * What each share does to the light: [WorldLight].
 */
data class WorldTime(
    val on: Double,
    val night: Double,
    val ember: Double,
    val ghosts: Double = 0.0,
    val ghostLight: GhostLight = GhostLight.Cold,
) {
    companion object {
        /** No time of day: the stage as it was made. */
        val OFF = WorldTime(on = 0.0, night = 0.0, ember = 0.0)
    }
}

/**
 * The time of day as the stage shows it: it follows the time asked for ([follow]) at a pace of its own, so a key that
 * turns day into night does not jump: [on] comes and goes in [ON_SECONDS], day turns to night (and night to ember, and
 * any of them to the ghosts) in [TURN_SECONDS], at an even pace. The ghosts' colour is taken at once. It starts at
 * [start], at once (a show that starts at night is night from its first frame).
 *
 * It goes by the time between the frames, as [FrontFade] does: a frozen or paused show still comes to the time asked.
 */
class WorldFade(start: WorldTime) {

    /** Where it stands now. */
    var now: WorldTime = start
        private set

    /**
     * Takes it toward [target] by what [dt] seconds allow, each share at its pace and never past it; gives where it
     * stands then. [turnSeconds]: how long a whole turn of the day and the night takes this time ([TURN_SECONDS]
     * without it; the show's opening turns slower, [WorldCourse]). A [dt] below 0 or no number moves nothing; a share
     * of [target] that is no number counts as 0; a [turnSeconds] that is no finite number above 0 is [TURN_SECONDS].
     */
    fun follow(target: WorldTime, dt: Double, turnSeconds: Double = TURN_SECONDS): WorldTime {
        // NaN-guard: a time of no number is no time
        val step = if (dt != dt) 0.0 else max(0.0, dt)
        // NaN-guard: a pace of no number (or an endless one) is the usual pace
        val turn = if (turnSeconds.isFinite() && turnSeconds > 0.0) turnSeconds else TURN_SECONDS

        now = WorldTime(
            on = toward(from = now.on, to = target.on, by = step / ON_SECONDS),
            night = toward(from = now.night, to = target.night, by = step / turn),
            ember = toward(from = now.ember, to = target.ember, by = step / turn),
            ghosts = toward(from = now.ghosts, to = target.ghosts, by = step / turn),
            ghostLight = target.ghostLight,
        )

        return now
    }

    private fun toward(from: Double, to: Double, by: Double): Double {
        // NaN-guard: a share of no number is 0
        val goal = if (to != to) 0.0 else to.coerceIn(0.0, 1.0)

        return if (goal > from) min(goal, from + by) else max(goal, from - by)
    }

    companion object {
        /** How long the time of day takes to come or go as a whole, in seconds. */
        const val ON_SECONDS = 1.5

        /** How long day takes to turn into night (or night into ember), in seconds. */
        const val TURN_SECONDS = 3.0
    }
}
