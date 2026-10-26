/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangScreen(
    size: KlangScreen.Size = KlangScreen.Size.Small,
    surface: String = "var(--kl-ground-sunken)",
    bezel: String = "var(--kl-ground-deep)",
    content: DIV.() -> Unit,
) = comp(
    KlangScreen.Props(size = size, surface = surface, bezel = bezel, content = content)
) {
    KlangScreen(it)
}

/**
 * A screen set into the machine (KLANG-00349; KLANG-00334 item 46, "a machine made of tiles"): a well in the panel,
 * the panels' dark edge round it, a shadow from its top edge for depth, and its corners cut off, triangles of the
 * panel's ground ([Props.bezel]) over them with the edge along their diagonal. The song page's editor sits in a
 * [Size.Large] one; a textarea or a code example in a [Size.Small] one.
 *
 * Nothing clips the content: the corners are laid over it, so popups fixed inside the content (the code editor's
 * hover docs, the code tools) are not cut at the screen's edge. The corners and the shadow never catch the mouse.
 * The screen fills what its parent gives it (a flex column); the content gets a flex column that fills the screen.
 */
class KlangScreen(ctx: Ctx<Props>) : Component<KlangScreen.Props>(ctx) {

    /** How far the corners are cut: about twice the bezel round a large screen, a little for a small one. */
    enum class Size(val cutPx: Int) {
        Small(cutPx = 8),
        Large(cutPx = 22),
    }

    data class Props(
        val size: Size,
        /** The screen's own colour, under the content: the editor's black, or a well's sunken ground. */
        val surface: String,
        /** The ground round the screen, which its cut corners show. */
        val bezel: String,
        val content: DIV.() -> Unit,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles

        div(classes = styles.screen.toString()) {
            css {
                put("--kl-screen-cut", "${props.size.cutPx}px")
                put("--kl-screen-bezel", props.bezel)
            }

            div(classes = styles.screenSurface.toString()) {
                css { put("background", props.surface) }

                div(classes = styles.screenShade.toString()) {
                    attributes["aria-hidden"] = "true"
                }

                div(classes = styles.screenContent.toString()) {
                    props.content(this)
                }
            }

            val corners = listOf(
                styles.screenCornerTopLeft,
                styles.screenCornerTopRight,
                styles.screenCornerBottomLeft,
                styles.screenCornerBottomRight,
            )

            for (corner in corners) {
                div(classes = "${styles.screenCorner} $corner") {
                    attributes["aria-hidden"] = "true"
                }
            }
        }
    }
}
