/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How often the nearest one pushes the camera at most. */
private const val PUSHES = 12

/** A place on the stage's floor. */
data class Ground(val x: Double, val z: Double)

/**
 * Where a camera that would stand at [camera] stands, so that it is [clear] metres at least from each of [others]
 * (the players it does not look at): one that is nearer pushes it straight away from themselves, out to that
 * distance. So a look at one player does not put the camera into another one where they stand, or into their
 * instrument (the look along the marimba's bars stood in the rhythm guitarist's guitar: the maintainer, 2026-11-17).
 *
 * It is for places that stand still, asked once: a camera pushed anew at every moment would swing right round
 * someone who walks through its place, and jump (see [clearLook], which the show uses). One who stands exactly on it pushes it [back], the way from
 * what the camera looks at to the camera. Pushed away from one it may come too near another: the nearest one pushes
 * again, a few times over, which brings it clear of both where they stand well apart; wedged between two that
 * stand close it ends as clear of both as those pushes get it.
 */
fun clearOf(camera: Ground, others: List<Ground>, clear: Double, back: Ground): Ground {
    var at = camera

    repeat(PUSHES) {
        val other = others.minByOrNull { hypot(at.x - it.x, at.z - it.z) } ?: return at
        val dx = at.x - other.x
        val dz = at.z - other.z
        val far = hypot(dx, dz)

        if (far >= clear) {
            return at
        }

        val length = hypot(back.x, back.z)
        val way = when {
            far > 1e-6 -> Ground(x = dx / far, z = dz / far)
            length > 1e-9 -> Ground(x = back.x / length, z = back.z / length)
            else -> Ground(x = 0.0, z = 1.0)
        }

        at = Ground(x = other.x + way.x * clear, z = other.z + way.z * clear)
    }

    return at
}

/** Where a player stands on the stage ([at]) and how they stand turned ([turn], radians round the upright). */
data class Standing(val at: Ground, val turn: Double)

/**
 * A look's camera place in the space of the player it looks at (x to their left, z ahead of them), moved so that it
 * is [clear] of the [others] (where they stand, on the stage) while that player stands at [home]: [camera] and
 * [target] are the look as it was set, in the player's space, and what comes back is the camera's place in that
 * space again.
 *
 * Worked out once for a look, the place then goes with its player as every look does: so it is the same at every
 * moment of a look, and a player who strolls takes their looks along without a jump.
 */
fun clearLook(camera: Ground, target: Ground, home: Standing, others: List<Ground>, clear: Double): Ground {
    val c = cos(home.turn)
    val s = sin(home.turn)

    fun onStage(p: Ground) = Ground(x = home.at.x + p.x * c + p.z * s, z = home.at.z - p.x * s + p.z * c)

    val from = onStage(camera)
    val to = onStage(target)
    val moved = clearOf(camera = from, others = others, clear = clear, back = Ground(x = from.x - to.x, z = from.z - to.z))

    // clear as it is set: given back as it is set, to the last digit
    if (moved == from) {
        return camera
    }

    val dx = moved.x - home.at.x
    val dz = moved.z - home.at.z

    return Ground(x = dx * c - dz * s, z = dx * s + dz * c)
}
