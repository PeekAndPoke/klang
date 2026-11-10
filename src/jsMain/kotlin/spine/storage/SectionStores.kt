/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine.storage

import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.workshop.storage.BrowserKeyValueStore
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage

private val log = Logger(area = LogArea.Storage, source = "SectionStores")

/**
 * Where the Spine's stored sections are kept, one store per scope (KLANG-00422; the maintainer, 2026-11-10: someone
 * has several tabs open, "so navigating around and opening / closing tools should not influence the other tab"):
 *
 * | Scope | Kept in | Other tabs | Other computers | For example |
 * |---|---|---|---|---|
 * | [user] | the browser's storage, later a server too | yes | yes | settings, looks, keys, songs |
 * | [machine] | the browser's storage | yes | no | the output volume, the MIDI setup |
 * | [tab] | the tab's own storage | no | no | which tool windows are open, the panel's tab |
 *
 * The one place of the app that names the browser's storage for a section.
 */
class SectionStores(
    /** What follows the user: every tab now, every computer once there is a server. */
    val user: KeyValueStore,
    /** What belongs to this machine: every tab of this browser. */
    val machine: KeyValueStore,
    /** What belongs to this tab: it lives through a reload and is no other tab's. */
    val tab: KeyValueStore,
) {
    companion object {
        /** The app's stores: the browser's storage for the user's and the machine's, the tab's own for the tab's. */
        fun inBrowser(): SectionStores {
            val problem: (String, Throwable?) -> Unit = { message, cause -> log.warn(message, cause) }
            // With the site's data blocked the browser gives none: the app runs on memory, and says so in its log
            val site = BrowserKeyValueStore.orMemory(onProblem = problem) { localStorage }
            val tab = BrowserKeyValueStore.orMemory(onProblem = problem) { sessionStorage }

            return SectionStores(user = site, machine = site, tab = tab)
        }

        /** Stores that live and die with their holder: a spec's, which never reach the browser's storage. */
        fun inMemory(): SectionStores =
            SectionStores(user = MemoryKeyValueStore(), machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore())
    }
}
