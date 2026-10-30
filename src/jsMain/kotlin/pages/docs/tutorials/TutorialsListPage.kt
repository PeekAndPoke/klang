/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.appLink
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.section
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.TutorialsListPage() = comp {
    TutorialsListPage(it)
}

/**
 * The Learn overview on the workbench (KLANG-00370, the canvas "Klang Learn on the workbench", board "Learn: the
 * overview"): the next lesson to continue with, then the tracks as panels, each a link to its track's page with a lamp
 * per lesson (the maintainer, 2026-10-27: "put each path on a panel ... multiple in one row"); while one searches or
 * filters, the lessons found as tiles under their tracks. The search and the level and depth filters are in the
 * Learn column (LearnTracksColumn), the area's own, from the sidebar; the filters live in this page's address.
 */
class TutorialsListPage(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        const val PARAM_DIFFICULTY = "difficulty"
        const val PARAM_DEPTH = "depth"

        /** The levels a filter list in the address names (`Beginner,Advanced`); an unknown name is left out. */
        fun difficultiesFromParam(param: String?): Set<TutorialDifficulty> =
            namesIn(param).mapNotNull { name -> TutorialDifficulty.entries.find { it.name.equals(name, ignoreCase = true) } }.toSet()

        /** The depths a filter list in the address names. */
        fun depthsFromParam(param: String?): Set<TutorialDepth> =
            namesIn(param).mapNotNull { name -> TutorialDepth.entries.find { it.name.equals(name, ignoreCase = true) } }.toSet()

        private fun namesIn(param: String?): List<String> = param.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val done by subscribingTo(TutorialStorage.done)

    /** The Gui-dance's lit step: the First Steps panel is one. */
    private val guiDanceLit by subscribingTo(KlangGuiDance.dance.lit)

    /** The search, typed in the Learn column (LearnTracksColumn). */
    private val searchText by subscribingTo(LearnSearch.text)

    /**
     * The address, for the levels and depths chosen in the Learn column (several of each, lists in it: a link keeps
     * them). Read, never written here: the column writes it, a step in the history for each choice.
     */
    private val currentRoute by subscribingTo(router.current)

    private val difficultyParam: String get() = currentRoute.matchedRoute.queryParam(PARAM_DIFFICULTY)

    private val depthParam: String get() = currentRoute.matchedRoute.queryParam(PARAM_DEPTH)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun filtering(): Boolean =
        searchText.isNotBlank() || difficultiesFromParam(difficultyParam).isNotEmpty() || depthsFromParam(depthParam).isNotEmpty()

    /**
     * Whether a lesson answers the search and the keys. The search reads words, each to be found somewhere: the title,
     * the description, a topic, the strand, the level and the depth; "done", "open" and "next" find lessons by where
     * the user stands ("beginner rhythm" finds the beginners' rhythm lessons).
     */
    private fun matches(lesson: Tutorial, progress: TutorialProgress): Boolean {
        val words = searchText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val state = progress.stateOf(lesson)
        val stateWord = when (state) {
            LessonState.Done -> "done"
            LessonState.Next -> "next open"
            LessonState.Open, LessonState.Later -> "open"
        }
        val text = listOf(
            lesson.title,
            lesson.description,
            lesson.strand()?.label.orEmpty(),
            lesson.difficulty.label,
            lesson.depth.label,
            stateWord,
        ) + lesson.tags.map { it.label }

        val matchesSearch = words.all { word -> text.any { it.contains(word, ignoreCase = true) } }
        val difficulties = difficultiesFromParam(difficultyParam)
        val depths = depthsFromParam(depthParam)

        // Several of a kind: any of them; none: all
        return matchesSearch &&
            (difficulties.isEmpty() || lesson.difficulty in difficulties) &&
            (depths.isEmpty() || lesson.depth in depths)
    }

    override fun VDom.render() {
        val progress = TutorialProgress(done = done)

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "22px")
                put("padding", "28px 36px")
                put("color", "var(--kl-ink-text)")
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "baseline")
                    put("flex-wrap", "wrap")
                    put("column-gap", "16px")
                }

                learnLabel("Learn")
                span {
                    css {
                        put("font-size", "14px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"Step by step, from your first beat to sound design: each lesson a page of short examples you play and change."
                }
            }

            progress.next?.takeIf { !filtering() }?.let { next -> renderContinue(next) }

            // Not looking for anything: each track a panel, to its page (the maintainer, 2026-10-27: "put each path on a
            // panel ... Clicking the panel will go to the details page"); looking: the lessons found, under their tracks
            if (filtering()) {
                renderFound(progress = progress)
            } else {
                renderPanels(progress = progress)
            }
        }
    }

    /** While one searches or filters: the lessons found, as tiles under their tracks; nothing found says so. */
    private fun FlowContent.renderFound(progress: TutorialProgress) {
        var shown = 0

        for (track in allTracks) {
            val lessons = track.lessons.filter { matches(it, progress) }

            if (lessons.isEmpty()) {
                continue
            }

            shown++
            renderTrack(track = track, lessons = lessons, progress = progress)
        }

        if (shown == 0) {
            div {
                key = "nothing-found"
                css { put("color", "var(--kl-ink-label)") }

                +"No lesson matches. Try fewer words, or another level or depth."
            }
        }
    }

    /** The next lesson on the main path, large: one press opens it. */
    private fun FlowContent.renderContinue(next: Tutorial) {
        val number = theKlangPathTrack.lessons.indexOf(next) + 1

        div {
            key = "continue"
            css { put("display", "flex") }

            KlangButton(
                onClick = { router.navToUri(Nav.tutorialInTrack(slug = next.slug, trackSlug = theKlangPathTrack.slug)) },
                shape = KlangButton.Shape.Tile,
                lamp = KlangLamp.State.Try,
            ) {
                attributes["data-continue"] = next.slug
                css {
                    put("flex", "1")
                    put("padding", "14px 18px 14px 30px")
                    put("text-align", "left")
                }

                span {
                    css {
                        put("display", "block")
                        put("font-size", "11px")
                        put("letter-spacing", "0.14em")
                        put("font-weight", "400")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"CONTINUE THE KLANG PATH"
                }

                span {
                    css {
                        put("display", "block")
                        put("font-size", "20px")
                        put("color", "var(--kl-ink-strong)")
                    }

                    +"$number. ${next.title}"
                }

                span {
                    css {
                        put("display", "block")
                        put("font-size", "13px")
                        put("font-weight", "400")
                    }

                    +listOfNotNull(next.strand()?.label, next.difficulty.label, next.depth.label.lowercase())
                        .joinToString(", ")
                    +": ${next.description}"
                }
            }
        }
    }

    /** The tracks as panels, one to three in a row as the page is wide: each a link to its track's page. */
    private fun FlowContent.renderPanels(progress: TutorialProgress) {
        val gapPx = 14

        div {
            key = "panels"
            css {
                put("display", "grid")
                // At least 300px and a third of the row less its two gaps, so never more than three; on a narrow page
                // one, never wider than the page
                put("grid-template-columns", "repeat(auto-fill, minmax(min(100%, max(300px, calc((100% - ${2 * gapPx}px) / 3))), 1fr))")
                put("gap", "${gapPx}px")
            }

            for (track in allTracks) {
                // First Steps is the Gui-dance's last step of its arc: lit, it wears the beacon
                val step = KlangGuiDance.LEARN_FIRST_STEPS.takeIf { track == firstStepsTrack }
                val lit = step != null && guiDanceLit == step

                appLink(router, Nav.tutorialTrack(track.slug), onFollow = step?.let { { KlangGuiDance.dance.used(it) } }) {
                    key = "panel-${track.slug}"
                    attributes["data-track"] = track.slug
                    attributes["class"] = listOfNotNull(
                        "kl-app-link",
                        LearnStyles.trackPanel.toString(),
                        KlangControlStyles.beacon.toString().takeIf { lit },
                    ).joinToString(" ")

                    div {
                        css {
                            put("display", "flex")
                            put("align-items", "baseline")
                            put("flex-wrap", "wrap")
                            put("column-gap", "10px")
                        }

                        span {
                            css {
                                put("font-size", "18px")
                                put("font-weight", "700")
                                put("color", "var(--kl-ink-strong)")
                            }

                            +track.title
                        }

                        span {
                            css {
                                put("font-size", "12px")
                                put("color", "var(--kl-ink-label)")
                            }

                            +"${progress.doneIn(track)} of ${track.lessons.size} done"
                        }
                    }

                    div {
                        css {
                            put("flex", "1")
                            put("font-size", "13px")
                            put("line-height", "1.45")
                            put("color", "var(--kl-ink-label)")
                        }

                        +track.description
                    }

                    // A lamp per lesson, in the track's order: where one stands at a glance
                    div {
                        css {
                            put("display", "flex")
                            put("flex-wrap", "wrap")
                            put("gap", "6px")
                        }

                        for (lesson in track.lessons) {
                            val state = progress.stateOf(lesson)

                            span {
                                attributes["data-lesson"] = lesson.slug
                                attributes["data-state"] = state.name
                                css { put("display", "inline-flex") }

                                KlangLamp(state = state.lamp(), size = 10.px)
                            }
                        }
                    }
                }
            }
        }
    }

    /** A track while one looks for lessons: its title, how far, what it is, and the lessons found as tiles. */
    private fun FlowContent.renderTrack(track: TutorialTrackDef, lessons: List<Tutorial>, progress: TutorialProgress) {
        section {
            key = "track-${track.slug}"
            attributes["data-track"] = track.slug
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "10px")
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "baseline")
                    put("flex-wrap", "wrap")
                    put("column-gap", "12px")
                }

                h2 {
                    css {
                        put("margin", "0")
                        put("font-size", "18px")
                        put("color", "var(--kl-ink-strong)")
                    }

                    appLink(router, Nav.tutorialTrack(track.slug)) { +track.title }
                }

                span {
                    css {
                        put("font-size", "12px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"${progress.doneIn(track)} of ${track.lessons.size} done"
                }

                span {
                    css {
                        put("font-size", "13px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +track.description
                }
            }

            renderTiles(track = track, lessons = lessons, progress = progress)
        }
    }

    private fun FlowContent.renderTiles(track: TutorialTrackDef, lessons: List<Tutorial>, progress: TutorialProgress) {
        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "repeat(auto-fill, minmax(190px, 1fr))")
                put("gap", "8px")
            }

            for (lesson in lessons) {
                div {
                    key = "lesson-${lesson.slug}"
                    css { put("display", "flex") }

                    lessonTile(
                        router = router,
                        lesson = lesson,
                        number = track.lessons.indexOf(lesson) + 1,
                        state = progress.stateOf(lesson),
                        track = track,
                    )
                }
            }
        }
    }
}
