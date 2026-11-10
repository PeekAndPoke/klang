/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/**
 * The editor's settings, as they are kept (KLANG-00422): one document, here in the shared code so a server can keep
 * the same one. A field that a stored document lacks takes its default, so a new setting needs no migration.
 */
@Serializable
data class EditorSettings(
    /** Whether the editor shows the code highlights at all (KLANG-00411). */
    val highlightsOn: Boolean = true,
    /** How many highlights one sound event may light (KLANG-00349). */
    val highlightsPerEvent: Int = HIGHLIGHTS_PER_EVENT_DEFAULT,
    /** The highlights' strength, a whole percent (KLANG-00411). */
    val highlightStrength: Int = HIGHLIGHT_STRENGTH_DEFAULT,
    /** Whether the editor shows a name's quick help on hover (KLANG-00349). */
    val hoverHelp: Boolean = true,
) {
    companion object {
        const val HIGHLIGHTS_PER_EVENT_DEFAULT = 25
        val HIGHLIGHTS_PER_EVENT_RANGE = 0..50

        /** 66 percent for one who never chose (the maintainer, 2026-11-05: "Current is 100% but I would default to 66%"). */
        const val HIGHLIGHT_STRENGTH_DEFAULT = 66
        val HIGHLIGHT_STRENGTH_RANGE = 0..100
    }

    /** These settings with every number held in its range: what is kept and shown. */
    fun inRange(): EditorSettings = copy(
        highlightsPerEvent = highlightsPerEvent.coerceIn(HIGHLIGHTS_PER_EVENT_RANGE),
        highlightStrength = highlightStrength.coerceIn(HIGHLIGHT_STRENGTH_RANGE),
    )
}
