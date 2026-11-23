/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

// The stage plot (the Bühnenaufstellung): who stands where on the stage, and how and when each one walks in. A
// song's score holds one (see Score); the band, the camera and the frontwoman's strolls are handed it.

/**
 * Where a player stands: [x] to the audience's right, [z] toward the audience, the [turn], and how high ([y], the
 * riser). [sits]: they sit there cross-legged on the floor (a strung player, the instrument in the lap).
 */
data class Place(val x: Double, val z: Double, val turn: Double, val y: Double = 0.0, val sits: Boolean = false)

/** A player's walk-in: from ([fromX], [fromZ]), off the stage, to its place, between the times [start] and [end]. */
data class Entrance(val fromX: Double, val fromZ: Double, val start: Double, val end: Double)

/**
 * A stage plot: where each player stands ([places]) and how each walks in ([entrances]: in this order they are placed
 * each frame), the walk-in beginning [intro] seconds before the song (the stage's clock starts then).
 */
data class StagePlot(
    val intro: Double,
    val places: Map<Player, Place>,
    val entrances: Map<Player, Entrance>,
)
