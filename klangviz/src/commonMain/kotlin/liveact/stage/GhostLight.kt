/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The colour of the ghosts world (`?ghosts=red`, [Switch.Ghosts]; [WorldLook.Ghosts]), to be judged by eye against
 * the three paintings of it (KLANG-00478, "The ghosts"). [word] is its name in the address and in the line at the
 * top. No key: every letter is taken; an app's controls choose it. It does something only in the ghosts world.
 *
 * Each colour has four parts, the light colours linear (red, green, blue):
 * - [glow]: the haze behind the fence, which carries all the light of the picture;
 * - [depth]: the surface behind the haze, where the haze is thin: the deep of the colour;
 * - [pale]: the figures in the haze (the band's shadows), the brightest and palest of it;
 * - [rim]: the lamps behind the band, which leave a thin rim of the colour on the band's edges (a colour as the lamps
 *   take it, `0xrrggbb`).
 *
 * - [Cold]: a moonlit blue white.
 * - [Red]: a deep red, the figures a pale rose.
 * - [Green]: a sea green with a violet depth, the figures a pale mint.
 */
enum class GhostLight(
    val word: String,
    val glow: Triple<Double, Double, Double>,
    val depth: Triple<Double, Double, Double>,
    val pale: Triple<Double, Double, Double>,
    val rim: Int,
) {
    Cold(
        word = "cold",
        glow = Triple(0.30, 0.40, 0.64),
        depth = Triple(0.015, 0.025, 0.07),
        pale = Triple(0.78, 0.86, 1.0),
        rim = 0x8fb0ff,
    ),
    Red(
        word = "red",
        glow = Triple(0.52, 0.045, 0.05),
        depth = Triple(0.06, 0.004, 0.01),
        pale = Triple(1.0, 0.68, 0.70),
        rim = 0xff3a30,
    ),
    Green(
        word = "green",
        glow = Triple(0.06, 0.48, 0.36),
        depth = Triple(0.03, 0.008, 0.06),
        pale = Triple(0.70, 1.0, 0.90),
        rim = 0x40e0b8,
    );

    companion object {
        /** The colour the address asks for: `?ghosts=red` [Red], `green` [Green]; any other value, or none, [Cold]. */
        fun of(switches: Switches): GhostLight = entries.firstOrNull { it.word == switches[Switch.Ghosts] } ?: Cold
    }
}
