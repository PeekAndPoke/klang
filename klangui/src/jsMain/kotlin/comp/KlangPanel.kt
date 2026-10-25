/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangPanel(
    content: DIV.() -> Unit,
) = comp(
    KlangPanel.Props(content = content)
) {
    KlangPanel(it)
}

/**
 * The riveted panel (KLANG-00337): the frame of every popup, a plate held by a rivet in each corner.
 *
 * The maintainer, 2026-10-25: the rivets "is something every popup should have" (KLANG-00334 item 11). The panel
 * draws the frame only; what it is (a dialog, a menu, with its role and name) its content or its user says. Canvas
 * cell: "Popups, all with rivets".
 */
class KlangPanel(ctx: Ctx<Props>) : Component<KlangPanel.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val content: DIV.() -> Unit,
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val styles = KlangControlStyles

        div(classes = styles.panel.toString()) {
            val corners = listOf(styles.rivetTopLeft, styles.rivetTopRight, styles.rivetBottomLeft, styles.rivetBottomRight)

            for (corner in corners) {
                span(classes = "${styles.rivet} $corner") {
                    attributes["aria-hidden"] = "true"
                }
            }

            props.content(this)
        }
    }
}
