/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.workshop.storage.DocumentStorage

/**
 * The keys the app keeps in the browser (KLANG-00323), byte for byte as the browsers of our users hold them: a key
 * that changes loses what is stored under it. A check for the future.
 *
 * The song keys are the ones `CodeSongPage` built before the storage moved to `storage/`
 * (`"song-$v-$songId-code"`, `-title`, `-rpm`); the Midi Playground's and the tutorials' are the ones their storage
 * objects held.
 */
class StorageKeysSpec : StringSpec({

    "a song's code, title and rpm keep the keys the song page used" {
        withClue("the code") {
            StorageKeys.songCode(version = "0.9.1", songId = "der-schmetterling") shouldBe
                    "song-0.9.1-der-schmetterling-code"
        }

        withClue("the title") {
            StorageKeys.songTitle(version = "0.9.1", songId = "new") shouldBe "song-0.9.1-new-title"
        }

        withClue("the rpm, with the version the app has before it knows its own") {
            StorageKeys.songRpm(version = "n/a", songId = "new") shouldBe "song-n/a-new-rpm"
        }
    }

    "a song's bookmarks sit beside them" {
        withClue("the bookmarks") {
            StorageKeys.songBookmarks(version = "0.9.1", songId = "der-schmetterling") shouldBe
                    "song-0.9.1-der-schmetterling-bookmarks"
        }
    }

    "the Midi Playground's and the tutorials' keys stay as they were" {
        withClue("the Midi Playground") {
            StorageKeys.MIDI_INSTRUMENTS shouldBe "klang-midi-instruments"
            StorageKeys.MIDI_INSTRUMENT_DRAFT shouldBe "klang-midi-instrument-draft"
            StorageKeys.MIDI_PLAY_CODE shouldBe "klang-midi-play-code"
            StorageKeys.MIDI_LOADOUT shouldBe "klang-midi-loadout"
        }

        withClue("the tutorials") {
            StorageKeys.tutorialCompleted(slug = "first-steps") shouldBe "klang-tutorial-completed-first-steps"
        }
    }

    "the master volume's key stays as it is" {
        StorageKeys.MASTER_VOLUME shouldBe "klang-master-volume"
    }

    "the keymap's key stays as it is" {
        StorageKeys.KEYMAP shouldBe "klang-keymap"
    }

    "the look's key stays as it is" {
        StorageKeys.LOOK shouldBe "klang-look"
    }

    "the editor's settings keep their key, and the keys they had before it" {
        StorageKeys.EDITOR shouldBe "klang-editor"
        StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT shouldBe "klang-editor-highlights-per-event"
        StorageKeys.EDITOR_HIGHLIGHT_STRENGTH shouldBe "klang-editor-highlight-strength"
        StorageKeys.EDITOR_HIGHLIGHTS_ON shouldBe "klang-editor-highlights-on"
        StorageKeys.EDITOR_HOVER_HELP shouldBe "klang-editor-hover-help"
    }

    "the Gui-dance keeps its key" {
        StorageKeys.GUI_DANCE_ARCS_DONE shouldBe "klang-gui-dance-arcs-done"
        StorageKeys.GUI_DANCE_MUTED_UNTIL shouldBe "klang-gui-dance-muted-until"
    }

    "the workbench's layout keeps its key, and the key it had before it" {
        StorageKeys.WORKBENCH shouldBe "klang-workbench"
        StorageKeys.WORKBENCH_LAYOUT shouldBe "klang-workbench-layout"
    }

    "the sections of KLANG-00422 keep their keys, and the keys they had before" {
        StorageKeys.OUTPUT shouldBe "klang-output"
        StorageKeys.LAST_OPENED shouldBe "klang-last-opened"
        StorageKeys.RECENTS shouldBe "klang-recents"
        StorageKeys.LESSONS_DONE shouldBe "klang-lessons-done"
        StorageKeys.SEEDS shouldBe "klang-seeds"
        StorageKeys.SEED_METAL shouldBe "klang-seed-metal"
    }

    "every key of a section and the key it had before belong to one part of the storage page" {
        val keys = listOf(
            StorageKeys.OUTPUT to "Master volume",
            StorageKeys.MASTER_VOLUME to "Master volume",
            StorageKeys.WORKBENCH to "Workbench",
            StorageKeys.WORKBENCH_LAYOUT to "Workbench",
            StorageKeys.LAST_OPENED to "Last opened",
            StorageKeys.RECENTS to "Last opened",
            StorageKeys.LESSONS_DONE to "Lessons done",
            StorageKeys.tutorialCompleted(slug = "first-steps") to "Lessons done",
            StorageKeys.SEEDS to "Metal",
            StorageKeys.SEED_METAL to "Metal",
        )

        for ((key, part) in keys) {
            withClue(key) { StorageKeys.parts.filter { it.covers(key) }.map { it.name } shouldBe listOf(part) }
        }
    }

    "what is put aside keeps its endings, and the storage page knows it as its own part" {
        DocumentStorage.UNREADABLE shouldBe "-unreadable"
        DocumentStorage.NEWER shouldBe "-newer"

        val aside = StorageKeys.parts.single { it.name == "Put aside" }

        aside.covers("klang-editor-unreadable") shouldBe true
        aside.covers("klang-editor-newer") shouldBe true
        aside.covers("klang-editor") shouldBe false
        aside.precious shouldBe true
    }
})
