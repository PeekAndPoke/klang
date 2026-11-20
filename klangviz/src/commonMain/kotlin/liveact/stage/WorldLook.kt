/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
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
 * - [Drift]: the time moves on its own, from day to night and back ([WorldLight.drift]), a round in about four minutes.
 */
enum class WorldLook(val word: String) {
    Off("off"),
    Day("day"),
    Night("night"),
    Ember("ember"),
    Drift("drift");

    /**
     * The time of day this look asks for at the show's time [t] (seconds), the drift taking [period] seconds a round:
     * the stage follows it at its own pace ([io.peekandpoke.klang.viz.liveact.lights.WorldFade]).
     */
    fun target(t: Double, period: Double = WorldLight.DRIFT_PERIOD): WorldTime = when (this) {
        Off -> WorldTime.OFF
        Day -> WorldTime(on = 1.0, night = 0.0, ember = 0.0)
        Night -> WorldTime(on = 1.0, night = 1.0, ember = 0.0)
        Ember -> WorldTime(on = 1.0, night = 1.0, ember = 1.0)
        Drift -> WorldTime(on = 1.0, night = WorldLight.drift(t = t, period = period), ember = 0.0)
    }

    companion object {
        /** The look the address asks for: `?world=day`, `night`, `ember` or `drift`; any other value, or none, [Off]. */
        fun of(switches: Switches): WorldLook = entries.firstOrNull { it.word == switches[Switch.World] } ?: Off

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
