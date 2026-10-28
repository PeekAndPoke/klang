/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.comp.appLink
import io.peekandpoke.klang.pages.docs.tutorials.LearnTracksColumn
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.SidebarMenu() = comp {
    SidebarMenu(it)
}

/**
 * The workbench's column for a page without a column of its own (KLANG-00341): Learn's search and tracks on Learn's
 * pages, which they drive (KLANG-00370); on every other page the map of the app ([AppMap]), one standard column, its
 * groups under silkscreen headings (the maintainer, 2026-10-28: the columns before were "inconsistent and do nothing").
 */
class SidebarMenu(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val currentRoute by subscribingTo(router.current)

    //  RENDER  ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "sidebar-menu"
            css {
                put("color", "var(--kl-ink-text)")
                put("height", "100%")
                put("display", "flex")
                put("flex-direction", "column")
            }

            if (AppArea.of(currentRoute.route) == AppArea.Learn) {
                heading(text = AppArea.Learn.label, top = true)

                div {
                    key = "learn-column"
                    css {
                        put("min-height", "0")
                        put("flex-grow", "1")
                    }

                    LearnTracksColumn()
                }
            } else {
                renderMap()
            }
        }
    }

    private fun FlowContent.renderMap() {
        div {
            key = "app-map"
            css {
                put("min-height", "0")
                put("flex-grow", "1")
                put("overflow-y", "auto")
                put("padding-bottom", "18px")
            }

            AppMap.groups.forEachIndexed { index, group ->
                heading(text = group.title, top = index == 0)

                div {
                    css {
                        put("display", "flex")
                        put("flex-direction", "column")
                        put("padding", "4px 0 0 16px")
                    }

                    for (page in group.pages) {
                        entry(page = page, current = page.isCurrent(currentRoute))
                    }
                }
            }
        }
    }

    /** A group's heading, a size above the labels under it (the maintainer, 2026-10-27). */
    private fun FlowContent.heading(text: String, top: Boolean) {
        div(classes = KlangControlStyles.heading.toString()) {
            key = "heading-$text"
            css {
                put("padding", if (top) "18px 20px 0" else "16px 20px 0")
                put("font-size", "13px")
            }
            +text
        }
    }

    /**
     * One page: a lamp, its icon, its label. The page shown has its lamp on and its label in the strong ink, as the
     * drawer and the toggles mark what is on (the maintainer, 2026-10-28: "with the lamps and the selected page has the
     * lamp on"); under the mouse the label brightens ([appLink]). A link, so it opens in a new tab too.
     */
    private fun FlowContent.entry(page: AppMap.Page, current: Boolean) {
        // The ink on a wrapper: the app link inherits it (a link's own colour loses to the app's link rule)
        div {
            key = "page-${page.label}"
            css { put("color", if (current) "var(--kl-ink-strong)" else "var(--kl-ink-text)") }

            appLink(router, page.target) {
                if (current) {
                    attributes["aria-current"] = "page"
                }

                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "10px")
                    put("padding", "6px 16px 6px 4px")
                }

                KlangLamp(state = if (current) KlangLamp.State.On else KlangLamp.State.Off, size = 7.px)

                icon.(page.icon)().then {
                    css {
                        put("margin", "0")
                        put("color", if (current) "var(--kl-ink-strong)" else "var(--kl-ink-label)")
                    }
                }

                span { +page.label }
            }
        }
    }
}
