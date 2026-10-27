/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.appLink
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.routing.urlParam
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
 * overview"): the next lesson to continue with, a slim search with the level and the depth as keys, and the tracks as
 * rows of lesson tiles, each with its lamp. Every track starts folded to a row of lamps, a key opens one; the
 * Continue tile is the way in (the maintainer, 2026-10-27: "collapse all tracks but keep the big continue button").
 * The column (the tracks) is the Learn area's own, from the sidebar.
 */
class TutorialsListPage(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        const val PARAM_DIFFICULTY = "difficulty"
        const val PARAM_DEPTH = "depth"

        fun difficultyFromParam(param: String?): TutorialDifficulty? =
            TutorialDifficulty.entries.find { it.name.equals(param, ignoreCase = true) }

        fun depthFromParam(param: String?): TutorialDepth? =
            TutorialDepth.entries.find { it.name.equals(param, ignoreCase = true) }
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val done by subscribingTo(TutorialStorage.done)

    private var searchText by value("")

    private var difficultyParam: String by urlParam(name = PARAM_DIFFICULTY, default = "")

    private var depthParam: String by urlParam(name = PARAM_DEPTH, default = "")

    /** The tracks shown with their tiles; the others are a row of lamps. None at first. */
    private var unfolded: Set<String> by value(emptySet())

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun filtering(): Boolean =
        searchText.isNotBlank() || difficultyFromParam(difficultyParam) != null || depthFromParam(depthParam) != null

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
        val difficulty = difficultyFromParam(difficultyParam)
        val depth = depthFromParam(depthParam)

        return matchesSearch && (difficulty == null || lesson.difficulty == difficulty) && (depth == null || lesson.depth == depth)
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

            renderFilters()

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

    /**
     * The search, then the level's and the depth's keys; a pressed key presses off. Keyed: the Continue tile above it
     * goes while one searches, and without a key the first letter rebuilt the field and took its focus away.
     */
    private fun FlowContent.renderFilters() {
        div {
            key = "filters"
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("align-items", "center")
                put("gap", "14px")
            }

            div {
                css { put("width", "320px") }

                KlangSearchField(
                    label = "Search the lessons",
                    value = searchText,
                    onInput = { searchText = it },
                    placeholder = "Search lessons, topics, done, open",
                )
            }

            renderKeys(
                label = "Level",
                all = "All levels",
                keys = TutorialDifficulty.entries.map { it.name to it.label },
                selected = difficultyFromParam(difficultyParam)?.name,
                onSelect = { difficultyParam = it.orEmpty() },
            )

            renderKeys(
                label = "Depth",
                all = "Any depth",
                keys = TutorialDepth.entries.map { it.name to it.label },
                selected = depthFromParam(depthParam)?.name,
                onSelect = { depthParam = it.orEmpty() },
            )
        }
    }

    private fun FlowContent.renderKeys(
        label: String,
        all: String,
        keys: List<Pair<String, String>>,
        selected: String?,
        onSelect: (String?) -> Unit,
    ) {
        div {
            attributes["role"] = "group"
            attributes["aria-label"] = label
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("gap", "4px")
            }

            KlangButton(onClick = { onSelect(null) }, pressed = selected == null) { +all }

            for ((name, text) in keys) {
                KlangButton(onClick = { onSelect(if (selected == name) null else name) }, pressed = selected == name) {
                    +text
                }
            }
        }
    }

    /** A track: its title, how far, what it is; its tiles, or folded, a lamp per lesson. */
    private fun FlowContent.renderTrack(track: TutorialTrackDef, lessons: List<Tutorial>, progress: TutorialProgress) {
        val folded = track.slug !in unfolded && !filtering()

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

            if (folded) {
                renderFolded(track = track, progress = progress)
            } else {
                renderTiles(track = track, lessons = lessons, progress = progress)
            }
        }
    }

    /** A folded track: a lamp per lesson, and the key that opens it. */
    private fun FlowContent.renderFolded(track: TutorialTrackDef, progress: TutorialProgress) {
        div {
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "6px")
            }

            for (lesson in track.lessons) {
                KlangLamp(state = progress.stateOf(lesson).lamp(), size = 10.px)
            }

            div { css { put("width", "10px") } }

            KlangButton(onClick = { unfolded = unfolded + track.slug }) {
                attributes["aria-expanded"] = "false"
                +"Show its ${track.lessons.size} lessons"
            }
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
