/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.kraft.vdom.custom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import kotlinx.css.LinearDimension
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.ButtonType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.svg
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.KlangMenuButton(
    open: Boolean,
    onClick: () -> Unit,
    size: LinearDimension = 31.px,
) = comp(
    KlangMenuButton.Props(open = open, onClick = onClick, size = size)
) {
    KlangMenuButton(it)
}

/**
 * The Motor's menu button (KLANG-00340, KLANG-00334 item 2): a small domed button with nine dots in the spot the
 * Motor kept empty beside its gauges, the volume knob's size. While its drawer is open it is pressed and its dots are
 * lit, without a lamp of its own (item 34); `aria-expanded` says the same.
 */
class KlangMenuButton(ctx: Ctx<Props>) : Component<KlangMenuButton.Props>(ctx) {

    data class Props(
        val open: Boolean,
        val onClick: () -> Unit,
        val size: LinearDimension,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val classes = listOfNotNull(
            styles.button,
            styles.darkDomed,
            styles.round,
            if (props.open) styles.pressedDark else null,
            if (props.open) styles.menuButtonOpen else null,
        )

        button(type = ButtonType.button, classes = classes.joinToString(" ")) {
            attributes["aria-label"] = "Menu"
            attributes["aria-expanded"] = props.open.toString()
            title = "Menu"
            onClick { props.onClick() }

            css {
                width = props.size
                height = props.size
                // Sits on the line's foot like the knob opposite, not on the baseline its dots would give it
                put("vertical-align", "bottom")
            }

            svg(classes = styles.menuButtonDots.toString()) {
                attributes["viewBox"] = "0 0 13 13"
                attributes["width"] = "13"
                attributes["height"] = "13"
                attributes["aria-hidden"] = "true"

                for (row in 0 until 3) {
                    for (column in 0 until 3) {
                        custom("circle") {
                            attributes["cx"] = (2.0 + column * 4.5).toString()
                            attributes["cy"] = (2.0 + row * 4.5).toString()
                            attributes["r"] = "1.6"
                        }
                    }
                }
            }
        }
    }
}
