/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * The built-in looks (KLANG-00339): the three the maintainer picked from the kitchen sink's samples (2026-10-25). A
 * custom look of the user's own comes later; the drawer shows it as "later" until then.
 */
object KlangLooks {

    val Default = KlangLookPreset(id = "default", name = "Default", values = emptyMap())

    val Copper = KlangLookPreset(
        id = "copper",
        name = "Copper",
        values = mapOf(
            KlangRole.MetalLight to "#f6d6bd",
            KlangRole.MetalMid to "#c98a5e",
            KlangRole.MetalDark to "#7a4a2c",
            KlangRole.MetalInk to "#1a0d05",
            KlangRole.AccentMuted to "#b0764c",
            // Selected in the metal's light, bronze; lighter than the warning's copper (#d19a66), so the two stay apart
            KlangRole.LampOn to "#e8b28a",
        ),
    )

    val Cream = KlangLookPreset(
        id = "cream",
        name = "Cream",
        values = mapOf(
            KlangRole.GroundApp to "#141311",
            KlangRole.GroundDeep to "#0d0c0b",
            KlangRole.GroundPanel to "#22201c",
            KlangRole.InkText to "#eadfc4",
            KlangRole.InkStrong to "#fff6dc",
            KlangRole.InkLabel to "#c6b996",
            KlangRole.MetalLight to "#fbf3dd",
            KlangRole.MetalMid to "#e3d5b0",
            KlangRole.MetalDark to "#a08f66",
            // Selected in the metal's light, cream
            KlangRole.LampOn to "#f2e3bb",
        ),
    )

    val all: List<KlangLookPreset> = listOf(Default, Copper, Cream)

    /** The preset with [id]; null for an id no preset has (one remembered from an older version, say). */
    fun byId(id: String): KlangLookPreset? = all.firstOrNull { it.id == id }
}
