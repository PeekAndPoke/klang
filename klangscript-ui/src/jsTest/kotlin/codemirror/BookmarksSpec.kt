/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * The rule of the numbered bookmarks (KLANG-00323): set, toggle off, move after a question, one per line, mapped
 * through changes and line moves, and a stored line past the end dropped on load.
 *
 * The fixture plays the editor without CodeMirror: a plain-text document whose edits are one replacement each, mapped
 * the way CodeMirror's `mapPos` maps with `assoc` 1 (an offset at an insertion point stays before the character after
 * it; the start of a replaced range stays at its start).
 */
class BookmarksSpec : StringSpec({

    "a set marks the cursor's line, and another digit there replaces it without asking" {
        val editor = BookmarkEditor(lines = 40)

        val first = editor.set(digit = 3, line = 10, column = 4)
        val second = editor.set(digit = 5, line = 10, column = 4)

        withClue("the first set") {
            first.shouldBeInstanceOf<Bookmarks.SetResult.Changed>()
        }

        withClue("another digit on the same line") {
            second.shouldBeInstanceOf<Bookmarks.SetResult.Changed>()
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 5, line = 10))
        }

        withClue("going to it: the first character of its line") {
            editor.bookmarks.posOf(5) shouldBe editor.lineStart(10)
            editor.bookmarks.posOf(3) shouldBe null
        }
    }

    "setting a digit on the line that holds it removes it" {
        val editor = BookmarkEditor(lines = 40)

        editor.set(digit = 3, line = 10)
        editor.set(digit = 4, line = 20)
        editor.set(digit = 3, line = 10, column = 2)

        withClue("the toggle") {
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 4, line = 20))
        }
    }

    "setting a digit that sits on another line asks first, and the move after a yes replaces what the line held" {
        val editor = BookmarkEditor(lines = 40)

        editor.set(digit = 3, line = 10)
        editor.set(digit = 7, line = 20)

        val result = editor.set(digit = 3, line = 20)

        withClue("the question") {
            result shouldBe Bookmarks.SetResult.AskToMove(digit = 3, from = editor.lineStart(10))
            // Nothing changes before the answer
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10), Bookmarks.Line(digit = 7, line = 20))
        }

        editor.moveAfterYes(digit = 3, line = 20)

        withClue("the move") {
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 20))
        }
    }

    "a bookmark follows its line while the text above it changes" {
        val editor = BookmarkEditor(lines = 40)

        editor.set(digit = 3, line = 10)
        editor.insert(pos = editor.lineStart(2), inserted = "a\nb\n")

        withClue("two lines typed above") {
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 12))
        }

        editor.deleteLineFromItsStart(line = 1)

        withClue("a line deleted above") {
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 11))
        }

        editor.insert(pos = editor.lineStart(11), inserted = "x")
        editor.insert(pos = editor.lineStart(11), inserted = "\n")

        withClue("text typed at the start of its line, then a line break there") {
            editor.textOf(12) shouldBe "xline 10"
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 12))
        }

        editor.insert(pos = editor.lineStart(12) + 1, inserted = "\n")

        withClue("a line break typed after the line's first character leaves it") {
            editor.textOf(12) shouldBe "x"
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 12))
        }

        editor.insert(pos = editor.lineStart(30), inserted = "below\n")

        withClue("typed below it") {
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 12))
        }

        // Backspace at the start of its line
        editor.delete(from = editor.lineStart(12) - 1, to = editor.lineStart(12))

        withClue("its line joined to the one above") {
            editor.textOf(11) shouldBe "x"
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 11))
        }
    }

    "on an indented line a bookmark holds on to the first character of the text" {
        val enter = BookmarkEditor(lines = 40, indent = "    ").apply {
            set(digit = 3, line = 10)
            // Enter in the indentation, as CodeMirror's Enter does it: the line's whitespace goes, the break comes
            replace(from = lineStart(10), to = lineStart(10) + 4, inserted = "\n    ")
        }
        val insideTheLine = BookmarkEditor(lines = 40, indent = "    ").apply {
            set(digit = 3, line = 10)
            // From the indentation into the text
            delete(from = lineStart(10) + 2, to = lineStart(10) + 6)
        }

        withClue("Enter in its indentation takes it down with the text") {
            enter.textOf(11) shouldBe "    line 10"
            enter.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 11))
        }

        withClue("a deletion inside its line leaves it there") {
            insideTheLine.textOf(10) shouldBe "  ne 10"
            insideTheLine.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
        }
    }

    "a blank line's bookmark stays on it while the line gets text and lines are typed after it" {
        val editor = BookmarkEditor("a\n\nc\nd")

        editor.set(digit = 3, line = 2)
        editor.insert(pos = editor.lineStart(2), inserted = "foo")
        editor.insert(pos = editor.lineStart(2) + 3, inserted = "\n")
        editor.insert(pos = editor.lineStart(3), inserted = "bar\n")

        withClue("text typed on it, then two lines after it") {
            editor.textOf(2) shouldBe "foo"
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 2))
        }
    }

    "a paste over a bookmarked line keeps the bookmark on the paste's first line" {
        val overTheText = BookmarkEditor("a();\n  b();\n  c();\nd();").apply {
            set(digit = 3, line = 3)
            replace(from = lineStart(3) + 2, to = lineStart(3) + 6, inserted = "p();\nq();")
        }
        val overTheLine = BookmarkEditor("a();\n  b();\n  c();\nd();").apply {
            set(digit = 3, line = 3)
            set(digit = 4, line = 4)
            // The whole line with its break, by a paste that ends in one
            replace(from = lineStart(3), to = lineStart(4), inserted = "p();\n")
        }

        withClue("over the line's text") {
            overTheText.textOf(3) shouldBe "  p();"
            overTheText.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 3))
        }

        withClue("over the whole line") {
            overTheLine.textOf(3) shouldBe "p();"
            overTheLine.lines() shouldBe
                    listOf(Bookmarks.Line(digit = 3, line = 3), Bookmarks.Line(digit = 4, line = 4))
        }
    }

    "a deletion that keeps the end of its line takes the bookmark to where the line's text went" {
        val editor = BookmarkEditor(lines = 40).apply {
            set(digit = 3, line = 10)
            set(digit = 4, line = 11)
            // From the middle of line 9 into the text of line 10
            delete(from = lineStart(9) + 2, to = lineStart(10) + 2)
        }

        withClue("joined into line 9") {
            editor.textOf(9) shouldBe "line 10"
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 9), Bookmarks.Line(digit = 4, line = 10))
        }
    }

    "a deleted line's bookmark lands on the line that takes its place" {
        val fromItsStart = BookmarkEditor(lines = 40).apply {
            set(digit = 3, line = 10)
            deleteLineFromItsStart(line = 10)
        }
        val asCodeMirrorDoes = BookmarkEditor(lines = 40).apply {
            set(digit = 3, line = 10)
            deleteLineAsCodeMirrorDoes(line = 10)
        }
        val theLastLine = BookmarkEditor(lines = 40).apply {
            set(digit = 3, line = 40)
            deleteLineAsCodeMirrorDoes(line = 40)
        }
        val blankLineAbove = BookmarkEditor("foo()\n\n// Section B\nbar()").apply {
            set(digit = 3, line = 3)
            deleteLineAsCodeMirrorDoes(line = 3)
        }

        withClue("deleted from its start to the next line's") {
            fromItsStart.textOf(10) shouldBe "line 11"
            fromItsStart.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
        }

        withClue("deleted from the end of the line above, as CodeMirror's delete line does") {
            asCodeMirrorDoes.textOf(10) shouldBe "line 11"
            asCodeMirrorDoes.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
        }

        withClue("the last line deleted: no line comes after it") {
            theLastLine.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 39))
        }

        withClue("a blank line above: the line after the deleted one takes its place, not the blank one") {
            blankLineAbove.textOf(3) shouldBe "bar()"
            blankLineAbove.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 3))
        }
    }

    "an empty bookmarked line deleted as CodeMirror's delete line does lands where a select-and-delete lands" {
        val asCodeMirrorDoes = BookmarkEditor("line 1\nline 2\n\nline 4\nline 5").apply {
            set(digit = 3, line = 3)
            deleteLineAsCodeMirrorDoes(line = 3)
        }
        val fromItsStart = BookmarkEditor("line 1\nline 2\n\nline 4\nline 5").apply {
            set(digit = 3, line = 3)
            deleteLineFromItsStart(line = 3)
        }

        withClue("the empty line deleted as CodeMirror's delete line does") {
            asCodeMirrorDoes.textOf(3) shouldBe "line 4"
            asCodeMirrorDoes.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 3))
        }

        withClue("the empty line deleted from its start") {
            fromItsStart.textOf(3) shouldBe "line 4"
            fromItsStart.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 3))
        }
    }

    "a deletion from inside one line to inside a later one takes a line in between to the joined line" {
        val editor = BookmarkEditor(lines = 14).apply {
            set(digit = 3, line = 10)
            // From column 2 of line 8 to column 2 of line 11
            delete(from = lineStart(8) + 2, to = lineStart(11) + 2)
        }

        withClue("joined into line 8") {
            editor.textOf(8) shouldBe "line 11"
            editor.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 8))
        }
    }

    "two bookmarks that land on one line keep one: the one whose line is still there, or else the one higher up" {
        val fromItsStart = BookmarkEditor(lines = 40).apply {
            set(digit = 3, line = 10)
            set(digit = 4, line = 11)
            deleteLineFromItsStart(line = 10)
        }
        val asCodeMirrorDoes = BookmarkEditor(lines = 40).apply {
            set(digit = 3, line = 10)
            set(digit = 4, line = 11)
            deleteLineAsCodeMirrorDoes(line = 10)
        }
        val bothDeleted = BookmarkEditor(lines = 40).apply {
            // The higher digit higher up, so neither the digits nor the order they were set in can decide
            set(digit = 4, line = 10)
            set(digit = 3, line = 11)
            delete(from = lineStart(10), to = lineStart(12))
        }
        val joined = BookmarkEditor(lines = 40).apply {
            set(digit = 3, line = 10)
            set(digit = 4, line = 11)
            // Backspace at the start of line 11: only the line break goes, both lines' text stays
            delete(from = lineStart(11) - 1, to = lineStart(11))
        }

        withClue("a deleted line's bookmark meets the next line's") {
            fromItsStart.lines() shouldBe listOf(Bookmarks.Line(digit = 4, line = 10))
        }

        withClue("the same, deleted as CodeMirror's delete line does") {
            asCodeMirrorDoes.lines() shouldBe listOf(Bookmarks.Line(digit = 4, line = 10))
        }

        withClue("both lines deleted") {
            bothDeleted.lines() shouldBe listOf(Bookmarks.Line(digit = 4, line = 10))
        }

        withClue("two lines joined") {
            joined.textOf(10) shouldBe "line 10line 11"
            joined.lines() shouldBe listOf(Bookmarks.Line(digit = 3, line = 10))
        }
    }

    "a moved line takes its bookmark along, and so does the line it jumped over" {
        val editor = BookmarkEditor(lines = 40)

        editor.set(digit = 3, line = 9)
        editor.set(digit = 4, line = 10)
        editor.set(digit = 5, line = 12)

        val cursorOnTen = listOf(Bookmarks.Span(from = editor.lineStart(10), to = editor.lineStart(10)))
        val up = Bookmarks.lineMoves(
            blocks = Bookmarks.lineBlocks(selection = cursorOnTen, doc = editor.doc),
            down = false,
            lineCount = 40,
        )

        editor.moveLines(up)

        withClue("line 10 moved up over line 9") {
            up shouldBe mapOf(10 to 9, 9 to 10)
            editor.textOf(9) shouldBe "line 10"
            editor.lines() shouldBe listOf(
                Bookmarks.Line(digit = 3, line = 10),
                Bookmarks.Line(digit = 4, line = 9),
                Bookmarks.Line(digit = 5, line = 12),
            )
        }
    }

    "the line blocks and moves of a move line: a range to a line's start, ranges that touch, the ends of the song" {
        val doc = FakeText((1..40).joinToString("\n") { "line $it" })

        fun span(fromLine: Int, toLine: Int) =
            Bookmarks.Span(from = doc.lineStart(fromLine), to = doc.lineStart(toLine))

        withClue("a selection to the start of a line leaves that line out") {
            Bookmarks.lineBlocks(selection = listOf(span(fromLine = 10, toLine = 12)), doc = doc) shouldBe
                    listOf(10..11)
        }

        withClue("cursors on two lines next to each other are one block") {
            val cursors = listOf(span(fromLine = 10, toLine = 10), span(fromLine = 11, toLine = 11))

            Bookmarks.lineBlocks(selection = cursors, doc = doc) shouldBe listOf(10..11)
        }

        withClue("a block moved down") {
            Bookmarks.lineMoves(blocks = listOf(10..11), down = true, lineCount = 40) shouldBe
                    mapOf(10 to 11, 11 to 12, 12 to 10)
        }

        withClue("a block at the end it moves towards stays") {
            Bookmarks.lineMoves(blocks = listOf(1..2), down = false, lineCount = 40) shouldBe emptyMap()
            Bookmarks.lineMoves(blocks = listOf(39..40), down = true, lineCount = 40) shouldBe emptyMap()
        }
    }

    "a load drops a line past the end, a digit outside 0 to 9, and a second bookmark on a digit or a line" {
        val editor = BookmarkEditor(lines = 40, indent = "  ")

        val loaded = editor.load(
            listOf(
                Bookmarks.Line(digit = 1, line = 5),
                Bookmarks.Line(digit = 2, line = 41),
                Bookmarks.Line(digit = 3, line = 0),
                Bookmarks.Line(digit = 10, line = 7),
                Bookmarks.Line(digit = -1, line = 8),
                Bookmarks.Line(digit = 1, line = 9),
                Bookmarks.Line(digit = 4, line = 5),
                Bookmarks.Line(digit = 0, line = 40),
            )
        )

        withClue("what is kept, by digit, each on the first character of its line's text") {
            loaded.placed shouldBe listOf(
                Bookmarks.Placed(digit = 0, pos = editor.lineStart(40) + 2),
                Bookmarks.Placed(digit = 1, pos = editor.lineStart(5) + 2),
            )
        }
    }
})

/** A plain-text document as the rule reads it. */
private class FakeText(val text: String) : Bookmarks.Doc {

    private val lines = text.split("\n")

    override val lineCount: Int get() = lines.size

    override fun lineOf(pos: Int): Int = text.substring(0, pos).split("\n").size

    override fun lineStart(line: Int): Int {
        checkLine(line)

        return lines.take(line - 1).sumOf { it.length + 1 }
    }

    override fun lineText(line: Int): String {
        checkLine(line)

        return lines[line - 1]
    }

    /** As CodeMirror's `doc.line`, which throws for a line the document does not have. */
    private fun checkLine(line: Int) {
        check(line in 1..lineCount) { "no line $line in $lineCount lines" }
    }
}

/** The editor played without CodeMirror: a document and its bookmarks. */
private class BookmarkEditor(text: String) {

    constructor(lines: Int, indent: String = "") : this((1..lines).joinToString("\n") { "${indent}line $it" })

    var doc = FakeText(text)
    var bookmarks = Bookmarks()

    fun lineStart(line: Int): Int = doc.lineStart(line)

    fun textOf(line: Int): String = doc.lineText(line)

    /** Ctrl+Shift+digit with the cursor [column] characters into [line]; a change is kept, a question returned. */
    fun set(digit: Int, line: Int, column: Int = 0): Bookmarks.SetResult {
        val result = bookmarks.set(digit = digit, at = lineStart(line) + column, doc = doc)

        if (result is Bookmarks.SetResult.Changed) {
            bookmarks = result.bookmarks
        }

        return result
    }

    /** The move after the user said yes to the question. */
    fun moveAfterYes(digit: Int, line: Int) {
        bookmarks = bookmarks.put(digit = digit, at = lineStart(line), doc = doc)
    }

    /** Replaces the text from [from] to [to] with [inserted], in one change. */
    fun replace(from: Int, to: Int, inserted: String) {
        val old = doc

        doc = FakeText(old.text.substring(0, from) + inserted + old.text.substring(to))

        bookmarks = bookmarks.mapped(
            mapPos = { p ->
                when {
                    p < from -> p
                    // The start of a replaced range stays at its start, as CodeMirror maps it
                    p == from && to > from -> from
                    p < to -> from + inserted.length
                    else -> p - (to - from) + inserted.length
                }
            },
            deletions = if (to > from) listOf(Bookmarks.Span(from = from, to = to)) else emptyList(),
            oldDoc = old,
            newDoc = doc,
        )
    }

    fun insert(pos: Int, inserted: String) {
        replace(from = pos, to = pos, inserted = inserted)
    }

    fun delete(from: Int, to: Int) {
        replace(from = from, to = to, inserted = "")
    }

    /** Deletes [line] from its start to the next line's start, as selecting it and pressing Delete does. */
    fun deleteLineFromItsStart(line: Int) {
        delete(from = lineStart(line), to = lineStart(line + 1))
    }

    /** Deletes [line] from the end of the line above to its own end, as CodeMirror's delete line does. */
    fun deleteLineAsCodeMirrorDoes(line: Int) {
        delete(from = lineStart(line) - 1, to = lineStart(line) + textOf(line).length)
    }

    /** Moves the lines as [moves] says (old line to new line), text and bookmarks. */
    fun moveLines(moves: Map<Int, Int>) {
        val old = doc
        val texts = (1..old.lineCount).associateBy({ moves[it] ?: it }, { old.lineText(it) })

        doc = FakeText((1..old.lineCount).joinToString("\n") { texts.getValue(it) })
        bookmarks = bookmarks.moved(moves = moves, oldDoc = old, newDoc = doc)
    }

    fun load(lines: List<Bookmarks.Line>): Bookmarks = Bookmarks.load(lines = lines, doc = doc)

    /** The bookmarks as the host keeps them. */
    fun lines(): List<Bookmarks.Line> = bookmarks.lines(doc)
}
