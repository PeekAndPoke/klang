/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.peekandpoke.klang.workshop.storage.DocumentStorage

/**
 * Every key the app keeps in the browser's storage, named in one place (KLANG-00323): in localStorage, and for the
 * workbench's layout also in the tab's own (sessionStorage).
 *
 * The browsers of our users hold values under these keys today, their songs among them: a key that changes loses what
 * is stored under it, so `StorageKeysSpec` pins each one. They live in common code so that spec runs on the JVM; the
 * storage that reads and writes them is `src/jsMain/kotlin/storage/` and, for the Spine's parts, `spine/storage/`.
 *
 * One key lives outside the app: `klang-tool-auto-update`, kept by `KlangToolAutoUpdate` in the `klangui` module,
 * which cannot reach the app's code.
 */
object StorageKeys {

    /** The Midi Playground's saved instruments. */
    const val MIDI_INSTRUMENTS = "klang-midi-instruments"

    /** The instrument the Midi Playground's editors held when last applied. */
    const val MIDI_INSTRUMENT_DRAFT = "klang-midi-instrument-draft"

    /** The MIDI play code (KLANG-00402, D7): one KlangScript input with the instruments and what a key plays. */
    const val MIDI_PLAY_CODE = "klang-midi-play-code"

    /** The MIDI loadout (KLANG-00402, step 5c): the instruments the instrument keys step through, as their codes. */
    const val MIDI_LOADOUT = "klang-midi-loadout"

    /** The laptop keys' layout chosen by hand, a layout's name; absent for "Automatic" (KLANG-00402, D9). */
    const val MIDI_KEY_LAYOUT = "klang-midi-key-layout"

    /** This machine's audio output, one document (KLANG-00422): `AudioOutput`, the master volume. */
    const val OUTPUT = "klang-output"

    /** Until KLANG-00422, read once into [OUTPUT]: the master volume, a linear gain from 0.0 to 1.0 (KLANG-00330). */
    const val MASTER_VOLUME = "klang-master-volume"

    /**
     * The keys the user gave Klang's actions, over the defaults (KLANG-00325), one document (KLANG-00422): each
     * action's id with its keys, or `null` when put back to its defaults.
     */
    const val USER_KEYS = "klang-keys"

    /** Until KLANG-00422, read once into [USER_KEYS]: the user's keys as one JSON object, no version. */
    const val KEYMAP = "klang-keymap"

    /** The look the user chose, a look's id (KLANG-00339). */
    const val LOOK = "klang-look"

    /** The user's own looks and the equipped slots, as JSON (KLANG-00382). */
    const val LOOKS = "klang-looks"

    /** The editor's settings, one document (KLANG-00422): `EditorSettings`, in the Spine's envelope. */
    const val EDITOR = "klang-editor"

    /** Until KLANG-00422, read once into [EDITOR]: the editor's highlights per sound event, a whole number. */
    const val EDITOR_HIGHLIGHTS_PER_EVENT = "klang-editor-highlights-per-event"

    /** Until KLANG-00422, read once into [EDITOR]: whether the editor shows the code highlights, `true` or `false`. */
    const val EDITOR_HIGHLIGHTS_ON = "klang-editor-highlights-on"

    /** Until KLANG-00422, read once into [EDITOR]: the strength of the editor's highlights, a whole percent. */
    const val EDITOR_HIGHLIGHT_STRENGTH = "klang-editor-highlight-strength"

    /** Until KLANG-00422, read once into [EDITOR]: whether the editor shows quick help on hover, `true` or `false`. */
    const val EDITOR_HOVER_HELP = "klang-editor-hover-help"

    /** The Gui-dance's story arcs done, their ids joined by commas (KLANG-00350). */
    const val GUI_DANCE_ARCS_DONE = "klang-gui-dance-arcs-done"

    /** Until when the Gui-dance's walkthroughs are muted, milliseconds since the epoch; 0 for not muted (KLANG-00350). */
    const val GUI_DANCE_MUTED_UNTIL = "klang-gui-dance-muted-until"

    /**
     * The user's random seeds, one document (KLANG-00422): `RandomSeeds`, with the seed the user's metal grows from (the
     * buttons' grain, the brushed metal, the start page's sheet), made once, on the first visit, so each user has a
     * sheet of their own and keeps it (the maintainer, 2026-10-28: "every user should get a ui-seed in the storage").
     */
    const val SEEDS = "klang-seeds"

    /** Until KLANG-00422, read once into [SEEDS]: the metal's seed, a whole number. */
    const val SEED_METAL = "klang-seed-metal"

    /**
     * The workbench's layout, one document (KLANG-00422): `BenchLayout`, the pinned tool windows and the bottom panel.
     * Kept twice under this key: in the tab's own storage, the tab's layout, and in the browser's storage, the last
     * layout of any tab, which a new tab begins with.
     */
    const val WORKBENCH = "klang-workbench"

    /** Until KLANG-00422, read once into [WORKBENCH]: the layout every tab shared, as JSON (KLANG-00349). */
    const val WORKBENCH_LAYOUT = "klang-workbench-layout"

    /** The songs and lessons opened last, one document (KLANG-00422): `LastOpened`. */
    const val LAST_OPENED = "klang-last-opened"

    /** Until KLANG-00422, read once into [LAST_OPENED]: the songs and lessons opened last, a JSON list (KLANG-00403). */
    const val RECENTS = "klang-recents"

    /** The lessons done, one document (KLANG-00422): each lesson's slug with `true`, or `false` when taken back. */
    const val LESSONS_DONE = "klang-lessons-done"

    /** A song's code; [version] is the app's version, [songId] the song's id (a built-in song's, or "new"). */
    fun songCode(version: String, songId: String): String = songKey(version = version, songId = songId, part = "code")

    /** A song's title; [version] and [songId] as for [songCode]. */
    fun songTitle(version: String, songId: String): String = songKey(version = version, songId = songId, part = "title")

    /** A song's rpm; [version] and [songId] as for [songCode]. */
    fun songRpm(version: String, songId: String): String = songKey(version = version, songId = songId, part = "rpm")

    /** A song's bookmarks; [version] and [songId] as for [songCode]. */
    fun songBookmarks(version: String, songId: String): String =
        songKey(version = version, songId = songId, part = "bookmarks")

    /** Until KLANG-00422, read once into [LESSONS_DONE]: whether the tutorial [slug] is completed, `true` when it is. */
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
    const val RANDOM_SEEDS = "Random seeds"

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
        Part(
            name = "Put aside",
            about = "What a Klang of this browser could not read, or read only in part, kept as it was",
            precious = true,
        ) { it.endsWith(DocumentStorage.UNREADABLE) || it.endsWith(DocumentStorage.NEWER) },
        Part(name = "The look worn", about = "The look you chose last", covers = exactly(LOOK)),
        Part(name = "Master volume", about = "The Motor's output level", covers = exactly(OUTPUT, MASTER_VOLUME)),
        Part(name = "Keys", about = "The keys you gave Klang's actions", precious = true, covers = exactly(USER_KEYS, KEYMAP)),
        Part(
            name = "Editor",
            about = "The code highlights on or off, per sound event and their strength, quick help on hover",
            covers = exactly(
                EDITOR,
                EDITOR_HIGHLIGHTS_ON,
                EDITOR_HIGHLIGHTS_PER_EVENT,
                EDITOR_HIGHLIGHT_STRENGTH,
                EDITOR_HOVER_HELP,
            ),
        ),
        Part(
            name = "Walkthroughs",
            about = "The walkthroughs done and their mute",
            covers = exactly(GUI_DANCE_ARCS_DONE, GUI_DANCE_MUTED_UNTIL),
        ),
        Part(
            name = "Workbench",
            about = "Pinned tool windows and the bottom panel",
            covers = exactly(WORKBENCH, WORKBENCH_LAYOUT),
        ),
        Part(name = "Last opened", about = "The songs and lessons the Motor's menu offers to pick up", covers = exactly(LAST_OPENED, RECENTS)),
        Part(name = "Lessons done", about = "The tutorials you finished") {
            it == LESSONS_DONE || it.startsWith("klang-tutorial-completed-")
        },
        Part(
            name = "MIDI play code",
            about = "The MIDI play code with your instruments, and the instruments saved before it",
            precious = true,
            covers = exactly(MIDI_PLAY_CODE, MIDI_INSTRUMENTS, MIDI_INSTRUMENT_DRAFT),
        ),
        Part(name = "MIDI loadout", about = "The instruments the instrument keys step through", covers = exactly(MIDI_LOADOUT)),
        Part(name = "Laptop keys' layout", about = "The letters the laptop keyboard prints", covers = exactly(MIDI_KEY_LAYOUT)),
        // The random seeds (the maintainer, 2026-10-28: "Make a random seeds "section" in the storage and the first
        // entry will be the seed for grain noise and the metal brushes"); reset, one is made anew on the next load
        Part(
            name = "Metal",
            about = "The buttons' grain, the brushed metal and the start page's sheet grow from it",
            section = RANDOM_SEEDS,
            covers = exactly(SEEDS, SEED_METAL),
        ),
    )
}
