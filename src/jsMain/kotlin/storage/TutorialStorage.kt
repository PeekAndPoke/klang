/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import kotlinx.browser.localStorage

object TutorialStorage {

    fun isCompleted(slug: String): Boolean {
        return localStorage.getItem(StorageKeys.tutorialCompleted(slug)) == "true"
    }

    fun setCompleted(slug: String, completed: Boolean) {
        if (completed) {
            localStorage.setItem(StorageKeys.tutorialCompleted(slug), "true")
        } else {
            localStorage.removeItem(StorageKeys.tutorialCompleted(slug))
        }
    }

    fun toggleCompleted(slug: String): Boolean {
        val newState = !isCompleted(slug)
        setCompleted(slug, newState)
        return newState
    }
}
