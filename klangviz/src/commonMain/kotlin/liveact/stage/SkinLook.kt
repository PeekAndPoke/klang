/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * What the band's bodies are made of (`?skin=lacquer`, [Switch.Skin]); [word] is its name in the address and in the
 * line at the top.
 * - [Suit]: the dark suit as it was made, each member's pattern in how it throws back the light, and glitter.
 * - [Lacquer] (KLANG-00478, to be judged by eye; the critic's direction B, "black lacquer and gold inlay"): deep black
 *   lacquer under a clear coat, no glitter, each member's pattern as thin inlaid gold that shows only where light
 *   catches it; never glowing. The instruments' bodies are lacquered too, their brass polished.
 */
enum class SkinLook(val word: String) {
    Suit("suit"),
    Lacquer("lacquer");

    companion object {
        /** What the address asks for: `?skin=lacquer` [Lacquer]; any other value, or none, [Suit]. */
        fun of(switches: Switches): SkinLook = entries.firstOrNull { it.word == switches[Switch.Skin] } ?: Suit
    }
}
