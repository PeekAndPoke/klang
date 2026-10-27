/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.RouterWithTransitions
import io.peekandpoke.klang.layouts.WorkbenchSlots
import io.peekandpoke.klang.pages.docs.tutorials.KlangPathColumn
import io.peekandpoke.klang.pages.docs.tutorials.TutorialColumn
import io.peekandpoke.klang.pages.docs.tutorials.TutorialPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialTrackPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialsListPage
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.pages.docs.tutorials.firstStepsTrack
import io.peekandpoke.klang.pages.docs.tutorials.patternLanguageTrack
import io.peekandpoke.klang.pages.docs.tutorials.theKlangPathTrack
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.marked.marked
import io.peekandpoke.kraft.addons.pixijs.pixiJs
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.routing.RootRouterBuilder
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.html.div
import org.w3c.dom.Element
import org.w3c.dom.EventInit
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event

/**
 * The Learn pages on hash routes, each with its column beside it, as the workbench shows them; the app's own router,
 * as the pages read theirs from the app.
 */
private fun RootRouterBuilder.learnRoutes() {
    useHashStrategy()

    mount(Nav.tutorials) {
        div(classes = "column") { KlangPathColumn() }
        div(classes = "page") { TutorialsListPage() }
    }
    mount(Nav.tutorialTrack) {
        div(classes = "column") { KlangPathColumn() }
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

    suspend fun withLearn(done: Set<String> = emptySet(), test: suspend (root: Element) -> Unit) {
        allTutorials.forEach { TutorialStorage.setCompleted(it.slug, it.slug in done) }

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
        }
    }

    /** Goes to a place in Learn by the hash, as a link would: [path] under the tutorials' base. */
    fun go(path: String = "") {
        window.location.hash = "${Nav.tutorialsBase}$path"
    }

    fun Element.states(selector: String): List<String> {
        val nodes = querySelectorAll("$selector [data-lesson][data-state]")

        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("data-state")!! }
    }

    fun Element.page() = querySelector(".router .current .page") as HTMLElement?

    fun Element.column() = querySelector(".router .current .column") as HTMLElement?

    "the overview: continue with the next lesson; its tiles and the column's list show the same lamps" {
        withLearn(done = setOf(path[0].slug, path[1].slug)) { root ->
            go()

            eventually { root.page()?.querySelector("[data-continue]")?.getAttribute("data-continue") shouldBe path[2].slug }

            val tiles = root.page()!!.querySelector("[data-track='${theKlangPathTrack.slug}']")!!.states("")

            tiles.take(4) shouldContainExactly listOf("Done", "Done", "Next", "Later")

            // Each state its lamp: next off (gold is only where one presses: Continue), later only its ring
            val klangPath = root.page()!!.querySelector("[data-track='${theKlangPathTrack.slug}']")!!

            root.page()!!.querySelector("[data-continue] [data-lamp]")?.getAttribute("data-lamp") shouldBe "Try"
            klangPath.querySelector("[data-lesson='${path[2].slug}'] [data-lamp]")?.getAttribute("data-lamp") shouldBe "Off"
            klangPath.querySelector("[data-lesson='${path[3].slug}'] [data-lamp]")?.getAttribute("data-lamp") shouldBe "Unlit"
            klangPath.querySelector("[data-lesson='${path[0].slug}'] [data-lamp]")?.getAttribute("data-lamp") shouldBe "On"
            tiles.count { it == "Next" } shouldBe 1
            root.column()!!.states("").count { it == "Done" } shouldBe 2
            root.column()!!.querySelector("[data-lesson='${path[2].slug}']")?.getAttribute("data-state") shouldBe "Next"
        }
    }

    "a track not taken is folded to its lamps; its key opens its tiles" {
        withLearn { root ->
            go()

            val firstSteps = "[data-track='${firstStepsTrack.slug}']"

            eventually { root.page()?.querySelector(firstSteps) shouldNotBe null }

            root.page()!!.querySelector(firstSteps)!!.states("").size shouldBe 0

            (root.page()!!.querySelector("$firstSteps [aria-expanded=false]") as HTMLElement).click()

            eventually { root.page()!!.querySelector(firstSteps)!!.states("").size shouldBe firstStepsTrack.lessons.size }
        }
    }

    "the search narrows the tiles by words, a level key by the level; nothing found says so" {
        withLearn { root ->
            go()

            eventually { root.page()?.querySelector("input") shouldNotBe null }

            val input = root.page()!!.querySelector("input") as HTMLInputElement

            input.value = "envelope"
            input.dispatchEvent(Event("input", EventInit(bubbles = true)))

            eventually {
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

    "a level key keeps only that level's lessons and is kept in the address" {
        withLearn { root ->
            go()

            eventually { root.page()?.querySelector("[role=group][aria-label=Level]") shouldNotBe null }

            fun beginner() = root.page()!!.querySelectorAll("[role=group][aria-label=Level] button").let { keys ->
                (0 until keys.length).map { keys.item(it) as HTMLElement }.first { it.textContent == "Beginner" }
            }

            beginner().click()

            eventually {
                withClue(window.location.href) { window.location.href.contains("difficulty=Beginner") shouldBe true }
                beginner().getAttribute("aria-pressed") shouldBe "true"

                val shown = root.page()!!.querySelectorAll("[data-lesson]")
                val slugs = (0 until shown.length).map { (shown.item(it) as Element).getAttribute("data-lesson") }.toSet()

                slugs shouldBe allTutorials.filter { it.difficulty.name == "Beginner" }.map { it.slug }.toSet()
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
