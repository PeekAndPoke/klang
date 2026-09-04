/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
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
import kotlinx.css.PointerEvents
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
import kotlinx.css.pointerEvents
import kotlinx.css.px
import kotlinx.css.vh
import kotlinx.css.top
import kotlinx.css.bottom
import kotlinx.css.left
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

    companion object {
        /** The sidebar's width; the content column starts here. */
        const val SIDEBAR_WIDTH_PX = 340

        /** How far the content's glow reaches over the sidebar's right edge. */
        const val CONTENT_GLOW_PX = 42
    }

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

            // Ambient accent light shining in from the screen's LEFT edge only —
            // same character as the editor's glow. Sits above the columns (which
            // have opaque backgrounds), ignores the mouse.
            //
            // Painted as a gradient rather than an inset box-shadow: an inset
            // shadow whose blur exceeds its offset bleeds a faint band onto the
            // three edges it is NOT aimed at, which is where the stray lights at
            // the top and far right came from. A gradient lights one edge, full stop.
            div {
                key = "edge-light"
                css {
                    position = Position.absolute
                    put("inset", "0")
                    put("pointer-events", "none")
                    zIndex = 5
                    put(
                        "background-image",
                        "linear-gradient(to right," +
                                " color-mix(in srgb, var(--klang-accent-muted) 5%, transparent) 0," +
                                " transparent 30px)"
                    )
                }
            }

            div {
                css {
                    width = SIDEBAR_WIDTH_PX.px
                    // Prevent the menu from shrinking on smaller screens
                    minWidth = SIDEBAR_WIDTH_PX.px
                    flexShrink = 0.0

                    height = 100.pct
                    overflowY = Overflow.hidden
                }

                SidebarMenu()
            }

            // The editor's glow bleeding over the sidebar's right edge — painted HERE, by the
            // layout, as a second light like the one above: an overlay with no children and no
            // hit-testing. It used to be the scroller's own box-shadow showing through a 100px
            // clip window (negative margin + padding on the scroller, z-index above the sidebar);
            // that strip belonged to the scroller and took every click and hover meant for the
            // Motor underneath it. The editor's own shadow is now clipped at the content edge,
            // exactly where this strip takes over. Same colour and strength as that shadow
            // (`accentMuted` at 18 %, blur 42px).
            div {
                key = "content-edge-light"
                css {
                    position = Position.absolute
                    top = 0.px
                    bottom = 0.px
                    left = (SIDEBAR_WIDTH_PX - CONTENT_GLOW_PX).px
                    width = CONTENT_GLOW_PX.px
                    put("pointer-events", "none")
                    zIndex = 5
                    put(
                        "background-image",
                        "linear-gradient(to left," +
                                " color-mix(in srgb, var(--klang-accent-muted) 18%, transparent) 0," +
                                " transparent 100%)"
                    )
                }
            }

            div {
                css {
                    flexGrow = 1.0
                    height = 100.pct
                    overflowY = Overflow.auto
                    // No horizontal scrollbar from shadows poking the right edge
                    put("overflow-x", "hidden")
                }
                props.inner(this)
            }
        }
    }
}
