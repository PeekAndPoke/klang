/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.localStorage

/**
 * The code editor's settings, kept in the browser's localStorage (KLANG-00349; the song page's Settings tool window):
 * how many highlights a sound event draws, and whether a name's quick help shows on hover (off by default: Alt+Q
 * shows it in the Help window, the maintainer, 2026-10-26).
 *
 * The storage can throw or come back empty (a private window, blocked site data); the settings then start at their
 * defaults and only forget on reload.
 */
object EditorSettings {

    /** Highlights per sound event, as the song page always had. */
    const val HIGHLIGHTS_PER_EVENT_DEFAULT = 15

    /** The highlights' range a user may choose; 0 draws none, as the header's EVT field allowed. */
    val HIGHLIGHTS_PER_EVENT_RANGE = 0..50

    private val highlightsSource = StreamSource(
        read(StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT)?.toIntOrNull()
            ?.coerceIn(HIGHLIGHTS_PER_EVENT_RANGE) ?: HIGHLIGHTS_PER_EVENT_DEFAULT
    )

    private val hoverHelpSource = StreamSource(read(StorageKeys.EDITOR_HOVER_HELP) == "true")

    val highlightsPerEvent: Stream<Int> = highlightsSource

    val hoverHelp: Stream<Boolean> = hoverHelpSource

    fun setHighlightsPerEvent(value: Int) {
        val next = value.coerceIn(HIGHLIGHTS_PER_EVENT_RANGE)

        highlightsSource(next)
        write(StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT, next.toString())
    }

    fun setHoverHelp(on: Boolean) {
        hoverHelpSource(on)
        write(StorageKeys.EDITOR_HOVER_HELP, on.toString())
    }

    private fun read(key: String): String? = try {
        localStorage.getItem(key)
    } catch (e: Throwable) {
        console.warn("EditorSettings: could not read $key", e)
        null
    }

    private fun write(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (e: Throwable) {
            console.warn("EditorSettings: could not remember $key", e)
        }
    }
}
