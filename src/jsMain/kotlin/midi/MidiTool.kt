/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.spine.MidiRig
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.spine.Player
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

        /** The tool as a tab of a bottom panel (on every page: `BottomTools`). */
        val tab = KlangWorkbench.Tab(id = TAB_ID, title = "MIDI") { MidiTool() }
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

    /**
     * The devices: a key that asks for them, where the browser has MIDI and they were not asked for yet; then what the
     * browser said, a refusal or the devices plugged in.
     */
    private fun FlowContent.renderDevices() {
        when {
            !isWebMidiSupported() -> span { +"No MIDI in this browser: the laptop keys play" }

            !rigState.devicesAsked -> ui.small.basic.button {
                onClick { rig.connectDevices() }
                icon.plug()
                +"Connect MIDI devices"
            }

            else -> span {
                +when (midiState.status) {
                    MidiConnector.Status.Unsupported -> "No MIDI in this browser"
                    MidiConnector.Status.Requesting -> "Asking for MIDI ..."
                    MidiConnector.Status.Denied -> "MIDI not allowed${midiState.error?.let { ": $it" } ?: ""}"
                    MidiConnector.Status.Ready -> midiState.devices.joinToString(", ").ifEmpty { "No MIDI devices plugged in" }
                }
            }
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

            // The Motor running, the rig's own playback starts by itself: a moment, no key
            if (!ctrlState.isReady && ctrlState.playerStatus == Player.Status.READY) {
                span { +"Getting the keys ready ..." }

                return@div
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

            // A sound the play code names by an expression: said so, not named wrongly; no play code playing: the plain one
            val plays = when {
                rigState.playing.isEmpty() -> MidiPlayCode.FALLBACK_SOUND
                else -> rigState.sound?.let { sound -> sound.code.trim('"') } ?: "as the play code says"
            }
            // Where it sits in the loadout, as the design drew it: "warmSaw (1/4)"
            // Counted over what the keys step through, the loadout's instruments that still are: only for one in it
            val stepping = rigState.stepping()
            val inLoadout = stepping.indexOf(rigState.sound).takeIf { it >= 0 && rigState.sound in rigState.loadout.sounds }
                ?.let { at -> " (${at + 1}/${stepping.size})" }
                .orEmpty()

            span { +"Plays: $plays$inLoadout" }

            if (rigState.pressed.keys.any { (_, control) -> control == ControlId.Sustain.id }) {
                span { +"Sustain" }
            }

            span {
                css { put("flex", "1") }

                +rigState.held.keys.sortedBy { it.note }.joinToString(" ") { MidiRig.noteLabel(it.note).substringBefore(" ") }
            }

            renderDevices()

            ui.small.basic.button {
                onClick { rig.panic(tsMs = window.performance.now()) }
                +"Panic"
            }
        }
    }
}
