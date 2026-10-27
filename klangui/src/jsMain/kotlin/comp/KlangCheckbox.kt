/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.onChange
import kotlinx.css.px
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.span
import org.w3c.dom.HTMLInputElement

@Suppress("FunctionName")
fun Tag.KlangCheckbox(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    look: KlangCheckbox.Look = KlangCheckbox.Look.Box,
) = comp(
    KlangCheckbox.Props(label = label, checked = checked, onChange = onChange, look = look)
) {
    KlangCheckbox(it)
}

/**
 * A checkbox (KLANG-00349; the maintainer: an "Enter Fullscreen" checkbox, "new design element"): a box sunk into the
 * panel that turns silver with a check, as the silver keys, and its [Props.label] beside it. A real checkbox input
 * under it, hidden from the eye only: it takes the keyboard (Space), its focus ring shows on the box, and the label
 * names it for screen readers. Controlled: [Props.checked] is shown, a click hands the other value to
 * [Props.onChange].
 *
 * [Look.Lamp] shows a lamp instead of the box, lit when checked (the maintainer, 2026-10-27, for the Learn filters:
 * "checkbox but with light"); the same input under it, the focus ring round the lamp.
 */
class KlangCheckbox(ctx: Ctx<Props>) : Component<KlangCheckbox.Props>(ctx) {

    data class Props(
        val label: String,
        val checked: Boolean,
        val onChange: (Boolean) -> Unit,
        val look: Look,
    )

    /** What shows the state: a box that turns silver with a check, or a lamp that lights. */
    enum class Look {
        Box,
        Lamp,
    }

    override fun VDom.render() {
        val styles = KlangControlStyles

        label(classes = styles.checkbox.toString()) {
            val inputStyle = if (props.look == Look.Lamp) styles.checkboxLampInput else styles.checkboxInput

            input(type = InputType.checkBox, classes = "${styles.visuallyHidden} $inputStyle") {
                checked = props.checked

                onChange { e ->
                    val input = e.target as? HTMLInputElement ?: return@onChange
                    val wanted = input.checked

                    // Controlled: the box shows what the props say until the owner hands the new value back
                    input.checked = props.checked
                    props.onChange(wanted)
                }
            }

            when (props.look) {
                Look.Box -> span(classes = styles.checkboxBox.toString()) {
                    attributes["aria-hidden"] = "true"
                }

                Look.Lamp -> span(classes = styles.checkboxLamp.toString()) {
                    attributes["aria-hidden"] = "true"

                    KlangLamp(state = if (props.checked) KlangLamp.State.On else KlangLamp.State.Off, size = 10.px)
                }
            }

            span { +props.label }
        }
    }
}
