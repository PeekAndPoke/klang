/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.ButtonType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.KlangIconToggle(
    icon: SemanticIconFn,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    large: Boolean = false,
) = comp(
    KlangIconToggle.Props(icon = icon, label = label, on = on, onClick = onClick, large = large)
) {
    KlangIconToggle(it)
}

/**
 * A small icon toggle (KLANG-00340): the tool strip's buttons, printed on the panel, sunk in while on.
 *
 * Its [Props.label] names it for screen readers and as its tooltip; [Props.on] is announced as `aria-pressed`.
 */
class KlangIconToggle(ctx: Ctx<Props>) : Component<KlangIconToggle.Props>(ctx) {

    data class Props(
        val icon: SemanticIconFn,
        val label: String,
        val on: Boolean,
        val onClick: () -> Unit,
        /** The tool strip's size (36 px) instead of a header's (28 px). */
        val large: Boolean,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val classes = listOfNotNull(
            styles.iconToggle,
            if (props.large) styles.iconToggleLarge else null,
            if (props.on) styles.iconToggleOn else null,
        )

        button(type = ButtonType.button, classes = classes.joinToString(" ")) {
            attributes["aria-label"] = props.label
            attributes["aria-pressed"] = props.on.toString()
            title = props.label
            onClick { props.onClick() }

            val iconFn = props.icon

            icon.iconFn().render { attributes["aria-hidden"] = "true" }
        }
    }
}
