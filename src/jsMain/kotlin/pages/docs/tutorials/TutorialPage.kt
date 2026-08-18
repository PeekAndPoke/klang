/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.PlayableCodeExample
import io.peekandpoke.klang.ui.comp.MarkdownDisplay
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.JustifyContent
import kotlinx.css.Padding
import kotlinx.css.Position
import kotlinx.css.alignItems
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.justifyContent
import kotlinx.css.marginTop
import kotlinx.css.padding
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.css.right
import kotlinx.css.top
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.TutorialPage() = comp {
    TutorialPage(it)
}

class TutorialPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    companion object {
        const val PARAM_TRACK = "track"
    }

    private val laf by subscribingTo(KlangTheme)
    private val currentRoute by subscribingTo(router.current)

    private var completedState by value(false)

    private fun currentSlug(): String = currentRoute.matchedRoute["slug"]

    private fun currentTutorial(): Tutorial? = allTutorials.find { it.slug == currentSlug() }

    /** The track context the reader is on — null when browsing without one. */
    private fun currentTrack(): TutorialTrackDef? {
        val trackSlug = currentRoute.matchedRoute.queryParams[PARAM_TRACK] ?: return null
        return allTracks.find { it.slug == trackSlug }
    }

    /** Prev/Next exist only within a track context. */
    private fun adjacentTutorials(): Pair<Tutorial?, Tutorial?> {
        val track = currentTrack() ?: return null to null
        val tutorial = currentTutorial() ?: return null to null
        val idx = track.lessons.indexOf(tutorial)
        if (idx < 0) return null to null
        val prev = if (idx > 0) track.lessons[idx - 1] else null
        val next = if (idx < track.lessons.size - 1) track.lessons[idx + 1] else null
        return prev to next
    }

    init {
        lifecycle {
            onMount {
                completedState = TutorialStorage.isCompleted(currentSlug())
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val tutorial = currentTutorial()

        ui.fluid.container {
            css { padding = Padding(2.rem) }

            if (tutorial == null) {
                ui.placeholder.segment {
                    ui.icon.header {
                        icon.dragon()
                        +"Tutorial not found"
                    }
                    ui.button {
                        onClick { router.navToUri(Nav.tutorials()) }
                        +"Back to Tutorials"
                    }
                }
                return@container
            }

            // Nav
            renderNav()

            // Header
            ui.segment {
                css { position = Position.relative }

                ui.large.header { +tutorial.title }

                if (completedState) {
                    span {
                        css {
                            position = Position.absolute
                            top = 12.px
                            right = 12.px
                            color = Color(laf.good)
                        }
                        icon.large.check_circle()
                    }
                }
                difficultyLabel(laf, tutorial.difficulty)
                scopeLabel(laf, tutorial.scope)
                tutorial.tags.forEach { tag ->
                    ui.mini.basic.label { +tag.label }
                }
                p {
                    css { marginTop = 1.rem }
                    +tutorial.description
                }
            }

            // Sections
            tutorial.sections.forEach { section ->
                ui.segment {
                    if (section.heading != null) {
                        ui.medium.header { +section.heading }
                    }

                    section.blocks.forEach { block ->
                        when (block) {
                            is Block.Text -> {
                                // Block text uses \n\n as paragraph breaks — render each as its own <p>
                                block.text.split("\n\n").forEach { paragraph ->
                                    if (paragraph.isNotBlank()) {
                                        p { +paragraph }
                                    }
                                }
                            }

                            is Block.Markdown -> MarkdownDisplay(markdown = block.markdown)

                            is Block.Code -> when (block.lang) {
                                "KlangScript" -> PlayableCodeExample(code = block.code, rpm = tutorial.rpm)
                                else -> pre { +block.code }
                            }

                            is Block.Visual.Adsr -> AdsrVisual(value = block.value, label = block.label)
                        }
                    }
                }
            }

            // Completed button
            ui.center.aligned.segment {
                if (completedState) {
                    ui.given(true) { with(laf.styles.goldButton()) }.button {
                        onClick {
                            completedState = TutorialStorage.toggleCompleted(tutorial.slug)
                        }
                        icon.check_circle()
                        +"Completed"
                    }
                } else {
                    ui.basic.button {
                        onClick {
                            completedState = TutorialStorage.toggleCompleted(tutorial.slug)
                        }
                        icon.circle_outline()
                        +"Mark as Completed"
                    }
                }
            }

            renderNav()
        }
    }

    private fun FlowContent.renderNav() {
        // Navigation — Prev/Next only exist within a track context
        val track = currentTrack()
        val tutorial = currentTutorial()
        val (prev, next) = adjacentTutorials()

        ui.segment {
            css {
                display = Display.flex
                justifyContent = JustifyContent.spaceBetween
                alignItems = Align.center
            }

            div {
                ui.black.button {
                    onClick { router.navToUri(Nav.tutorials()) }
                    icon.th_list()
                    +"All Tutorials"
                }

                if (track != null) {
                    ui.black.button {
                        onClick { router.navToUri(Nav.tutorialTrack(track.slug)) }
                        icon.map_signs()
                        +track.title
                    }
                }
            }

            if (track != null) {
                div {
                    if (prev != null) {
                        ui.black.button {
                            onClick { router.navToUri(Nav.tutorialInTrack(prev.slug, track.slug)) }
                            icon.arrow_left()
                            +prev.title
                        }
                    }

                    if (next != null) {
                        ui.black.button {
                            onClick { router.navToUri(Nav.tutorialInTrack(next.slug, track.slug)) }
                            +next.title
                            icon.arrow_right()
                        }
                    }
                }
            } else if (tutorial != null) {
                // No track context: the tracks containing this lesson are the way in
                div {
                    val containing = allTracks.filter { tutorial in it.lessons }
                    if (containing.isNotEmpty()) {
                        span { +"Part of: " }
                        containing.forEach { t ->
                            ui.mini.basic.button {
                                onClick { router.navToUri(Nav.tutorialInTrack(tutorial.slug, t.slug)) }
                                +t.title
                            }
                        }
                    }
                }
            }
        }
    }
}
