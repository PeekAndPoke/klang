/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.TutorialTrackPage() = comp {
    TutorialTrackPage(it)
}

/**
 * One track on its own (KLANG-00370): its title, how far, what it builds on, and its lessons as tiles in its order.
 * A lesson opened from here carries the track, so the tutorial page walks this track.
 */
class TutorialTrackPage(ctx: NoProps) : PureComponent(ctx) {

    private val currentRoute by subscribingTo(router.current)

    private val done by subscribingTo(TutorialStorage.done)

    private fun currentTrack(): TutorialTrackDef? = allTracks.find { it.slug == currentRoute.matchedRoute["slug"] }

    override fun VDom.render() {
        val track = currentTrack()
        val progress = TutorialProgress(done = done)

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "16px")
                put("padding", "28px 36px")
                put("color", "var(--kl-ink-text)")
            }

            backLink(router = router, label = "Learn", to = Nav.tutorials())

            if (track == null) {
                div { +"There is no such track." }

                return@div
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "baseline")
                    put("flex-wrap", "wrap")
                    put("column-gap", "14px")
                }

                h1 {
                    css {
                        put("margin", "0")
                        put("font-size", "26px")
                        put("color", "var(--kl-ink-strong)")
                    }

                    +track.title
                }

                span {
                    css {
                        put("font-size", "13px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"${progress.doneIn(track)} of ${track.lessons.size} done"
                }
            }

            div {
                css { put("font-size", "15px") }

                +track.description
            }

            if (track.buildsOn.isNotEmpty()) {
                div {
                    css {
                        put("font-size", "13px")
                        put("color", "var(--kl-ink-label)")
                    }

                    +"Builds on "

                    for ((index, other) in track.buildsOn.withIndex()) {
                        if (index > 0) {
                            +", "
                        }

                        appLink(router, Nav.tutorialTrack(other.slug)) {
                            span {
                                css { put("color", "var(--kl-ink-text)") }
                                +other.title
                            }
                        }
                    }
                }
            }

            div {
                css {
                    put("display", "grid")
                    put("grid-template-columns", "repeat(auto-fill, minmax(190px, 1fr))")
                    put("gap", "8px")
                }

                for ((index, lesson) in track.lessons.withIndex()) {
                    div {
                        key = "lesson-${lesson.slug}"
                        css { put("display", "flex") }

                        lessonTile(router = router, lesson = lesson, number = index + 1, state = progress.stateOf(lesson), track = track)
                    }
                }
            }
        }
    }
}
