/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import kotlin.math.hypot

// Whether someone comes walking at the camera (the maintainer, 2026-11-22: the frontwoman "moved towards the camera
// and when she was half way there the camera took the flyback to the back of the crowd", a moment that had happened
// by chance, and "please also add a camera move as I described ... which needs detection on where the camera is").
// The camera watches where she is against where it stands, from frame to frame; when she has come straight at it
// for a stretch, it takes off backward over the audience before her (see the choreography's `retreat`). Only her
// own steps count: a camera that comes toward her (the total moving in again after the ghosts) is no walk of hers.

/**
 * What is kept from one frame to the next of someone's way toward the camera: the moment [t] (seconds), where they
 * were ([x], [z], in metres on the stage), and how far they have [come] straight at it without a break, in metres.
 */
data class Approach(val t: Double, val x: Double, val z: Double, val come: Double) {
    /** Whether they have come at the camera far enough for it to answer ([Approaching.COME]). */
    val arrived: Boolean get() = come >= Approaching.COME
}

/** How an [Approach] goes on from frame to frame. */
object Approaching {
    /** They walk at the camera where their own steps take them toward it by at least this many metres a second, */
    const val PACE = 0.2

    /** and where at least this share of their way is toward it (the rest across). */
    const val STRAIGHT = 0.9

    /** The camera answers when they have come this many metres straight at it: about half such a walk. */
    const val COME = 1.2

    /** Two frames further apart than this many seconds are not one walk (the page slept, the time was moved). */
    const val GAP = 0.5

    /**
     * The approach at [t] of someone at [x], [z] toward a camera at [camX], [camZ], after [before] (null: nothing is
     * known yet). What they have come is kept while they walk at the camera and begins anew when they stop, turn
     * off, or the time jumps.
     */
    fun next(before: Approach?, t: Double, x: Double, z: Double, camX: Double, camZ: Double): Approach {
        val dt = if (before == null) 0.0 else t - before.t
        val far = if (before == null) 0.0 else hypot(camX - before.x, camZ - before.z)

        // NaN-guard: a time or a place that is no number begins anew (every comparison with it is false)
        if (before == null || !(dt > 0.0 && dt <= GAP) || !(far > 0.0) || x != x || z != z) {
            return Approach(t = t, x = x, z = z, come = 0.0)
        }

        // her step, and how much of it goes toward where the camera stands
        val stepX = x - before.x
        val stepZ = z - before.z
        val nearer = (stepX * (camX - before.x) + stepZ * (camZ - before.z)) / far
        val moved = hypot(stepX, stepZ)
        val atIt = nearer >= PACE * dt && nearer >= STRAIGHT * moved

        return Approach(t = t, x = x, z = z, come = if (atIt) before.come + nearer else 0.0)
    }
}
