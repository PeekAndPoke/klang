/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codetools

import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager
import io.peekandpoke.kraft.semanticui.modals.FadingModal
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.LinearDimension
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.CodeToolModal(
    handle: ModalsManager.Handle<Unit>,
    content: FlowContent.(ModalsManager.Handle<Unit>) -> Unit,
) = comp(
    CodeToolModal.Props(
        handle = handle,
        content = content,
    )
) {
    CodeToolModal(it)
}

/** A code tool in a modal; it answers nothing ([Unit]), the tool commits or cancels through its own context. */
class CodeToolModal(ctx: Ctx<Props>) : FadingModal<CodeToolModal.Props, Unit>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        override val handle: ModalsManager.Handle<Unit>,
        val content: FlowContent.(ModalsManager.Handle<Unit>) -> Unit,
    ) : FadingModal.Props<Unit>()

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun FlowContent.renderContent() {
        ui.modal.transition.visible.active.front {
            css {
                width = LinearDimension.fitContent
            }
            val content = props.content
            content(props.handle)
        }
    }
}
