/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

/** A figure's left or right. */
enum class Side(val sign: Double) {
    L(1.0),
    R(-1.0);

    val other: Side get() = if (this == L) R else L
}
