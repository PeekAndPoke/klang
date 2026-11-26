/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality

/**
 * A quality of the whole picture, set by one choice: the oversampling ([samples]), the fog ([fog]), the lamps'
 * shadows ([shadows]) and the mirror floor's reflections ([reflections]) together (KLANG-00492; the maintainer,
 * 2026-11-26: "four levels and a 'custom' level, when the user changes an individual setting and then has a
 * combination that is none of the presets"). [word] is its name in the address (`?quality=low`) and in an app's
 * controls.
 *
 * [Medium] is the picture the show starts with at 1x, [High] the same at 2x: what the machine test measures, so it
 * chooses among [Low], [Medium] and [High]. [Max] is the visitor's choice only. A combination of the four settings that
 * is none of them is "custom" ([matching] gives none).
 */
enum class PicturePreset(
    val word: String,
    val samples: Double,
    val fog: FogQuality,
    val shadows: ShadowQuality,
    val reflections: ReflectionQuality,
) {
    Low(word = "low", samples = 1.0, fog = FogQuality.Low, shadows = ShadowQuality.Low, reflections = ReflectionQuality.Off),
    Medium(word = "medium", samples = 1.0, fog = FogQuality.High, shadows = ShadowQuality.High, reflections = ReflectionQuality.Mid),
    High(word = "high", samples = 2.0, fog = FogQuality.High, shadows = ShadowQuality.High, reflections = ReflectionQuality.Mid),
    Max(word = "max", samples = 4.0, fog = FogQuality.High, shadows = ShadowQuality.High, reflections = ReflectionQuality.High);

    companion object {
        /** The preset named [word] (`low`); null for any other word. */
        fun named(word: String?): PicturePreset? = entries.firstOrNull { it.word == word }

        /** The preset the address asks for (`?quality=high`); null without it, or with a word that names none. */
        fun of(switches: Switches): PicturePreset? = named(switches[Switch.Quality])

        /** The preset whose four settings are [samples], [fog], [shadows] and [reflections]; null: custom. */
        fun matching(
            samples: Double,
            fog: FogQuality,
            shadows: ShadowQuality,
            reflections: ReflectionQuality,
        ): PicturePreset? = entries.firstOrNull {
            it.samples == samples && it.fog == fog && it.shadows == shadows && it.reflections == reflections
        }
    }
}
