/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * Whether and how finely the reflections are drawn: the mirror floor's picture of the stage, where the look has the
 * mirror floor ([FloorLook.Mirror], [LookVariants.mirrors]). [word] is its name in the address (`?reflections=low`)
 * and in an app's controls.
 *
 * A quality of the picture, a visitor's choice for a slow machine, not a part of the look: the look's floor stays the
 * mirror where it was chosen, and [Off] draws it as the plain floor until the reflections are on again (low, mid
 * or high). The mirror draws the stage a second time each frame. On at every fog since 2026-11-20 (the maintainer:
 * "Please decouple the floor reflection from the fog settings. it needs its own setting and should default to
 * enabled."); before, the mirror was drawn only with the fog at high.
 *
 * Three steps since 2026-11-26 (KLANG-00484; the maintainer: "another setting for the reflection quality would be
 * nice: low, mid, high ... where mid is the current setting"). How finely the mirror draws its picture:
 * - [Mid], the reflections as they were ("on"): the picture at the screen's own pixels (no more than the page's at a
 *   pixel ratio of 2, and at most [mostPixels]), and where the stage's floor fills much of the screen it draws at most
 *   [budget] of the picture's pixels in a frame (a smaller share of it, down to half each way).
 * - [Low]: the picture at [size] half of Mid's each way, a quarter of its pixels, on every screen: the mirror costs
 *   about a quarter of what Mid's does, its image softer.
 * - [High]: the whole picture every frame however close the camera is ([budget] 1), up to a 4K screen's pixels.
 * [Off] draws none.
 */
enum class ReflectionQuality(val word: String, val size: Double, val budget: Double, val mostPixels: Double) {
    Off(word = "off", size = 0.0, budget = 0.0, mostPixels = 0.0),
    Low(word = "low", size = 0.5, budget = 0.5, mostPixels = 1920.0 * 1200.0),
    Mid(word = "mid", size = 1.0, budget = 0.5, mostPixels = 1920.0 * 1200.0),
    High(word = "high", size = 1.0, budget = 1.0, mostPixels = 3840.0 * 2160.0);

    companion object {
        /**
         * The quality named [word] (`off`, `low`, `mid`, `high`), and `on`, the word the reflections had when they were
         * only off or on, for [Mid]; null for any other word.
         */
        fun named(word: String?): ReflectionQuality? = if (word == "on") Mid else entries.firstOrNull { it.word == word }

        /** The quality the address asks for (`?reflections=low`); [Mid] without it, or with another word. */
        fun of(switches: Switches): ReflectionQuality = named(switches[Switch.Reflections]) ?: Mid
    }
}
