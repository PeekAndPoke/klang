/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.peekandpoke.klang.viz.liveact.smooth

/**
 * Sirene's eyes in her shadow (`?eyes=shadow`, KLANG-00478): where the front lamp would throw her eyes onto the
 * surface that takes the band's shadows, and how they cross from her face to it and back.
 *
 * The lamp throws a point onto an upright surface (the wall, or the surface behind the fence: both stand across the
 * stage, at one place along z) where the line from the lamp through the point meets it: the lamp's place plus the way
 * from the lamp to the point, [scale] times over. So her shadow and the eyes in it are [scale] times her size, and
 * move with her as her shadow does.
 *
 * The crossing goes by a share from 0 (her eyes in her face) to 1 (in her shadow): her face's eyes fade out over the
 * first [CROSS] of it ([face]), the shadow's fade in over the last [CROSS] ([wall]), each eased. The two overlap only
 * in the middle, both faint there: they never both shine at full (the maintainer's wish, 2026-11-20).
 */
object EyesInShadow {
    /** The share of the crossing each side's fade takes: more than a half, so the two overlap a little. */
    const val CROSS = 0.6

    /**
     * How many times its way from the lamp a point at [eyeZ] is thrown, by a lamp at [lampZ], onto a surface at
     * [planeZ] (all along z, in metres): more than 1 where the surface lies behind the point, seen from the lamp.
     * Null where the lamp throws no shadow of the point there: the surface before the point or behind the lamp, or
     * the point level with the lamp (no finite number).
     */
    fun scale(lampZ: Double, eyeZ: Double, planeZ: Double): Double? {
        val k = (planeZ - lampZ) / (eyeZ - lampZ)

        // no finite number (the point level with the lamp, or NaN), or the surface not behind the point
        if (!k.isFinite() || k <= 1.0) {
            return null
        }

        return k
    }

    /** How brightly her eyes shine in her face at [share] of the crossing: fully at 0, not at all from [CROSS] on. */
    fun face(share: Double): Double = 1.0 - smooth(share / CROSS)

    /** How brightly her eyes shine in her shadow at [share] of the crossing: not at all up to 1 - [CROSS], fully at 1. */
    fun wall(share: Double): Double = smooth((share - (1.0 - CROSS)) / CROSS)
}
