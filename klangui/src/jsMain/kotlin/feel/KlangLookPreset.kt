/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * A look (KLANG-00339): an [id] to remember it by, a [name] to show, and the role values it puts on the user's layer.
 * An empty [values] map is the default look. A built-in look is read only; one of the user's [own] (KLANG-00382) can be
 * edited, renamed and deleted, and carries its [author] when it is shared.
 */
data class KlangLookPreset(
    val id: String,
    val name: String,
    val values: Map<KlangRole, String>,
    val author: String = "",
    val own: Boolean = false,
) {
    companion object {
        /**
         * The longest name a look of the user's own may have, so it fits a drawer tile (about 80 px at 11 px). A count
         * cannot promise a width ("WWWWWWWWWW" is wider than "Asphalt"), so a tile still cuts a name that overflows
         * with "..."; KLANG-00382 decision 6, 10 taken for now (the maintainer, 2026-10-28: "Not sure we will see").
         */
        const val NAME_MAX = 10

        /** A swatch's stops, lit to dark: the light once, the body and the shadow twice each. */
        private val SWATCH_ROLES = listOf(
            KlangRole.MetalLight,
            KlangRole.MetalMid,
            KlangRole.MetalMid,
            KlangRole.MetalDark,
            KlangRole.MetalDark,
        )
    }

    /**
     * Its metal, lit to dark, for a swatch: the preset's own values where it has them and they fit their role, the
     * defaults elsewhere. Weighted to the metal's body and shadow, its light only a small highlight: spread evenly, the
     * light took a third of the lamp and every look read pale (the maintainer, 2026-10-28: "the lamps do not really
     * show the color to expect").
     */
    val swatch: List<String>
        get() = SWATCH_ROLES.map { role -> values[role]?.takeIf { role.accepts(it) } ?: role.default }
}
