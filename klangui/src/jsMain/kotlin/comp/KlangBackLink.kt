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
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangBackLink(
    label: String,
    href: String,
    onNavigate: () -> Unit,
) = comp(
    KlangBackLink.Props(label = label, href = href, onNavigate = onNavigate)
) {
    KlangBackLink(it)
}

/**
 * Back to where one came from, the same everywhere (the maintainer, 2026-10-27: "a common "navigate back" element that
 * is a bit more obvious", for Learn and the Lexikon): a pill with a thin outline, a chevron and the place's name.
 *
 * A real link to [Props.href]: with a key held (Ctrl, Shift, Cmd) or another button the browser opens it its own way;
 * a plain click calls [Props.onNavigate], so the app goes there without a reload.
 */
class KlangBackLink(ctx: Ctx<Props>) : Component<KlangBackLink.Props>(ctx) {

    data class Props(
        /** The place it leads back to: "Learn", "All terms". */
        val label: String,
        val href: String,
        val onNavigate: () -> Unit,
    )

    override fun VDom.render() {
        a(href = props.href, classes = KlangControlStyles.backLink.toString()) {
            onClick { event ->
                val mouse = event.asDynamic()

                if (mouse.button == 0 && mouse.ctrlKey != true && mouse.metaKey != true && mouse.shiftKey != true && mouse.altKey != true) {
                    event.preventDefault()
                    props.onNavigate()
                }
            }

            icon.angle_left.render { attributes["aria-hidden"] = "true" }
            span { +props.label }
        }
    }
}
