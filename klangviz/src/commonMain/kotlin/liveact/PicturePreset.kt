/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality

/**
 * The picture's level of detail, set by one choice: the fog ([fog]), the lamps' shadows ([shadows]) and the mirror
 * floor's reflections ([reflections]) together (KLANG-00492; the maintainer, 2026-11-26: "four levels and a 'custom'
 * level, when the user changes an individual setting and then has a combination that is none of the presets").
 * [word] is its name in the address (`?quality=low`) and in an app's controls.
 *
 * The oversampling is not part of it (the maintainer, 2026-11-27: "On a macbook air with retina display we will only
 * get 1x oversampling but the setting should still not be low"): how sharp the picture is drawn depends on the
 * display (a dense one has the pixels already), the detail on the machine. They are chosen apart, as games keep the
 * detail preset apart from the render scale.
 *
 * [High] is the picture the show starts with; the machine test chooses among [Low], [Medium] and [High], [Max] is
 * the visitor's choice only. A combination of the three settings that is none of them is "custom" ([matching] gives
 * none).
 */
enum class PicturePreset(
    val word: String,
    val fog: FogQuality,
    val shadows: ShadowQuality,
    val reflections: ReflectionQuality,
) {
    Low(word = "low", fog = FogQuality.Low, shadows = ShadowQuality.Low, reflections = ReflectionQuality.Off),
    Medium(word = "medium", fog = FogQuality.High, shadows = ShadowQuality.Low, reflections = ReflectionQuality.Low),
    High(word = "high", fog = FogQuality.High, shadows = ShadowQuality.High, reflections = ReflectionQuality.Mid),
    Max(word = "max", fog = FogQuality.High, shadows = ShadowQuality.High, reflections = ReflectionQuality.High);

    companion object {
        /** The preset named [word] (`low`); null for any other word. */
        fun named(word: String?): PicturePreset? = entries.firstOrNull { it.word == word }

        /** The preset the address asks for (`?quality=high`); null without it, or with a word that names none. */
        fun of(switches: Switches): PicturePreset? = named(switches[Switch.Quality])

        /** The preset whose three settings are [fog], [shadows] and [reflections]; null: custom. */
        fun matching(fog: FogQuality, shadows: ShadowQuality, reflections: ReflectionQuality): PicturePreset? =
            entries.firstOrNull { it.fog == fog && it.shadows == shadows && it.reflections == reflections }
    }
}
