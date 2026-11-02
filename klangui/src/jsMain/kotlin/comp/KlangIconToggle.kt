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
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.KlangIconToggle(
    icon: SemanticIconFn,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    large: Boolean = false,
    beacon: Boolean = false,
    count: Int = 0,
) = comp(
    KlangIconToggle.Props(icon = icon, label = label, on = on, onClick = onClick, large = large, beacon = beacon, count = count)
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
        /** The first-visit trail's "try me" on it (KLANG-00350). */
        val beacon: Boolean,
        /**
         * How many new things wait behind it (the Console's errors not seen yet, KLANG-00407): above 0, a count on its
         * corner and in its label.
         */
        val count: Int = 0,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val classes = listOfNotNull(
            styles.iconToggle,
            if (props.large) styles.iconToggleLarge else null,
            if (props.on) styles.iconToggleOn else null,
            if (props.beacon) styles.beacon else null,
        )

        // More than two digits do not fit the corner: the badge and the name say "99+" alike
        val count = if (props.count > 99) "99+" else props.count.toString()
        val label = if (props.count > 0) "${props.label} ($count new)" else props.label

        button(type = ButtonType.button, classes = classes.joinToString(" ")) {
            attributes["aria-label"] = label
            attributes["aria-pressed"] = props.on.toString()
            title = label
            onClick { props.onClick() }

            val iconFn = props.icon

            icon.iconFn().render { attributes["aria-hidden"] = "true" }

            if (props.count > 0) {
                span(classes = styles.iconToggleCount.toString()) {
                    attributes["aria-hidden"] = "true"
                    +count
                }
            }
        }
    }
}
