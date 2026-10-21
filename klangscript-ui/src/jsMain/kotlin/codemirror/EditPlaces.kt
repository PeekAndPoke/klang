/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

/**
 * The places where the user last edited, newest last, and how far a walk back through them has gone (KLANG-00322).
 *
 * The rule behind Ctrl+Shift+Backspace (Cmd+Shift+Backspace on a Mac), IntelliJ's "last edit location". It holds no
 * CodeMirror, so it is tested on its own; [EditPlacesExtension] only wires it into the editor.
 *
 * - Every document change is an edit (the ticket's point 3, as changed 2026-10-21), and edits are grouped by
 *   distance, not by time: an edit within [NEAR_LINES] of the newest place replaces it (the place moves to the edit's
 *   end), an edit further away pushes a new place. A typing burst, a fix two lines up and a pause in the same spot
 *   therefore stay one place. (The undo history groups by time, which is right for undo and not for navigation.)
 * - Every change maps each place forward first, so typing above a place keeps it on its line.
 * - [back] skips the place the cursor is at and walks older, one place per step. Any new edit ends the walk.
 * - At most [MAX_PLACES] are kept; a push beyond them drops the oldest.
 *
 * Immutable, as the value of a CodeMirror state field must be: every step returns a new value.
 */
data class EditPlaces(
    /** Document offsets of the places, newest last. */
    val places: List<Int> = emptyList(),
    /** The index in [places] the last step back went to, or null while no walk is under way. */
    val walkIndex: Int? = null,
) {
    /** One range a change wrote, from [from] to [to], as offsets in the document after the change. */
    data class ChangedRange(val from: Int, val to: Int)

    /** One step back: the offset the cursor moves to, and the index in [places] the walk then stands at. */
    data class Step(val target: Int, val walkIndex: Int)

    companion object {
        /**
         * An edit at most this many lines from the newest place is the same place.
         *
         * Two lines keep a typing burst, a fix two lines up and a rename across one line together, and still tell two
         * parts of a song apart. The same distance decides whether the cursor is already at a place when going back,
         * so each step lands somewhere visibly different.
         */
        const val NEAR_LINES = 2

        /**
         * The most places the list keeps.
         *
         * Thirty reaches far back through a session on one song, and every step back is one key press, so a longer
         * list would only hold steps nobody takes.
         */
        const val MAX_PLACES = 30
    }

    /**
     * The places after a document change, which is always an edit.
     *
     * Every place is mapped through [mapPos] first, so all offsets are in the new document. Then the walk ends, and the
     * change replaces the newest place when that lies within [NEAR_LINES] of any one of its [ranges], or pushes a new
     * place otherwise; either way the place goes to the end of the last range. Each range counts on its own, so an edit
     * with two cursors far apart (or a "replace all") does not swallow a place that lies between them.
     * [lineOf] gives the line number of an offset in the new document.
     */
    fun changed(mapPos: (Int) -> Int, ranges: List<ChangedRange>, lineOf: (Int) -> Int): EditPlaces {
        val mapped = places.map(mapPos)
        // A change writes at least one range; without one there is no place to record, only the mapping and the end of
        // the walk
        val end = ranges.lastOrNull()?.to ?: return EditPlaces(places = mapped, walkIndex = null)
        val newest = mapped.lastOrNull()
        val sameAsNewest = newest != null && ranges.any { range ->
            isNear(place = newest, fromLine = lineOf(range.from), toLine = lineOf(range.to), lineOf = lineOf)
        }
        val kept = if (sameAsNewest) mapped.dropLast(1) else mapped

        return EditPlaces(places = (kept + end).takeLast(MAX_PLACES), walkIndex = null)
    }

    /**
     * One step back from the cursor at [cursor]: to the newest place older than the walk so far that the cursor is not
     * already at (within [NEAR_LINES]), or null when there is none. [lineOf] gives the line number of an offset. The
     * step does not change these places; [walkedTo] records it.
     */
    fun back(cursor: Int, lineOf: (Int) -> Int): Step? {
        val cursorLine = lineOf(cursor)
        val start = (walkIndex ?: places.size) - 1

        for (index in start downTo 0) {
            if (!isNear(place = places[index], fromLine = cursorLine, toLine = cursorLine, lineOf = lineOf)) {
                return Step(target = places[index], walkIndex = index)
            }
        }

        return null
    }

    /** The same places with the walk standing at [index], as a [Step] left it. */
    fun walkedTo(index: Int): EditPlaces = copy(walkIndex = index)

    /** True when [place] lies on the lines [fromLine] to [toLine], or at most [NEAR_LINES] above or below them. */
    private fun isNear(place: Int, fromLine: Int, toLine: Int, lineOf: (Int) -> Int): Boolean {
        val line = lineOf(place)

        return line >= fromLine - NEAR_LINES && line <= toLine + NEAR_LINES
    }
}
