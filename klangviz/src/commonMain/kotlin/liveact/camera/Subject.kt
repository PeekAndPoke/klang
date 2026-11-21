/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.band.Player

// What a look of the camera shows, and the looks it chooses from, in plain numbers: the camera's choice of a look
// (see chooseLook) works on these. Where a look puts the camera in the world, the choreography works out.

/** A fixed place of the camera, in plain numbers: where it is, what it looks at, the lens. */
data class Placement(
    val pos: Triple<Double, Double, Double>,
    val at: Triple<Double, Double, Double>,
    val fov: Double,
)

/** What a look shows: the stage from another angle, or one player. */
sealed class Subject {
    /** The player looked at; null for the stage. */
    abstract val player: Player?

    /** What it shows, in words (for the line at the top). */
    abstract val words: String

    /** The stage from [angle]. */
    class Angle(val angle: Placement) : Subject() {
        override val player: Player? get() = null

        override val words: String
            get() = when {
                angle === STADIUM -> "stadium"
                angle === REVERSE -> "reverse total"
                else -> "the stage from elsewhere"
            }
    }

    /** The [player], by [look] (in the player's own space). */
    class OnPlayer(override val player: Player, val look: Placement) : Subject() {
        override val words: String get() = player.key
    }
}

/**
 * Looks at a player who stands, in the player's own space (x to their left, y up, z ahead of them): where the camera
 * is, what it looks at, the lens.
 */
val STANDING_LOOKS: List<Placement> = listOf(
    Placement(pos = Triple(0.7, 0.75, 2.4), at = Triple(0.0, 1.20, 0.1), fov = 42.0),       // from below, in front
    Placement(pos = Triple(2.0, 1.45, 0.9), at = Triple(0.0, 1.15, 0.2), fov = 38.0),       // from their left
    Placement(pos = Triple(-0.9, 1.55, 1.7), at = Triple(0.0, 1.30, 0.1), fov = 30.0),      // close on head and hands
    Placement(pos = Triple(-1.9, 1.1, 1.3), at = Triple(0.0, 1.10, 0.2), fov = 40.0),       // from their right, low
)

/** Looks at the players who do not stand ([looksOf] gives everyone else [STANDING_LOOKS]), as [STANDING_LOOKS]. */
val LOOKS: Map<Player, List<Placement>> = mapOf(
    Player.Wucht to listOf(
        Placement(pos = Triple(1.2, 1.0, 3.1), at = Triple(0.0, 0.95, 0.4), fov = 40.0),    // over the kick drum
        Placement(pos = Triple(-1.1, 2.2, -1.5), at = Triple(0.0, 0.85, 0.7), fov = 55.0),  // over his shoulder, down on the kit
        Placement(pos = Triple(2.4, 1.25, 0.7), at = Triple(0.0, 0.95, 0.4), fov = 38.0),   // from his left
    ),
    Player.Rune to listOf(
        Placement(pos = Triple(0.2, 2.2, 2.3), at = Triple(0.0, 0.90, 0.5), fov = 42.0),    // down on the bars
        // along the bars, low, from before the rhythm guitarist: his guitar at the picture's edge, and she is seen
        // (from beside him he stood before her, and the camera in his guitar)
        Placement(pos = Triple(1.6, 0.95, 2.1), at = Triple(0.0, 1.05, 0.5), fov = 38.0),
        Placement(pos = Triple(-0.8, 1.5, 2.2), at = Triple(0.0, 1.25, 0.2), fov = 32.0),
    ),
)

/** The looks at [player]. */
fun looksOf(player: Player): List<Placement> = LOOKS[player] ?: STANDING_LOOKS

/** The stadium shot: from far back and high in the audience, the whole stage small above a field of held-up lights. */
val STADIUM: Placement = Placement(pos = Triple(5.0, 11.0, 46.0), at = Triple(0.0, 0.0, 14.0), fov = 27.0)

/**
 * The reverse total: from behind the drummer, over his left shoulder, the band from the back and the audience's
 * lights before it (the maintainer, 2026-11-17: "so the crowd can be seen for longer").
 */
val REVERSE: Placement = Placement(pos = Triple(1.1, 2.4, -4.6), at = Triple(-0.8, 0.6, 12.0), fov = 40.0)

/** The stage from elsewhere. */
val ANGLES: List<Placement> = listOf(
    Placement(pos = Triple(-7.2, 1.1, 5.6), at = Triple(0.0, 1.0, 0.6), fov = 40.0),        // from the left, low
    Placement(pos = Triple(0.0, 5.6, 7.4), at = Triple(0.0, 0.6, 0.6), fov = 38.0),         // from above
    Placement(pos = Triple(7.0, 1.4, 5.2), at = Triple(0.0, 1.0, 0.6), fov = 40.0),         // from the right
    Placement(pos = Triple(0.0, 0.45, 6.6), at = Triple(0.0, 1.5, 0.0), fov = 44.0),        // from the floor
    STADIUM,                                                                                // from far back in the audience
    // more from the floor (the maintainer, 2026-11-21: "we could have more and different floor-shots"), a hand's
    // breadth over the mirror floor, so half the picture is the band's reflection; all of them stand outside where the
    // trumpet player strolls (her big loop comes to 4.75 m before the stage's middle and 3.6 m to either side). After
    // the ones above, which the keys and the looks' pinned choices know by their place in the list.
    Placement(pos = Triple(-5.2, 0.30, 6.2), at = Triple(0.4, 1.3, 0.8), fov = 42.0),       // from the floor, front left
    Placement(pos = Triple(5.0, 0.30, 6.0), at = Triple(-0.6, 1.3, 0.8), fov = 42.0),       // from the floor, front right
    Placement(pos = Triple(0.6, 0.25, 10.5), at = Triple(0.0, 1.1, 0.0), fov = 34.0),       // from the floor, far and wide
    Placement(pos = Triple(-1.4, 0.20, 6.0), at = Triple(0.3, 1.7, 0.0), fov = 50.0),       // from the floor, looking up steeply
)

/** The players in the order the camera tries them, and the keys 1 to 6 choose them. */
val ORDER: List<Player> = listOf(Player.Wucht, Player.Skalpell, Player.Sirene, Player.Beben, Player.Klaue, Player.Rune)
