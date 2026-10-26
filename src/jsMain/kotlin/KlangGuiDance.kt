/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.ui.GuiDance
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlin.js.Date

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

    /** Another song opened on the songs page; the next one after the first is lit. */
    const val SONGS_OPEN = "songs.open"

    /** The fullscreen key at the workbench strip's foot. */
    const val FULLSCREEN = "workbench.fullscreen"

    /** The walkthroughs key above it, opening the walkthroughs window. */
    const val WALKTHROUGHS = "workbench.walkthroughs"

    /**
     * The arcs, in their order (the maintainer, 2026-10-26). The first: Play on the song page, the Motor button,
     * "Songs" in its drawer, then the next song on the songs page. The second starts as it ends: the fullscreen key,
     * then the walkthroughs key itself.
     */
    val arcs = listOf(
        GuiDance.Arc(
            id = "first-song",
            title = "Your first song, and the next",
            steps = listOf(SONG_PLAY, MOTOR_MENU, DRAWER_SONGS, SONGS_OPEN),
        ),
        GuiDance.Arc(
            id = "fullscreen",
            title = "Fullscreen, and the walkthroughs",
            steps = listOf(FULLSCREEN, WALKTHROUGHS),
        ),
    )

    val dance = GuiDance(arcs = arcs, store = BrowserStore)

    /** The timer that ends a mute on time. */
    private var muteTimer: Int? = null

    init {
        wakeAfterMute()
    }

    /** Mutes the walkthroughs for [forMs] milliseconds; the lamp comes back by itself after. */
    fun mute(forMs: Double) {
        dance.mute(forMs)
        wakeAfterMute()
    }

    fun unmute() {
        dance.unmute()
        wakeAfterMute()
    }

    /** When a mute runs, a timer for its end, so the lamp comes back without a reload. */
    private fun wakeAfterMute() {
        muteTimer?.let { window.clearTimeout(it) }
        muteTimer = null

        val until = dance.mutedUntil() ?: return
        // setTimeout takes at most about 24.8 days; a longer mute wakes then and looks again
        val delay = (until - Date.now()).coerceIn(0.0, 2_000_000_000.0).toInt() + 50

        muteTimer = window.setTimeout({
            dance.refresh()
            wakeAfterMute()
        }, delay)
    }

    /**
     * The arcs done, in localStorage, their ids joined by commas, and the mute's end. The storage can throw or come back
     * empty (a private window, blocked site data); the dance then starts from its first arc, unmuted, and only forgets
     * on reload.
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

        override fun loadMutedUntil(): Double = try {
            localStorage.getItem(StorageKeys.GUI_DANCE_MUTED_UNTIL)?.toDoubleOrNull() ?: 0.0
        } catch (e: Throwable) {
            console.warn("KlangGuiDance: could not read the mute", e)
            0.0
        }

        override fun saveMutedUntil(until: Double) {
            try {
                localStorage.setItem(StorageKeys.GUI_DANCE_MUTED_UNTIL, until.toString())
            } catch (e: Throwable) {
                console.warn("KlangGuiDance: could not remember the mute", e)
            }
        }
    }
}
