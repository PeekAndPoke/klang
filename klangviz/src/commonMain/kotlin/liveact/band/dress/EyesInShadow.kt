/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

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
 *
 * They cross only while she looks away from the surface that takes her shadow ([facing]; the maintainer,
 * 2026-11-21: "the eyes should only appear in the shadows when Sirene looks away from the fence / backdrop ... no
 * matter where the camera is"; before that it went by the camera). Turned toward it, her eyes are in her face, also
 * while the lamp is on.
 */
object EyesInShadow {
    /** The share of the crossing each side's fade takes: more than a half, so the two overlap a little. */
    const val CROSS = 0.6

    /**
     * She looks a way while her face points no more than [TOWARD] off it, and no more once it points more than
     * [AWAY] off it, both in radians (75 and 88 degrees), seen from above; between the two it stays as it was, so
     * the eyes do not flutter where she stands at the edge. Wide on purpose: she looks away from the surface behind
     * her wherever she does not look toward it (they were 50 and 65 while the rule went by the camera, and the eyes
     * came only sometimes).
     */
    const val TOWARD = 75 * PI / 180
    const val AWAY = 88 * PI / 180

    /**
     * Whether she looks a given way (for the eyes: straight away from the surface behind her): her face points along
     * ([faceX], [faceZ]) and the way is ([toX], [toZ]), both across the stage and along it (how long they are does
     * not count). [was]: whether
     * she did the frame before; it stays where the two are between [TOWARD] and [AWAY] apart, or where a way has no
     * length or is no number.
     */
    fun facing(was: Boolean, faceX: Double, faceZ: Double, toX: Double, toZ: Double): Boolean {
        val lengths = hypot(faceX, faceZ) * hypot(toX, toZ)

        // NaN-guard: a way of no number or no length says nothing
        if (lengths != lengths || lengths <= 0.0) {
            return was
        }

        val cosine = (faceX * toX + faceZ * toZ) / lengths

        return when {
            cosine >= cos(TOWARD) -> true
            cosine < cos(AWAY) -> false
            else -> was
        }
    }

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
