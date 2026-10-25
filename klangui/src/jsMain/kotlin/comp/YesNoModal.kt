/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager
import io.peekandpoke.kraft.semanticui.modals.FadingModal
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.id
import org.w3c.dom.HTMLElement

/**
 * A yes or no question in a small modal, in the look klang.css gives every modal ([YesNoModal]).
 *
 * [onResult] is called exactly once, when the modal is gone: with true for the yes button ([yesText]; Enter or Space,
 * since it has the keyboard when the modal opens), with false for the no button ([noText]), Esc, or any other way the
 * modal closes (a click beside it, back navigation, the modals manager: Kraft's dismissals, which answer no).
 */
@Suppress("FunctionName")
fun Tag.YesNoModal(
    handle: ModalsManager.Handle<Boolean>,
    headline: String,
    content: FlowContent.() -> Unit,
    yesText: String,
    noText: String,
    onResult: (yes: Boolean) -> Unit,
) = comp(
    YesNoModal.Props(
        handle = handle,
        headline = headline,
        content = content,
        yesText = yesText,
        noText = noText,
        onResult = onResult,
    )
) {
    YesNoModal(it)
}

/**
 * A yes or no question in a small modal: the headline, the content, a quiet no button and the app's main-answer
 * (positive) button for yes, in the look klang.css gives every modal. Both are real buttons, and the yes button takes
 * the keyboard when the modal opens.
 *
 * The answer is decided at once (a button, Esc, or the start of any close) and handed to [Props.onResult] when the
 * modal is gone, after it faded out: until then the keyboard stays in the modal, so a key typed while it fades does
 * not reach what had the keyboard before. Kraft's handle keeps the answer (the first one stands) and delivers it
 * once; a dismissal answers `null`, which is a no here.
 *
 * Klang keeps its own dialog over Kraft's OkCancel for clear control over the look (maintainer, 2026-10-25); the
 * dialog's Esc handling stays here until the key map (KLANG-00325) carries it.
 */
class YesNoModal(ctx: Ctx<Props>) : FadingModal<YesNoModal.Props, Boolean>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        override val handle: ModalsManager.Handle<Boolean>,
        /** The question, short. */
        val headline: String,
        /** What the question is about, below the headline. */
        val content: FlowContent.() -> Unit,
        /** The yes button's text, the answer the button names ("Move"). */
        val yesText: String,
        /** The no button's text ("Keep"). */
        val noText: String,
        /** Called exactly once, when the modal is gone: true for yes, false for every other way out. */
        val onResult: (yes: Boolean) -> Unit,
    ) : FadingModal.Props<Boolean>()

    private companion object {
        /** `KeyboardEvent.key` of the Esc key. */
        const val ESCAPE_KEY = "Escape"
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** Marks the yes button, so it can take the keyboard when the modal opens. */
    private val yesButtonId = "yes-no-modal-yes-${hashCode()}"

    init {
        // Once, when the modal is gone (after its fade, closed through its handle, or by closeAll): a dismissal is a no
        props.handle.onResult { yes -> props.onResult(yes ?: false) }

        lifecycle {
            onMount {
                (dom?.querySelector("#$yesButtonId") as? HTMLElement)?.focus()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun FlowContent.renderContent() {
        ui.tiny.modal.transition.visible.active.front {
            // Focusable, so a click on the text keeps the keyboard in the modal and Esc still answers
            attributes["tabindex"] = "-1"

            onKeyDown { event ->
                if (event.key == ESCAPE_KEY) {
                    event.preventDefault()
                    close()
                }
            }

            ui.header { +props.headline }

            noui.content {
                props.content(this)
            }

            noui.actions {
                ui.basic.button Button {
                    type = ButtonType.button
                    onClick { close(result = false) }
                    +props.noText
                }

                ui.positive.button Button {
                    id = yesButtonId
                    type = ButtonType.button
                    onClick { close(result = true) }
                    +props.yesText
                }
            }
        }
    }
}
