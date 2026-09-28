/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.LinearDimension
import kotlinx.css.Padding
import kotlinx.css.alignItems
import kotlinx.css.backgroundColor
import kotlinx.css.borderColor
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.gap
import kotlinx.css.height
import kotlinx.css.marginLeft
import kotlinx.css.marginTop
import kotlinx.css.padding
import kotlinx.css.pct
import kotlinx.css.rem
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.img
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.DevStatusPage() = comp {
    DevStatusPage(it)
}

/**
 * Where Klang stands: cards for the static pages next to the SPA (the topic map, the mission log,
 * the white paper). The pages live in `src/jsMain/resources`, built by `console/dev-status/build.py`,
 * their preview images by `console/dev-status/previews.sh`.
 */
class DevStatusPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)

    private data class Entry(
        val path: String,
        val title: String,
        val kind: String,
        val description: String,
        val image: String,
    )

    private val entries = listOf(
        Entry(
            path = "/klang-topic-map.html",
            title = "Topic map",
            kind = "Graph",
            description = "Every open task and plan as a zoomable graph: grouped by area, marked by its real " +
                    "state, linked the way the docs link each other. The archive of finished work sits behind a toggle.",
            image = "/images/dev-status/topic-map.jpg",
        ),
        Entry(
            path = "/klang-mission-log.html",
            title = "Mission log",
            kind = "Timeline",
            description = "Everything built so far on a timeline, by the day each task was closed, and a rough " +
                    "guess at when the open ones land. The guesses come from the pace so far and the priority order.",
            image = "/images/dev-status/mission-log.jpg",
        ),
        Entry(
            path = "/klang-whitepaper.html",
            title = "White paper",
            kind = "Document",
            description = "How Klangmotor works: the pattern language, the scripting language, the audio engine " +
                    "and the wire between them. What exists, and where it might go.",
            image = "/images/dev-status/whitepaper.jpg",
        ),
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            key = "dev-status-page"

            css { padding = Padding(2.rem) }

            ui.segment {
                ui.header { +"Dev status" }
                ui.sub.header {
                    +"Where Klang stands: the open work, what was built so far, and how it all fits together. "
                    +"Snapshots built from the project's task files; each opens in a new tab."
                }
            }

            ui.three.stackable.cards {
                entries.forEach { renderCard(it) }
            }
        }
    }

    /**
     * The whole card is one link to the page, in a new tab.
     *
     * The click must not reach the router: it listens on `window`, takes every href it does not
     * recognise as external (a relative path, and `http://localhost:port/...` too, since its URL
     * regex wants a top-level domain) and navigates in place, whatever `target` says. Stopping the
     * propagation here, without preventing the default, leaves the click to the browser, which
     * honours `target = "_blank"`.
     */
    private fun FlowContent.renderCard(entry: Entry) {
        ui.card.A {
            key = "dev-status-${entry.title}"
            href = entry.path
            target = "_blank"
            title = "Open ${entry.title} in a new tab"
            onClick { evt -> evt.stopPropagation() }

            css { backgroundColor = Color(laf.cardBackground) }

            noui.image {
                css {
                    put("aspect-ratio", "16 / 9")
                    put("overflow", "hidden")
                }

                img(src = entry.image, alt = "${entry.title}, preview") {
                    css {
                        width = 100.pct
                        height = 100.pct
                        display = Display.block
                        put("object-fit", "cover")
                    }
                }
            }

            noui.content {
                ui.small.header {
                    css { color = Color(laf.textPrimary) }
                    +entry.title
                }

                noui.meta {
                    css {
                        display = Display.flex
                        alignItems = Align.center
                        gap = 0.5.rem
                        marginTop = 0.25.rem
                    }

                    ui.mini.with(laf.styles.goldButton()).label {
                        icon.bolt()
                        +"Klang"
                    }
                    ui.mini.basic.label { +entry.kind }
                }

                noui.description {
                    css {
                        marginTop = 0.5.rem
                        color = Color(laf.textSecondary)
                    }
                    +entry.description
                }
            }

            noui.extra.content {
                css {
                    borderColor = Color(laf.textTertiary)
                    display = Display.flex
                    alignItems = Align.center
                }

                span {
                    css {
                        marginLeft = LinearDimension.auto
                        color = Color(laf.textPrimary)
                    }
                    icon.external_alternate()
                    +"Open in a new tab"
                }
            }
        }
    }
}
