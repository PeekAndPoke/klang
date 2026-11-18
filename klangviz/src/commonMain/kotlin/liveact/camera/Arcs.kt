/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.band.Player

// The arcs: the camera's slow ways round one player. Each is set for where the band stands (the stage plot), so
// that the camera neither stands in another player nor looks at its player through one (KLANG-00454): `LooksSpec`
// holds every arc against the places. Who moves a player sets the arcs anew.

/**
 * A slow way round one player, in the player's own space: how [far] off, how [high], what it looks [at], the lens
 * ([fov], degrees), and the part of the round it takes: from the angle [from] to the angle [to] (radians; 0 is
 * straight before the player, a positive angle toward their left). [cutsBack]: the way back from it to the total is
 * a cut, never a dolly: for an arc that ends where the straight way to the total's camera would pass through
 * another player.
 */
data class Orbit(
    val far: Double,
    val high: Double,
    val at: Triple<Double, Double, Double>,
    val fov: Double,
    val from: Double,
    val to: Double,
    val cutsBack: Boolean = false,
) {
    /**
     * The camera's angle as far through the arc as [u] (0 to 1): from [from] to [to] where [way] is 1, the other
     * way round where it is -1.
     */
    fun angle(way: Int, u: Double): Double {
        val middle = (from + to) / 2
        val half = (to - from) / 2

        return middle + way * half * (2 * u - 1)
    }
}

/** Where the total's camera stands, on the ground: where a dolly back from an arc goes to. */
val TOTAL_FROM = Ground(x = 0.0, z = 9.4)

/**
 * Round one player who stands with room about them: Klaue's (and the frontwoman's, for an arc by hand; the round
 * gives her none). The others have an arc of their own ([ARCS]).
 */
private val STANDING_ARC = Orbit(far = 2.7, high = 1.35, at = Triple(0.0, 1.15, 0.1), fov = 38.0, from = -0.75, to = 0.75)

/**
 * The arcs of those behind a kit or an instrument, or with others close by, where the whole of [STANDING_ARC] would
 * pass through someone.
 */
private val ARCS: Map<Player, Orbit> = mapOf(
    // the drummer, at the back: near him, inside the row before him, from his right side round to his front. The
    // straight way back to the total would pass the frontwoman's head: it cuts
    Player.Wucht to Orbit(
        far = 2.0, high = 1.30, at = Triple(0.0, 0.95, 0.4), fov = 40.0, from = -1.25, to = 0.18, cutsBack = true,
    ),
    // the marimba player: the frontwoman's place is on her right, the guitarist's on her left
    Player.Rune to Orbit(far = 3.0, high = 1.60, at = Triple(0.0, 1.00, 0.4), fov = 40.0, from = -0.30, to = 0.45),
    // the bass player stands between two who stand before him: further off, and only the little there is room for
    Player.Beben to Orbit(far = 3.4, high = 1.35, at = Triple(0.0, 1.15, 0.1), fov = 31.0, from = -0.30, to = 0.22),
    // the lead guitarist: the frontwoman's place is on his left
    Player.Skalpell to Orbit(far = 2.7, high = 1.35, at = Triple(0.0, 1.15, 0.1), fov = 38.0, from = -0.95, to = 0.50),
)

/** The arc round [player]. */
fun arcOf(player: Player): Orbit = ARCS[player] ?: STANDING_ARC
