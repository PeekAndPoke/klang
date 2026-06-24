/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.css.Display
import kotlinx.css.Overflow
import kotlinx.css.display
import kotlinx.css.flexGrow
import kotlinx.css.flexShrink
import kotlinx.css.height
import kotlinx.css.maxHeight
import kotlinx.css.minWidth
import kotlinx.css.overflow
import kotlinx.css.overflowY
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.css.vh
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.MenuLayout(
    inner: FlowContent.() -> Unit,
) = comp(
    MenuLayout.Props(inner = inner)
) {
    MenuLayout(it)
}

class MenuLayout(ctx: Ctx<Props>) : Component<MenuLayout.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val inner: FlowContent.() -> Unit,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "menu-layout"
            css {
                width = 100.pct
                height = 100.vh
                maxHeight = 100.vh
                display = Display.flex
                overflow = Overflow.hidden
            }

            div {
                css {
                    width = 340.px
                    // Prevent the menu from shrinking on smaller screens
                    minWidth = 340.px
                    flexShrink = 0.0

                    height = 100.pct
                    overflowY = Overflow.hidden
                }

                SidebarMenu()
            }

            div {
                css {
                    flexGrow = 1.0
                    height = 100.pct
                    overflowY = Overflow.auto
                }
                props.inner(this)
            }
        }
    }
}
