/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/**
 * What is kept of a song (KLANG-00422): one document per song with what the user changed, a built-in song's under
 * the release that kept it, the user's own song under its id alone, and what a browser kept before read once.
 */
class SongsSpec : StringSpec({

    val original = Songs.Original(code = "sound(\"bd\")", title = "Der Schmetterling", rpm = 30.0)

    /** The built-in song's key under the release 1.0, and the user's own song's. */
    val key = StorageKeys.song(songId = "song", release = "1.0")
    val ownKey = StorageKeys.song(songId = "song")

    fun songs(user: MemoryKeyValueStore) =
        Songs(stores = SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore()))

    fun open(user: MemoryKeyValueStore, version: String = "1.0", builtIn: Boolean = true, from: Songs.Original = original) =
        songs(user).open(songId = "song", version = version, builtIn = builtIn, original = from)

    "a song never changed shows its own code, title and tempo, and keeping what it shows keeps nothing" {
        val user = MemoryKeyValueStore()
        val song = open(user)

        song.code shouldBe "sound(\"bd\")"
        song.title shouldBe "Der Schmetterling"
        song.rpm shouldBe 30.0
        song.bookmarks shouldBe emptyMap()

        // As the page does on every visit and every play of an unchanged song
        song.keepCode("sound(\"bd\")")
        song.keepTitle("Der Schmetterling")
        song.keepRpm(30.0)
        song.keepBookmarks(emptyMap())

        user.all shouldBe emptyMap()
    }

    "what the user changes is kept as one document under the release, and read back" {
        val user = MemoryKeyValueStore()
        val song = open(user)

        song.keepCode("sound(\"hh\")")
        user.all shouldBe mapOf("klang-song-1.0-song" to """{"v":1,"data":{"code":"sound(\"hh\")"}}""")

        song.keepRpm(42.5)
        song.keepTitle("Mine")
        song.keepBookmarks(mapOf(1 to 12))

        song.code shouldBe "sound(\"hh\")"
        song.rpm shouldBe 42.5
        user.read(key) shouldBe """{"v":1,"data":{"code":"sound(\"hh\")","rpm":42.5,"title":"Mine","bookmarks":{"1":12}}}"""

        withClue("the page opened again, the same release") {
            val again = open(user)

            again.code shouldBe "sound(\"hh\")"
            again.title shouldBe "Mine"
            again.rpm shouldBe 42.5
            again.bookmarks shouldBe mapOf(1 to 12)
        }

        withClue("back to the song's own code, title and tempo: kept as never changed, so the song is followed again") {
            song.keepCode("sound(\"bd\")")
            song.keepTitle("Der Schmetterling")
            song.keepRpm(30.0)

            song.code shouldBe "sound(\"bd\")"
            user.read(key) shouldBe """{"v":1,"data":{"code":null,"rpm":null,"title":null,"bookmarks":{"1":12}}}"""

            val changed = open(user, from = original.copy(code = "sound(\"hh\")", title = "Der Falter", rpm = 40.0))

            changed.code shouldBe "sound(\"hh\")"
            changed.title shouldBe "Der Falter"
            changed.rpm shouldBe 40.0
        }
    }

    "a built-in song starts fresh with every release, and no release touches what another one kept" {
        val user = MemoryKeyValueStore()
        val older = open(user, version = "1.0")

        older.keepCode("mine in 1.0")
        older.keepBookmarks(mapOf(1 to 2))

        val kept = """{"v":1,"data":{"code":"mine in 1.0","bookmarks":{"1":2}}}"""
        val newer = open(user, version = "1.1")

        withClue("the new release: the song's own, nothing forgotten") {
            newer.code shouldBe "sound(\"bd\")"
            newer.bookmarks shouldBe emptyMap()
            user.all shouldBe mapOf(key to kept)
        }

        newer.keepCode("mine in 1.1")

        withClue("a tab still on the older build goes on changing its own") {
            older.keepRpm(60.5)

            user.all shouldBe mapOf(
                key to """{"v":1,"data":{"code":"mine in 1.0","bookmarks":{"1":2},"rpm":60.5}}""",
                StorageKeys.song(songId = "song", release = "1.1") to """{"v":1,"data":{"code":"mine in 1.1"}}""",
            )
        }

        withClue("so the newer release finds its song as it left it, and a deploy taken back finds the older one's") {
            open(user, version = "1.1").let {
                it.code shouldBe "mine in 1.1"
                it.rpm shouldBe 30.0
            }
            open(user, version = "1.0").let {
                it.code shouldBe "mine in 1.0"
                it.rpm shouldBe 60.5
            }
        }

        withClue("while the app's version is not known: a song of its own, as under the old keys") {
            val unknown = open(user, version = "n/a")

            unknown.code shouldBe "sound(\"bd\")"
            unknown.keepCode("mine meanwhile")
            user.read("klang-song-n/a-song") shouldBe """{"v":1,"data":{"code":"mine meanwhile"}}"""
        }
    }

    "the user's own song is kept under its id alone and stays whatever the release" {
        val user = MemoryKeyValueStore()

        open(user, version = "1.0", builtIn = false).keepCode("mine")
        user.all shouldBe mapOf("klang-song-song" to """{"v":1,"data":{"code":"mine"}}""")

        val next = open(user, version = "1.1", builtIn = false)

        next.code shouldBe "mine"
        next.keepRpm(60.5)
        user.all shouldBe mapOf(ownKey to """{"v":1,"data":{"code":"mine","rpm":60.5}}""")

        withClue("also while the version is not known") { open(user, version = "n/a", builtIn = false).code shouldBe "mine" }
    }

    "what a browser kept before, a key each under this version, is read once, and those keys go" {
        val user = MemoryKeyValueStore(
            initial = mapOf(
                StorageKeys.songCode(version = "1.0", songId = "song") to "\"sound(\\\"hh\\\")\"",
                StorageKeys.songTitle(version = "1.0", songId = "song") to "\"Mine\"",
                StorageKeys.songRpm(version = "1.0", songId = "song") to "42.5",
                StorageKeys.songBookmarks(version = "1.0", songId = "song") to """{"1":12}""",
                StorageKeys.songCode(version = "0.9", songId = "song") to "\"older\"",
                StorageKeys.songCode(version = "1.0", songId = "other") to "\"another song\"",
            )
        )

        val song = open(user)

        song.code shouldBe "sound(\"hh\")"
        song.title shouldBe "Mine"
        song.rpm shouldBe 42.5
        song.bookmarks shouldBe mapOf(1 to 12)

        withClue("one document; an earlier version's key and another song's are left alone") {
            user.all shouldBe mapOf(
                key to """{"v":1,"data":{"code":"sound(\"hh\")","title":"Mine","rpm":42.5,"bookmarks":{"1":12}}}""",
                StorageKeys.songCode(version = "0.9", songId = "song") to "\"older\"",
                StorageKeys.songCode(version = "1.0", songId = "other") to "\"another song\"",
            )
        }

        withClue("a key that cannot be read stays, the others are read") {
            val odd = MemoryKeyValueStore(
                initial = mapOf(
                    StorageKeys.songCode(version = "1.0", songId = "song") to "not json",
                    StorageKeys.songRpm(version = "1.0", songId = "song") to "42.5",
                )
            )

            open(odd).rpm shouldBe 42.5
            odd.all shouldBe mapOf(
                StorageKeys.songCode(version = "1.0", songId = "song") to "not json",
                key to """{"v":1,"data":{"rpm":42.5}}""",
            )
        }

        withClue("kept before under the version not known: read into that version's document") {
            val unknown = MemoryKeyValueStore(initial = mapOf(StorageKeys.songRpm(version = "n/a", songId = "song") to "42.5"))

            open(unknown, version = "n/a").rpm shouldBe 42.5
            unknown.all shouldBe mapOf(StorageKeys.song(songId = "song", release = "n/a") to """{"v":1,"data":{"rpm":42.5}}""")
        }

        withClue("nothing kept before: no document is made up") {
            MemoryKeyValueStore().also { open(it) }.all shouldBe emptyMap()
        }
    }

    "what the old page kept on every visit, the song's own title and tempo, is no change of the user's" {
        val user = MemoryKeyValueStore(
            initial = mapOf(
                StorageKeys.songCode(version = "1.0", songId = "song") to "\"sound(\\\"bd\\\")\"",
                StorageKeys.songTitle(version = "1.0", songId = "song") to "\"Der Schmetterling\"",
                StorageKeys.songRpm(version = "1.0", songId = "song") to "30",
            )
        )

        open(user)

        withClue("the old keys go, nothing of the song's own is kept as changed") {
            user.all shouldBe mapOf(key to """{"v":1,"data":{}}""")
        }
        withClue("so a deploy that changes the built-in song reaches this browser") {
            open(user, from = original.copy(code = "sound(\"hh\")", rpm = 40.0)).let {
                it.code shouldBe "sound(\"hh\")"
                it.rpm shouldBe 40.0
            }
        }
    }

    "the user's own song kept only by an earlier version is brought along, from the latest; a built-in song's is not" {
        val old = mapOf(
            StorageKeys.songCode(version = "0.9", songId = "song") to "\"oldest\"",
            StorageKeys.songCode(version = "0.9.10", songId = "song") to "\"latest before\"",
            StorageKeys.songRpm(version = "0.9.10", songId = "song") to "42.5",
            StorageKeys.songCode(version = "0.9.9", songId = "song") to "\"in between\"",
            StorageKeys.songCode(version = "n/a", songId = "song") to "\"unknown\"",
            StorageKeys.songCode(version = "0.9.11", songId = "another-song") to "\"another song's\"",
        )

        withClue("the user's own song") {
            val user = MemoryKeyValueStore(initial = old)
            val song = open(user, version = "1.0", builtIn = false)

            song.code shouldBe "latest before"
            song.rpm shouldBe 42.5
            withClue("a copy: that version's keys stay, so a deploy taken back to it still finds its song") {
                user.all shouldBe old + mapOf(ownKey to """{"v":1,"data":{"code":"latest before","rpm":42.5}}""")
            }
        }

        withClue("this version's own keys win over an earlier version's") {
            val user = MemoryKeyValueStore(initial = old + mapOf(StorageKeys.songCode(version = "1.0", songId = "song") to "\"now\""))

            open(user, version = "1.0", builtIn = false).code shouldBe "now"
        }

        withClue("only the version not known kept it") {
            val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.songCode(version = "n/a", songId = "song") to "\"unknown\""))

            open(user, version = "1.0", builtIn = false).code shouldBe "unknown"
        }

        withClue("a song whose id ends in this one's is another song: its key is not read as this song's, and stays") {
            val other = mapOf(StorageKeys.songCode(version = "0.9.11", songId = "another-song") to "\"another song's\"")
            val user = MemoryKeyValueStore(initial = other)

            open(user, version = "1.0", builtIn = false).code shouldBe "sound(\"bd\")"
            user.all shouldBe other
        }

        withClue("a built-in song: an earlier version's keys are that release's and stay") {
            val user = MemoryKeyValueStore(initial = old)

            open(user, version = "1.0", builtIn = true).code shouldBe "sound(\"bd\")"
            user.all shouldBe old
        }
    }

    "a version that kept only what the old page wrote on every visit, or nothing readable, is passed over" {
        // The old page wrote the title and the tempo on every visit: this version and the latest before hold only those
        val visited = mapOf(
            StorageKeys.songTitle(version = "1.0", songId = "song") to "\"Der Schmetterling\"",
            StorageKeys.songRpm(version = "1.0", songId = "song") to "30",
            StorageKeys.songTitle(version = "0.9.10", songId = "song") to "\"Der Schmetterling\"",
            StorageKeys.songRpm(version = "0.9.10", songId = "song") to "30",
            StorageKeys.songCode(version = "0.9.9", songId = "song") to "not json",
            StorageKeys.songCode(version = "0.9.8", songId = "song") to "\"the user's\"",
            StorageKeys.songCode(version = "0.9.7", songId = "song") to "\"older still\"",
        )

        withClue("the code is found two versions further back") {
            val user = MemoryKeyValueStore(initial = visited)

            open(user, version = "1.0", builtIn = false).code shouldBe "the user's"
            user.all shouldBe visited + mapOf(ownKey to """{"v":1,"data":{"code":"the user's"}}""")
        }

        withClue("bookmarks alone are the user's too") {
            val user = MemoryKeyValueStore(
                initial = mapOf(
                    StorageKeys.songRpm(version = "1.0", songId = "song") to "30",
                    StorageKeys.songBookmarks(version = "0.9.10", songId = "song") to """{"1":12}""",
                    StorageKeys.songCode(version = "0.9.9", songId = "song") to "\"earlier\"",
                )
            )

            open(user, version = "1.0", builtIn = false).let {
                it.bookmarks shouldBe mapOf(1 to 12)
                it.code shouldBe "sound(\"bd\")"
            }
        }

        withClue("nothing of the user's anywhere: this version's keys go, no change is kept") {
            val user = MemoryKeyValueStore(
                initial = mapOf(
                    StorageKeys.songRpm(version = "1.0", songId = "song") to "30",
                    StorageKeys.songRpm(version = "0.9.10", songId = "song") to "30",
                )
            )

            open(user, version = "1.0", builtIn = false).rpm shouldBe 30.0
            user.all shouldBe mapOf(
                StorageKeys.songRpm(version = "0.9.10", songId = "song") to "30",
                ownKey to """{"v":1,"data":{}}""",
            )
        }
    }

    "a page open in another tab does not follow, and what it keeps leaves the other tab's fields alone" {
        val user = MemoryKeyValueStore()
        val song = open(user)

        song.keepCode("mine")
        user.changeFromElsewhere(key = key, text = """{"v":1,"data":{"code":"theirs","title":"Theirs"}}""")

        withClue("the editor here is not changed under the writer's hands") { song.code shouldBe "mine" }

        song.keepRpm(60.5)
        withClue("the tempo written, the other tab's code and title left") {
            user.read(key) shouldBe """{"v":1,"data":{"code":"theirs","title":"Theirs","rpm":60.5}}"""
        }

        withClue("a page opened afterwards reads what is kept now") { open(user).code shouldBe "theirs" }
    }

    "the Spine opens songs on its own storage" {
        val user = MemoryKeyValueStore()
        val spine = Spine(stores = SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore()))

        spine.songs.open(songId = "song", version = "1.0", builtIn = true, original = original).keepCode("mine")

        user.read(key) shouldBe """{"v":1,"data":{"code":"mine"}}"""
    }
})
