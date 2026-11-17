/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

import io.peekandpoke.klang.viz.liveact.stage.Stage

// The stage plot (the Bühnenaufstellung): who stands where on the stage, and how and when each one walks in.

/** Where a player stands: [x] to the audience's right, [z] toward the audience, the [turn], and how high ([y], the riser). */
data class Place(val x: Double, val z: Double, val turn: Double, val y: Double = 0.0)

/** A player's walk-in: from ([fromX], [fromZ]), off the stage, to its place, between the times [start] and [end]. */
data class Entrance(val fromX: Double, val fromZ: Double, val start: Double, val end: Double)

/**
 * The band's stage plot: where each member stands and how each walks in. Hardcoded, one plot for every show (the
 * maintainer, 2026-11-17: "into a different thing, like Bühnenaufstellung ... for now hardcoded").
 */
object StagePlot {
    /** The walk-in: the stage's clock starts this many seconds before the song. */
    const val INTRO = 15.0

    /**
     * Where each player stands: x to the audience's right, z toward the audience, the turn, and how high (the
     * riser).
     */
    val PLACES: Map<Player, Place> = mapOf(
        Player.Wucht to Place(x = 0.1, z = -2.1, turn = 0.0, y = Stage.RISER),
        Player.Beben to Place(x = -2.15, z = 0.4, turn = 0.15),
        Player.Skalpell to Place(x = -3.0, z = 1.7, turn = 0.3),
        Player.Sirene to Place(x = -0.7, z = 2.2, turn = 0.1),
        Player.Rune to Place(x = 1.7, z = 0.4, turn = -0.15),
        Player.Klaue to Place(x = 3.0, z = 1.7, turn = -0.3),
    )

    /**
     * The walk-in: each player comes from off the stage between two times; the drummer first, from the back and
     * up onto the riser, the frontwoman last. In this order they are placed each frame.
     */
    val ENTRANCES: Map<Player, Entrance> = linkedMapOf(
        Player.Wucht to Entrance(fromX = 0.1, fromZ = -7.5, start = -15.0, end = -9.0),
        Player.Beben to Entrance(fromX = -8.5, fromZ = 0.9, start = -13.5, end = -7.5),
        Player.Klaue to Entrance(fromX = 8.5, fromZ = 2.0, start = -12.5, end = -6.5),
        Player.Skalpell to Entrance(fromX = -8.5, fromZ = 2.1, start = -11.5, end = -5.5),
        Player.Rune to Entrance(fromX = 8.5, fromZ = 0.9, start = -10.5, end = -4.8),
        Player.Sirene to Entrance(fromX = -8.5, fromZ = 2.9, start = -8.5, end = -2.4),
    )
}
