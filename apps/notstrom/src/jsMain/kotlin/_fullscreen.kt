/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import kotlinx.browser.document

// Fullscreen for the page, as the studio enters it (its FullscreenController): a browser without the Fullscreen API,
// or one that refuses, leaves the page as it is, and the show goes on.

/** Whether the page is shown in fullscreen now. */
fun isFullscreen(): Boolean = document.asDynamic().fullscreenElement != null

/** The page in fullscreen; call it inside a click (the browser grants it only to a user's gesture). */
fun enterFullscreen() {
    val root = document.documentElement ?: return

    if (isFullscreen() || root.asDynamic().requestFullscreen == undefined) {
        return
    }

    try {
        root.requestFullscreen().catch { console.warn("Notstrom: fullscreen refused", it) }
    } catch (e: Throwable) {
        console.warn("Notstrom: fullscreen failed", e)
    }
}

/** Out of fullscreen, where the page is in it. */
fun leaveFullscreen() {
    if (!isFullscreen()) {
        return
    }

    try {
        document.exitFullscreen().catch { console.warn("Notstrom: leaving fullscreen refused", it) }
    } catch (e: Throwable) {
        console.warn("Notstrom: leaving fullscreen failed", e)
    }
}
