/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import kotlinx.browser.localStorage

object TutorialStorage {

    private const val KEY_PREFIX = "klang-tutorial-completed-"

    fun isCompleted(slug: String): Boolean {
        return localStorage.getItem("$KEY_PREFIX$slug") == "true"
    }

    fun setCompleted(slug: String, completed: Boolean) {
        if (completed) {
            localStorage.setItem("$KEY_PREFIX$slug", "true")
        } else {
            localStorage.removeItem("$KEY_PREFIX$slug")
        }
    }

    fun toggleCompleted(slug: String): Boolean {
        val newState = !isCompleted(slug)
        setCompleted(slug, newState)
        return newState
    }
}
