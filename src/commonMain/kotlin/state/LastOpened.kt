/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/**
 * What the user opened last, as it is stored (KLANG-00403, a stored section since KLANG-00422): the [entries], the
 * newest first. The list is one field: of two writers at once the later one's list stays.
 */
@Serializable
data class LastOpened(val entries: List<Entry> = emptyList()) {

    @Serializable
    enum class Kind {
        Song,
        Tutorial,
    }

    /**
     * One thing opened: its [kind] and [id] (a song's id, a lesson's slug), its [title] and [at] when, in milliseconds
     * since the epoch. A song keeps its [rpm]; a lesson the [track] it was opened in (its slug), if any.
     */
    @Serializable
    data class Entry(
        val kind: Kind,
        val id: String,
        val title: String,
        val at: Double,
        val rpm: Double? = null,
        val track: String? = null,
    )
}
