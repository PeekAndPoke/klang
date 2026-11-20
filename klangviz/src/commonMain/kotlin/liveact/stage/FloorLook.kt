/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The stage's floor (`?floor=mirror`, [Switch.Floor]); [word] is its name in the address and in the line at the top.
 * - [Plain]: the floor as it was made, dark and a little glossy, the lamps' pools on it.
 * - [Mirror] (KLANG-00478, to be judged by eye): polished dark stone that mirrors the band, the riser and what
 *   stands behind them, softly and dimmer than they are; the lamps' pools on it softer.
 */
enum class FloorLook(val word: String) {
    Plain("plain"),
    Mirror("mirror");

    companion object {
        /** The floor the address asks for: `?floor=mirror` [Mirror]; any other value, or none, [Plain]. */
        fun of(switches: Switches): FloorLook = entries.firstOrNull { it.word == switches[Switch.Floor] } ?: Plain
    }
}
