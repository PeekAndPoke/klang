/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * Whether the reflections are drawn: the mirror floor's picture of the stage, where the look has the mirror floor
 * ([FloorLook.Mirror], [LookVariants.mirrors]). [word] is its name in the address (`?reflections=off`) and in an app's
 * controls.
 *
 * A quality of the picture, a visitor's choice for a slow machine, not a part of the look: the look's floor stays the
 * mirror where it was chosen, and [Off] draws it as the plain floor until the reflections are [On] again. The mirror
 * draws the stage a second time each frame. On at every fog since 2026-11-20 (the maintainer: "Please decouple the
 * floor reflection from the fog settings. it needs its own setting and should default to enabled."); before, the
 * mirror was drawn only with the fog at high.
 */
enum class ReflectionQuality(val word: String) {
    Off(word = "off"),
    On(word = "on");

    companion object {
        /** The quality named [word] (`off`); null for any other word. */
        fun named(word: String?): ReflectionQuality? = entries.firstOrNull { it.word == word }

        /** The quality the address asks for (`?reflections=off`); [On] without it, or with another word. */
        fun of(switches: Switches): ReflectionQuality = named(switches[Switch.Reflections]) ?: On
    }
}
