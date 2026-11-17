/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.bass

import io.peekandpoke.klang.viz.liveact.band.instruments.strings.BodyOutline
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringDesign
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.Tuning

/**
 * The bass: its own shape (a long-horned body, the head, the pickups, four strings and their tuners), its longer scale and tuning,
 * how its right hand plucks (more from the arm, narrower) and how it hangs, lower. Played by a string player (see `StringPlayer`).
 */
object Bass {
    /** Long horns: the bass's own body. */
    val LONG_HORN: BodyOutline = BodyOutline(
        listOf(
            0.15 to -0.05, 0.18 to -0.09, 0.22 to -0.12, 0.16 to -0.15, 0.05 to -0.135, -0.03 to -0.115, -0.16 to -0.18,
            -0.255 to -0.11, -0.275 to 0.0, -0.255 to 0.11, -0.15 to 0.172, -0.02 to 0.125, 0.08 to 0.14, 0.20 to 0.137,
            0.295 to 0.10, 0.225 to 0.075, 0.15 to 0.05,
        ),
    )

    /** The bass: what the string player reads of it. */
    val DESIGN: StringDesign = StringDesign(
        scale = 0.84, bodyHalfLength = 0.25, bodyHalfWidth = 0.17, strings = 4, neck = 0.05, tilt = 0.62,
        swing = 0.040, tau = 0.02, hang = -0.13, arm = 0.35, outline = LONG_HORN,
        pickups = listOf(0.0, 0.07),
        head = listOf(0.0 to -0.025, 0.04 to -0.04, 0.19 to -0.048, 0.23 to -0.015, 0.20 to 0.034, 0.05 to 0.036, 0.0 to 0.025),
        tuning = Tuning.BASS,
    )
}
