/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.onClick
import kotlinx.html.ButtonType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.KlangLookPicker(
    onChosen: (KlangLookPreset) -> Unit = {},
    onEdit: ((slot: Int?) -> Unit)? = null,
) = comp(
    KlangLookPicker.Props(onChosen = onChosen, onEdit = onEdit)
) {
    KlangLookPicker(it)
}

/**
 * The LOOK row (KLANG-00339, KLANG-00334 item 33): the eight equipped looks ([KlangLookShelf], KLANG-00382) as swatch
 * buttons, the one worn pressed.
 *
 * A press puts the look on the user's layer ([KlangTheme.choose]) and hands it to [Props.onChosen], where the app
 * remembers it (the picker lives in `klangui` and cannot reach the app's storage). With [Props.onEdit] the heading has
 * a quiet "Edit" key and an empty slot is a dashed "+", both opening the look editor (the slot's index for an empty
 * slot); without, an empty slot is only its dashed place.
 */
class KlangLookPicker(ctx: Ctx<Props>) : Component<KlangLookPicker.Props>(ctx) {

    data class Props(
        val onChosen: (KlangLookPreset) -> Unit,
        val onEdit: ((slot: Int?) -> Unit)?,
    )

    /** Redraws when the look changes, from here or from anywhere. */
    @Suppress("unused")
    private val look by subscribingTo(KlangTheme.look)

    private val shelf by subscribingTo(KlangLookShelf)

    override fun VDom.render() {
        val styles = KlangControlStyles
        val chosen = KlangTheme.chosen
        val onEdit = props.onEdit

        div {
            attributes["role"] = "group"
            attributes["aria-label"] = "Look"

            div(classes = styles.lookHeading.toString()) {
                div(classes = styles.heading.toString()) { +"Look" }

                if (onEdit != null) {
                    button(type = ButtonType.button, classes = styles.quietKey.toString()) {
                        title = "Edit the looks: equip them, make your own"
                        onClick { onEdit(null) }

                        span {
                            attributes["aria-hidden"] = "true"
                            +"\u270E"
                        }
                        +"Edit"
                    }
                }
            }

            div(classes = styles.lookRow.toString()) {
                shelf.equipped.forEachIndexed { slot, preset ->
                    if (preset != null) {
                        KlangSwatchButton(
                            name = preset.name,
                            swatch = preset.swatch,
                            pressed = preset == chosen,
                            onClick = {
                                KlangTheme.choose(preset)
                                props.onChosen(preset)
                            },
                        )
                    } else if (onEdit != null) {
                        button(type = ButtonType.button, classes = styles.lookEmpty.toString()) {
                            attributes["aria-label"] = "Empty slot: open the look editor to fill it"
                            onClick { onEdit(slot) }
                            +"+"
                        }
                    } else {
                        div(classes = styles.lookEmpty.toString()) {
                            attributes["aria-hidden"] = "true"
                        }
                    }
                }
            }
        }
    }
}
