/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.spine.MidiRig
import io.peekandpoke.klang.spine.MidiSettings
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Padding
import kotlinx.css.padding
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.DeviceView() = comp {
    DeviceView(it)
}

/**
 * The MIDI device's drawing (the KeyLab), lit from the rig ([Playbacks.live]) and played by a click or a touch on any
 * control (KLANG-00402, step 3), with the keybed's alignment above it ([MidiSettings.keyBedOffset]). Drawn by the MIDI
 * page and the MIDI tool alike.
 */
class DeviceView(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /** The device this drawing shows. One value per supported controller. */
        val DEVICE = KeyLabEssential49Mk3
    }

    private val rig = Playbacks.live

    private val rigState by subscribingTo(rig.state)

    private val keyBedOffset by subscribingTo(MidiSettings.keyBedOffset)

    override fun VDom.render() {
        div {
            renderAlignment()

            MidiPanel(
                panel = DEVICE,
                heldNotes = rigState.heldNotes(laptopSide = false),
                activeControls = rigState.controlValues + rigState.pressed(laptopSide = false),
                tints = rigState.pressedTints,
                outlines = rigState.padOutlines(),
                noteOffset = keyBedOffset,
                screen = MidiRig.DEVICE_SCREEN,
                onInput = { events -> events.forEach { rig.handle(it) } },
            )
        }
    }

    /**
     * Aligns the drawing to the device's Transpose setting. Has to be manual: Transp -/+ changes what the keys send
     * without emitting anything (see [MidiSettings.keyBedOffset]).
     */
    private fun FlowContent.renderAlignment() {
        val offset = keyBedOffset
        val set: (Int) -> Unit = { MidiSettings.keyBedOffset(it) }

        div {
            key = "transpose"
            css { padding = Padding(0.px, 0.px, 4.px, 0.px) }

            noui.tiny.header { +"Keybed alignment" }

            ui.mini.buttons {
                ui.button {
                    onClick { set(offset - 1) }
                    +"semitone -"
                }
                ui.button {
                    onClick { set(offset - 12) }
                    +"oct -"
                }
                ui.basic.button {
                    onClick { set(0) }
                    +when {
                        offset == 0 -> "aligned"
                        offset > 0 -> "+$offset: reset"
                        else -> "$offset: reset"
                    }
                }
                ui.button {
                    onClick { set(offset + 12) }
                    +"oct +"
                }
                ui.button {
                    onClick { set(offset + 1) }
                    +"semitone +"
                }
            }
        }
    }
}
