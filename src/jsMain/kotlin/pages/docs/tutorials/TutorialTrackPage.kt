/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.JustifyContent
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.gap
import kotlinx.css.justifyContent
import kotlinx.css.marginLeft
import kotlinx.css.marginTop
import kotlinx.css.padding
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.TutorialTrackPage() = comp {
    TutorialTrackPage(it)
}

/**
 * Overview of one tutorial track: its lessons in order, with completion state.
 * Entering a lesson from here carries the track context, so Prev/Next on the
 * lesson page walk this track.
 */
class TutorialTrackPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)
    private val currentRoute by subscribingTo(router.current)

    private fun currentSlug(): String = currentRoute.matchedRoute["slug"]

    private fun currentTrack(): TutorialTrackDef? = allTracks.find { it.slug == currentSlug() }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val track = currentTrack()

        ui.fluid.container {
            css { padding = Padding(2.rem) }

            if (track == null) {
                ui.placeholder.segment {
                    ui.icon.header {
                        icon.dragon()
                        +"Track not found"
                    }
                    ui.button {
                        onClick { router.navToUri(Nav.tutorials()) }
                        +"Back to Tutorials"
                    }
                }
                return@container
            }

            // Nav
            ui.segment {
                ui.black.button {
                    onClick { router.navToUri(Nav.tutorials()) }
                    icon.th_list()
                    +"All Tutorials"
                }
            }

            // Header
            val completed = track.lessons.count { TutorialStorage.isCompleted(it.slug) }

            ui.segment {
                ui.large.header {
                    +track.title
                    ui.basic.label {
                        css { marginLeft = 1.rem }
                        icon.check_circle()
                        +"$completed of ${track.lessons.size} completed"
                    }
                }
                p { +track.description }

                if (track.buildsOn.isNotEmpty()) {
                    div {
                        css { marginTop = 1.rem }
                        span { +"Builds on: " }
                        track.buildsOn.forEach { other ->
                            ui.mini.basic.button {
                                onClick { router.navToUri(Nav.tutorialTrack(other.slug)) }
                                +other.title
                            }
                        }
                    }
                }
            }

            // Lessons, in track order
            track.lessons.forEachIndexed { index, lesson ->
                renderLesson(track, index, lesson)
            }
        }
    }

    private fun FlowContent.renderLesson(track: TutorialTrackDef, index: Int, lesson: Tutorial) {
        val isCompleted = TutorialStorage.isCompleted(lesson.slug)

        ui.segment {
            onClick { router.navToUri(Nav.tutorialInTrack(lesson.slug, track.slug)) }
            css {
                cursor = Cursor.pointer
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
                alignItems = Align.center
            }

            div {
                css {
                    display = Display.flex
                    alignItems = Align.center
                    gap = 0.75.rem
                }

                if (isCompleted) {
                    span {
                        css { color = Color(laf.good) }
                        icon.large.check_circle()
                    }
                } else {
                    ui.circular.basic.label { +"${index + 1}" }
                }

                div {
                    ui.small.header {
                        css { color = Color(laf.textPrimary) }
                        +lesson.title
                    }
                    noui.description {
                        css { color = Color(laf.textSecondary) }
                        +lesson.description
                    }
                }
            }

            div {
                css {
                    display = Display.flex
                    alignItems = Align.center
                    gap = 0.5.rem
                }

                difficultyLabel(laf, lesson.difficulty)
            }
        }
    }
}
