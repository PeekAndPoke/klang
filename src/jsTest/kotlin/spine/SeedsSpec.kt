/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/** The user's random seeds as a stored section (KLANG-00422): made once, kept, the same from then on. */
class SeedsSpec : StringSpec({

    fun stores(user: MemoryKeyValueStore) =
        SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore())

    "the metal's seed is made on the first visit, kept, and the same on the next" {
        val user = MemoryKeyValueStore()
        val first = Seeds(stores = stores(user))

        withClue("nothing is made until it is asked for") { user.all shouldBe emptyMap() }

        val made = first.metal

        first.metal shouldBe made
        user.read(StorageKeys.SEEDS) shouldBe """{"v":1,"data":{"metal":$made}}"""
        withClue("the next visit") { Seeds(stores = stores(user)).metal shouldBe made }
    }

    "each user has a seed of their own" {
        val seeds = (1..8).map { Seeds(stores = stores(MemoryKeyValueStore())).metal }.toSet()

        withClue("eight visits of eight users: $seeds") { seeds.size shouldNotBe 1 }
    }

    "the seed kept before the section existed stays the user's, and its old key goes" {
        val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.SEED_METAL to "-1234567"))

        Seeds(stores = stores(user)).metal shouldBe -1234567

        user.read(StorageKeys.SEED_METAL) shouldBe null
        user.read(StorageKeys.SEEDS) shouldBe """{"v":1,"data":{"metal":-1234567}}"""
    }

    "an old seed that is no whole number: a new one is made" {
        val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.SEED_METAL to "not a number"))
        val made = Seeds(stores = stores(user)).metal

        Seeds(stores = stores(user)).metal shouldBe made
    }
})
