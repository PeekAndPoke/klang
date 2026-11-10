/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.state.RandomSeeds
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import kotlin.random.Random

/**
 * The user's random seeds, a part of the Spine (the maintainer, 2026-10-28: "every user should get a ui-seed in the
 * storage"; a stored section since KLANG-00422): each made once, on the first visit, then the same on every visit,
 * so a user's metal is their own and stays so. Where nothing can be kept the seed is new on each load, and the app
 * works on.
 */
class Seeds(stores: SectionStores) {

    companion object {
        /**
         * The metal's seed as it was kept until KLANG-00422, under a key of its own. Scaffolding: it goes when no
         * browser holds the old key any more.
         */
        private fun before(store: KeyValueStore): DocumentStorage.Legacy<RandomSeeds>? {
            val metal = store.read(StorageKeys.SEED_METAL)?.toIntOrNull() ?: return null

            return DocumentStorage.Legacy(value = RandomSeeds(metal = metal), keys = listOf(StorageKeys.SEED_METAL))
        }
    }

    private val kept = Stored(
        key = StorageKeys.SEEDS,
        version = 1,
        serializer = RandomSeeds.serializer(),
        default = RandomSeeds(),
        store = stores.user,
        legacy = ::before,
    )

    internal val sections: List<Stored<*>> = listOf(kept)

    /**
     * The seed the user's metal grows from: the buttons' grain, the brushed metal, the start page's sheet. Made and
     * kept when first asked for, and the same from then on in this run, also after a reset of the storage.
     */
    val metal: Int by lazy {
        kept().metal ?: Random.nextInt().also { made -> kept.update { it.copy(metal = made) } }
    }
}
