/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.lights.WorldCourse
import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime

/**
 * The two worlds (`?world=night`, [Switch.World]; KLANG-00478, to be judged by eye; the maintainer, 2026-11-19: "I
 * would really like to express 'two worlds'... day and night or good and evil"): one time of day ([WorldTime]) that
 * drives the sun behind the drummer, the haze, the lamps, the lacquer and, behind the fence, a cold world seen
 * through the band's shadows ([WorldLight]). [word] is its name in the address and in the line at the top.
 * - [Off]: the stage as it was made; the time of day changes nothing.
 * - [Day]: a big, low, warm sun, a warm lit haze, the band's shadows soft and faint.
 * - [Night]: a small, cold moon, a thinner cold haze, the shadows large and hard, each a window into the cold world.
 * - [Ember]: the night's other side, good and evil: a deep red furnace in place of the moon, red smoke.
 * - [Ghosts]: the stage and the band almost fully dark, a thin rim of colour on their edges, their eyes the bright
 *   points they are; all the light behind the fence, the haze glowing in one colour ([GhostLight], `?ghosts=red`)
 *   and the band's shadows pale figures in it, there all the time, not only while the front lamp is on (the
 *   maintainer, 2026-11-20: "the stage and the musician are almost fully dark (different light colors possible) but
 *   behind the fence we see the 'ghosts'"). The figures need the fence and the haze giving way to the shadows
 *   ([HazeLook.Parts]); with the haze whole they stand veiled behind it, and before the wall the world is only the
 *   dark stage.
 * - [Drift]: the time moves on its own, from day to night and back ([WorldLight.drift]), a round in about four
 *   minutes. Its way does not pass through the ghosts, but now and then they visit for a while, in their three
 *   colours in turn ([io.peekandpoke.klang.viz.liveact.lights.GhostVisits]).
 */
enum class WorldLook(val word: String) {
    Off("off"),
    Day("day"),
    Night("night"),
    Ember("ember"),
    Ghosts("ghosts"),
    Drift("drift");

    /**
     * The time of day this look asks for at the show's time [t] (seconds), the drift taking [period] seconds a round,
     * the ghosts in [ghost] (carried by every look, so the colour stays while the stage turns to or from the ghosts):
     * the stage follows it at its own pace ([io.peekandpoke.klang.viz.liveact.lights.WorldFade]).
     */
    fun target(t: Double, period: Double = WorldLight.DRIFT_PERIOD, ghost: GhostLight = GhostLight.Cold): WorldTime = when (this) {
        Off -> WorldTime.OFF.copy(ghostLight = ghost)
        Day -> WorldTime(on = 1.0, night = 0.0, ember = 0.0, ghostLight = ghost)
        Night -> WorldTime(on = 1.0, night = 1.0, ember = 0.0, ghostLight = ghost)
        Ember -> WorldTime(on = 1.0, night = 1.0, ember = 1.0, ghostLight = ghost)
        Ghosts -> WorldTime(on = 1.0, night = 1.0, ember = 0.0, ghosts = 1.0, ghostLight = ghost)
        Drift -> WorldTime(on = 1.0, night = WorldLight.drift(t = t, period = period), ember = 0.0, ghostLight = ghost)
    }

    companion object {
        /**
         * The look the address asks for: `?world=day`, `night`, `ember`, `ghosts` or `drift`; any other value, or
         * none, [Off].
         */
        fun of(switches: Switches): WorldLook = entries.firstOrNull { it.word == switches[Switch.World] } ?: Off

        /**
         * The world the show opens in, as the address asks (`?opening=ember`, [Switch.Opening]): a world's word; off,
         * any other value, or none, [Off]: no opening ([WorldCourse]).
         */
        fun opening(switches: Switches): WorldLook = entries.firstOrNull { it.word == switches[Switch.Opening] } ?: Off

        /**
         * Whether the show opens ([opening] names a world): then its light comes up from pitch black as the band walks
         * in ([io.peekandpoke.klang.viz.liveact.lights.LightsUp]), whatever the look's world is.
         */
        fun opens(switches: Switches): Boolean = opening(switches) != Off

        /**
         * How long one round of the drift takes, in seconds, as the address asks (`?drift=30`, to watch it quickly or
         * take stills of it); without it, or with no number above 1 s, [WorldLight.DRIFT_PERIOD].
         */
        fun period(switches: Switches): Double {
            val asked = switches.double(Switch.Drift)

            // NaN-guard: a period of no number (or an endless one) is the period as made
            return if (asked != null && asked.isFinite() && asked > 1.0) asked else WorldLight.DRIFT_PERIOD
        }
    }
}
