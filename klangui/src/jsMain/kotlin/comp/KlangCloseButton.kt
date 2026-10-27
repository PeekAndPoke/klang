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
import kotlinx.html.ButtonType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.KlangCloseButton(
    label: String,
    onClick: () -> Unit,
    size: KlangCloseButton.Size = KlangCloseButton.Size.Regular,
) = comp(
    KlangCloseButton.Props(label = label, onClick = onClick, size = size)
) {
    KlangCloseButton(it)
}

/**
 * The close of a panel, a drawer or a window, the same everywhere (the maintainer, 2026-10-27: "Can we use the same
 * decent 'x' for close everywhere?"): only the x, flat and without a ring ("Can we make the KlangCloseButton flat
 * please", "Maybe not even the circle"), brighter under the mouse.
 *
 * Its [Props.label] names it for screen readers and as its tooltip ("Close Docs").
 */
class KlangCloseButton(ctx: Ctx<Props>) : Component<KlangCloseButton.Props>(ctx) {

    data class Props(
        val label: String,
        val onClick: () -> Unit,
        val size: Size,
    )

    enum class Size {
        /** A button's height (34 px): a drawer's header. */
        Regular,

        /** A header's icon size (28 px), beside a tool window's pin. */
        Small,
    }

    override fun VDom.render() {
        val styles = KlangControlStyles
        val classes = listOfNotNull(
            styles.button,
            styles.round,
            styles.closeFlat,
            if (props.size == Size.Small) styles.roundSmall else null,
        )

        button(type = ButtonType.button, classes = classes.joinToString(" ")) {
            attributes["aria-label"] = props.label
            title = props.label
            onClick { props.onClick() }

            icon.times.render { attributes["aria-hidden"] = "true" }
        }
    }
}
