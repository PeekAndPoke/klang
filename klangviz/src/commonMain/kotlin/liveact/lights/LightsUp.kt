/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.Switch

/**
 * How the stage's light comes up in a show that opens (`?opening=ember`, [Switch.Opening]; the maintainer,
 * 2026-11-20: "I would only do the lights and start from pitch black", "Do the initial fade in of the stage over 10
 * seconds please", "Guys can already walk in"): the house level, from 0 (pitch black) when the walk-in begins to 1
 * after [SECONDS], eased at both ends, without the flash of lamps switched on that a show without an opening has. The
 * walk-in goes on meanwhile, and the song may begin before the light is fully up.
 *
 * The seconds since the walk-in began are counted frame by frame ([advance]), each frame at most [LONGEST_FRAME], as
 * the stage's own fades count: a frame that holds the page (the first frames compile the stage's shaders, a second or
 * more on a slow machine) does not eat the rise.
 */
object LightsUp {

    /** How long the light takes to come up from pitch black, in seconds. A first guess for the maintainer's eye. */
    const val SECONDS = 10.0

    /** The most one frame counts, in seconds: a longer frame (a hitch, a page that was hidden) counts as this long. */
    const val LONGEST_FRAME = 0.25

    /**
     * The seconds since the walk-in began after one more frame of [frame] seconds, counted from [since]: at most
     * [LONGEST_FRAME], and a frame below 0 or of no number counts nothing.
     */
    fun advance(since: Double, frame: Double): Double {
        // NaN-guard: a frame of no number counts nothing
        val counted = if (frame != frame) 0.0 else frame.coerceIn(0.0, LONGEST_FRAME)

        return since + counted
    }

    /** The house level [since] seconds after the walk-in began, 0 to 1; a time below 0, or no number, is 0. */
    fun level(since: Double): Double {
        // NaN-guard: no time is the beginning, pitch black
        val along = if (since != since) 0.0 else (since / SECONDS).coerceIn(0.0, 1.0)

        return along * along * (3 - 2 * along)
    }
}
