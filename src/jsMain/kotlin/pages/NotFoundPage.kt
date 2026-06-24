/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Nav
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.JoinedPageTitle
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.Padding
import kotlinx.css.TextAlign
import kotlinx.css.alignItems
import kotlinx.css.backgroundColor
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.em
import kotlinx.css.flexDirection
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.padding
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.css.textAlign
import kotlinx.css.vh
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.NotFoundPage() = comp {
    NotFoundPage(it)
}

class NotFoundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        JoinedPageTitle { listOf("Dragons", "404") }

        div {
            key = "not-found-page"
            css {
                height = 100.vh
                width = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                justifyContent = JustifyContent.center
                backgroundColor = Color.black
                color = Color.white
                textAlign = TextAlign.center
                cursor = Cursor.pointer
            }

            onClick {
                router.navToUri(Nav.dashboard())
            }

            div {
                icon.huge.dragon()
            }

            ui.hidden.divider()

            div {

                div { +"You got lost!" }
                div { +"And you are surrounded by" }
                div {
                    css {
                        padding = Padding(24.px)
                        fontSize = 3.em
                    }
                    +"404"
                }
                div { +"dragons!" }
            }
        }
    }
}
