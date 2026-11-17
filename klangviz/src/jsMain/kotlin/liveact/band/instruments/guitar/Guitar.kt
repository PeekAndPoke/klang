/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.guitar

import io.peekandpoke.klang.viz.liveact.band.instruments.strings.BodyOutline
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringDesign
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.Tuning

/**
 * The guitar: its own shape (the body's outlines, the head, the pickups, six strings and their tuners), its scale and tuning, how
 * its right hand strums (from the wrist, wide and quick) and how it hangs. Played by a string player (see `StringPlayer`).
 */
object Guitar {
    /** Two horns: the guitar's own body. */
    val DOUBLE_CUTAWAY: BodyOutline = BodyOutline(
        listOf(
            0.12 to -0.045, 0.15 to -0.09, 0.185 to -0.125, 0.14 to -0.15, 0.06 to -0.14, -0.02 to -0.12, -0.13 to -0.175,
            -0.225 to -0.105, -0.24 to 0.0, -0.225 to 0.10, -0.13 to 0.17, -0.02 to 0.125, 0.07 to 0.145, 0.16 to 0.15,
            0.215 to 0.115, 0.17 to 0.085, 0.12 to 0.045,
        ),
    )

    /** One horn, the other side round. */
    val SINGLE_CUTAWAY: BodyOutline = BodyOutline(
        listOf(
            0.12 to -0.045, 0.165 to -0.075, 0.19 to -0.115, 0.14 to -0.148, 0.05 to -0.135, -0.02 to -0.118,
            -0.13 to -0.172, -0.22 to -0.108, -0.235 to 0.0, -0.22 to 0.108, -0.13 to 0.170, -0.02 to 0.122,
            0.07 to 0.140, 0.13 to 0.112, 0.145 to 0.07, 0.12 to 0.045,
        ),
    )

    /** The guitar: what the string player reads of it. */
    val DESIGN: StringDesign = StringDesign(
        scale = 0.64, bodyHalfLength = 0.22, bodyHalfWidth = 0.165, strings = 6, neck = 0.044, tilt = 0.50,
        swing = 0.095, tau = 0.028, hang = -0.09, arm = 0.15, outline = DOUBLE_CUTAWAY,
        pickups = listOf(0.01, 0.075),
        head = listOf(0.0 to -0.022, 0.035 to -0.032, 0.16 to -0.038, 0.19 to -0.012, 0.172 to 0.026, 0.04 to 0.03, 0.0 to 0.022),
        tuning = Tuning.GUITAR,
    )
}
