/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.localStorage

private val log = Logger(area = LogArea.Storage, source = "EditorSettings")

/**
 * The code editor's settings, kept in the browser's localStorage (KLANG-00349; the song page's Settings tool window):
 * whether the code highlights show at all, on by default, how many a sound event draws and how strong they show
 * (KLANG-00411), and whether a name's quick help shows on hover: on by default (the maintainer, 2026-10-27; off from
 * 2026-10-26, when Alt+Q came to show it in the Help window), and a user's own choice kept.
 *
 * The storage can throw or come back empty (a private window, blocked site data); the settings then start at their
 * defaults and only forget on reload.
 */
object EditorSettings {

    /** Highlights per sound event, as the song page always had. */
    const val HIGHLIGHTS_PER_EVENT_DEFAULT = 25

    /** The highlights' range a user may choose; 0 draws none, as the header's EVT field allowed. */
    val HIGHLIGHTS_PER_EVENT_RANGE = 0..50

    /** The highlights' strength, in percent: 66 for anyone who never chose (the maintainer, 2026-11-05). */
    const val HIGHLIGHT_STRENGTH_DEFAULT = 66

    /** The strength a user may choose, in percent; 0 hides the highlights, 100 is how they always showed. */
    val HIGHLIGHT_STRENGTH_RANGE = 0..100

    private val highlightsSource = StreamSource(
        read(StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT)?.toIntOrNull()
            ?.coerceIn(HIGHLIGHTS_PER_EVENT_RANGE) ?: HIGHLIGHTS_PER_EVENT_DEFAULT
    )

    private val strengthSource = StreamSource(
        read(StorageKeys.EDITOR_HIGHLIGHT_STRENGTH)?.toIntOrNull()
            ?.coerceIn(HIGHLIGHT_STRENGTH_RANGE) ?: HIGHLIGHT_STRENGTH_DEFAULT
    )

    private val highlightsOnSource = StreamSource(highlightsOnFrom(read(StorageKeys.EDITOR_HIGHLIGHTS_ON)))

    /** The hover's quick help unless the user turned it off ("false" kept); on for anyone who never chose. */
    private val hoverHelpSource = StreamSource(read(StorageKeys.EDITOR_HOVER_HELP) != "false")

    val highlightsPerEvent: Stream<Int> = highlightsSource

    /** Whether the code highlights show at all. */
    val highlightsOn: Stream<Boolean> = highlightsOnSource

    /** The highlights' strength, in percent. */
    val highlightStrength: Stream<Int> = strengthSource

    val hoverHelp: Stream<Boolean> = hoverHelpSource

    fun setHighlightsPerEvent(value: Int) {
        val next = value.coerceIn(HIGHLIGHTS_PER_EVENT_RANGE)

        highlightsSource(next)
        write(StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT, next.toString())
    }

    /** The code highlights unless the user switched them off ([stored] "false"); on for anyone who never chose. */
    fun highlightsOnFrom(stored: String?): Boolean = stored != "false"

    fun setHighlightsOn(on: Boolean) {
        highlightsOnSource(on)
        write(StorageKeys.EDITOR_HIGHLIGHTS_ON, on.toString())
    }

    fun setHighlightStrength(percent: Int) {
        val next = percent.coerceIn(HIGHLIGHT_STRENGTH_RANGE)

        strengthSource(next)
        write(StorageKeys.EDITOR_HIGHLIGHT_STRENGTH, next.toString())
    }

    fun setHoverHelp(on: Boolean) {
        hoverHelpSource(on)
        write(StorageKeys.EDITOR_HOVER_HELP, on.toString())
    }

    private fun read(key: String): String? = try {
        localStorage.getItem(key)
    } catch (e: Throwable) {
        log.warn("Could not read $key", e)
        null
    }

    private fun write(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (e: Throwable) {
            log.warn("Could not remember $key", e)
        }
    }
}
