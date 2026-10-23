/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The rule of the edit places behind Ctrl+Shift+Backspace (KLANG-00322): one row per point of the ticket's tests, and
 * three from the reviews (an edit at several places, a cursor one line off a place, a change with no range).
 *
 * The fixture plays the editor without CodeMirror: a plain-text document, typing that maps the places the way
 * CodeMirror's `mapPos` does (a place at the insertion point stays before it), and a cursor that goes where the last
 * edit ended or where the last step back went.
 */
class EditPlacesSpec : StringSpec({

    /** Three places, on lines 5, 20 and 35 of a 40-line song, typed in that order; the cursor is at the last. */
    fun threePlaces(): FakeEditor = FakeEditor(lines = 40).apply {
        typeAtEndOf(line = 5, text = "a")
        typeAtEndOf(line = 20, text = "b")
        typeAtEndOf(line = 35, text = "c")
    }

    "a burst on one line is one place" {
        val editor = FakeEditor(lines = 40)

        editor.typeAtEndOf(line = 10, text = "h")

        listOf("e", "l", "l", "o").forEach { editor.type(pos = editor.cursor, text = it) }

        withClue("a burst on one line") {
            editor.doc.textOf(10) shouldBe "line 10hello"
            editor.places.places shouldBe listOf(editor.cursor)
        }
    }

    "an edit three lines away is a new place" {
        val editor = FakeEditor(lines = 40)

        editor.typeAtEndOf(line = 10, text = "a")
        // Two lines away is still the same place; it moves to the new edit
        editor.typeAtEndOf(line = 12, text = "b")

        withClue("three lines away, after an edit two lines below") {
            editor.placeLines() shouldBe listOf(12)
        }

        editor.typeAtEndOf(line = 15, text = "c")

        withClue("three lines away, below the newest place") {
            editor.placeLines() shouldBe listOf(12, 15)
        }

        editor.typeAtEndOf(line = 12, text = "d")

        withClue("three lines away, above the newest place") {
            editor.placeLines() shouldBe listOf(12, 15, 12)
        }
    }

    "text inserted above moves a place with its line" {
        val editor = FakeEditor(lines = 40)

        editor.typeAtEndOf(line = 20, text = "x")
        editor.type(pos = editor.doc.startOf(2), text = "a new line\n")

        withClue("text inserted above") {
            editor.doc.textOf(21) shouldBe "line 20x"
            editor.places.places.first() shouldBe editor.doc.startOf(21) + "line 20x".length
        }
    }

    "an edit at several places does not swallow the newest place between them" {
        val editor = FakeEditor(lines = 40)

        editor.typeAtEndOf(line = 20, text = "a")
        // Two cursors, far above and far below the place on line 20
        editor.typeAtEndOfEach(lines = listOf(3, 40), text = "b")

        withClue("several places around the newest place") {
            editor.placeLines() shouldBe listOf(20, 40)
        }

        // Two cursors again, the second one a line from the newest place
        editor.typeAtEndOfEach(lines = listOf(5, 39), text = "c")

        withClue("several places, one of them near the newest place") {
            editor.placeLines() shouldBe listOf(20, 39)
        }

        // A place near the top, then two cursors with the near one first: ranges come in document order
        editor.typeAtEndOf(line = 3, text = "d")
        editor.typeAtEndOfEach(lines = listOf(4, 30), text = "e")

        withClue("several places, the first of them near the newest place") {
            editor.placeLines() shouldBe listOf(20, 39, 30)
        }
    }

    "going back skips the cursor's own place and walks older" {
        val atNewest = threePlaces()

        withClue("going back from the cursor's own place") {
            atNewest.back() shouldBe 20
        }

        val elsewhere = threePlaces()

        elsewhere.moveCursorTo(line = 12)

        withClue("going back from elsewhere, one place older per step") {
            elsewhere.back() shouldBe 35
            elsewhere.back() shouldBe 20
            elsewhere.back() shouldBe 5
            elsewhere.back() shouldBe null
        }
    }

    "going back skips a place one line from the cursor too" {
        val editor = threePlaces()

        editor.moveCursorTo(line = 34)

        withClue("one line off the newest place") {
            editor.back() shouldBe 20
        }
    }

    "a change with no range only maps the places and ends the walk" {
        val editor = threePlaces()

        editor.back()

        val before = editor.places.places
        val after = editor.places.changed(mapPos = { it + 1 }, ranges = emptyList(), lineOf = editor.doc::lineOf)

        withClue("a change with no range") {
            after.places shouldBe before.map { it + 1 }
            after.walkIndex shouldBe null
        }
    }

    "a new edit ends the walk" {
        val editor = threePlaces()

        editor.back()
        editor.back()
        // The walk stands at the oldest place; an edit there starts over from the newest
        editor.type(pos = editor.cursor, text = "d")

        withClue("a new edit ends the walk") {
            editor.placeLines() shouldBe listOf(5, 20, 35, 5)
            editor.back() shouldBe 35
        }
    }

    "a cursor move ends the walk" {
        val editor = threePlaces()

        editor.moveCursorTo(line = 12)
        editor.back()
        editor.back()
        // The walk stands at line 20; the user moves the cursor back to where it was
        editor.moveCursorTo(line = 12)

        withClue("a cursor move ends the walk") {
            editor.places.walkIndex shouldBe null
            editor.back() shouldBe 35
        }
    }

    "the cap drops the oldest" {
        val editor = FakeEditor(lines = 100)

        // 31 places, each three lines below the one before
        (1..31).forEach { editor.typeAtEndOf(line = it * 3, text = "x") }

        withClue("the cap drops the oldest") {
            editor.places.places.size shouldBe 30
            editor.placeLines().first() shouldBe 6
            editor.placeLines().last() shouldBe 93
        }
    }
})

/** A document as plain text, with the lookups the rule and the fixture need. Lines count from 1. */
private class FakeDoc(val text: String) {

    fun lineOf(pos: Int): Int = text.substring(0, pos).split("\n").size

    fun startOf(line: Int): Int = text.split("\n").take(line - 1).sumOf { it.length + 1 }

    fun textOf(line: Int): String = text.split("\n")[line - 1]
}

/** The editor played without CodeMirror: a document, its places, and the cursor. */
private class FakeEditor(lines: Int) {

    var doc = FakeDoc((1..lines).joinToString("\n") { "line $it" })
    var places = EditPlaces()
    var cursor = 0

    /** The user types [text] at [pos]: the places map forward, the edit is recorded, the cursor ends after it. */
    fun type(pos: Int, text: String) {
        typeAtEach(positions = listOf(pos), text = text)
    }

    fun typeAtEndOf(line: Int, text: String) {
        type(pos = endOf(line), text = text)
    }

    /** Types [text] at the end of each of [lines] in one change, as several cursors do. */
    fun typeAtEndOfEach(lines: List<Int>, text: String) {
        typeAtEach(positions = lines.map { endOf(it) }, text = text)
    }

    /** One change that types [text] at each of [positions] (offsets before the change). */
    private fun typeAtEach(positions: List<Int>, text: String) {
        val sorted = positions.sorted()
        // Typed from the last position back, so the earlier positions still hold
        val after = FakeDoc(
            sorted.reversed().fold(doc.text) { acc, pos -> acc.substring(0, pos) + text + acc.substring(pos) }
        )
        // Each range in the new document, moved by the text typed before it
        val ranges = sorted.mapIndexed { index, pos ->
            EditPlaces.ChangedRange(from = pos + index * text.length, to = pos + (index + 1) * text.length)
        }

        places = places.changed(
            // A place at a typing position stays before the text, as CodeMirror maps it by default
            mapPos = { place -> place + sorted.count { it < place } * text.length },
            ranges = ranges,
            lineOf = after::lineOf,
        )
        doc = after
        cursor = ranges.last().to
    }

    private fun endOf(line: Int): Int = doc.startOf(line) + doc.textOf(line).length

    /** The user moves the cursor to the start of [line], which ends a walk, as the editor's field does. */
    fun moveCursorTo(line: Int) {
        cursor = doc.startOf(line)
        places = places.cursorMoved()
    }

    /** One press of the key: the line the cursor went to, or null when it stayed. */
    fun back(): Int? {
        val step = places.back(cursor = cursor, lineOf = doc::lineOf) ?: return null

        places = places.walkedTo(step.walkIndex)
        cursor = step.target

        return doc.lineOf(cursor)
    }

    /** The line of each place, oldest first. */
    fun placeLines(): List<Int> = places.places.map { doc.lineOf(it) }
}
