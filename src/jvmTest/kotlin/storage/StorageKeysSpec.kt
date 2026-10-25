/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

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
})
