/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * A built-in look (KLANG-00339): an [id] to remember it by, a [name] to show, and the role values it puts on the
 * user's layer. An empty [values] map is the default look.
 */
data class KlangLookPreset(
    val id: String,
    val name: String,
    val values: Map<KlangRole, String>,
) {
    /**
     * Its metal, lit to dark, for a swatch: the preset's own values where it has them and they fit their role, the
     * defaults elsewhere.
     */
    val swatch: List<String>
        get() = listOf(KlangRole.MetalLight, KlangRole.MetalMid, KlangRole.MetalDark).map { role ->
            values[role]?.takeIf { role.accepts(it) } ?: role.default
        }
}
