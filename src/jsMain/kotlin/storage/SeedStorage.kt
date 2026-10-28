/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import kotlinx.browser.localStorage
import kotlin.random.Random

/**
 * The user's random seeds, kept in the browser (the maintainer, 2026-10-28: "every user should get a ui-seed in the
 * storage"): each made once, on the first visit, then the same on every visit, so a user's metal is their own and
 * stays so. Where the storage cannot be read or written the seed is new on each load, and the app works on.
 */
object SeedStorage {

    /** The seed the user's metal grows from: the buttons' grain, the brushed metal, the start page's sheet. */
    val metal: Int by lazy { seed(StorageKeys.SEED_METAL) }

    private fun seed(key: String): Int {
        val kept = try {
            localStorage.getItem(key)?.toIntOrNull()
        } catch (e: Throwable) {
            console.warn("SeedStorage: could not read $key", e)
            null
        }

        if (kept != null) {
            return kept
        }

        val made = Random.nextInt()

        try {
            localStorage.setItem(key, made.toString())
        } catch (e: Throwable) {
            console.warn("SeedStorage: could not keep $key", e)
        }

        return made
    }
}
