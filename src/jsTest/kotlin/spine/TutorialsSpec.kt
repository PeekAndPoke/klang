/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/** The lessons done as a stored section (KLANG-00422): a field per lesson, so two writers both keep theirs. */
class TutorialsSpec : StringSpec({

    fun stores(user: MemoryKeyValueStore) =
        SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore())

    "a lesson marked done is done at once and after a reload; a mark taken back is kept as taken back" {
        val user = MemoryKeyValueStore()
        val tutorials = Tutorials(stores = stores(user))
        val seen = mutableListOf<Set<String>>()
        val watching = tutorials.done.subscribeToStream { seen.add(it) }

        try {
            tutorials.setCompleted("never-done", false)
            withClue("a mark never set, taken back: nothing is written") { user.all shouldBe emptyMap() }

            tutorials.setCompleted("a", true)
            tutorials.setCompleted("b", true)
            tutorials.setCompleted("a", false)

            tutorials.isCompleted("a") shouldBe false
            tutorials.isCompleted("b") shouldBe true
            seen.takeLast(3) shouldBe listOf(setOf("a"), setOf("a", "b"), setOf("b"))

            user.read(StorageKeys.LESSONS_DONE) shouldBe """{"v":1,"data":{"a":false,"b":true}}"""
            Tutorials(stores = stores(user)).done() shouldBe setOf("b")
        } finally {
            watching()
        }
    }

    "two tabs that each mark a lesson both keep theirs, and each shows the other's at once" {
        val user = MemoryKeyValueStore()
        val one = Tutorials(stores = stores(user))
        val other = Tutorials(stores = stores(user))

        one.setCompleted("a", true)
        // The other tab did not hear of it (a write between the event and its arrival): it writes its own mark
        other.setCompleted("b", true)

        user.read(StorageKeys.LESSONS_DONE) shouldBe """{"v":1,"data":{"a":true,"b":true}}"""

        user.changeFromElsewhere(key = StorageKeys.LESSONS_DONE, text = """{"v":1,"data":{"a":true,"b":true,"c":true}}""")

        one.done() shouldBe setOf("a", "b", "c")
        other.done() shouldBe setOf("a", "b", "c")
    }

    "the lessons done before the section existed are read once, and their old keys go" {
        val first = allTutorials[0].slug
        val second = allTutorials[1].slug
        val third = allTutorials[2].slug
        val user = MemoryKeyValueStore(
            initial = mapOf(
                StorageKeys.tutorialCompleted(first) to "true",
                StorageKeys.tutorialCompleted(third) to "true",
                StorageKeys.tutorialCompleted(second) to "false",
            )
        )

        Tutorials(stores = stores(user)).done() shouldBe setOf(first, third)

        withClue("the old keys of the lessons done") {
            user.read(StorageKeys.tutorialCompleted(first)) shouldBe null
            user.read(StorageKeys.tutorialCompleted(third)) shouldBe null
        }
        Tutorials(stores = stores(user)).done() shouldBe setOf(first, third)
    }
})
