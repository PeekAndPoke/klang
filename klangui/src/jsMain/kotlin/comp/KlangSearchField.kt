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
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.span
import org.w3c.dom.HTMLInputElement

@Suppress("FunctionName")
fun Tag.KlangSearchField(
    label: String,
    value: String,
    onInput: (String) -> Unit,
    placeholder: String? = null,
    keyHint: String? = null,
) = comp(
    KlangSearchField.Props(label = label, value = value, onInput = onInput, placeholder = placeholder, keyHint = keyHint)
) {
    KlangSearchField(it)
}

/**
 * A search field (KLANG-00339): a well cut into the panel with a search icon, the input, and a printed key cap for the
 * key that focuses it.
 *
 * Controlled: [Props.value] is shown, every keystroke goes to [Props.onInput]. [Props.keyHint] only prints the cap;
 * the key itself is the app's (the key manager, KLANG-00325).
 */
class KlangSearchField(ctx: Ctx<Props>) : Component<KlangSearchField.Props>(ctx) {

    data class Props(
        /** What is searched, for screen readers ("Search songs, docs, samples"). */
        val label: String,
        val value: String,
        val onInput: (String) -> Unit,
        val placeholder: String?,
        /** The key that focuses the field, printed as a cap ("Mod K"); null for none. */
        val keyHint: String?,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles

        label(classes = styles.searchField.toString()) {
            icon.search.render {
                attributes["aria-hidden"] = "true"
            }

            input(type = InputType.search, classes = styles.searchInput.toString()) {
                attributes["aria-label"] = props.label
                props.placeholder?.let { placeholder = it }
                value = props.value

                onInput { e ->
                    (e.target as? HTMLInputElement)?.let { props.onInput(it.value) }
                }
            }

            props.keyHint?.let { hint ->
                span(classes = styles.keyCap.toString()) {
                    attributes["aria-hidden"] = "true"
                    +hint
                }
            }
        }
    }
}
