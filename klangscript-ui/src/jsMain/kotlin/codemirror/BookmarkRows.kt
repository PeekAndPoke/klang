/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.EditorState

/** A bookmark as an overview shows it (KLANG-00325): its digit, its line, and the code on that line. */
data class BookmarkRow(val digit: Int, val line: Int, val code: String)

/**
 * The bookmarks of the editor whose state is [state], by digit, each with the code on its line, trimmed and with a tab
 * or a run of blanks at most two spaces (maintainer, 2026-10-26: a comment aligned far right); empty when
 * the editor has no bookmarks, or not the bookmarks extension. The way in for the app (the song page's bookmarks panel); the
 * extension itself stays internal.
 */
fun bookmarkRowsOf(state: EditorState): List<BookmarkRow> = BookmarksExtension.rowsOf(state)
