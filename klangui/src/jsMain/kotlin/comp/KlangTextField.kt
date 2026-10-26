/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.onInput
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.input
import kotlinx.html.label
import org.w3c.dom.HTMLInputElement

@Suppress("FunctionName")
fun Tag.KlangTextField(
    label: String,
    value: String,
    onInput: (String) -> Unit,
    placeholder: String? = null,
) = comp(
    KlangTextField.Props(label = label, value = value, onInput = onInput, placeholder = placeholder)
) {
    KlangTextField(it)
}

/**
 * A text field (KLANG-00349, the song's title): the search field's well, without the icon. Controlled: [Props.value]
 * is shown, every keystroke goes to [Props.onInput].
 */
class KlangTextField(ctx: Ctx<Props>) : Component<KlangTextField.Props>(ctx) {

    data class Props(
        /** What the field holds, for screen readers ("Song title"). */
        val label: String,
        val value: String,
        val onInput: (String) -> Unit,
        val placeholder: String?,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles

        label(classes = styles.searchField.toString()) {
            input(type = InputType.text, classes = styles.searchInput.toString()) {
                attributes["aria-label"] = props.label
                props.placeholder?.let { placeholder = it }
                value = props.value

                onInput { e ->
                    (e.target as? HTMLInputElement)?.let { props.onInput(it.value) }
                }
            }
        }
    }
}
