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

@Suppress("FunctionName")
fun Tag.KlangCard(
    content: DIV.() -> Unit,
) = comp(
    KlangCard.Props(content = content)
) {
    KlangCard(it)
}

/**
 * A card (KLANG-00339): a flat plate on the ground that groups a few things, its content stacked with a gap.
 *
 * No rivets: rivets mark a popup ([KlangPanel]). The maintainer, 2026-10-25: the kitchen sink's cells should be such a
 * component; it serves the tutorial and sample cards too (today Fomantic's `ui.card`). Canvas: the kitchen sink's
 * cells, the tutorial card.
 */
class KlangCard(ctx: Ctx<Props>) : Component<KlangCard.Props>(ctx) {

    data class Props(
        val content: DIV.() -> Unit,
    )

    override fun VDom.render() {
        div(classes = KlangControlStyles.card.toString()) {
            props.content(this)
        }
    }
}
