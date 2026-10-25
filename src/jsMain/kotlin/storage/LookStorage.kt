/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLooks
import kotlinx.browser.localStorage

/**
 * The look the user chose, in the browser's localStorage (KLANG-00339): a built-in preset's id.
 *
 * The storage can throw or come back empty (a private window, blocked site data); the app then starts with the
 * default look and still works, it only forgets on reload. An id no preset has any more starts the default too.
 */
object LookStorage {

    /** The remembered preset; null when nothing (readable, known) is stored. */
    fun load(): KlangLookPreset? {
        val id = try {
            localStorage.getItem(StorageKeys.LOOK)
        } catch (e: Throwable) {
            console.warn("LookStorage: could not read the look", e)
            null
        }

        return id?.let { KlangLooks.byId(it) }
    }

    fun save(preset: KlangLookPreset) {
        try {
            localStorage.setItem(StorageKeys.LOOK, preset.id)
        } catch (e: Throwable) {
            console.warn("LookStorage: could not remember the look", e)
        }
    }
}
