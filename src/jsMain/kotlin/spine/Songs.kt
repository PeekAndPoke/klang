/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.AppVersion
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.state.SongKept
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What the browser keeps of the songs (KLANG-00422): one document per song, the user's, opened by the song's page
 * for as long as it shows ([open]).
 *
 * A built-in song's document has the app's version in its key, so each release keeps its own: a new release starts
 * the song fresh because nothing is kept under its key yet, and no release ever reads, changes or forgets what
 * another one kept. The user's own song has no version in its key and stays whatever the release.
 *
 * A song's document is read when its page opens it and does not follow another tab: an editor is not changed under
 * the writer's hands, and what this page writes is merged field by field, so it leaves alone what another tab kept
 * of the same song. Nothing is held between two visits, so no list of songs grows here; the storage page forgets
 * the songs by their keys.
 */
class Songs(private val stores: SectionStores) {

    /** What a song says itself where the user changed nothing: a built-in song's own, or the new song's. */
    data class Original(val code: String, val title: String, val rpm: Double)

    /** One song as it is kept and shown: what the user changed, else the [original]. */
    class Kept internal constructor(
        private val section: Stored<SongKept>,
        private val original: Original,
    ) {
        val code: String get() = section().code ?: original.code

        val title: String get() = section().title ?: original.title

        val rpm: Double get() = section().rpm ?: original.rpm

        /** The editor's bookmarks, digit to line number (counted from 1). */
        val bookmarks: Map<Int, Int> get() = section().bookmarks

        /**
         * Keeps [code]; nothing where it is what the song shows already. Changed back to the song's own, it is kept
         * as never changed (null), so the song is followed again when it changes.
         */
        fun keepCode(code: String) {
            if (code != this.code) {
                section.update { it.copy(code = code.takeIf { own -> own != original.code }) }
            }
        }

        fun keepTitle(title: String) {
            if (title != this.title) {
                section.update { it.copy(title = title.takeIf { own -> own != original.title }) }
            }
        }

        fun keepRpm(rpm: Double) {
            if (rpm != this.rpm) {
                section.update { it.copy(rpm = rpm.takeIf { own -> own != original.rpm }) }
            }
        }

        fun keepBookmarks(bookmarks: Map<Int, Int>) {
            if (bookmarks != this.bookmarks) {
                section.update { it.copy(bookmarks = bookmarks) }
            }
        }
    }

    companion object {
        private val beforeJson = Json { ignoreUnknownKeys = true }

        /** The value Ultra's stream kept under [key], decoded; null when there is none or it cannot be read. */
        private fun <T> before(store: KeyValueStore, key: String, serializer: KSerializer<T>): T? = try {
            store.read(key)?.let { beforeJson.decodeFromString(serializer, it) }
        } catch (_: Exception) {
            null
        }

        /** A version's numbers between its dots (`0.8.12.2`); null where it is no such version (`n/a`). */
        private fun numbers(version: String): List<Int>? {
            return version.split(".").map { it.toIntOrNull() ?: return null }
        }

        /**
         * Orders two versions, the later one first: by their numbers, part by part, a missing part as 0. One that
         * has no numbers comes after every one that has.
         */
        private fun laterFirst(a: String, b: String): Int {
            val one = numbers(a)
            val other = numbers(b)

            if (one == null || other == null) {
                return (if (one == null) 1 else 0) - (if (other == null) 1 else 0)
            }

            for (i in 0 until maxOf(one.size, other.size)) {
                val order = other.getOrElse(i) { 0 }.compareTo(one.getOrElse(i) { 0 })

                if (order != 0) {
                    return order
                }
            }

            return 0
        }

        /**
         * Every version that kept something of the song [songId] under the old keys, the latest first. None where
         * the store cannot list its keys. Only what a version looks like counts (numbers between dots, or `n/a`):
         * the key of a song whose id ends in this one's (`another-song` for `song`) is not this song's.
         */
        private fun versionsBefore(store: KeyValueStore, songId: String): List<String> {
            val versions = store.keys().mapNotNull { key ->
                listOf("code", "title", "rpm", "bookmarks").firstNotNullOfOrNull { part ->
                    key.takeIf { it.startsWith("song-") && it.endsWith("-$songId-$part") }
                        ?.removePrefix("song-")?.removeSuffix("-$songId-$part")
                }
            }.filter { numbers(it) != null || it == AppVersion.N_A }.toSet()

            return versions.sortedWith { a, b -> laterFirst(a = a, b = b) }
        }

        /**
         * The song as it was kept until KLANG-00422: a key each for its code, title, rpm and bookmarks, with the app's
         * [version] in the key. A value that is the song's own ([original]) was no change of the user's (the old page
         * wrote the title and the tempo on every visit) and is not kept as one.
         *
         * A built-in song's keys of this version are read; those of another version belong to that release and stay.
         * The user's own song ([builtIn] false) went out of sight with every version, and stays from now on, so this
         * is the one moment to bring it along: where this version's keys hold nothing of the user's, it is taken from
         * the latest version whose keys do. That version's keys stay, as a copy was taken: a deploy taken back to it
         * still finds its song. Scaffolding: it goes when no browser holds the old keys.
         */
        private fun songBefore(
            store: KeyValueStore,
            songId: String,
            version: String,
            builtIn: Boolean,
            original: Original,
        ): DocumentStorage.Legacy<SongKept>? {
            fun read(from: String, keysGo: Boolean): DocumentStorage.Legacy<SongKept>? {
                val codeKey = StorageKeys.songCode(version = from, songId = songId)
                val titleKey = StorageKeys.songTitle(version = from, songId = songId)
                val rpmKey = StorageKeys.songRpm(version = from, songId = songId)
                val bookmarksKey = StorageKeys.songBookmarks(version = from, songId = songId)

                val code = before(store = store, key = codeKey, serializer = String.serializer())
                val title = before(store = store, key = titleKey, serializer = String.serializer())
                val rpm = before(store = store, key = rpmKey, serializer = Double.serializer())
                val bookmarks = before(store = store, key = bookmarksKey, serializer = MapSerializer(Int.serializer(), Int.serializer()))

                if (code == null && title == null && rpm == null && bookmarks == null) {
                    return null
                }

                return DocumentStorage.Legacy(
                    value = SongKept(
                        code = code.takeIf { it != original.code },
                        title = title.takeIf { it != original.title },
                        rpm = rpm.takeIf { it != original.rpm },
                        bookmarks = bookmarks.orEmpty(),
                    ),
                    // Only what was read goes: a text that could not be read stays where it is
                    keys = listOfNotNull(
                        codeKey.takeIf { code != null },
                        titleKey.takeIf { title != null },
                        rpmKey.takeIf { rpm != null },
                        bookmarksKey.takeIf { bookmarks != null },
                    ).filter { keysGo },
                )
            }

            val current = read(from = version, keysGo = true)

            if (builtIn || (current != null && current.value != SongKept())) {
                return current
            }

            return versionsBefore(store = store, songId = songId)
                .filter { it != version }
                .firstNotNullOfOrNull { earlier -> read(from = earlier, keysGo = false)?.takeIf { it.value != SongKept() } }
                ?: current
        }
    }

    /**
     * The song [songId] as it is kept, for its page. A [builtIn] song is kept under the app's [version] (`n/a` while
     * it is not known), so each release has its own; the user's own song is kept under its id alone.
     */
    fun open(songId: String, version: String, builtIn: Boolean, original: Original): Kept {
        val section = Stored(
            key = StorageKeys.song(songId = songId, release = version.takeIf { builtIn }),
            version = 1,
            serializer = SongKept.serializer(),
            default = SongKept(),
            store = stores.user,
            legacy = { store ->
                songBefore(store = store, songId = songId, version = version, builtIn = builtIn, original = original)
            },
            follows = false,
        )

        return Kept(section = section, original = original)
    }
}
