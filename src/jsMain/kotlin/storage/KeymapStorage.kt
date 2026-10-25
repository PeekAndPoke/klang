/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.klang.workshop.keys.KeymapStore
import kotlinx.browser.localStorage

/**
 * The keys the user gave Klang's actions, in the browser's localStorage (KLANG-00325), as [KeymapJson] writes them.
 * Later, with a server, they live there.
 *
 * The storage can throw or come back empty (a private window, blocked site data); the keys are then the defaults and
 * still work, a change only lasts until reload.
 */
object KeymapStorage : KeymapStore {

    override fun load(): Map<String, List<String>> {
        val stored = try {
            localStorage.getItem(StorageKeys.KEYMAP)
        } catch (e: Throwable) {
            console.warn("KeymapStorage: could not read the keymap", e)
            null
        }

        return stored?.let { KeymapJson.read(it) } ?: emptyMap()
    }

    override fun save(userKeys: Map<String, List<String>>) {
        try {
            localStorage.setItem(StorageKeys.KEYMAP, KeymapJson.write(userKeys))
        } catch (e: Throwable) {
            console.warn("KeymapStorage: could not keep the keymap", e)
        }
    }
}
