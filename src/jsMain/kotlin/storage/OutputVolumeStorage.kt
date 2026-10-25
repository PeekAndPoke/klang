/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import kotlinx.browser.localStorage

/**
 * The master volume in the browser's localStorage (KLANG-00330): the linear gain, 0.0 to 1.0, that `Player.outputVolume`
 * starts from on the next visit.
 *
 * The storage can throw or come back empty (a private window, blocked site data); the volume then starts at full and
 * still works, it only forgets on reload.
 */
object OutputVolumeStorage {

    /** The remembered gain, kept in 0.0 to 1.0; full when nothing (readable) is stored. */
    fun load(): Double {
        val stored = try {
            localStorage.getItem(StorageKeys.MASTER_VOLUME)?.toDoubleOrNull()
        } catch (e: Throwable) {
            console.warn("OutputVolumeStorage: could not read the volume", e)
            null
        }

        return when {
            stored == null -> 1.0
            // NaN-guard: an unreadable stored value starts at full, as a first visit does
            stored != stored -> 1.0
            else -> stored.coerceIn(0.0, 1.0)
        }
    }

    fun save(gain: Double) {
        try {
            localStorage.setItem(StorageKeys.MASTER_VOLUME, gain.toString())
        } catch (e: Throwable) {
            console.warn("OutputVolumeStorage: could not remember the volume", e)
        }
    }
}
