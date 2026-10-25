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
import kotlinx.html.Tag
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.KlangSwatchButton(
    name: String,
    swatch: List<String>,
    onClick: () -> Unit,
    pressed: Boolean = false,
    availability: KlangButton.Availability = KlangButton.Availability.Enabled,
) = comp(
    KlangSwatchButton.Props(name = name, swatch = swatch, onClick = onClick, pressed = pressed, availability = availability)
) {
    KlangSwatchButton(it)
}

/**
 * A swatch button (KLANG-00339): a domed tile with a round swatch of colour over its name, a toggle. The maintainer,
 * 2026-10-25, on the drawer's look buttons: "looks like a new button type".
 *
 * [Props.swatch] is lit to dark (three colours make a domed swatch, one a flat one); an empty list draws the dashed
 * place of a swatch that is not there yet. The colours come from code (a preset), never from text a user typed: a
 * custom look's swatch goes through its roles' check first.
 */
class KlangSwatchButton(ctx: Ctx<Props>) : Component<KlangSwatchButton.Props>(ctx) {

    data class Props(
        val name: String,
        /** Lit to dark; empty for none yet. */
        val swatch: List<String>,
        val onClick: () -> Unit,
        val pressed: Boolean,
        val availability: KlangButton.Availability,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val swatch = props.swatch

        KlangButton(
            onClick = props.onClick,
            surface = KlangButton.Surface.Domed,
            shape = KlangButton.Shape.Tile,
            pressed = if (props.availability == KlangButton.Availability.Later) null else props.pressed,
            availability = props.availability,
        ) {
            if (swatch.isEmpty()) {
                span(classes = "${styles.swatch} ${styles.swatchEmpty}") {
                    attributes["aria-hidden"] = "true"
                }
            } else {
                span(classes = styles.swatch.toString()) {
                    attributes["aria-hidden"] = "true"
                    css {
                        val stops = if (swatch.size == 1) "${swatch[0]}, ${swatch[0]}" else swatch.joinToString(", ")
                        put("background", "radial-gradient(circle at 40% 30%, $stops)")
                    }
                }
            }

            span { +props.name }
        }
    }
}
