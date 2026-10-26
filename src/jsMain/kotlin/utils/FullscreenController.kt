/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.utils

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.browser.document
import kotlinx.browser.window

class FullscreenController : Stream<FullscreenController.FullscreenState> {

    data class FullscreenState(
        val isFullscreen: Boolean,
        val canExitWithClick: Boolean
    )

    private fun getCurrentState(): FullscreenState {
        val isApiFullscreen = document.fullscreenElement != null
        val isF11Fullscreen = window.innerWidth == window.screen.width && window.innerHeight == window.screen.height

        val isFullscreen = isApiFullscreen || isF11Fullscreen
        // We can only exit via click if we are in API fullscreen.
        // If we are not in any fullscreen, we consider it "exitable/clickable" because we can still enter it.
        val canExitWithClick = !isFullscreen || isApiFullscreen

        return FullscreenState(isFullscreen, canExitWithClick)
    }

    private val source = StreamSource<FullscreenState>(getCurrentState())

    init {
        document.onfullscreenchange = {
            source(getCurrentState())
        }

        // Listen to resize events to catch F11 toggles
        window.addEventListener("resize", {
            source(getCurrentState())
        })
    }

    override fun invoke(): FullscreenState = source()

    override fun subscribeToStream(sub: (FullscreenState) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    /**
     * Fullscreen, unless the page already is (the start page's "Enter fullscreen", KLANG-00349). Called from a click:
     * the browser grants fullscreen only to a user's gesture; a refusal leaves the page as it is.
     */
    fun enter() {
        if (getCurrentState().isFullscreen) {
            return
        }

        // A browser without the Fullscreen API (an iPhone, an older Safari) throws instead of refusing: the caller goes
        // on all the same, as "Make Music Now" must open the song
        val root = document.documentElement ?: return

        if (root.asDynamic().requestFullscreen == undefined) {
            return
        }

        try {
            root.requestFullscreen().catch { console.warn("Fullscreen refused:", it) }
        } catch (e: Throwable) {
            console.warn("Fullscreen failed:", e)
        }
    }

    fun toggle() {
        val currentState = getCurrentState()
        if (currentState.isFullscreen && !currentState.canExitWithClick) {
            // Cannot programmatically exit F11 fullscreen, do nothing
            console.warn("Cannot exit F11 fullscreen programmatically.")
            return
        }

        if (document.fullscreenElement != null) {
            // We are in API fullscreen, we can exit it
            document.exitFullscreen()
        } else {
            // We are not in fullscreen. Requesting API fullscreen works robustly.
            document.documentElement?.requestFullscreen()
        }
    }
}
