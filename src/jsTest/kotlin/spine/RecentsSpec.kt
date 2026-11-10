/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.Recents.Entry
import io.peekandpoke.klang.spine.Recents.Kind
import io.peekandpoke.klang.spine.storage.RecentsStorage
import io.peekandpoke.klang.storage.StorageKeys
import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/** What was opened last (KLANG-00403, step 2): the list's order, its counts per kind, and how it is kept. */
class RecentsSpec : StringSpec({

    fun song(id: String, at: Double) = Entry(kind = Kind.Song, id = id, title = "Song $id", at = at, rpm = 30.0)

    fun lesson(id: String, at: Double) = Entry(kind = Kind.Tutorial, id = id, title = "Lesson $id", at = at, track = "t")

    val keep = mapOf(Kind.Song to 3, Kind.Tutorial to 2)

    "the entry opened goes first, an earlier one for the same thing goes, the rest keep their order" {
        val before = listOf(song("b", at = 30.0), song("a", at = 20.0), lesson("a", at = 10.0))

        val after = Recents.record(entries = before, entry = song("a", at = 40.0), keep = keep)

        after.map { "${it.kind}:${it.id}" } shouldContainExactly listOf("Song:a", "Song:b", "Tutorial:a")
        after.first().at shouldBe 40.0
    }

    "the order is the order of opening, not of the clock: an entry opened with a clock set back still goes first" {
        val before = listOf(song("b", at = 300.0), song("a", at = 200.0))

        Recents.record(entries = before, entry = song("c", at = 100.0), keep = keep).map { it.id } shouldContainExactly
            listOf("c", "b", "a")
    }

    "each kind is cut to its count, the oldest go; a kind without a count is not kept" {
        val songs = (1..3).map { song("$it", at = it.toDouble()) }.reversed()

        val withSong = Recents.record(entries = songs, entry = song("new", at = 10.0), keep = keep)

        withSong.map { it.id } shouldContainExactly listOf("new", "3", "2")

        val withLessons = listOf(lesson("x", at = 11.0), lesson("y", at = 12.0), lesson("z", at = 13.0))
            .fold(withSong) { list, entry -> Recents.record(entries = list, entry = entry, keep = keep) }

        withLessons.map { "${it.kind}:${it.id}" } shouldContainExactly
            listOf("Tutorial:z", "Tutorial:y", "Song:new", "Song:3", "Song:2")

        Recents.record(entries = withLessons, entry = song("q", at = 20.0), keep = mapOf(Kind.Tutorial to 2))
            .map { it.kind }.toSet() shouldBe setOf(Kind.Tutorial)
    }

    "the list goes into the browser and comes back as it was" {
        val list = listOf(lesson("a", at = 3.0), song("b", at = 2.0).copy(rpm = 37.2), song("c", at = 1.0).copy(rpm = null))

        RecentsStorage.decode(RecentsStorage.encode(list)) shouldBe list
    }

    "an entry that cannot be read is left out, not the list" {
        val json = """
            [
              {"kind": "Song", "id": "ok", "title": "Ok", "at": 1, "rpm": 30},
              {"kind": "Picture", "id": "x", "title": "X", "at": 1},
              {"kind": "Song", "title": "No id", "at": 1},
              {"kind": "Song", "id": "n", "title": "Not a time", "at": "soon"},
              {"kind": "Tutorial", "id": 5, "title": "A number for an id", "at": 1},
              "not an entry"
            ]
        """.trimIndent()

        RecentsStorage.decode(Json.parseToJsonElement(json)).map { it.id } shouldContainExactly listOf("ok")
        RecentsStorage.decode(Json.parseToJsonElement("""{"kind": "Song"}""")) shouldBe emptyList()
    }

    "a song opened is kept in the browser, the newest first" {
        val kept = localStorage.getItem(StorageKeys.RECENTS)

        try {
            val recents = Recents()

            recents.openedSong(id = "spec-a", title = "A", rpm = 40.0)
            recents.openedTutorial(slug = "spec-b", title = "B", track = null)
            recents.openedSong(id = "spec-a", title = "A again", rpm = 41.0)

            val stored = RecentsStorage.load()

            stored.take(2).map { it.id } shouldContainExactly listOf("spec-a", "spec-b")
            stored.first().title shouldBe "A again"
            stored.count { it.id == "spec-a" } shouldBe 1
        } finally {
            if (kept == null) {
                localStorage.removeItem(StorageKeys.RECENTS)
            } else {
                localStorage.setItem(StorageKeys.RECENTS, kept)
            }
        }
    }
})
