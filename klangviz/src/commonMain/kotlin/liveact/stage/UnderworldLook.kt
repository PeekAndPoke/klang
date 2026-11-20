/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The underworld in the mirror (`?underworld=on`, [Switch.Underworld]; KLANG-00478, to be judged by eye: "the floor
 * reflects the other world, cold ... for one moment of a show, not all the time"); [word] is its name in the address
 * and in the line at the top. Only the mirror floor shows it ([FloorLook.Mirror]).
 * - [Off]: the mirror shows the stage as it is.
 * - [On]: the mirror shows the other world: its reflection cold (blue silver moonlight), darker and stiller, the sun
 *   in it a cold white moon, and Sirene's eyes, while they are in her shadow, shining in her reflection too. It comes
 *   over about 2 s and goes over about 3 s ([FADE_IN], [FADE_OUT]). No alter egos in the reflection yet: there are no
 *   figures of them to mirror.
 */
enum class UnderworldLook(val word: String) {
    Off("off"),
    On("on");

    companion object {
        /** How long the underworld takes to come into the mirror, in seconds. */
        const val FADE_IN = 2.0

        /** How long it takes to go again, in seconds. */
        const val FADE_OUT = 3.0

        /** The underworld the address asks for: `?underworld=on` [On]; any other value, or none, [Off]. */
        fun of(switches: Switches): UnderworldLook = entries.firstOrNull { it.word == switches[Switch.Underworld] } ?: Off
    }
}
