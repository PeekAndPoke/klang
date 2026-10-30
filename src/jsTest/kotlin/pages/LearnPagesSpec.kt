/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.RouterWithTransitions
import io.peekandpoke.klang.layouts.WorkbenchSlots
import io.peekandpoke.klang.pages.docs.tutorials.LearnSearch
import io.peekandpoke.klang.pages.docs.tutorials.LearnTracksColumn
import io.peekandpoke.klang.pages.docs.tutorials.TutorialColumn
import io.peekandpoke.klang.pages.docs.tutorials.TutorialPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialTrackPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialsListPage
import io.peekandpoke.klang.pages.docs.tutorials.allTracks
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.pages.docs.tutorials.firstStepsTrack
import io.peekandpoke.klang.pages.docs.tutorials.patternLanguageTrack
import io.peekandpoke.klang.pages.docs.tutorials.soundDesignBasicsTrack
import io.peekandpoke.klang.pages.docs.tutorials.theKlangPathTrack
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.pixijs.pixiJs
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.routing.RootRouterBuilder
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.TestBedScope
import io.peekandpoke.kraft.testing.awaitUri
import io.peekandpoke.kraft.testing.eventually
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.html.div
import org.w3c.dom.Element
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.js.Date

/**
 * The Learn pages on hash routes, each with its column beside it, as the workbench shows them; the app's own router,
 * as the pages read theirs from the app.
 */
private fun RootRouterBuilder.learnRoutes() {
    useHashStrategy()

    mount(Nav.tutorials) {
        div(classes = "column") { LearnTracksColumn() }
        div(classes = "page") { TutorialsListPage() }
    }
    mount(Nav.tutorialTrack) {
        div(classes = "column") { LearnTracksColumn() }
        div(classes = "page") { TutorialTrackPage() }
    }
    mount(Nav.tutorial) {
        div(classes = "column") { TutorialColumn() }
        div(classes = "page") { TutorialPage() }
    }
}

/** The Learn area on the workbench (KLANG-00370): the overview, a track and a tutorial, their columns and lamps. */
class LearnPagesSpec : StringSpec({

    val path = theKlangPathTrack.lessons

    suspend fun withLearn(done: Set<String> = emptySet(), test: suspend TestBedScope.(root: Element) -> Unit) {
        allTutorials.forEach { TutorialStorage.setCompleted(it.slug, it.slug in done) }
        // The search is shared by the column and the overview: each case starts with it empty
        LearnSearch.text("")

        try {
            TestBed.preact(
                appSetup = {
                    // The app's addons: the examples' editors draw their highlights with PixiJS
                    addons {
                        browserDetect()
                        marked()
                        pixiJs(lazy = true)
                    }
                    routing { learnRoutes() }
                },
                view = { RouterWithTransitions() },
            ) { root ->
                test(root[0])
            }
        } finally {
            window.location.hash = ""
            allTutorials.forEach { TutorialStorage.setCompleted(it.slug, false) }
            WorkbenchSlots.clear()
            LearnSearch.text("")
        }
    }

    /** Goes to a place in Learn through the app's router, as a link would: [path] under the tutorials' base. */
    suspend fun TestBedScope.go(path: String = "") {
        val uri = "${Nav.tutorialsBase}$path"

        router.navToUri(uri)
        router.awaitUri(uri)
    }

    fun Element.states(selector: String): List<String> {
        val nodes = querySelectorAll("$selector [data-lesson][data-state]")

        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("data-state")!! }
    }

    fun Element.page() = querySelector(".router .current .page") as HTMLElement?

    fun Element.column() = querySelector(".router .current .column") as HTMLElement?

    /** A filter's checkbox in the column: the input of the [group]'s choice labelled [text]. */
    fun Element.choice(group: String, text: String): HTMLInputElement {
        val labels = column()!!.querySelectorAll("[role=group][aria-label=$group] label")

        return (0 until labels.length).map { labels.item(it) as HTMLElement }
            .first { it.textContent == text }
            .querySelector("input") as HTMLInputElement
    }

    "the overview: continue with the next lesson; the path's panel shows its lamps; the column lists the tracks, how far" {
        withLearn(done = setOf(path[0].slug, path[1].slug)) { root ->
            go()

            eventually { root.page()?.querySelector("[data-continue]")?.getAttribute("data-continue") shouldBe path[2].slug }

            // The path's panel: a lamp per lesson (the maintainer, 2026-10-27: each track on a panel)
            eventually { root.page()!!.querySelector("[data-track='${theKlangPathTrack.slug}']")!!.states("").size shouldBe path.size }

            val tiles = root.page()!!.querySelector("[data-track='${theKlangPathTrack.slug}']")!!.states("")

            tiles.take(4) shouldContainExactly listOf("Done", "Done", "Next", "Later")

            // Each state its lamp: next off (gold is only where one presses: Continue), later only its ring
            val klangPath = root.page()!!.querySelector("[data-track='${theKlangPathTrack.slug}']")!!

            root.page()!!.querySelector("[data-continue] [data-lamp]")?.getAttribute("data-lamp") shouldBe "Try"
            klangPath.querySelector("[data-lesson='${path[2].slug}'] [data-lamp]")?.getAttribute("data-lamp") shouldBe "Off"
            klangPath.querySelector("[data-lesson='${path[3].slug}'] [data-lamp]")?.getAttribute("data-lamp") shouldBe "Unlit"
            klangPath.querySelector("[data-lesson='${path[0].slug}'] [data-lamp]")?.getAttribute("data-lamp") shouldBe "On"
            tiles.count { it == "Next" } shouldBe 1

            // The column: only the tracks, each with how far (the maintainer, 2026-10-27: "only show the tracks")
            val listed = root.column()!!.querySelectorAll("[data-track]")

            (0 until listed.length).map { (listed.item(it) as Element).getAttribute("data-track") } shouldContainExactly
                allTracks.map { it.slug }
            root.column()!!.querySelector("[data-track='${theKlangPathTrack.slug}']")!!.textContent!! shouldContain "2 of ${path.size}"
            root.column()!!.querySelector("[data-lesson]") shouldBe null
        }
    }

    "not looking for anything, each track is a panel: a link to its page, a lamp per lesson; a click goes there" {
        withLearn { root ->
            go()

            eventually { root.page()?.querySelectorAll("a[data-track]")?.length shouldBe allTracks.size }

            for (track in allTracks) {
                val panel = root.page()!!.querySelector("a[data-track='${track.slug}']") as HTMLElement

                panel.getAttribute("href")!! shouldContain "/track/${track.slug}"
                panel.querySelectorAll("[data-lesson] [data-lamp]").length shouldBe track.lessons.size
            }

            // No key to open a track here: its page shows its lessons
            root.page()!!.querySelector("[aria-expanded]") shouldBe null

            // The First Steps panel wears the Gui-dance's beacon only while its step is lit
            val firstStepsLit = KlangGuiDance.dance.lit() == KlangGuiDance.LEARN_FIRST_STEPS

            (root.page()!!.querySelector("a[data-track='${firstStepsTrack.slug}']") as HTMLElement)
                .classList.contains(KlangControlStyles.beacon.toString()) shouldBe firstStepsLit
            root.page()!!.querySelector("[data-lesson] button") shouldBe null

            (root.page()!!.querySelector("a[data-track='${firstStepsTrack.slug}']") as HTMLElement).click()

            eventually {
                window.location.hash shouldContain "/track/${firstStepsTrack.slug}"
                root.page()!!.states("").size shouldBe firstStepsTrack.lessons.size
            }
        }
    }

    "the Gui-dance's First Steps arc ends on the First Steps panel: it wears the beacon, a click on it ends the arc" {
        val dance = KlangGuiDance.dance
        val doneBefore = dance.doneArcs().map { it.id }.toSet()
        val mutedBefore = dance.mutedUntil()

        try {
            KlangGuiDance.unmute()
            dance.reopen("first-steps")

            // Danced up to the panel, as a user would: every step before it used in turn, the rest after each finished
            // arc waited out
            repeat(20) {
                if (dance.mutedUntil() != null) {
                    KlangGuiDance.unmute()
                }

                val step = dance.lit() ?: error("nothing lit")

                if (step != KlangGuiDance.LEARN_FIRST_STEPS) {
                    dance.used(step)
                }
            }

            dance.lit() shouldBe KlangGuiDance.LEARN_FIRST_STEPS

            withLearn { root ->
                go()

                val beacon = KlangControlStyles.beacon.toString()

                eventually { root.page()?.querySelector("a[data-track='${firstStepsTrack.slug}']") shouldNotBe null }

                for (track in allTracks) {
                    val panel = root.page()!!.querySelector("a[data-track='${track.slug}']") as HTMLElement

                    panel.classList.contains(beacon) shouldBe (track == firstStepsTrack)
                }

                (root.page()!!.querySelector("a[data-track='${firstStepsTrack.slug}']") as HTMLElement).click()

                eventually { dance.doneArcs().map { it.id } shouldContain "first-steps" }
            }
        } finally {
            // As it was: the arcs this case finished open again, the mute back. An arc done before and reopened above
            // stays open if the case fails before its end: the dance has no way to mark an arc done but to dance it
            for (arc in KlangGuiDance.arcs.reversed()) {
                if (arc.id !in doneBefore) {
                    dance.reopen(arc.id)
                }
            }

            mutedBefore?.let { until -> KlangGuiDance.mute(forMs = until - Date.now()) }
        }
    }

    "typed on the overview, the search keeps the level chosen: no navigation that would drop it" {
        withLearn { root ->
            go()

            eventually { root.column()?.querySelector("[role=group][aria-label=Level]") shouldNotBe null }

            val beginner = { root.choice(group = "Level", text = "Beginner") }

            beginner().click()

            eventually { window.location.hash shouldContain "difficulty=" }

            val input = root.column()!!.querySelector("input") as HTMLInputElement

            input.value = "beat"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            eventually {
                (root.page()?.querySelectorAll("[data-lesson]")?.length ?: 0) shouldNotBe 0
                window.location.hash shouldContain "difficulty="
                beginner().checked shouldBe true
            }
        }
    }

    // In the app the column is the sidebar's and stays through the navigation (its focus with it); here each route
    // mounts its own column, so this case checks where the search leads, not the focus on the way
    "typed in the column on a track's page, the search takes the reader to the overview, its results there" {
        withLearn { root ->
            go("/track/${soundDesignBasicsTrack.slug}")

            eventually { root.column()?.querySelector("input") shouldNotBe null }

            val input = root.column()!!.querySelector("input") as HTMLInputElement

            input.value = "envelope"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            eventually {
                window.location.hash shouldBe "#${Nav.tutorialsBase}"
                root.page()?.querySelector("[data-continue]") shouldBe null
                (root.page()?.querySelectorAll("[data-lesson]")?.length ?: 0) shouldNotBe 0
            }
        }
    }

    "the search narrows the tiles by words, a level key by the level; nothing found says so" {
        withLearn { root ->
            go()

            eventually { root.column()?.querySelector("input") shouldNotBe null }

            // The search is in the column (the maintainer, 2026-10-27), apart from the tracks it narrows
            val input = root.column()!!.querySelector("input") as HTMLInputElement

            input.focus()
            input.value = "envelope"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            eventually {
                // The same field, still in focus, while the page redraws its tracks
                (root.column()!!.querySelector("input") === input) shouldBe true
                (document.activeElement === input) shouldBe true

                val shown = root.page()!!.querySelectorAll("[data-lesson]")
                val slugs = (0 until shown.length).map { (shown.item(it) as Element).getAttribute("data-lesson") }.toSet()

                slugs shouldBe allTutorials.filter { tutorial ->
                    listOf(tutorial.title, tutorial.description).any { it.contains("envelope", ignoreCase = true) }
                }.map { it.slug }.toSet()
            }

            // Words: each found somewhere, not the phrase as a whole
            input.value = "beginner rhythm"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            // Your First Beat is a beginners' rhythm lesson, and no lesson says "beginner rhythm" as one phrase; a
            // beginners' lesson with no rhythm anywhere has only one of the words
            val noRhythm = allTutorials.first { tutorial ->
                tutorial.difficulty.name == "Beginner" &&
                        (listOf(tutorial.title, tutorial.description) + tutorial.tags.map { it.label })
                            .none { it.contains("rhythm", ignoreCase = true) }
            }

            eventually {
                root.page()!!.querySelector("[data-lesson='${path[0].slug}']") shouldNotBe null
                root.page()!!.querySelector("[data-lesson='${noRhythm.slug}']") shouldBe null
            }

            input.value = "no such lesson at all"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            eventually { root.page()!!.textContent!!.contains("No lesson matches") shouldBe true }
        }
    }

    "a tutorial in a track: done and on goes to that track's next lesson; stay marks it done; the column's lamp lights" {
        withLearn { root ->
            go("/${path[2].slug}?${TutorialPage.PARAM_TRACK}=${patternLanguageTrack.slug}")

            eventually { root.page()?.querySelector("[data-done=stay]") shouldNotBe null }

            // In the Pattern Language, First Notes is followed by Subdivision; on the main path it would be The Four Waveforms
            root.column()!!.querySelector("[aria-current=page]")?.getAttribute("data-lesson") shouldBe path[2].slug

            (root.page()!!.querySelector("[data-done=stay]") as HTMLElement).click()

            eventually {
                TutorialStorage.isCompleted(path[2].slug) shouldBe true
                root.column()!!.querySelector("[data-lesson='${path[2].slug}']")?.getAttribute("data-state") shouldBe "Done"
                root.page()!!.querySelector("[data-next]")?.getAttribute("data-next") shouldBe patternLanguageTrack.lessons[3].slug
            }

            (root.page()!!.querySelector("[data-done=undo]") as HTMLElement).click()

            eventually { root.page()!!.querySelector("[data-done=next]") shouldNotBe null }

            (root.page()!!.querySelector("[data-done=next]") as HTMLElement).click()

            eventually {
                TutorialStorage.isCompleted(path[2].slug) shouldBe true
                root.page()?.querySelector("h1")?.textContent shouldBe patternLanguageTrack.lessons[3].title
            }
        }
    }

    "from one tutorial to the next, the column stays the tutorial's: the page fading out takes nothing away" {
        withLearn { root ->
            go("/${path[0].slug}")

            eventually { WorkbenchSlots.column() shouldNotBe null }

            go("/${path[1].slug}")

            eventually { root.page()?.querySelector("h1")?.textContent shouldBe path[1].title }

            // Past the fade (200 ms): the copy of the page before is gone, and so would be the column it emptied
            delay(600)

            WorkbenchSlots.column() shouldNotBe null
        }
    }

    "a hand-written address lights the lamps in any case, and each lets go on a click" {
        withLearn { root ->
            go("?difficulty=beginner,%20Expert")

            eventually { root.choice(group = "Level", text = "Beginner").checked shouldBe true }

            root.choice(group = "Level", text = "Beginner").click()

            eventually {
                root.choice(group = "Level", text = "Beginner").checked shouldBe false
                // The unknown name is gone with it
                window.location.hash shouldNotContain "difficulty="
            }
        }
    }

    "the level filters in the column: several at once, any of them shown, kept in the address as a list" {
        withLearn { root ->
            go()

            eventually { root.column()?.querySelector("[role=group][aria-label=Level]") shouldNotBe null }

            fun shown() = root.page()!!.querySelectorAll("[data-lesson]").let { nodes ->
                (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("data-lesson") }.toSet()
            }

            fun lessonsOf(vararg levels: String) = allTutorials.filter { it.difficulty.name in levels }.map { it.slug }.toSet()

            // Intermediate first: the address keeps the levels' own order all the same
            root.choice(group = "Level", text = "Intermediate").click()

            eventually {
                root.choice(group = "Level", text = "Intermediate").checked shouldBe true
                shown() shouldBe lessonsOf("Intermediate")
            }

            root.choice(group = "Level", text = "Beginner").click()

            eventually {
                withClue(window.location.hash) {
                    decodeURIComponent(window.location.hash) shouldContain "difficulty=Beginner,Intermediate"
                }
                shown() shouldBe lessonsOf("Beginner", "Intermediate")
            }

            // A depth narrows the levels' lessons further
            root.choice(group = "Depth", text = "Quick").click()

            eventually {
                root.choice(group = "Depth", text = "Quick").checked shouldBe true
                decodeURIComponent(window.location.hash) shouldContain "depth=Quick"
                shown() shouldBe allTutorials
                    .filter { it.difficulty.name in setOf("Beginner", "Intermediate") && it.depth.name == "Quick" }
                    .map { it.slug }.toSet()
            }

            root.choice(group = "Depth", text = "Quick").click()

            eventually { root.choice(group = "Depth", text = "Quick").checked shouldBe false }

            // Let go of one: the other stays
            root.choice(group = "Level", text = "Beginner").click()

            eventually {
                root.choice(group = "Level", text = "Beginner").checked shouldBe false
                shown() shouldBe lessonsOf("Intermediate")
            }
        }
    }

    "a tutorial puts its column and the help panel into the workbench; the overview takes them back out" {
        withLearn { root ->
            go("/${path[0].slug}")

            eventually {
                WorkbenchSlots.column() shouldNotBe null
                WorkbenchSlots.bottom() shouldNotBe null
            }

            go()

            eventually { root.page()?.querySelector("[data-continue]") shouldNotBe null }
            // Past the fade
            delay(600)

            WorkbenchSlots.column() shouldBe null
            WorkbenchSlots.bottom() shouldBe null
        }
    }

    "the lesson's end: gold only on the next lesson; a track's last lesson, once done, leads back to Learn" {
        withLearn(done = setOf(path[0].slug, path[1].slug)) { root ->
            // First Notes is next on the path, and First Steps' last lesson
            go("/${path[2].slug}?${TutorialPage.PARAM_TRACK}=${firstStepsTrack.slug}")

            eventually { root.page()?.querySelector("[data-done=next] [data-lamp]")?.getAttribute("data-lamp") shouldBe "Try" }

            (root.page()!!.querySelector("[data-done=next]") as HTMLElement).click()

            eventually { root.page()?.querySelector("[data-next=learn]") shouldNotBe null }

            // The Shape of a Note is open, not next (The Four Waveforms is): its end has no gold
            go("/${path[4].slug}")

            eventually {
                root.page()?.querySelector("h1")?.textContent shouldBe path[4].title
                root.page()?.querySelector("[data-done=next]") shouldNotBe null
                root.page()?.querySelector("[data-done=next] [data-lamp]") shouldBe null
            }
        }
    }
})

private external fun decodeURIComponent(encoded: String): String
