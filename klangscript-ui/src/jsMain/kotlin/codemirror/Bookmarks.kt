/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

/**
 * The numbered bookmarks of one song, 0 to 9, as IntelliJ keeps them (KLANG-00323).
 *
 * The rule behind Ctrl+Shift+digit (set) and Ctrl+digit (go). It holds no CodeMirror, so it is tested on its own;
 * [BookmarksExtension] only wires it into the editor.
 *
 * - A line holds one bookmark, and a digit sits on one line.
 * - Setting a digit on the line that holds it removes it (IntelliJ's toggle). Setting a digit that sits on another
 *   line asks first ([SetResult.AskToMove]), and [put] moves it after a yes. Setting a digit on a line that holds
 *   another one replaces that one without asking.
 * - A bookmark sits on its line's anchor: the first non-whitespace character, or the line's end on a blank line. Every
 *   change maps the anchor forward and then puts the bookmark on the anchor of the line it reached, so it follows its
 *   line while the text above changes, a line break typed before the text (Enter at the start of the line, also in
 *   its indentation) takes it down with the text, a line break typed after the text leaves it, and a line joined to
 *   the one above takes it along.
 * - When a change deletes the anchor but not the whole line, the bookmark goes to the line where that deletion
 *   started (a paste over the line's text: the paste's first line). When the deletion took the whole line (it started
 *   before the line and reached its end, as CodeMirror's delete line does from the end of the line above), the
 *   bookmark lands on the line of the text right after the deletion: the line that takes its place. A line break is
 *   not that text: a deletion that ends at a line's end lands on the next line, one that ends inside a line on that
 *   line, now joined to where the deletion started.
 * - A moved line ([moved], CodeMirror's move line up or down) takes its bookmark along, and so does the line it
 *   jumped over.
 * - Two bookmarks that land on one line keep one: the one whose anchor survived wins over one whose anchor was
 *   deleted, and between two of a kind the one that was higher up. The other one is gone, also after an undo
 *   (maintainer, 2026-10-21: a deleted line takes its bookmark with it).
 * - The host keeps them as line numbers ([Line]); [load] drops a line past the end of the song.
 *
 * Immutable, as the value of a CodeMirror state field must be: every step returns a new value.
 */
data class Bookmarks(
    /** The bookmarks by digit, each at the anchor of its line. */
    val placed: List<Placed> = emptyList(),
) {
    /** Bookmark [digit] at the offset [pos], the anchor of its line. */
    data class Placed(val digit: Int, val pos: Int)

    /** Bookmark [digit] on [line] (counted from 1), as the host keeps it. */
    data class Line(val digit: Int, val line: Int)

    /** The offsets [from] to [to] of a document: a range a change deleted, or one selection range. */
    data class Span(val from: Int, val to: Int)

    /** What the rule reads of a document. */
    interface Doc {
        /** The number of lines. */
        val lineCount: Int

        /** The line (counted from 1) that holds the offset [pos]. */
        fun lineOf(pos: Int): Int

        /** The offset where [line] starts. */
        fun lineStart(line: Int): Int

        /** The text of [line], without its line break. */
        fun lineText(line: Int): String
    }

    /** What setting a bookmark comes to. */
    sealed interface SetResult {
        /** Set, replaced or removed: the bookmarks after it. */
        data class Changed(val bookmarks: Bookmarks) : SetResult

        /** Bookmark [digit] sits on another line, at the offset [from]: moving it asks first. */
        data class AskToMove(val digit: Int, val from: Int) : SetResult
    }

    companion object {
        /** The digits a bookmark can have, one per number key. */
        val DIGITS: IntRange = 0..9

        /**
         * The bookmarks the host kept, in [doc].
         *
         * A line past the end of the document (or before its first line) is dropped, and so is a digit outside
         * [DIGITS]. When two share a digit or a line (a store edited by hand), the first one wins.
         */
        fun load(lines: List<Line>, doc: Doc): Bookmarks {
            val kept = mutableListOf<Line>()

            for (bookmark in lines) {
                val fits = bookmark.digit in DIGITS && bookmark.line in 1..doc.lineCount
                val free = kept.none { it.digit == bookmark.digit || it.line == bookmark.line }

                if (fits && free) {
                    kept.add(bookmark)
                }
            }

            return Bookmarks(
                placed = kept.map { Placed(digit = it.digit, pos = anchorOf(line = it.line, doc = doc)) }
                    .sortedBy { it.digit }
            )
        }

        /** The anchor of [line]: the offset of its first non-whitespace character, or of its end on a blank line. */
        fun anchorOf(line: Int, doc: Doc): Int {
            val text = doc.lineText(line)

            return doc.lineStart(line) + (text.length - text.trimStart().length)
        }

        /**
         * The blocks of whole lines that the [selection] ranges cover in [doc], as CodeMirror's move line takes them:
         * a range that ends at a line's start leaves that line out, and blocks that touch are one.
         */
        fun lineBlocks(selection: List<Span>, doc: Doc): List<IntRange> {
            val blocks = mutableListOf<IntRange>()

            for (range in selection) {
                val first = doc.lineOf(range.from)
                val endsAtLineStart = range.to > range.from && doc.lineStart(doc.lineOf(range.to)) == range.to
                val last = if (endsAtLineStart) doc.lineOf(range.to - 1) else doc.lineOf(range.to)
                val previous = blocks.lastOrNull()

                if (previous != null && previous.last + 1 >= first) {
                    blocks[blocks.size - 1] = previous.first..maxOf(previous.last, last)
                } else {
                    blocks.add(first..last)
                }
            }

            return blocks
        }

        /**
         * Where each line goes when CodeMirror moves the [blocks] one line [down] (or up) in a document of [lineCount]
         * lines: the block's lines by one, the line it jumps over by the block's size the other way. A block at the
         * end it moves towards stays, as CodeMirror leaves it.
         */
        fun lineMoves(blocks: List<IntRange>, down: Boolean, lineCount: Int): Map<Int, Int> {
            val moves = mutableMapOf<Int, Int>()

            for (block in blocks) {
                val atTheEnd = if (down) block.last == lineCount else block.first == 1

                if (atTheEnd) {
                    continue
                }

                if (down) {
                    block.forEach { moves[it] = it + 1 }
                    moves[block.last + 1] = block.first
                } else {
                    block.forEach { moves[it] = it - 1 }
                    moves[block.first - 1] = block.last
                }
            }

            return moves
        }
    }

    /** The anchor of bookmark [digit]'s line, or null when it is not set. */
    fun posOf(digit: Int): Int? = placed.firstOrNull { it.digit == digit }?.pos

    /**
     * Bookmark [digit] set on the line that holds the offset [at] (the cursor), in [doc].
     *
     * Removes the bookmark when that line holds it already, asks first when it sits on another line, and sets it
     * otherwise, replacing whatever bookmark the line holds.
     */
    fun set(digit: Int, at: Int, doc: Doc): SetResult {
        val current = placed.firstOrNull { it.digit == digit }
            ?: return SetResult.Changed(put(digit = digit, at = at, doc = doc))

        if (doc.lineOf(current.pos) == doc.lineOf(at)) {
            return SetResult.Changed(Bookmarks(placed = placed - current))
        }

        return SetResult.AskToMove(digit = digit, from = current.pos)
    }

    /**
     * Bookmark [digit] put on the line that holds the offset [at], in [doc], wherever it was before, replacing any
     * other bookmark on that line: a set that needs no question, or the move after a yes.
     */
    fun put(digit: Int, at: Int, doc: Doc): Bookmarks {
        val line = doc.lineOf(at)
        val others = placed.filter { it.digit != digit && doc.lineOf(it.pos) != line }

        return Bookmarks(
            placed = (others + Placed(digit = digit, pos = anchorOf(line = line, doc = doc))).sortedBy { it.digit }
        )
    }

    /**
     * The bookmarks after a document change from [oldDoc] to [newDoc].
     *
     * [mapPos] maps an offset into the new document and keeps it before the character it sat before; [deletions] are
     * the ranges the change deleted or replaced, in the old document.
     */
    fun mapped(mapPos: (Int) -> Int, deletions: List<Span>, oldDoc: Doc, newDoc: Doc): Bookmarks {
        if (placed.isEmpty()) {
            return this
        }

        // Taken from the top, so that between two of a kind the one that was higher up keeps the line
        val moved = placed.sortedBy { it.pos }.map {
            mapOne(bookmark = it, mapPos = mapPos, deletions = deletions, oldDoc = oldDoc, newDoc = newDoc)
        }
        val kept = moved.groupBy { newDoc.lineOf(it.now.pos) }.values.map { onOneLine ->
            onOneLine.firstOrNull { !it.displaced } ?: onOneLine.first()
        }

        return Bookmarks(placed = kept.map { it.now }.sortedBy { it.digit })
    }

    /** The bookmarks after a move of lines from [oldDoc] to [newDoc]: each goes where [moves] sends its line. */
    fun moved(moves: Map<Int, Int>, oldDoc: Doc, newDoc: Doc): Bookmarks = Bookmarks(
        placed = placed.map {
            val line = oldDoc.lineOf(it.pos)

            it.copy(pos = anchorOf(line = moves[line] ?: line, doc = newDoc))
        }
    )

    /** The bookmarks by digit, as the host keeps them, in [doc]. */
    fun lines(doc: Doc): List<Line> = placed.map { Line(digit = it.digit, line = doc.lineOf(it.pos)) }

    /** One bookmark through a change: on the anchor of the line it reaches, and whether the change deleted its own. */
    private fun mapOne(bookmark: Placed, mapPos: (Int) -> Int, deletions: List<Span>, oldDoc: Doc, newDoc: Doc): Moved {
        val line = oldDoc.lineOf(bookmark.pos)
        val lineStart = oldDoc.lineStart(line)
        val lineEnd = lineStart + oldDoc.lineText(line).length
        val tookTheLine = deletions.firstOrNull { it.from < lineStart && it.to >= lineEnd }
        val tookTheAnchor = deletions.firstOrNull { it.from <= bookmark.pos && bookmark.pos < it.to }

        val reached = when {
            // The text right after the deletion: the line that takes this one's place
            tookTheLine != null -> mapPos(textAfter(pos = tookTheLine.to, doc = oldDoc))
            tookTheAnchor != null -> mapPos(tookTheAnchor.from)
            else -> mapPos(bookmark.pos)
        }
        val pos = anchorOf(line = newDoc.lineOf(reached), doc = newDoc)

        return Moved(now = bookmark.copy(pos = pos), displaced = tookTheLine != null || tookTheAnchor != null)
    }

    /**
     * Where the text right after a deletion that ends at [pos] starts: [pos], or the next line's start when [pos] is
     * the end of its line (the line break is no text), unless that line is the last.
     */
    private fun textAfter(pos: Int, doc: Doc): Int {
        val line = doc.lineOf(pos)
        val lineEnd = doc.lineStart(line) + doc.lineText(line).length

        if (pos != lineEnd || line == doc.lineCount) {
            return pos
        }

        return doc.lineStart(line + 1)
    }

    /** A bookmark [now], after a change, and whether the change deleted its anchor. */
    private data class Moved(val now: Placed, val displaced: Boolean)
}
