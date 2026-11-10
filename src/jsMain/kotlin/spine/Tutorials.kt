/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.ultra.streams.Stream
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Which tutorials the user marked as done, a part of the Spine (KLANG-00370, stored as a section since KLANG-00422);
 * [done] as a stream, so the Learn column, the tiles and the tutorial page show a lesson marked done at once.
 *
 * The one section that is no data class: the document's fields are the lessons' slugs, each with `true` or
 * `false`. A write merges fields, so a lesson done on one computer and another done on a second both stay; a
 * list of slugs in one field would keep only the later writer's. A mark taken back is `false`, not left out: a
 * write cannot remove a field.
 */
class Tutorials(stores: SectionStores) {

    companion object {
        /**
         * The lessons done as they were kept until KLANG-00422, a key each, for the lessons the app has. Scaffolding:
         * it goes when no browser holds the old keys any more.
         */
        private fun before(store: KeyValueStore): DocumentStorage.Legacy<Map<String, Boolean>>? {
            val slugs = allTutorials.map { it.slug }.filter { store.read(StorageKeys.tutorialCompleted(it)) == "true" }

            if (slugs.isEmpty()) {
                return null
            }

            return DocumentStorage.Legacy(
                value = slugs.associateWith { true },
                keys = slugs.map { StorageKeys.tutorialCompleted(it) },
            )
        }
    }

    private val marks = Stored(
        key = StorageKeys.LESSONS_DONE,
        version = 1,
        serializer = MapSerializer(String.serializer(), Boolean.serializer()),
        default = emptyMap(),
        // The user's: every tab shows a lesson done at once
        store = stores.user,
        legacy = ::before,
    )

    internal val sections: List<Stored<*>> = listOf(marks)

    /** The slugs of the tutorials marked as done. */
    val done: Stream<Set<String>> = marks.part { all -> all.filterValues { it }.keys.toSet() }

    fun isCompleted(slug: String): Boolean = marks()[slug] == true

    fun setCompleted(slug: String, completed: Boolean) {
        marks.update { all ->
            when {
                // Never marked: taking the mark back changes nothing
                !completed && slug !in all -> all
                else -> all + (slug to completed)
            }
        }
    }
}
