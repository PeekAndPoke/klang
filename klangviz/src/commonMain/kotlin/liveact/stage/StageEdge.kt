/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where the stage ends on the floor (the maintainer, 2026-11-20: "only the stage should reflect ... Should be an arc,
 * a few 'meters' away from the audience"). Behind, the fence or the wall, both at [BACK]; before, an arc that follows
 * the audience's first row ([CrowdPlaces]: the open ground before the stage is an oval, [CrowdPlaces.OPEN_SIDE] to
 * the sides and [CrowdPlaces.FRONT] ahead) drawn [MARGIN] m inward, so an oval [HALF_X] m to the sides and [HALF_Z] m
 * ahead of the stage's middle; it closes at the sides where it meets the back.
 *
 * The mirror floor mirrors within it only, fading out over the last [FADE] m; the outline
 * ([OutlineLook]) draws it.
 */
object StageEdge {
    /** The back, along z: the fence's mesh and the wall stand there (both at -8 m). */
    const val BACK = -8.0

    /** How far the front arc stays before the audience's first row, in metres. */
    const val MARGIN = 2.5

    /** The front arc's half width to the sides, and how far it reaches ahead, from the stage's middle. */
    const val HALF_X = CrowdPlaces.OPEN_SIDE - MARGIN
    const val HALF_Z = CrowdPlaces.FRONT - MARGIN

    /** The mirror fades out over this much before the edge, in metres. */
    const val FADE = 0.5

    /**
     * How far the floor's point at [x], [z] lies inside the stage's edge, in metres: above 0 inside, 0 on the edge,
     * below 0 outside. Exact to the back and along the arc's axes; elsewhere on the arc a close estimate (the oval's
     * own measure over its slope), good to some centimetres near the edge, which is where it is read. Toward the middle
     * it grows to [HALF_X] at most.
     */
    fun distance(x: Double, z: Double): Double {
        val back = z - BACK
        val oval = (x / HALF_X) * (x / HALF_X) + (z / HALF_Z) * (z / HALF_Z)
        val gx = x / (HALF_X * HALF_X)
        val gz = z / (HALF_Z * HALF_Z)
        val slope = 2.0 * sqrt(gx * gx + gz * gz)
        // at the very middle the oval has no slope: as far inside as it gets (no division by nought)
        val arc = if (slope > 1e-9) min((1.0 - oval) / slope, HALF_X) else HALF_X

        return min(back, arc)
    }

    /** Whether the floor's point at [x], [z] lies on the stage (on its edge counts as on it). */
    fun inside(x: Double, z: Double): Boolean = distance(x = x, z = z) >= 0.0
}
