/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

/**
 * Turns on the numbered bookmarks of an editor (KLANG-00323) and connects them to the host, which keeps them.
 *
 * The editor knows no storage: it starts from [initial], reports every change through [onChanged], and asks the host
 * through [askToMove] before it moves a bookmark from another line. An editor without these options has no bookmarks,
 * and their keys reach the browser.
 */
data class BookmarkOptions(
    /**
     * The bookmarks the editor starts with, read once when the editor is built (as its code is). A line past the end
     * of the song is dropped, and the next report leaves it out.
     */
    val initial: List<Bookmarks.Line>,
    /**
     * Called with all bookmarks, by digit, after each change: one set, moved or removed, or a line that an edit
     * above it moved.
     */
    val onChanged: (List<Bookmarks.Line>) -> Unit,
    /**
     * Asks the user whether to [Move] a bookmark. The host shows the question and calls `answer` once, with true for
     * yes; the editor then moves the bookmark, and takes the focus back on either answer.
     */
    val askToMove: (move: Move, answer: (yes: Boolean) -> Unit) -> Unit,
) {
    /** Bookmark [digit] is on line [fromLine], and the user set it on line [toLine]. */
    data class Move(val digit: Int, val fromLine: Int, val toLine: Int)
}
