/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import kotlin.math.pow

/**
 * How finely the fog on the floor is drawn: in [layers] level sheets (none for [Off]). [word] is its name in the
 * address (`?fog=low`) and in an app's controls.
 *
 * Where the fog is worked out as maps, one map for each sheet, the maps lie in one picture in [cols] columns and
 * [rows] rows. Each sheet lets [share] of its fog through, so that fewer sheets veil as much as [High]'s twelve do
 * (see [share]). [High] is the picture the live act was made with, and the one it starts with.
 */
enum class FogQuality(val layers: Int, val word: String, val cols: Int, val rows: Int) {
    Off(layers = 0, word = "off", cols = 0, rows = 0),
    Low(layers = 4, word = "low", cols = 2, rows = 2),
    Medium(layers = 8, word = "medium", cols = 4, rows = 2),
    High(layers = 12, word = "high", cols = 4, rows = 3),
    XHigh(layers = 16, word = "xhigh", cols = 4, rows = 4);

    /**
     * The see-through share of each sheet, before it fades as the camera comes to its height: [High]'s sheets each
     * let [HIGH_SHARE] through, and with fewer or more sheets each lets so much that all of them together veil
     * what is behind as much as [High]'s do: `1 - (1 - 0.22)^(12 / layers)`. Low's four about 0.53, XHigh's sixteen
     * about 0.17; 0 for [Off].
     */
    val share: Double
        get() = if (layers == 0) 0.0 else 1.0 - (1.0 - HIGH_SHARE).pow(High.layers.toDouble() / layers)

    companion object {
        /** The see-through share of each of [High]'s sheets: the fog as it was made. */
        const val HIGH_SHARE = 0.22

        /** The quality named [word] (`low`); null for any other word. */
        fun named(word: String?): FogQuality? = entries.firstOrNull { it.word == word }

        /**
         * The quality the address asks for: `?fog=low` (a word of [FogQuality]), else `?nofog` [Off], else [High]
         * (also for a word that names none).
         */
        fun of(switches: Switches): FogQuality = named(switches[Switch.Fog])
            ?: if (switches.has(Switch.NoFog)) Off else High
    }
}
