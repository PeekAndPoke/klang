/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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
import kotlinx.css.Position
import kotlinx.css.position
import kotlinx.css.zIndex
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
                position = Position.relative
            }

            // Ambient accent light shining in from the screen's top and left
            // edges — same character as the editor's glow. Sits above the
            // columns (which have opaque backgrounds), ignores the mouse.
            div {
                key = "edge-light"
                css {
                    position = Position.absolute
                    put("inset", "0")
                    put("pointer-events", "none")
                    zIndex = 5
                    put(
                        "box-shadow",
                        "inset 0 8px 30px color-mix(in srgb, var(--klang-accent-muted) 5%, transparent)," +
                                " inset 8px 0 30px color-mix(in srgb, var(--klang-accent-muted) 5%, transparent)"
                    )
                }
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
                    // Above the sidebar (which is position:relative via chrome-bg),
                    // so the editor's outer glow can bleed over the menu
                    position = Position.relative
                    zIndex = 1
                    // Clip-window trick: the negative margin + equal padding keep
                    // the layout pixel-identical, but move this scroller's clip
                    // edge 100px INTO the sidebar — page content (e.g. the
                    // editor's glow) may paint over the menu within that window.
                    put("margin-left", "-100px")
                    put("padding-left", "100px")
                    // No horizontal scrollbar from shadows poking the right edge
                    put("overflow-x", "hidden")
                }
                props.inner(this)
            }
        }
    }
}
