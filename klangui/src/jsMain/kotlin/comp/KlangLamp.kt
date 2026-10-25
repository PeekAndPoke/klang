/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.css.Align
import kotlinx.css.Display
import kotlinx.css.LinearDimension
import kotlinx.css.alignItems
import kotlinx.css.display
import kotlinx.css.gap
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.FlowOrPhrasingContent
import kotlinx.html.Tag
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangLamp(
    state: KlangLamp.State,
    size: LinearDimension = 8.px,
    label: String? = null,
) = comp(
    KlangLamp.Props(state = state, size = size, label = label)
) {
    KlangLamp(it)
}

/**
 * A lamp (KLANG-00337): the indicator beside a control or a status, round glass that is dark when off.
 *
 * The lamp itself is decoration and hidden from screen readers; what it means is said by the [Props.label] beside
 * it, or by the control it sits on. [State.Try] is the first-visit trail's "try me" and pulses (still under
 * "reduce motion"). Canvas cell: "Lamps and status lamps".
 */
class KlangLamp(ctx: Ctx<Props>) : Component<KlangLamp.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val state: State,
        val size: LinearDimension,
        /** Printed beside the lamp; null for a lamp whose control says what it means. */
        val label: String?,
    )

    enum class State {
        Off,
        On,
        Try,
        Warning,
        Critical,
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val label = props.label

        if (label == null) {
            renderLamp()
        } else {
            span {
                css {
                    display = Display.inlineFlex
                    alignItems = Align.center
                    gap = 8.px
                }
                renderLamp()
                +label
            }
        }
    }

    private fun FlowOrPhrasingContent.renderLamp() {
        val stateClass = when (props.state) {
            State.Off -> null
            State.On -> KlangControlStyles.lampOn
            State.Try -> KlangControlStyles.lampTry
            State.Warning -> KlangControlStyles.lampWarning
            State.Critical -> KlangControlStyles.lampCritical
        }

        span(classes = listOfNotNull(KlangControlStyles.lamp, stateClass).joinToString(" ")) {
            attributes["aria-hidden"] = "true"
            attributes["data-lamp"] = props.state.name
            css {
                width = props.size
                height = props.size
            }
        }
    }
}
