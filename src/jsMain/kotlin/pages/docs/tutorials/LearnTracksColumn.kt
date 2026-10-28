/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.appLink
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangCheckbox
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.html.FlowContent
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
 * The level and depth filters with it, several of each, a lamp lit for each chosen (the maintainer, 2026-10-27: "move
 * the filters to the sidebar", "checkbox but with light"); they live in the overview's address, so a link keeps them.
 */
class LearnTracksColumn(ctx: NoProps) : PureComponent(ctx) {

    private val done by subscribingTo(TutorialStorage.done)

    private val search by subscribingTo(LearnSearch.text)

    /** Where the reader is: on the overview the filters show what its address holds. */
    private val currentRoute by subscribingTo(router.current)

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

            renderFilters()

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

            // The learning resources, links and reading, are Learn's (the maintainer, 2026-10-28)
            learnLabel("More to read")

            div {
                css { put("font-size", "13px") }

                appLink(router, Nav.resources()) {
                    attributes["data-resources"] = ""
                    +"Resources: links and reading"
                }
            }
        }
    }

    /** Level and depth side by side, each a column of lamp checkboxes. */
    private fun FlowContent.renderFilters() {
        val query = if (currentRoute.route == Nav.tutorials) currentRoute.matchedRoute.queryParams else emptyMap()
        val levels = TutorialsListPage.difficultiesFromParam(query[TutorialsListPage.PARAM_DIFFICULTY]).map { it.name }.toSet()
        val depths = TutorialsListPage.depthsFromParam(query[TutorialsListPage.PARAM_DEPTH]).map { it.name }.toSet()

        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "1fr 1fr")
                put("gap", "12px")
            }

            renderChoices(
                label = "Level",
                choices = TutorialDifficulty.entries.map { it.name to it.label },
                chosen = levels,
                param = TutorialsListPage.PARAM_DIFFICULTY,
            )

            renderChoices(
                label = "Depth",
                choices = TutorialDepth.entries.map { it.name to it.label },
                chosen = depths,
                param = TutorialsListPage.PARAM_DEPTH,
            )
        }
    }

    private fun FlowContent.renderChoices(label: String, choices: List<Pair<String, String>>, chosen: Set<String>, param: String) {
        div {
            attributes["role"] = "group"
            attributes["aria-label"] = label
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "6px")
            }

            learnLabel(label)

            for ((name, text) in choices) {
                KlangCheckbox(
                    label = text,
                    checked = name in chosen,
                    onChange = { on -> choose(param = param, chosen = chosen, name = name, on = on, order = choices.map { it.first }) },
                )
            }
        }
    }

    /**
     * Chooses [name] in the filter [param], or lets it go: the overview with its address changed, a step in the history
     * (Back undoes it); from elsewhere the overview opens with it. The overview follows its address, and a change of
     * the query alone does not fade the page. Not `replaceUri`: under the hash strategy Kraft's writes a path, the route
     * is not resolved (ULTRA-00263).
     */
    private fun choose(param: String, chosen: Set<String>, name: String, on: Boolean, order: List<String>) {
        val current = router.current()
        val query = if (current.route == Nav.tutorials) current.matchedRoute.queryParams else emptyMap()
        // From the choices as the lamps show them (parsed: any case, unknown names dropped), so a lamp always lets go
        val next = (if (on) chosen + name else chosen - name).sortedBy { order.indexOf(it) }
        val updated = (query + (param to next.joinToString(","))).filterValues { it.isNotBlank() }

        router.navToUri(Nav.tutorials().withQueryParams(updated))
    }
}
