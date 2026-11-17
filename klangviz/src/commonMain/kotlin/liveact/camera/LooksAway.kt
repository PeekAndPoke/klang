/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

/**
 * Whether a camera that looks along ([aheadX], [aheadZ]) on the ground looks away from where the total looks
 * ([totalX], [totalZ]): at a right angle to it or backward (a look from the side that still leans toward the
 * stage does not). A dolly back to the total from such a shot would carry the camera
 * past what it looks at and swing it right round, so the way back from it is a cut. How steeply either looks down
 * does not count.
 */
fun looksAway(aheadX: Double, aheadZ: Double, totalX: Double, totalZ: Double): Boolean {
    return aheadX * totalX + aheadZ * totalZ <= 0.0
}
