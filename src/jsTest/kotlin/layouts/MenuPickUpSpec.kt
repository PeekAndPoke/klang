/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.pages.docs.tutorials.filtersTutorial
import io.peekandpoke.klang.pages.docs.tutorials.soundDesignBasicsTrack
import io.peekandpoke.klang.pages.docs.tutorials.theFourWaveformsTutorial
import io.peekandpoke.klang.pages.docs.tutorials.theKlangPathTrack
import io.peekandpoke.klang.spine.Recents
import io.peekandpoke.klang.spine.Recents.Entry
import io.peekandpoke.klang.spine.Recents.Kind
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.kraft.routing.Route
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import kotlinx.browser.localStorage
import org.w3c.dom.HTMLElement

/** "Pick up where you left off" in the Motor's menu (KLANG-00403, step 2): what it says and where a pick leads. */
class MenuPickUpSpec : StringSpec({

    val minute = 60_000.0

    "the song whose page is open: a song's page gives its id, the new song's page the new song, any other page none" {
        Nav.songIdOf(route = Nav.editSongCode, id = "builtin-song-kokon") shouldBe "builtin-song-kokon"
        Nav.songIdOf(route = Nav.newSongCode, id = null) shouldBe Nav.NEW_SONG_ID
        Nav.songIdOf(route = Nav.songs, id = null) shouldBe null
    }

    "when something was opened, said plainly" {
        val now = 1_000_000_000.0

        MenuPickUp.whenText(now = now, at = now - 30_000) shouldBe "just now"
        MenuPickUp.whenText(now = now, at = now + 5 * minute) shouldBe "just now"
        MenuPickUp.whenText(now = now, at = now - 1 * minute) shouldBe "1 min ago"
        MenuPickUp.whenText(now = now, at = now - 59 * minute) shouldBe "59 min ago"
        MenuPickUp.whenText(now = now, at = now - 60 * minute) shouldBe "1 h ago"
        MenuPickUp.whenText(now = now, at = now - (24 * 60 - 1) * minute) shouldBe "23 h ago"
        MenuPickUp.whenText(now = now, at = now - 24 * 60 * minute) shouldBe "yesterday"
        MenuPickUp.whenText(now = now, at = now - 48 * 60 * minute) shouldBe "2 days ago"
    }

    "the track to go on with is the last lesson's, in the track it was opened in" {
        val entries = listOf(
            Entry(kind = Kind.Song, id = "s", title = "S", at = 3.0),
            Entry(kind = Kind.Tutorial, id = filtersTutorial.slug, title = "F", at = 2.0, track = soundDesignBasicsTrack.slug),
            Entry(kind = Kind.Tutorial, id = theFourWaveformsTutorial.slug, title = "W", at = 1.0),
        )
        val firstTwo = soundDesignBasicsTrack.lessons.take(2).map { it.slug }.toSet()

        val learning = MenuPickUp.continueOf(entries = entries, done = firstTwo)!!

        learning.title shouldBe soundDesignBasicsTrack.title
        learning.next shouldBe soundDesignBasicsTrack.lessons[2].title
        learning.done shouldBe 2
        learning.total shouldBe soundDesignBasicsTrack.lessons.size
        learning.target shouldBe Nav.tutorialInTrack(slug = soundDesignBasicsTrack.lessons[2].slug, trackSlug = soundDesignBasicsTrack.slug)
    }

    "a lesson opened in no track goes on in the main path; all done leads to the track's page" {
        val entries = listOf(Entry(kind = Kind.Tutorial, id = theFourWaveformsTutorial.slug, title = "W", at = 1.0))

        MenuPickUp.continueOf(entries = entries, done = emptySet())!!.title shouldBe theKlangPathTrack.title

        val allDone = MenuPickUp.continueOf(entries = entries, done = theKlangPathTrack.lessons.map { it.slug }.toSet())!!

        allDone.next shouldBe null
        allDone.target shouldBe Nav.tutorialTrack(theKlangPathTrack.slug)
    }

    "no lesson opened, or one the app no longer has: nothing to go on with" {
        MenuPickUp.continueOf(entries = emptyList(), done = emptySet()) shouldBe null

        val gone = Entry(kind = Kind.Tutorial, id = "no-such-lesson", title = "Gone", at = 2.0)

        MenuPickUp.continueOf(entries = listOf(gone), done = emptySet()) shouldBe null

        // One gone from the app is passed over for the lesson before it
        val before = Entry(kind = Kind.Tutorial, id = filtersTutorial.slug, title = "F", at = 1.0, track = soundDesignBasicsTrack.slug)

        MenuPickUp.continueOf(entries = listOf(gone, before), done = emptySet())!!.title shouldBe soundDesignBasicsTrack.title
    }

    "the songs opened show with their tempo, the newest first, the one whose page is open lit; a pick leads to the song" {
        val kept = localStorage.getItem(StorageKeys.RECENTS)
        val picked = mutableListOf<Route.Bound>()

        try {
            Recents.openedSong(id = "new", title = "My draft", rpm = 30.0)
            Recents.openedSong(id = "builtin-song-kokon", title = "Kokon", rpm = 19.0)
            // A built-in song gone from the app (renamed in an update): not offered
            Recents.openedSong(id = "builtin-song-gone", title = "Gone song", rpm = 25.0)

            TestBed.preact(view = { MenuPickUp(currentSongId = "builtin-song-kokon", onOpen = { picked.add(it) }) }) { root ->
                val view = root[0]

                // Other specs may have opened songs too (the record is the app's one): the two of this spec by their titles
                fun tile(title: String): HTMLElement? {
                    val all = view.querySelectorAll("button")

                    return (0 until all.length).map { all.item(it) as HTMLElement }
                        .firstOrNull { it.textContent?.startsWith(title) == true }
                }

                fun indexOf(title: String): Int {
                    val all = view.querySelectorAll("button")

                    return (0 until all.length).indexOfFirst { (all.item(it) as HTMLElement).textContent?.startsWith(title) == true }
                }

                eventually { (tile("Kokon") != null && tile("My draft") != null) shouldBe true }

                tile("Kokon")!!.textContent!! shouldContain "19 RPM"
                withClue("the lamp of the song whose page is open lit, the others dark") {
                    tile("Kokon")!!.querySelector("[data-lamp]")!!.getAttribute("data-lamp") shouldBe "On"
                    tile("My draft")!!.querySelector("[data-lamp]")!!.getAttribute("data-lamp") shouldBe "Off"
                }
                tile("Gone song") shouldBe null
                (indexOf("Kokon") < indexOf("My draft")) shouldBe true

                tile("Kokon")!!.click()
                tile("My draft")!!.click()

                picked shouldContainExactly listOf(Nav.editSongCode("builtin-song-kokon"), Nav.newSongCode())
            }
        } finally {
            if (kept == null) {
                localStorage.removeItem(StorageKeys.RECENTS)
            } else {
                localStorage.setItem(StorageKeys.RECENTS, kept)
            }
        }
    }
})
