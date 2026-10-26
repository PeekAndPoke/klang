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
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.HTMLElement

@Suppress("FunctionName")
fun Tag.KlangYesNoModal(
    handle: ModalsManager.Handle<Boolean>,
    headline: String,
    content: FlowContent.() -> Unit,
    yesText: String,
    noText: String,
    onResult: (yes: Boolean) -> Unit,
) = comp(
    KlangYesNoModal.Props(
        handle = handle,
        headline = headline,
        content = content,
        yesText = yesText,
        noText = noText,
        onResult = onResult,
    )
) {
    KlangYesNoModal(it)
}

/**
 * A yes or no question in a modal, drawn as a [KlangDialog] (KLANG-00349; the maintainer: "now we need the
 * KlangYesNoModal"): the headline, the content, and the key row: the no key, then the main key for yes, silver. Both
 * are real buttons, and the yes key takes the keyboard when the modal opens.
 *
 * The answer is decided at once (a key, a shortcut, or the start of any close) and handed to [Props.onResult] when the
 * modal is gone, after it faded out: until then the keyboard stays in the modal, so a key typed while it fades does
 * not reach what had the keyboard before. Kraft's handle keeps the answer (the first one stands) and delivers it
 * once; a dismissal answers `null`, which is a no here.
 *
 * Its keys (KLANG-00325) are in a layer of their own over the page, so the page's keys wait while it is open, as the
 * mouse does under the dimmer: Ctrl+Enter (Cmd on a Mac) answers yes, Esc closes as a no, each with its hint on its
 * key. Enter presses the key that has the keyboard.
 *
 * Klang keeps its own dialog over Kraft's OkCancel for clear control over the look (maintainer, 2026-10-25).
 */
class KlangYesNoModal(ctx: Ctx<Props>) : FadingModal<KlangYesNoModal.Props, Boolean>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        override val handle: ModalsManager.Handle<Boolean>,
        /** The question, short. */
        val headline: String,
        /** What the question is about, below the headline. */
        val content: FlowContent.() -> Unit,
        /** The yes key's text, the answer the key names ("Move"). */
        val yesText: String,
        /** The no key's text ("Keep"). */
        val noText: String,
        /** Called exactly once, when the modal is gone: true for yes, false for every other way out. */
        val onResult: (yes: Boolean) -> Unit,
    ) : FadingModal.Props<Boolean>()

    init {
        // Its keys over the page's, for the components below it too; closed when it unmounts
        openKeyLayer()

        // Once, when the modal is gone (after its fade, closed through its handle, or by closeAll): a dismissal is a no
        props.handle.onResult { yes -> props.onResult(yes ?: false) }

        lifecycle {
            onMount {
                // The yes key, the last in the row
                val keys = dom?.querySelectorAll(".${KlangControlStyles.dialogKeys} button")
                val yes = keys?.let { it.item(it.length - 1) } as? HTMLElement

                yes?.focus()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun FlowContent.renderContent() {
        div {
            css {
                // In the middle of the dimmer, which covers the window
                put("position", "absolute")
                put("top", "50%")
                put("left", "50%")
                put("transform", "translate(-50%, -50%)")
                put("width", "380px")
                put("max-width", "calc(100vw - 32px)")
                put("outline", "none")
            }
            // Focusable, so a click on the text keeps the keyboard in the modal
            attributes["tabindex"] = "-1"

            KlangDialog(
                title = props.headline,
                actions = listOf(
                    KlangDialog.Action(
                        label = props.noText,
                        key = KlangKeyBindings.DialogClose,
                        onClick = { close(result = false) },
                    ),
                    KlangDialog.Action(
                        label = props.yesText,
                        key = KlangKeyBindings.DialogConfirm,
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
