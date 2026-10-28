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

    /** The look the user chose, a look's id (KLANG-00339). */
    const val LOOK = "klang-look"

    /** The user's own looks and the equipped slots, as JSON (KLANG-00382). */
    const val LOOKS = "klang-looks"

    /** The editor's highlights per sound event, a whole number (KLANG-00349). */
    const val EDITOR_HIGHLIGHTS_PER_EVENT = "klang-editor-highlights-per-event"

    /** Whether the editor shows a name's quick help on hover, `true` or `false` (KLANG-00349). */
    const val EDITOR_HOVER_HELP = "klang-editor-hover-help"

    /** The Gui-dance's story arcs done, their ids joined by commas (KLANG-00350). */
    const val GUI_DANCE_ARCS_DONE = "klang-gui-dance-arcs-done"

    /** Until when the Gui-dance's walkthroughs are muted, milliseconds since the epoch; 0 for not muted (KLANG-00350). */
    const val GUI_DANCE_MUTED_UNTIL = "klang-gui-dance-muted-until"

    /**
     * The seed the user's metal grows from: the buttons' grain, the brushed metal, the start page's sheet; made once, on
     * the first visit, so each user has a sheet of their own and keeps it (the maintainer, 2026-10-28: "every user
     * should get a ui-seed in the storage").
     */
    const val SEED_METAL = "klang-seed-metal"

    /** The workbench's layout: its pinned tool windows and the bottom panel, as JSON (KLANG-00349). */
    const val WORKBENCH_LAYOUT = "klang-workbench-layout"

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

    /**
     * A part of what the browser keeps, as the Dev Tools' storage page shows it and resets it (the maintainer,
     * 2026-10-28: "reset everything in the local storage or individual settings"): its [name], what it holds
     * ([about]), and which keys are its own ([covers]). [precious] parts hold the user's own work: resetting them asks
     * first.
     */
    class Part(
        val name: String,
        val about: String,
        val precious: Boolean = false,
        /** The section the storage page shows it in. */
        val section: String = SETTINGS,
        val covers: (key: String) -> Boolean,
    )

    /** The storage page's sections. */
    const val SETTINGS = "Settings"
    const val SEEDS = "Random seeds"

    private fun exactly(vararg keys: String): (String) -> Boolean = { it in keys }

    /** Every part, one per kept setting; a new key gets a part here, or the storage page lists it as unknown. */
    val parts: List<Part> = listOf(
        Part(name = "Songs", about = "Your songs: their code, titles, tempos and bookmarks", precious = true) {
            it.startsWith("song-")
        },
        Part(
            name = "Your own looks",
            about = "The looks you made and the eight equipped",
            precious = true,
            covers = exactly(LOOKS),
        ),
        Part(name = "The look worn", about = "The look you chose last", covers = exactly(LOOK)),
        Part(name = "Master volume", about = "The Motor's output level", covers = exactly(MASTER_VOLUME)),
        Part(name = "Keys", about = "The keys you gave Klang's actions", precious = true, covers = exactly(KEYMAP)),
        Part(
            name = "Editor",
            about = "Highlights per sound event, quick help on hover",
            covers = exactly(EDITOR_HIGHLIGHTS_PER_EVENT, EDITOR_HOVER_HELP),
        ),
        Part(
            name = "Walkthroughs",
            about = "The walkthroughs done and their mute",
            covers = exactly(GUI_DANCE_ARCS_DONE, GUI_DANCE_MUTED_UNTIL),
        ),
        Part(
            name = "Workbench",
            about = "Pinned tool windows and the bottom panel",
            covers = exactly(WORKBENCH_LAYOUT),
        ),
        Part(name = "Lessons done", about = "The tutorials you finished") {
            it.startsWith("klang-tutorial-completed-")
        },
        Part(
            name = "MIDI instruments",
            about = "The Midi Playground's instruments and its draft",
            precious = true,
            covers = exactly(MIDI_INSTRUMENTS, MIDI_INSTRUMENT_DRAFT),
        ),
        // The random seeds (the maintainer, 2026-10-28: "Make a random seeds "section" in the storage and the first
        // entry will be the seed for grain noise and the metal brushes"); reset, one is made anew on the next load
        Part(
            name = "Metal",
            about = "The buttons' grain, the brushed metal and the start page's sheet grow from it",
            section = SEEDS,
            covers = exactly(SEED_METAL),
        ),
    )
}
