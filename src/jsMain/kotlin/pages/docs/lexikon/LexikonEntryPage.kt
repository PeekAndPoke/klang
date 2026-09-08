/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.lexikon

import io.peekandpoke.klang.Nav
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FlexWrap
import kotlinx.css.FontStyle
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flexWrap
import kotlinx.css.fontSize
import kotlinx.css.fontStyle
import kotlinx.css.gap
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.maxWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.p

@Suppress("FunctionName")
fun Tag.LexikonEntryPage(slug: String) = comp(LexikonEntryPage.Props(slug = slug)) {
    LexikonEntryPage(it)
}

/**
 * One Lexikon term on a page of its own.
 *
 * The list page is a good place to browse and a bad place to link AT: its search is full text, so a
 * link that means "what a send is" would land on every entry whose prose says "send". This page is
 * the permalink the DSL docs link to, one term per URL, so `@scope` badges and KDoc can point at a
 * definition instead of a result list.
 */
class LexikonEntryPage(ctx: Ctx<Props>) : Component<LexikonEntryPage.Props>(ctx) {

    data class Props(val slug: String)

    private val laf by subscribingTo(KlangTheme)

    private val entry: LexikonEntry? get() = lexikonEntryBySlug(props.slug)

    override fun VDom.render() {
        val found = entry

        ui.container {
            css { maxWidth = 52.rem }

            // SPA navigation, so coming back from a KDoc link does not reload the whole app
            a(href = Nav.manualsLexikon.pattern) {
                css { color = Color(laf.textSecondary); cursor = Cursor.pointer }
                onClick { event ->
                    event.preventDefault()
                    router.navToUri(Nav.manualsLexikon())
                }
                icon.arrow_left()
                +"All terms"
            }

            if (found == null) {
                ui.header { +"Unknown term" }
                p {
                    css { color = Color(laf.textSecondary) }
                    +"There is no Lexikon entry called \"${props.slug}\"."
                }
            } else {
                renderEntry(found)
            }
        }
    }

    private fun FlowContent.renderEntry(entry: LexikonEntry) {
        ui.huge.header {
            css { color = Color(laf.gold); marginTop = 8.px; marginBottom = 4.px }
            +entry.term
        }

        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                gap = 4.px
                marginBottom = 12.px
            }

            ui.mini.basic.label { +entry.category.label }
            entry.tags.forEach { tag ->
                ui.mini.basic.label { +tag.label }
            }
        }

        p {
            css { fontSize = 1.25.rem; color = Color(laf.textPrimary) }
            +entry.summary
        }

        p {
            css { color = Color(laf.textPrimary) }
            +entry.detail
        }

        entry.conventional?.let { conventional ->
            p {
                css { color = Color(laf.textSecondary); fontStyle = FontStyle.italic }
                +"Elsewhere known as: $conventional"
            }
        }

        entry.trivia?.let { trivia ->
            ui.segment {
                p {
                    css { color = Color(laf.textPrimary) }
                    icon.lightbulb()
                    +" "
                    +trivia.text
                }

                val meta = listOfNotNull(trivia.year, trivia.person, trivia.device)

                if (meta.isNotEmpty()) {
                    p {
                        css { color = Color(laf.textSecondary); fontSize = 0.88.rem }
                        +meta.joinToString(" · ")
                    }
                }

                trivia.url?.let { url ->
                    a(href = url, target = "_blank") {
                        css { color = Color(laf.accent); fontSize = 0.88.rem }
                        +"Read more"
                    }
                }
            }
        }
    }
}
