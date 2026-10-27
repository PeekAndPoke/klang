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
            // Grounds of its own, a reddish brown in the copper's hue, darker than the cream's and apart from the
            // default's blue-black (the maintainer, 2026-10-27: "a more distinct background color"); its keys' caps
            // and the fields' well in the same warmth; then all a step darker ("a bit darker, not much")
            KlangRole.GroundApp to "#171210",
            KlangRole.GroundDeep to "#1e1713",
            // The tool windows a greyish variant of the grounds, as the default's are (the maintainer, 2026-10-27)
            KlangRole.GroundPanel to "#27231f",
            KlangRole.GroundSunken to "#110c0a",
            KlangRole.CapLight to "#5b4a40",
            KlangRole.CapDark to "#241b17",
            // Its inks warm as well: the default's blue-grey text "sticks out as not being part of the palette" (the
            // maintainer, 2026-10-27)
            KlangRole.InkText to "#e8d8cc",
            KlangRole.InkStrong to "#fff2e8",
            KlangRole.InkLabel to "#c2a593",
            KlangRole.MetalLight to "#f6d6bd",
            KlangRole.MetalMid to "#c98a5e",
            KlangRole.MetalDark to "#7a4a2c",
            KlangRole.MetalInk to "#1a0d05",
            KlangRole.AccentMuted to "#b0764c",
            // Selected in the blue opposite the copper (hue about 25, the azure about 204): warm and cold, a classic pair
            KlangRole.LampOn to "#4fa3d9",
        ),
    )

    val Cream = KlangLookPreset(
        id = "cream",
        name = "Cream",
        values = mapOf(
            // The grounds lifted off the editor's black, warm (the maintainer, 2026-10-27: the panel's ground was "too
            // dark, there is no contrast to the editor"); the sunken one, the fields' well, in the cream's hue too, not
            // the default's blue-black ("too blueish")
            KlangRole.GroundApp to "#1d1b17",
            KlangRole.GroundDeep to "#25221c",
            // The tool windows a greyish variant of the grounds, as the default's are (the maintainer, 2026-10-27)
            KlangRole.GroundPanel to "#2b2a28",
            KlangRole.GroundSunken to "#15130f",
            // The keys' caps warm too, not the default's blue-grey
            KlangRole.CapLight to "#5a5344",
            KlangRole.CapDark to "#25221b",
            KlangRole.InkText to "#eadfc4",
            KlangRole.InkStrong to "#fff6dc",
            KlangRole.InkLabel to "#c6b996",
            KlangRole.MetalLight to "#fbf3dd",
            KlangRole.MetalMid to "#e3d5b0",
            KlangRole.MetalDark to "#a08f66",
            // Selected in the blue opposite the cream (hue about 44, the periwinkle about 223), muted as the cream is soft
            KlangRole.LampOn to "#8099d6",
        ),
    )

    val all: List<KlangLookPreset> = listOf(Default, Copper, Cream)

    /** The preset with [id]; null for an id no preset has (one remembered from an older version, say). */
    fun byId(id: String): KlangLookPreset? = all.firstOrNull { it.id == id }
}
