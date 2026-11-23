/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

import kotlin.math.hypot
import kotlin.math.min

// The stage plot (the Bühnenaufstellung): who stands where on the stage, and how and when each one walks in. A
// song's score holds one (see Score); the band, the camera and the frontwoman's strolls are handed it.

/**
 * Where a player stands: [x] to the audience's right, [z] toward the audience, the [turn], and how high ([y], the
 * riser). [sits]: they sit there cross-legged on the floor (a strung player, the instrument in the lap).
 */
data class Place(val x: Double, val z: Double, val turn: Double, val y: Double = 0.0, val sits: Boolean = false)

/**
 * A player's walk to its place: from ([fromX], [fromZ]) between the times [start] and [end]. A walk-in comes from off
 * the stage, carrying the instrument. A walk from the place of the song before ([StagePlot.after]) begins there, as
 * high as that place was ([fromY]: the riser), and the hands are empty ([carries] false): the instrument appears when
 * its player has arrived (the maintainer, 2026-11-23: "I would only teleport the instruments not the members").
 */
data class Entrance(
    val fromX: Double,
    val fromZ: Double,
    val start: Double,
    val end: Double,
    val fromY: Double = 0.0,
    val carries: Boolean = true,
)

/**
 * A stage plot: where each player stands ([places]) and how each walks in ([entrances]: in this order they are placed
 * each frame), the walk-in beginning [intro] seconds before the song (the stage's clock starts then).
 */
data class StagePlot(
    val intro: Double,
    val places: Map<Player, Place>,
    val entrances: Map<Player, Entrance>,
)

/** How a band changes from one song's places to the next one's ([StagePlot.after]), in seconds and metres a second. */
object Changeover {
    /** The stage's clock starts this long before the next song. */
    const val INTRO = 12.0

    /** The first one sets off this long after that, and each next one this much later. */
    const val FIRST = 1.0
    const val APART = 0.5

    /** A walk's pace, and the least and the most a walk takes. */
    const val PACE = 0.9
    const val LEAST = 2.0
    const val MOST = 6.0

    /** Everyone has arrived this long before the song at the latest: time to sit down and to take the instrument. */
    const val SETTLE = 2.5

    /** Who moves less than this far stays where they are: no walk at all. */
    const val STAYS = 0.15
}

/**
 * This plot as the band takes it up coming from the song [before]: the same places, but everyone walks there from
 * their place in that song instead of from off the stage (the maintainer, 2026-11-23: "we need a model for where
 * everyone's home-position is in each song and a planner for in between the songs"). In this plot's order they set
 * off one after the other, each at a walking pace, the hands empty; who stands where they stood does not walk (their
 * walk is over before the clock starts). A first draft of the way between two songs: straight lines, no care yet for
 * who crosses whom.
 */
fun StagePlot.after(before: StagePlot): StagePlot {
    var leaves = -Changeover.INTRO + Changeover.FIRST
    val walks = entrances.keys.associateWith { player ->
        val to = places.getValue(player)
        val from = before.places[player] ?: to
        val far = hypot(to.x - from.x, to.z - from.z)

        if (far < Changeover.STAYS && from.y == to.y) {
            // there already: arrived long before the clock starts
            Entrance(fromX = from.x, fromZ = from.z, start = -Changeover.INTRO - 20.0, end = -Changeover.INTRO - 10.0, fromY = from.y, carries = false)
        } else {
            val takes = (far / Changeover.PACE).coerceIn(Changeover.LEAST, Changeover.MOST)
            val start = leaves
            // (never later than in time to settle: a long way is walked faster)
            val end = min(start + takes, -Changeover.SETTLE)

            leaves += Changeover.APART

            Entrance(fromX = from.x, fromZ = from.z, start = start, end = end, fromY = from.y, carries = false)
        }
    }

    return copy(intro = Changeover.INTRO, entrances = walks)
}
