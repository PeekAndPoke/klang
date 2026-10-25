/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangLookPicker(
    onChosen: (KlangLookPreset) -> Unit = {},
) = comp(
    KlangLookPicker.Props(onChosen = onChosen)
) {
    KlangLookPicker(it)
}

/**
 * The LOOK row (KLANG-00339, KLANG-00334 item 33): the built-in looks as swatch buttons, the one in use pressed, and
 * "Custom" as a place for the user's own look, later.
 *
 * A press puts the preset on the user's layer ([KlangTheme.choose]) and hands it to [Props.onChosen], where the app
 * remembers it (the picker lives in `klangui` and cannot reach the app's storage).
 */
class KlangLookPicker(ctx: Ctx<Props>) : Component<KlangLookPicker.Props>(ctx) {

    data class Props(
        val onChosen: (KlangLookPreset) -> Unit,
    )

    /** Redraws when the look changes, from here or from anywhere. */
    @Suppress("unused")
    private val look by subscribingTo(KlangTheme.look)

    override fun VDom.render() {
        val styles = KlangControlStyles
        val chosen = KlangTheme.chosen

        div {
            attributes["role"] = "group"
            attributes["aria-label"] = "Look"

            div(classes = styles.heading.toString()) { +"Look" }

            div(classes = styles.lookRow.toString()) {
                for (preset in KlangLooks.all) {
                    KlangSwatchButton(
                        name = preset.name,
                        swatch = preset.swatch,
                        pressed = preset == chosen,
                        onClick = {
                            KlangTheme.choose(preset)
                            props.onChosen(preset)
                        },
                    )
                }

                KlangSwatchButton(
                    name = "Custom",
                    swatch = emptyList(),
                    onClick = {},
                    availability = KlangButton.Availability.Later,
                )
            }
        }
    }
}
