/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * A surrounding of the stage and the audience (KLANG-00478; the maintainer, 2026-11-21: "i like the 'images' left and
 * right", "I also like the idea of having these 'images' correlate with the surrounding and i can well imagine that
 * every time it is really dark we flip the surrounding to another one. So let us try 2 or 3."). One theme seen in two
 * places that belong together: large dark shapes lit from below, left and right of the stage behind the fence where it
 * runs on past the band, and the same theme as a horizon of silhouettes behind and around the audience, under the
 * night sky. [word] is its name in the address.
 *
 * - [Forest]: tall old trees, bare-limbed giants and dark pines (the maintainer's own idea: "trees lit from below").
 * - [Ruins]: the ruins of a colossal ancient theatre, broken arches and shattered columns.
 * - [Stones]: standing stones, a ring of towering rough monoliths.
 *
 * Each is drawn into its own colour channel of the same two pictures ([ordinal]: red, green, blue), so turning from one
 * to the next builds nothing.
 */
enum class Surrounding(val word: String) {
    Forest("forest"),
    Ruins("ruins"),
    Stones("stones"),
}

/**
 * What surrounds the stage and the audience (`?around=ruins`, [Switch.Around]); [word] is its name in the address.
 * - [Turns]: the surroundings come by turns, the forest first, the next one each time the stage is really dark
 *   ([AroundCourse]).
 * - [Forest], [Ruins], [Stones]: that one, all through ([held]), to look at it.
 * - [Off]: none, the stage in the void as it was made.
 */
enum class AroundLook(val word: String, val held: Surrounding?) {
    Turns("turns", null),
    Forest("forest", Surrounding.Forest),
    Ruins("ruins", Surrounding.Ruins),
    Stones("stones", Surrounding.Stones),
    Off("off", null);

    companion object {
        /**
         * What the address asks for: `?around=forest` [Forest], `ruins` [Ruins], `stones` [Stones], `off` [Off]; any
         * other value, or none, [Turns].
         */
        fun of(switches: Switches): AroundLook = entries.firstOrNull { it.word == switches[Switch.Around] } ?: Turns
    }
}
