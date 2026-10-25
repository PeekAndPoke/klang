/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangCaption(
    title: String,
    note: String? = null,
) = comp(
    KlangCaption.Props(title = title, note = note)
) {
    KlangCaption(it)
}

/** A caption (KLANG-00339): a short title and, under it, a small note (where a thing lives, a detail). */
class KlangCaption(ctx: Ctx<Props>) : Component<KlangCaption.Props>(ctx) {

    data class Props(
        val title: String,
        val note: String?,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles

        div {
            div(classes = styles.captionTitle.toString()) { +props.title }

            props.note?.let { note ->
                div(classes = styles.captionNote.toString()) { +note }
            }
        }
    }
}
