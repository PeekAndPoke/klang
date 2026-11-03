/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.localStorage

private val log = Logger(area = LogArea.Storage, source = "TutorialStorage")

/**
 * Which tutorials the user marked as done, in the browser's localStorage; [done] as a stream, so the Learn column,
 * the tiles and the tutorial page show a lesson marked done at once (KLANG-00370). The storage can throw (a private
 * window, blocked site data): then nothing is kept, and the app still works.
 */
object TutorialStorage {

    private val doneSource = StreamSource(readDone())

    /** The slugs of the tutorials marked as done. */
    val done: Stream<Set<String>> = doneSource

    fun isCompleted(slug: String): Boolean = slug in doneSource()

    fun setCompleted(slug: String, completed: Boolean) {
        try {
            if (completed) {
                localStorage.setItem(StorageKeys.tutorialCompleted(slug), "true")
            } else {
                localStorage.removeItem(StorageKeys.tutorialCompleted(slug))
            }
        } catch (e: Throwable) {
            log.warn("Could not keep the tutorial's state", e)
        }

        doneSource(if (completed) doneSource() + slug else doneSource() - slug)
    }

    private fun readDone(): Set<String> = try {
        allTutorials.map { it.slug }.filter { localStorage.getItem(StorageKeys.tutorialCompleted(it)) == "true" }.toSet()
    } catch (e: Throwable) {
        log.warn("Could not read the tutorials' state", e)
        emptySet()
    }
}
