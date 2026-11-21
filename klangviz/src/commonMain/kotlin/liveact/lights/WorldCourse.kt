/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.stage.GhostLight
import io.peekandpoke.klang.viz.liveact.stage.WorldLook

/**
 * The time of day as the stage shows it over a show (KLANG-00478): it follows the time the look asks for
 * ([WorldLook.target]) at the pace of a [WorldFade], and where the show has an [opening] (`?opening=ember`,
 * [Switch.Opening]; the maintainer, 2026-11-20: "It should also be dark in the beginning"), it opens in that world:
 * - Before the song (the show's time below 0: the wait for the click, the walk-in) the stage stands in the opening's
 *   world, from its first frame.
 * - When the song begins (the time reaches 0), it turns to the look's own at the slower pace [TURN_SECONDS], until it
 *   has arrived there once; from then on it follows the look at the usual paces, as a show without an opening does.
 *
 * No opening where the look's world is off (the look as made, where the time of day changes nothing), nor in a show
 * first seen at its song's start or later (`?t=0`, `?t=62.1`, a show that follows music already running): it starts
 * where the look asks at the song's start, as without one. A world chosen by hand while the show opens ([chosen]: the
 * key n, the key x, an app's controls) ends the opening at once. Where the stage starts is decided at the first
 * [follow], which knows the show's time; [period] is the drift's round ([WorldLook.period]).
 */
class WorldCourse(private val opening: WorldLook, private val period: Double = WorldLight.DRIFT_PERIOD) {

    /** Where the show is in its opening. */
    private enum class Part {
        /** Not followed yet: whether it opens is decided at the first [follow]. */
        Unseen,

        /** Before the song: the opening's world. */
        Before,

        /** The song has begun: on the way to the look's own, at [TURN_SECONDS]. */
        Turning,

        /** No opening, or it is over: the look's own at the usual paces. */
        Over,
    }

    private var part: Part = if (opening == WorldLook.Off) Part.Over else Part.Unseen

    /** The time of day as the stage shows it, from the first [follow] on. */
    private var fade: WorldFade? = null

    /** Whether the show is still in its opening: before the song, or on its way from the opening's world. */
    val opens: Boolean
        get() = part == Part.Before || part == Part.Turning

    /**
     * Takes the stage on by [dt] seconds, at the show's time [t] (seconds), toward what [look] asks for or, while the
     * show opens, its opening's world, the ghosts in [ghost]; gives where it stands then. The first call starts it
     * where it begins, at once. [turnSeconds]: how long a whole turn takes once the opening is over, where it is to
     * be slower than the usual pace (the ghosts' visits, [GhostVisits]); without it the usual pace.
     */
    fun follow(
        t: Double,
        look: WorldLook,
        dt: Double,
        ghost: GhostLight = GhostLight.Cold,
        turnSeconds: Double = WorldFade.TURN_SECONDS,
    ): WorldTime {
        val fade = fade ?: begin(t = t, look = look, ghost = ghost)

        if (part == Part.Before && t >= 0.0) {
            part = Part.Turning
        }

        val target = if (part == Part.Before) {
            opening.target(t = t, period = period, ghost = ghost)
        } else {
            look.target(t = t, period = period, ghost = ghost)
        }
        val turn = if (part == Part.Turning) TURN_SECONDS else turnSeconds
        val now = fade.follow(target = target, dt = dt, turnSeconds = turn)

        if (part == Part.Turning && now == target) {
            part = Part.Over
        }

        return now
    }

    /** A world was chosen by hand: the opening, where it runs or has yet to begin, is over at once. */
    fun chosen() {
        part = Part.Over
    }

    /**
     * The fade, made at the show's first time [t] with the look's [look]: in the opening's world where the show opens
     * (seen before its song, with a look whose world is on), else where the look asks at the song's start.
     */
    private fun begin(t: Double, look: WorldLook, ghost: GhostLight): WorldFade {
        part = if (part == Part.Unseen && t < 0.0 && look != WorldLook.Off) Part.Before else Part.Over

        val start = if (part == Part.Before) {
            opening.target(t = t, period = period, ghost = ghost)
        } else {
            look.target(t = 0.0, period = period, ghost = ghost)
        }

        return WorldFade(start = start).also { fade = it }
    }

    companion object {
        /**
         * How long the turn from the opening's world to the look's own takes when the song begins, in seconds, as a
         * whole turn (from night to day): slow enough to be felt as the song's dawn, not as a switch. It was 8 (the
         * maintainer, 2026-11-21: "The change in mood after the walkin is too sudden"); the front lamp that lit the
         * walk-in goes down in the same time ([LightPlan.OPENING_FADE]).
         */
        const val TURN_SECONDS = 20.0
    }
}
