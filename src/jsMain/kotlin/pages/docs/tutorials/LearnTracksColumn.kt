/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.appLink
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

/**
 * What the Learn search holds: typed in the column, read by the overview, which narrows its tracks by it. It stays
 * while one reads: back from a lesson found by it, the overview shows the same results, until the field is cleared.
 */
object LearnSearch {
    val text: StreamSource<String> = StreamSource("")
}

@Suppress("FunctionName")
fun Tag.LearnTracksColumn() = comp { LearnTracksColumn(it) }

/**
 * The Learn area's column: the search, then the tracks, each with how far (the maintainer, 2026-10-27: "In the menu i
 * would only show the tracks for now", "Put the search above the "Tracks" in the left side please"); The Klang Path
 * first. A lesson's own column lists its track's lessons (TutorialColumn).
 *
 * The search lives here, beside the page: the overview redraws its tracks with every letter, and a field in the column
 * keeps its focus through that. Typed on a track's page, it takes the reader to the overview, where the results are.
 */
class LearnTracksColumn(ctx: NoProps) : PureComponent(ctx) {

    private val done by subscribingTo(TutorialStorage.done)

    private val search by subscribingTo(LearnSearch.text)

    override fun VDom.render() {
        val progress = TutorialProgress(done = done)

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "14px")
                // On the area headline's left edge, 20px in as the other areas' menus
                put("padding", "18px 20px")
                put("color", "var(--kl-ink-text)")
            }

            KlangSearchField(
                label = "Search the lessons",
                value = search,
                onInput = { text ->
                    LearnSearch.text(text)

                    // Only from elsewhere, and only for something to find: navigating again on the overview would
                    // drop its level and depth keys from the address; a cleared field has no results to show
                    if (text.isNotBlank() && router.current().route != Nav.tutorials) {
                        router.navToUri(Nav.tutorials())
                    }
                },
                placeholder = "Search lessons, topics, done, open",
            )

            learnLabel("Tracks")

            div {
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("gap", "8px")
                    put("font-size", "13px")
                }

                for (track in allTracks) {
                    appLink(router, Nav.tutorialTrack(track.slug)) {
                        attributes["data-track"] = track.slug
                        css { put("display", "flex") }

                        span {
                            css { put("flex", "1") }
                            +track.title
                        }
                        span {
                            css {
                                put("font-size", "12px")
                                put("color", "var(--kl-ink-label)")
                            }

                            +"${progress.doneIn(track)} of ${track.lessons.size}"
                        }
                    }
                }
            }
        }
    }
}
