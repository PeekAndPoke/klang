/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

/**
 * Every key the app keeps in the browser's localStorage, named in one place (KLANG-00323).
 *
 * The browsers of our users hold values under these keys today, their songs among them: a key that changes loses what
 * is stored under it, so `StorageKeysSpec` pins each one. They live in common code so that spec runs on the JVM; the
 * storage that reads and writes them is `src/jsMain/kotlin/storage/`.
 *
 * One key lives outside the app: `klang-tool-auto-update`, kept by `KlangToolAutoUpdate` in the `klangui` module,
 * which cannot reach the app's code.
 */
object StorageKeys {

    /** The Midi Playground's saved instruments. */
    const val MIDI_INSTRUMENTS = "klang-midi-instruments"

    /** The instrument the Midi Playground's editors held when last applied. */
    const val MIDI_INSTRUMENT_DRAFT = "klang-midi-instrument-draft"

    /** The master volume, a linear gain from 0.0 to 1.0 (KLANG-00330). */
    const val MASTER_VOLUME = "klang-master-volume"

    /** The keys the user gave Klang's actions, over the defaults (KLANG-00325). */
    const val KEYMAP = "klang-keymap"

    /** The look the user chose, a built-in preset's id (KLANG-00339). */
    const val LOOK = "klang-look"

    /** The editor's highlights per sound event, a whole number (KLANG-00349). */
    const val EDITOR_HIGHLIGHTS_PER_EVENT = "klang-editor-highlights-per-event"

    /** Whether the editor shows a name's quick help on hover, `true` or `false` (KLANG-00349). */
    const val EDITOR_HOVER_HELP = "klang-editor-hover-help"

    /** The Gui-dance's story arcs done, their ids joined by commas (KLANG-00350). */
    const val GUI_DANCE_ARCS_DONE = "klang-gui-dance-arcs-done"

    /** Until when the Gui-dance's walkthroughs are muted, milliseconds since the epoch; 0 for not muted (KLANG-00350). */
    const val GUI_DANCE_MUTED_UNTIL = "klang-gui-dance-muted-until"

    /** A song's code; [version] is the app's version, [songId] the song's id (a built-in song's, or "new"). */
    fun songCode(version: String, songId: String): String = songKey(version = version, songId = songId, part = "code")

    /** A song's title; [version] and [songId] as for [songCode]. */
    fun songTitle(version: String, songId: String): String = songKey(version = version, songId = songId, part = "title")

    /** A song's rpm; [version] and [songId] as for [songCode]. */
    fun songRpm(version: String, songId: String): String = songKey(version = version, songId = songId, part = "rpm")

    /** A song's bookmarks; [version] and [songId] as for [songCode]. */
    fun songBookmarks(version: String, songId: String): String =
        songKey(version = version, songId = songId, part = "bookmarks")

    /** Whether the tutorial [slug] is completed: present with `true` when it is. */
    fun tutorialCompleted(slug: String): String = "klang-tutorial-completed-$slug"

    private fun songKey(version: String, songId: String, part: String): String = "song-$version-$songId-$part"
}
