/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.state

import kotlinx.serialization.Serializable

/**
 * What is kept of one song (KLANG-00422): one document per song, holding what the user changed. A field that is null
 * was never changed, and shows what the song itself says: a built-in song's own code, title and tempo, also when
 * they change.
 */
@Serializable
data class SongKept(
    val code: String? = null,
    val title: String? = null,
    val rpm: Double? = null,
    /** The editor's bookmarks, digit to line number (counted from 1). */
    val bookmarks: Map<Int, Int> = emptyMap(),
)
