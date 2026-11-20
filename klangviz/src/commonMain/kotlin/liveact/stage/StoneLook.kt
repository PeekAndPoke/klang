/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The mirror floor's stone (`?stone=plain`, [Switch.Stone]; the key k turns it over). [word] is its name in the
 * address and in the line at the top. Only the mirror floor has it ([FloorLook.Mirror]).
 * - [Plain]: the dark stone as it was made, all one.
 * - [Marble]: the default (the maintainer, 2026-11-20: "Can we try crisp with the marble pattern on the floor?";
 *   2026-11-21: "Please enable marble by default and make it stronger"): dark polished marble: long, soft veins,
 *   lighter and rougher than the stone and standing a hair proud of it (so they bend the reflection a little, as the
 *   ripple does), over a cloudy variation. It shows where light falls on it.
 */
enum class StoneLook(val word: String) {
    Plain("plain"),
    Marble("marble");

    companion object {
        /** The stone the address asks for: plain or marble; any other value, or none, [Marble]. */
        fun of(switches: Switches): StoneLook = entries.firstOrNull { it.word == switches[Switch.Stone] } ?: Marble
    }
}
