/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.ui.GuiDance
import kotlinx.browser.localStorage

/**
 * Klang's Gui-dance (KLANG-00350): its story arcs and their steps, and the arcs done kept in the browser. A component
 * whose step it is asks [dance] whether it is lit, wears the beacon then, and reports its use.
 */
object KlangGuiDance {

    /** Play on the song page. */
    const val SONG_PLAY = "song.play"

    /** The Motor's menu button, opening the drawer. */
    const val MOTOR_MENU = "motor.menu"

    /** "Songs" in the Motor's drawer. */
    const val DRAWER_SONGS = "drawer.songs"

    /**
     * The arcs, in their order. The first (the maintainer, 2026-10-26): Play on the song page, then the Motor button,
     * then "Songs" in its drawer.
     */
    val arcs = listOf(
        GuiDance.Arc(id = "first-song", steps = listOf(SONG_PLAY, MOTOR_MENU, DRAWER_SONGS)),
    )

    val dance = GuiDance(arcs = arcs, store = BrowserStore)

    /**
     * The arcs done, in localStorage, their ids joined by commas. The storage can throw or come back empty (a private
     * window, blocked site data); the dance then starts from its first arc and only forgets on reload.
     */
    private object BrowserStore : GuiDance.Store {
        override fun loadDone(): Set<String> = try {
            localStorage.getItem(StorageKeys.GUI_DANCE_ARCS_DONE)
                ?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet()
        } catch (e: Throwable) {
            console.warn("KlangGuiDance: could not read the arcs done", e)
            emptySet()
        }

        override fun saveDone(done: Set<String>) {
            try {
                localStorage.setItem(StorageKeys.GUI_DANCE_ARCS_DONE, done.joinToString(","))
            } catch (e: Throwable) {
                console.warn("KlangGuiDance: could not remember the arcs done", e)
            }
        }
    }
}
