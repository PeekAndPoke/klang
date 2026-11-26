/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.PicturePreset
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import kotlin.math.pow

/**
 * How finely the fog on the floor is drawn: in [layers] level sheets (none for [Off]). [word] is its name in the
 * address (`?fog=low`) and in an app's controls.
 *
 * Where the fog is worked out as maps, one map for each sheet, the maps lie in one picture in [cols] columns and
 * [rows] rows. Each sheet lets [share] of its fog through, so that fewer sheets veil as much as the twelve the fog was
 * made with ([MADE_LAYERS]) do (see [share]). [High] is the one the live act starts with.
 *
 * Three qualities since 2026-11-20 (the maintainer: "I also think we only need three fog options now: off, low,
 * medium -> with todays settings just renaming to: off, low, high"): [High] is the former medium's eight sheets, so
 * the picture a show starts with draws fewer sheets than the twelve before, veiling as much. The former high (twelve)
 * and xhigh (sixteen) are gone; their words in an address name no quality now.
 *
 * Half the sheets since 2026-11-26 (KLANG-00484; the maintainer: "There is barely any difference noticable between
 * low and high, so we could maybe half the number of layer"): [Low] two (was four), [High] four (was eight).
 */
enum class FogQuality(val layers: Int, val word: String, val cols: Int, val rows: Int) {
    Off(layers = 0, word = "off", cols = 0, rows = 0),
    Low(layers = 2, word = "low", cols = 2, rows = 1),
    High(layers = 4, word = "high", cols = 2, rows = 2);

    /**
     * The see-through share of each sheet, before it fades as the camera comes to its height: the twelve sheets the fog
     * was made with ([MADE_LAYERS]) each let [MADE_SHARE] through, and with fewer sheets each lets so much that all of
     * them together veil what is behind as much as those twelve did: `1 - (1 - 0.22)^(12 / layers)`. Low's two
     * about 0.77, High's four about 0.53; 0 for [Off].
     */
    val share: Double
        get() = if (layers == 0) 0.0 else 1.0 - (1.0 - MADE_SHARE).pow(MADE_LAYERS.toDouble() / layers)

    companion object {
        /** The sheets the fog was made with: the veil every quality matches ([share]). */
        const val MADE_LAYERS = 12

        /** The see-through share of each of the [MADE_LAYERS] sheets the fog was made with. */
        const val MADE_SHARE = 0.22

        /** The quality named [word] (`low`); null for any other word. */
        fun named(word: String?): FogQuality? = entries.firstOrNull { it.word == word }

        /**
         * The quality the address asks for: `?fog=off`, `low` or `high` (a word of [FogQuality]), else `?nofog`
         * [Off], else the quality's (`?quality=`, [PicturePreset]), else [High] (also for a word that names none, the
         * former `medium` and `xhigh` among them).
         */
        fun of(switches: Switches): FogQuality = named(switches[Switch.Fog])
            ?: if (switches.has(Switch.NoFog)) Off else PicturePreset.of(switches)?.fog ?: High
    }
}
