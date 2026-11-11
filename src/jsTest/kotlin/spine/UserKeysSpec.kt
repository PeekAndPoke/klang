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
 * The user's keys as a stored section (KLANG-00422): a field per action, an action put back to its defaults kept as
 * `null`, so two tabs both keep theirs and a reset in one reaches the other.
 */
class UserKeysSpec : StringSpec({

    fun stores(user: MemoryKeyValueStore) =
        SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore())

    "the keys saved are kept as one document and read back; an action no longer among them is kept as null" {
        val user = MemoryKeyValueStore()
        val store = UserKeys(stores = stores(user)).store

        withClue("nothing kept: no keys, no document") {
            store.load() shouldBe emptyMap()
            user.all shouldBe emptyMap()
        }

        store.save(mapOf("klang.stop" to listOf("F8", "Mod-."), "klang.play" to emptyList()))

        user.read(StorageKeys.USER_KEYS) shouldBe """{"v":1,"data":{"klang.stop":["F8","Mod-."],"klang.play":[]}}"""
        withClue("an empty list, every key taken off, is a choice") {
            UserKeys(stores = stores(user)).store.load() shouldBe
                mapOf("klang.stop" to listOf("F8", "Mod-."), "klang.play" to emptyList())
        }

        withClue("the key manager saves all the user's keys: one action back to its defaults is one missing") {
            store.save(mapOf("klang.play" to emptyList()))

            user.read(StorageKeys.USER_KEYS) shouldBe """{"v":1,"data":{"klang.stop":null,"klang.play":[]}}"""
            store.load() shouldBe mapOf("klang.play" to emptyList())
            UserKeys(stores = stores(user)).store.load() shouldBe mapOf("klang.play" to emptyList())
        }

        withClue("every action back to its defaults, and one given keys again") {
            store.save(emptyMap())
            user.read(StorageKeys.USER_KEYS) shouldBe """{"v":1,"data":{"klang.stop":null,"klang.play":null}}"""
            store.load() shouldBe emptyMap()

            store.save(mapOf("klang.stop" to listOf("F9")))
            user.read(StorageKeys.USER_KEYS) shouldBe """{"v":1,"data":{"klang.stop":["F9"],"klang.play":null}}"""
        }
    }

    "two tabs that each change another action both keep theirs, and a reset in one is not undone by the other" {
        val user = MemoryKeyValueStore()
        val one = UserKeys(stores = stores(user)).store
        val other = UserKeys(stores = stores(user)).store

        one.save(mapOf("a" to listOf("F1")))
        // The other tab did not hear of it (a write between the event and its arrival): it saves what it knows
        other.save(mapOf("b" to listOf("F2")))

        user.read(StorageKeys.USER_KEYS) shouldBe """{"v":1,"data":{"a":["F1"],"b":["F2"]}}"""

        one.save(emptyMap())
        other.save(mapOf("b" to listOf("F3")))

        withClue("a's reset stays, though the other tab never heard of a") {
            user.read(StorageKeys.USER_KEYS) shouldBe """{"v":1,"data":{"a":null,"b":["F3"]}}"""
        }
    }

    "the store tells of keys changed in another tab and of a reset, with the actions that have keys" {
        val user = MemoryKeyValueStore()
        val keys = UserKeys(stores = stores(user))
        val told = mutableListOf<Map<String, List<String>>>()
        val unwatch = keys.store.watch { told.add(it) }

        try {
            user.changeFromElsewhere(key = StorageKeys.USER_KEYS, text = """{"v":1,"data":{"a":["F1"],"b":null}}""")

            told.last() shouldBe mapOf("a" to listOf("F1"))
            keys.store.load() shouldBe mapOf("a" to listOf("F1"))

            withClue("an action this tab only heard of, put back here: null, as its own would be") {
                keys.store.save(emptyMap())

                user.read(StorageKeys.USER_KEYS) shouldBe """{"v":1,"data":{"a":null,"b":null}}"""
                told.last() shouldBe emptyMap()
            }

            withClue("the storage page's reset") {
                keys.sections.forEach { it.reset() }

                told.last() shouldBe emptyMap()
                user.all shouldBe emptyMap()
            }

            val before = told.size
            unwatch()
            user.changeFromElsewhere(key = StorageKeys.USER_KEYS, text = """{"v":1,"data":{"a":["F4"]}}""")
            withClue("no longer watched") { told.size shouldBe before }
        } finally {
            unwatch()
        }
    }

    "the keys a browser kept before, one JSON object, are read once into the document, and the old key goes" {
        val user = MemoryKeyValueStore(
            initial = mapOf(StorageKeys.KEYMAP to """{"klang.stop":["F8"],"klang.play":[],"odd":"F9"}""")
        )

        withClue("the readable entries, the odd one skipped as it was before") {
            UserKeys(stores = stores(user)).store.load() shouldBe mapOf("klang.stop" to listOf("F8"), "klang.play" to emptyList())
        }
        user.all shouldBe mapOf(StorageKeys.USER_KEYS to """{"v":1,"data":{"klang.stop":["F8"],"klang.play":[]}}""")

        withClue("an old text with nothing readable in it stays where it is, and no document is made up") {
            val odd = MemoryKeyValueStore(initial = mapOf(StorageKeys.KEYMAP to "not json"))

            UserKeys(stores = stores(odd)).store.load() shouldBe emptyMap()
            odd.all shouldBe mapOf(StorageKeys.KEYMAP to "not json")
        }
    }

    "the Spine's reset of the keys' key forgets them" {
        val user = MemoryKeyValueStore()
        val spine = Spine(stores = stores(user))

        spine.userKeys.store.save(mapOf("a" to listOf("F1")))
        spine.resetStored(StorageKeys.USER_KEYS)

        spine.userKeys.store.load() shouldBe emptyMap()
        user.read(StorageKeys.USER_KEYS) shouldBe null
    }
})
