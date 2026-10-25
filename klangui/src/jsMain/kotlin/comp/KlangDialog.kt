/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.workshop.keys.KeyAction
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id

@Suppress("FunctionName")
fun Tag.KlangDialog(
    title: String,
    actions: List<KlangDialog.Action>,
    content: DIV.() -> Unit,
) = comp(
    KlangDialog.Props(title = title, actions = actions, content = content)
) {
    KlangDialog(it)
}

/**
 * A dialog (KLANG-00339): a riveted panel with a title and its text, and at its foot a key row, edge to edge, its keys
 * sharing the width (the maintainer, 2026-10-25: neither left nor right alignment sat well). Only the top pair of
 * rivets: at the foot they sat too close to the keys, and lifted over the key row they looked strange (the
 * maintainer, 2026-10-25).
 *
 * The keys stand in the order given; by convention the caller puts the main one last, on the right, and marks it
 * [Action.primary], which makes it silver. Their labels are verbs that say what happens ("Keep", "Move"), never "Yes"
 * and "No" (the maintainer, 2026-10-25). A key's shortcut is an `OnKey` in the button: the key manager answers it and
 * draws its hint over the button, nothing is printed beside it (the maintainer, 2026-10-25). Its keys answer in the
 * caller's key layer: a dialog that should own Esc and Mod Enter while it shows sits in a layer of its own (as a
 * modal opens one), else it takes them from the whole page. Where the dialog opens is the caller's.
 */
class KlangDialog(ctx: Ctx<Props>) : Component<KlangDialog.Props>(ctx) {

    private companion object {
        /** Numbers the dialogs on a page, for the ids their title and text are referred to by. */
        var count = 0
    }

    private val idBase = "kl-dialog-${count++}"

    /** One key: its [label], what it does, the key that does the same ([key]), whether it is the main one. */
    data class Action(
        val label: String,
        val onClick: () -> Unit,
        val key: KeyAction? = null,
        val primary: Boolean = false,
    )

    data class Props(
        val title: String,
        val actions: List<Action>,
        val content: DIV.() -> Unit,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles

        KlangPanel(bottomRivets = false) {
            attributes["role"] = "dialog"
            // Named by its title, described by its text: a screen reader reads both
            attributes["aria-labelledby"] = "$idBase-title"
            attributes["aria-describedby"] = "$idBase-body"

            div(classes = styles.dialogTitle.toString()) {
                id = "$idBase-title"
                +props.title
            }

            div(classes = styles.dialogBody.toString()) {
                id = "$idBase-body"
                props.content(this)
            }

            div(classes = styles.dialogKeys.toString()) {
                for (action in props.actions) {
                    div(classes = styles.dialogKey.toString()) {
                        KlangButton(
                            onClick = action.onClick,
                            finish = if (action.primary) KlangFinish.Silver else KlangFinish.Dark,
                        ) {
                            action.key?.let { key -> OnKey(key) { action.onClick() } }
                            +action.label
                        }
                    }
                }
            }
        }
    }
}
