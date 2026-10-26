/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.openKeyLayer
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager
import io.peekandpoke.kraft.semanticui.modals.FadingModal
import io.peekandpoke.ultra.html.css
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.HTMLElement

@Suppress("FunctionName")
fun Tag.KlangNoticeModal(
    handle: ModalsManager.Handle<Boolean>,
    headline: String,
    okText: String = "OK",
    content: DIV.() -> Unit,
) = comp(
    KlangNoticeModal.Props(handle = handle, headline = headline, okText = okText, content = content)
) {
    KlangNoticeModal(it)
}

/**
 * A notice in a modal, drawn as a [KlangDialog] (KLANG-00350): a headline, what there is to know, and one key that
 * closes it, holding the keyboard when it opens (Enter), Esc closing it too. Its keys are in a layer of their own over
 * the page, as [KlangYesNoModal]'s are. The handle answers true when the key or Esc closed it, null for a dismissal
 * (a click beside it, back navigation, closing all modals).
 */
class KlangNoticeModal(ctx: Ctx<Props>) : FadingModal<KlangNoticeModal.Props, Boolean>(ctx) {

    data class Props(
        override val handle: ModalsManager.Handle<Boolean>,
        val headline: String,
        /** The one key's text ("OK", "Got it"). */
        val okText: String,
        val content: DIV.() -> Unit,
    ) : FadingModal.Props<Boolean>()

    init {
        // Its keys over the page's, for the components below it too; closed when it unmounts
        openKeyLayer()

        lifecycle {
            onMount {
                (dom?.querySelector(".${KlangControlStyles.dialogKeys} button") as? HTMLElement)?.focus()
            }
        }
    }

    override fun FlowContent.renderContent() {
        div {
            css {
                // In the middle of the dimmer, which covers the window; the dimmer's centred, unselectable text undone
                put("position", "absolute")
                put("top", "50%")
                put("left", "50%")
                put("transform", "translate(-50%, -50%)")
                put("width", "380px")
                put("max-width", "calc(100vw - 32px)")
                put("outline", "none")
                put("text-align", "left")
                put("line-height", "normal")
                put("user-select", "text")
            }
            // Focusable, so a click on the text keeps the keyboard in the modal
            attributes["tabindex"] = "-1"

            KlangDialog(
                title = props.headline,
                actions = listOf(
                    KlangDialog.Action(
                        label = props.okText,
                        key = KlangKeyBindings.DialogClose,
                        primary = true,
                        onClick = { close(result = true) },
                    ),
                ),
            ) {
                props.content(this)
            }
        }
    }
}
