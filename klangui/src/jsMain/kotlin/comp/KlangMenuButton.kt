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
import io.peekandpoke.kraft.vdom.custom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.css.LinearDimension
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.svg
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.KlangMenuButton(
    open: Boolean,
    onClick: () -> Unit,
    size: LinearDimension = 42.px,
    key: KeyAction? = null,
    beacon: Boolean = false,
    label: String = "Menu",
    icon: SemanticIconFn? = null,
) = comp(
    KlangMenuButton.Props(open = open, onClick = onClick, size = size, key = key, beacon = beacon, label = label, icon = icon)
) {
    KlangMenuButton(it)
}

/**
 * The Motor's menu button (KLANG-00340, KLANG-00334 item 2): a small domed button with nine dots in the spot the
 * Motor kept empty beside its gauges, inside a ring of its own, laid out as the volume knob opposite is: [Props.size]
 * is the ring's, and the button sits inside it as the knob's cap does. The ring is dark while the drawer is closed;
 * while it is open the ring is lit, the button pressed and its dots lit (the maintainer, 2026-10-26: "a ring of its
 * own which is "disabled" dark and lit when pressed down"); `aria-expanded` says the same. With a [Props.key] (Alt+M
 * in the app) the key toggles it too, and the key manager shows its hint on the button.
 */
class KlangMenuButton(ctx: Ctx<Props>) : Component<KlangMenuButton.Props>(ctx) {

    data class Props(
        val open: Boolean,
        val onClick: () -> Unit,
        /** The ring's size; the button inside it is smaller, as the knob's cap is. */
        val size: LinearDimension,
        /** The key that presses it, answered through the key manager, its hint drawn by the button; null for none. */
        val key: KeyAction?,
        /** The first-visit trail's "try me" on it (KLANG-00350), while the drawer is closed. */
        val beacon: Boolean,
        /** What it is called, for screen readers and the tooltip. */
        val label: String,
        /**
         * Another button in the menu button's dress: an icon instead of the nine dots, [open] its pressed state, said as
         * pressed, not as a menu expanded (the maintainer, 2026-10-29: a visual's play/stop, "same button style other
         * icons"); null for the menu's dots.
         */
        val icon: SemanticIconFn?,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val classes = listOfNotNull(
            styles.button,
            styles.darkDomed,
            styles.round,
            if (props.open) styles.pressedDark else null,
            if (props.open) styles.menuButtonOpen else null,
            if (props.beacon && !props.open) styles.beacon else null,
        )

        val ringClasses = listOfNotNull(styles.menuButtonRing, if (props.open) styles.menuButtonRingLit else null)
        // The button inside the ring, as the knob's cap inside its ring, so the two read as a pair
        val inset = KlangKnob.CAP_INSET_WITH_RING_PCT

        div(classes = styles.menuButtonFrame.toString()) {
            css {
                width = props.size
                height = props.size
            }

            svg(classes = ringClasses.joinToString(" ")) {
                attributes["viewBox"] = "0 0 100 100"
                attributes["aria-hidden"] = "true"

                custom("circle") {
                    attributes["cx"] = "50"
                    attributes["cy"] = "50"
                    attributes["r"] = "45"
                    attributes["stroke-width"] = "5"
                }
            }

            button(type = ButtonType.button, classes = classes.joinToString(" ")) {
                attributes["aria-label"] = props.label

                if (props.icon == null) {
                    attributes["aria-expanded"] = props.open.toString()
                } else {
                    attributes["aria-pressed"] = props.open.toString()
                }

                title = props.label
                onClick { props.onClick() }

                props.key?.let { action -> OnKey(action) { props.onClick() } }

                css {
                    put("position", "absolute")
                    put("left", "$inset%")
                    put("top", "$inset%")
                    put("width", "${100 - 2 * inset}%")
                    put("height", "${100 - 2 * inset}%")
                }

                val iconFn = props.icon

                if (iconFn == null) {
                    renderDots()
                } else {
                    // In the dots' place and colour, lit with them while pressed
                    icon.iconFn().with(styles.menuButtonDots.toString()).render {
                        attributes["aria-hidden"] = "true"
                        css {
                            put("margin", "0")
                            put("font-size", "13px")
                            put("line-height", "1")
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderDots() {
        val styles = KlangControlStyles

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
