/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The mirror floor's stone (`?stone=marble`, [Switch.Stone]; the key k turns it over), to be judged by eye (the
 * maintainer, 2026-11-20: "Can we try crisp with the marble pattern on the floor?"). [word] is its name in the address
 * and in the line at the top. Only the mirror floor has it ([FloorLook.Mirror]).
 * - [Plain]: the dark stone as it was made, all one.
 * - [Marble]: dark polished marble: a few long, thin, soft veins, a little lighter and a little rougher than the
 *   stone and standing a hair proud of it (so they bend the reflection a little, as the ripple does), over a faint
 *   cloudy variation. Low in contrast: it shows where light falls on it.
 */
enum class StoneLook(val word: String) {
    Plain("plain"),
    Marble("marble");

    companion object {
        /** The stone the address asks for: `?stone=marble` [Marble]; any other value, or none, [Plain]. */
        fun of(switches: Switches): StoneLook = entries.firstOrNull { it.word == switches[Switch.Stone] } ?: Plain
    }
}
