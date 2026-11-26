/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.lights

import io.peekandpoke.klang.viz.liveact.PicturePreset
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * How finely the lamps' shadows are drawn. [word] is its name in the address (`?shadows=low`) and in an app's
 * controls; [mapSize] the points each way of a lamp's shadow map; [innerCasts]: whether the inner lamp behind
 * ([BackPlace.INNER], the dimmest of the three) casts a shadow.
 *
 * Every shadow map draws the scene's depth anew each frame (the figures included), and every lit pixel reads every
 * map: [Low] draws a quarter of the points in three maps instead of four. The lamps that cast none at [High] cast none
 * at [Low] either, and the low front lamp, which throws the band's big shadows on the wall, casts at both.
 */
enum class ShadowQuality(val word: String, val mapSize: Int, val innerCasts: Boolean) {
    Low(word = "low", mapSize = 1024, innerCasts = false),
    High(word = "high", mapSize = 2048, innerCasts = true);

    /** Whether a lamp that casts a shadow at [High], hanging at [place] (null: not behind the band), casts one here. */
    fun casts(place: BackPlace?): Boolean = place != BackPlace.INNER || innerCasts

    companion object {
        /** The quality named [word] (`low`); null for any other word. */
        fun named(word: String?): ShadowQuality? = entries.firstOrNull { it.word == word }

        /**
         * The quality the address asks for (`?shadows=low`); without it (or with a word that names none) the
         * quality's (`?quality=`, [PicturePreset]); else [High], the shadows as they were made.
         */
        fun of(switches: Switches): ShadowQuality = named(switches[Switch.Shadows]) ?: PicturePreset.of(switches)?.shadows ?: High
    }
}
