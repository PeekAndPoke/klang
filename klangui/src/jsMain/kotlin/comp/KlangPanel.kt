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
    tight: Boolean = false,
    bottomRivets: Boolean = true,
    content: DIV.() -> Unit,
) = comp(
    KlangPanel.Props(tight = tight, bottomRivets = bottomRivets, content = content)
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
        /** Less padding, for a panel that holds a list running to its edges (a menu). */
        val tight: Boolean,
        /** False for a panel whose foot is a key row (the dialog): rivets there sit too close to the keys. */
        val bottomRivets: Boolean,
        val content: DIV.() -> Unit,
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val styles = KlangControlStyles

        val classes = listOfNotNull(styles.panel, if (props.tight) styles.panelTight else null)

        div(classes = classes.joinToString(" ")) {
            val corners = listOf(styles.rivetTopLeft, styles.rivetTopRight) +
                    if (props.bottomRivets) listOf(styles.rivetBottomLeft, styles.rivetBottomRight) else emptyList()

            for (corner in corners) {
                span(classes = "${styles.rivet} $corner") {
                    attributes["aria-hidden"] = "true"
                }
            }

            props.content(this)
        }
    }
}
