/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.state.LastOpened.Entry
import io.peekandpoke.klang.state.LastOpened.Kind
import io.peekandpoke.klang.storage.StorageKeys

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

    "what was opened is kept as the user's, the newest first, and a visit after it finds it" {
        val stores = SectionStores.inMemory()
        val recents = Recents(stores = stores)

        recents.openedSong(id = "spec-a", title = "A", rpm = 40.0)
        recents.openedTutorial(slug = "spec-b", title = "B", track = null)
        recents.openedSong(id = "spec-a", title = "A again", rpm = 41.0)

        withClue("kept under the section's key, in the user's storage only") {
            (stores.user.read(StorageKeys.LAST_OPENED) != null) shouldBe true
            stores.machine.read(StorageKeys.LAST_OPENED) shouldBe null
            stores.tab.read(StorageKeys.LAST_OPENED) shouldBe null
        }

        val again = Recents(stores = stores).entries()

        again.map { it.id } shouldContainExactly listOf("spec-a", "spec-b")
        again.first().title shouldBe "A again"
        again.first().rpm shouldBe 41.0
        again shouldBe recents.entries()
    }

    "the list kept before the section existed is read once, and its old key goes" {
        val old = """
            [
              {"kind": "Tutorial", "id": "l", "title": "Lesson", "at": 3, "track": "t"},
              {"kind": "Song", "id": "s", "title": "Song", "at": 2, "rpm": 37.2}
            ]
        """.trimIndent()
        val stores = SectionStores.inMemory().also { it.user.write(key = StorageKeys.RECENTS, text = old) }

        Recents(stores = stores).entries() shouldContainExactly listOf(
            Entry(kind = Kind.Tutorial, id = "l", title = "Lesson", at = 3.0, track = "t"),
            Entry(kind = Kind.Song, id = "s", title = "Song", at = 2.0, rpm = 37.2),
        )

        withClue("the old key") { stores.user.read(StorageKeys.RECENTS) shouldBe null }
        withClue("found again under the new key") { Recents(stores = stores).entries().map { it.id } shouldContainExactly listOf("l", "s") }
    }

    "an old list that cannot be read gives an empty one, and stays where it is" {
        val old = """[{"kind": "Picture", "id": "x", "title": "X", "at": 1}]"""
        val stores = SectionStores.inMemory().also { it.user.write(key = StorageKeys.RECENTS, text = old) }

        Recents(stores = stores).entries() shouldBe emptyList()
        stores.user.read(StorageKeys.RECENTS) shouldBe old
    }
})
