/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.ui.comp.KlangBackLink
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.kraft.routing.Route
import io.peekandpoke.kraft.routing.Router
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import kotlinx.html.A
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.span

/**
 * The Learn area's shared parts (KLANG-00370, the canvas "Klang Learn on the workbench"): a lesson's lamp, its tile,
 * the small engraved label, a link that stays in the app, the way back, a track's lesson list (both columns show it:
 * `KlangPathColumn`, `TutorialColumn`), and the track a tutorial is read in.
 */

/** A lesson's state in a word, for a screen reader beside its lamp (the lamp itself is hidden from them). */
fun LessonState.word(): String = when (this) {
    LessonState.Done -> "done"
    LessonState.Next -> "next"
    LessonState.Open -> "open"
    LessonState.Later -> "later"
}

/**
 * A lesson's lamp: done lit, open off, later only its ring. Next is off too, its title bold: the gold "try me" is only
 * where one presses (the Continue tile, "Done. On to ..."), one of Learn's gold lamps at a time (the maintainer,
 * 2026-10-27). The Gui-dance's beacon on the bench (the Motor's menu, the strip's keys) is its own and may be lit too.
 */
fun LessonState.lamp(): KlangLamp.State = when (this) {
    LessonState.Done -> KlangLamp.State.On
    LessonState.Next -> KlangLamp.State.Off
    LessonState.Open -> KlangLamp.State.Off
    LessonState.Later -> KlangLamp.State.Unlit
}

/** A lesson as a tile: its lamp, its number and title, its strand and depth under it; a press opens it in [track]. */
fun FlowContent.lessonTile(router: Router, lesson: Tutorial, number: Int, state: LessonState, track: TutorialTrackDef?) {
    KlangButton(
        onClick = { router.navToUri(Nav.tutorialInTrack(slug = lesson.slug, trackSlug = track?.slug)) },
        shape = KlangButton.Shape.Tile,
        lamp = state.lamp(),
    ) {
        attributes["data-lesson"] = lesson.slug
        attributes["data-state"] = state.name
        css { put("flex", "1") }

        span {
            css {
                put("display", "block")
                put("text-align", "left")
                put("opacity", if (state == LessonState.Later) "0.7" else "1")

                if (state == LessonState.Next) {
                    put("color", "var(--kl-ink-strong)")
                }
            }

            +"$number. ${lesson.title}"
            span(classes = KlangControlStyles.visuallyHidden.toString()) { +", ${state.word()}" }
        }

        span {
            css {
                put("display", "block")
                put("font-size", "11px")
                put("font-weight", "400")
                put("color", "var(--kl-ink-label)")
            }

            // The next lesson says so: every tile's title is bold, its lamp is off as an open one's
            +listOfNotNull(
                "Next".takeIf { state == LessonState.Next },
                lesson.strand()?.label,
                lesson.depth.label.lowercase(),
            ).joinToString(", ")
        }
    }
}

/** A small engraved label over a group: "THE KLANG PATH", "OTHER TRACKS". */
fun FlowContent.learnLabel(text: String) {
    div {
        css {
            put("font-family", "monospace")
            put("font-size", "11px")
            put("letter-spacing", "0.18em")
            put("text-transform", "uppercase")
            put("color", "var(--kl-ink-label)")
        }

        +text
    }
}

/** A link to a place in the app: a real link (a new tab with a key held), a plain click stays in the app. */
fun FlowContent.appLink(router: Router, to: Route.Bound, block: A.() -> Unit) {
    // The colour from a class (klang.css): a caller's own css {} would replace an inline one
    a(href = router.strategy.render(to), classes = "kl-app-link") {
        onClick { event ->
            val mouse = event.asDynamic()

            if (mouse.ctrlKey != true && mouse.metaKey != true && mouse.shiftKey != true && mouse.altKey != true && mouse.button == 0) {
                event.preventDefault()
                router.navToUri(to)
            }
        }

        block()
    }
}

/**
 * A track's lessons as a list, numbered, each with its lamp; [current] (the lesson read) marked. The Learn column
 * shows The Klang Path so, a tutorial's column its track (the maintainer, 2026-10-27, of the tree of strands and
 * stages first drawn here: "Too much and not clear what it shows").
 */
fun FlowContent.lessonList(router: Router, track: TutorialTrackDef, progress: TutorialProgress, current: Tutorial?) {
    div {
        css {
            put("display", "flex")
            put("flex-direction", "column")
            put("gap", "2px")
            put("font-size", "13px")
        }

        for ((index, lesson) in track.lessons.withIndex()) {
            val isCurrent = lesson == current
            val state = progress.stateOf(lesson)

            appLink(router, Nav.tutorialInTrack(slug = lesson.slug, trackSlug = track.slug)) {
                attributes["data-lesson"] = lesson.slug
                attributes["data-state"] = state.name

                if (isCurrent) {
                    attributes["aria-current"] = "page"
                }

                div {
                    css {
                        put("display", "flex")
                        put("align-items", "center")
                        put("gap", "8px")
                        put("padding", "5px 8px")
                        put("border-radius", "var(--kl-radius)")

                        if (isCurrent) {
                            put("background", "var(--kl-ground-panel)")
                        }

                        if (isCurrent || state == LessonState.Next) {
                            put("color", "var(--kl-ink-strong)")
                            put("font-weight", "700")
                        }
                    }

                    KlangLamp(state = state.lamp())
                    span {
                        +"${index + 1}. ${lesson.title}"
                        span(classes = KlangControlStyles.visuallyHidden.toString()) { +", ${state.word()}" }
                    }
                }
            }
        }
    }
}

/** The common way back (`KlangBackLink`) to a place in the app: a real link, a plain click stays in the app. */
fun FlowContent.backLink(router: Router, label: String, to: Route.Bound) {
    KlangBackLink(label = label, href = router.strategy.render(to), onNavigate = { router.navToUri(to) })
}

/** The track a tutorial is read in: the one named in the address, else the main path; null if neither holds it. */
fun tutorialTrack(trackSlug: String?, tutorial: Tutorial): TutorialTrackDef? =
    allTracks.firstOrNull { it.slug == trackSlug && tutorial in it.lessons }
        ?: theKlangPathTrack.takeIf { tutorial in it.lessons }
