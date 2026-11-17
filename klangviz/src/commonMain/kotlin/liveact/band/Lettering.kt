/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

import kotlin.math.min

/** The space between two letters of the name on the bass drum, as a share of the font's size. */
const val NAME_GAP = 0.03

/**
 * The font size at which a name takes [room]: its letters are [widths] wide at the size 1, and [NAME_GAP] lies
 * between each two. So the name is as wide on every machine, whatever font it has. Never above [most], so a
 * short name stays a name on the drum; 0 for no letters and for letters without a width.
 */
fun nameSize(widths: List<Double>, room: Double, most: Double): Double {
    val wide = widths.sum()

    if (widths.isEmpty() || wide <= 0.0) {
        return 0.0
    }

    return min(most, room / (wide + NAME_GAP * (widths.size - 1)))
}
