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
import kotlinx.css.LinearDimension
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.Tag
import kotlinx.html.span
import kotlinx.html.svg

@Suppress("FunctionName")
fun Tag.KlangSpinner(
    label: String,
    size: LinearDimension = 16.px,
) = comp(
    KlangSpinner.Props(label = label, size = size)
) {
    KlangSpinner(it)
}

/**
 * Something is happening (KLANG-00347; the maintainer, 2026-10-26: "While it says "Warming Up" we need some
 * indication that something is happening ... a little spinner"): a dim ring with a short arc running round it, both in
 * the ink of the text beside it, so it reads on a dark panel and on a silver button alike (the look's metal vanished on
 * silver, the maintainer, 2026-10-27). Its [Props.label] is the text of a `status`, hidden from the eye, so screen
 * readers that read the page find it. Under "reduce motion" the arc stands and pulses gently instead.
 */
class KlangSpinner(ctx: Ctx<Props>) : Component<KlangSpinner.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** What is happening, for screen readers ("Warming up"). */
        val label: String,
        val size: LinearDimension,
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val styles = KlangControlStyles

        span(classes = styles.spinner.toString()) {
            attributes["role"] = "status"

            css {
                width = props.size
                height = props.size
            }

            svg {
                attributes["viewBox"] = "0 0 100 100"
                attributes["aria-hidden"] = "true"

                custom("circle") {
                    attributes["class"] = styles.spinnerTrack.toString()
                    attributes["cx"] = "50"
                    attributes["cy"] = "50"
                    attributes["r"] = "40"
                    attributes["stroke-width"] = "12"
                }

                // A quarter of the ring (2 pi 40 is about 251)
                custom("circle") {
                    attributes["class"] = styles.spinnerArc.toString()
                    attributes["cx"] = "50"
                    attributes["cy"] = "50"
                    attributes["r"] = "40"
                    attributes["stroke-width"] = "12"
                    attributes["stroke-dasharray"] = "63 251"
                }
            }

            // The label as text in the status, hidden from the eye: a name alone is not what a status announces
            span(classes = styles.visuallyHidden.toString()) { +props.label }
        }
    }
}
