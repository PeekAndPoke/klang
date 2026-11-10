/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore
import kotlinx.serialization.Serializable

/** A section for the spec: a name, and a count held at 10 at most. */
@Serializable
private data class Sample(val name: String = "none", val count: Int = 1)

/**
 * A stored section of the Spine (KLANG-00422), the Spine's side of it: the default, every value in its range, a part
 * as a stream, a reset, another tab's change. How the document is kept and merged is the workshop's
 * (`DocumentStorageSpec`).
 */
class StoredSpec : StringSpec({

    fun section(store: KeyValueStore, follows: Boolean = true) = Stored(
        key = "sample",
        version = 2,
        serializer = Sample.serializer(),
        default = Sample(),
        store = store,
        normalise = { it.copy(count = it.count.coerceAtMost(10)) },
        follows = follows,
    )

    "nothing kept: the default, and nothing is written until something changes" {
        val store = MemoryKeyValueStore()
        val stored = section(store)

        stored() shouldBe Sample()
        store.all shouldBe emptyMap()

        stored.update { it }
        withClue("a change that changes nothing") { store.all shouldBe emptyMap() }
    }

    "a document that cannot be read: the default is shown" {
        section(MemoryKeyValueStore(initial = mapOf("sample" to "not json at all")))() shouldBe Sample()
    }

    "a value out of range is held, when kept and when set; a part says something only when it changes" {
        val store = MemoryKeyValueStore(initial = mapOf("sample" to """{"v":2,"data":{"name":"a","count":99}}"""))
        val stored = section(store)
        val counts = mutableListOf<Int>()
        val watching = stored.part { it.count }.subscribeToStream { counts.add(it) }

        try {
            withClue("kept out of range") { stored() shouldBe Sample(name = "a", count = 10) }

            stored.update { it.copy(count = 50) }
            withClue("set out of range: held, so nothing changed and nothing is written") {
                store.read("sample") shouldBe """{"v":2,"data":{"name":"a","count":99}}"""
            }

            stored.update { it.copy(count = 4) }
            stored.update { it.copy(name = "b") }

            counts.takeLast(2) shouldBe listOf(10, 4)
            store.read("sample") shouldBe """{"v":2,"data":{"name":"b","count":4}}"""
        } finally {
            watching()
        }
    }

    "another tab's change shows at once in a section that follows, held in its range; not in one that does not" {
        val store = MemoryKeyValueStore()
        val follows = section(store)
        val alone = section(store = store, follows = false)

        store.changeFromElsewhere(key = "sample", text = """{"v":2,"data":{"name":"theirs","count":77}}""")

        follows() shouldBe Sample(name = "theirs", count = 10)
        alone() shouldBe Sample()
    }

    "reset: nothing kept under the key, the default shown, and no change brings the old values back" {
        val store = MemoryKeyValueStore()
        val stored = section(store)

        stored.update { it.copy(name = "mine", count = 3) }
        stored.reset()

        stored() shouldBe Sample()
        store.all shouldBe emptyMap()

        stored.update { it.copy(count = 2) }
        store.read("sample") shouldBe """{"v":2,"data":{"count":2}}"""
    }
})
