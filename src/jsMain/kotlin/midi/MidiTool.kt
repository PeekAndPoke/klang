/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.spine.MidiRig
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.ui.comp.KlangWorkbench
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.MidiTool() = comp {
    MidiTool(it)
}

/**
 * The MIDI tool (KLANG-00402, step 4, section 6): the play surface, a tab of the bottom panel on every page, so one plays
 * along with a song wherever one is (the maintainer, 2026-11-01: "the midi controller must live at any time, so the
 * user can run a beat and play along"). A line with what plays, the sustain, the notes sounding and a panic key; the
 * KeyLab's drawing while one is connected ([DeviceView]); the laptop's keys ([LaptopKeysView]), playing while they have
 * the focus. It brings the rig up ([MidiRig.start]) and hooks the devices where MIDI was granted before; elsewhere a key
 * asks for them, so a plain visit asks the user nothing.
 */
class MidiTool(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /** The tool as a tab of a bottom panel, with this id beside the page's own tabs. */
        const val TAB_ID = "midi"

        /** The tool as a tab of a bottom panel. */
        val tab = KlangWorkbench.Tab(id = TAB_ID, title = "MIDI") { MidiTool() }

        /** The bottom panel of a page that has none of its own: the MIDI tool alone, closed until opened. */
        val panel = KlangWorkbench.Bottom(title = "MIDI", icon = { keyboard }, tabs = listOf(tab), ambient = true)

        /** [page]'s bottom panel with the MIDI tool beside its own tabs; the MIDI tool alone for a page with none. */
        fun with(page: KlangWorkbench.Bottom?): KlangWorkbench.Bottom = page?.copy(tabs = page.tabs + tab) ?: panel
    }

    private val rig = Playbacks.live

    private val rigState by subscribingTo(rig.state)

    private val ctrlState by subscribingTo(rig.playback)

    private val midiState by subscribingTo(rig.devices)

    init {
        lifecycle {
            onMount {
                rig.start()
                // The devices only where MIDI was granted before: a plain visit asks the user nothing
                rig.connectIfGranted()
            }
        }
    }

    override fun VDom.render() {
        val keyLabConnected = midiState.devices.any { it.contains("KeyLab", ignoreCase = true) }

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "8px")
                put("padding", "8px 12px")
            }

            renderLine()

            if (keyLabConnected) {
                DeviceView()
            }

            LaptopKeysView()
        }
    }

    /** What plays, the sustain and the notes sounding; or, before the Motor runs, the key that starts it. */
    private fun FlowContent.renderLine() {
        div {
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "12px")
                put("flex-wrap", "wrap")
            }

            if (!ctrlState.isReady) {
                // Browsers start the engine from a gesture only: this click is it
                ui.small.primary.button {
                    onClick { rig.startEngine() }

                    when {
                        ctrlState.isFailed -> {
                            icon.exclamation_triangle()
                            +"Retry"
                        }

                        ctrlState.isLoading -> {
                            icon.loading.spinner()
                            +"Starting the Motor ..."
                        }

                        else -> {
                            icon.power_off()
                            +"Start the Motor to play"
                        }
                    }
                }

                ctrlState.error?.let { span { +it } }

                return@div
            }

            span { +"Plays: ${rigState.instrument ?: rigState.selectedSound}" }

            if (rigState.pressed.keys.any { (_, control) -> control == ControlId.Sustain.id }) {
                span { +"Sustain" }
            }

            span {
                css { put("flex", "1") }

                +rigState.held.keys.sortedBy { it.note }.joinToString(" ") { MidiRig.noteLabel(it.note).substringBefore(" ") }
            }

            if (!rigState.devicesAsked) {
                ui.small.basic.button {
                    onClick { rig.connectDevices() }
                    icon.plug()
                    +"Connect MIDI devices"
                }
            }

            ui.small.basic.button {
                onClick { rig.panic(tsMs = window.performance.now()) }
                +"Panic"
            }
        }
    }
}
