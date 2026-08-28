/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_engine.KlangRealtimeVoicePlayback
import io.peekandpoke.klang.midi.MidiAccess
import io.peekandpoke.klang.midi.MidiInput
import io.peekandpoke.klang.midi.MidiMessageEvent
import io.peekandpoke.klang.midi.byteAt
import io.peekandpoke.klang.midi.isWebMidiSupported
import io.peekandpoke.klang.midi.requestMidiAccess
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.FontWeight
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.minHeight
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag

@Suppress("FunctionName")
fun Tag.MidiPlaygroundPage() = comp {
    MidiPlaygroundPage(it)
}

/**
 * Midi Playground — v0 of the MIDI keyboard workstream (docs/tasks/midi-keyboard-playground.md).
 *
 * This first step only listens: connect a MIDI device, show what is plugged in, and display
 * the keys as they are pressed. Making sound comes next.
 */
class MidiPlaygroundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private sealed interface MidiState {
        data object Unsupported : MidiState
        data object Requesting : MidiState
        data class Failed(val message: String) : MidiState
        data class Ready(val access: MidiAccess) : MidiState
    }

    private var midi: MidiState by value(MidiState.Requesting)

    /** Names of the currently connected MIDI inputs */
    private var devices: List<String> by value(emptyList())

    /** Currently held keys: midi note number -> velocity (1..127) */
    private var pressed: Map<Int, Int> by value(emptyMap())

    /** Last events, newest first */
    private var eventLog: List<String> by value(emptyList())

    /** The always-on realtime playback — null until the player is loaded */
    private var realtime: KlangRealtimeVoicePlayback? by value(null)

    companion object {
        private const val EVENT_LOG_SIZE = 16

        /** v0: fixed note length; real note-off (held gates) comes with the stop command (v2) */
        private const val NOTE_LENGTH_SEC = 1.0
    }

    init {
        lifecycle {
            onMount {
                launch {
                    val player = Player.ensure().await()
                    realtime = player.createRealtimePlayback("midi-playground")
                    console.log("[MidiPlayground] realtime playback ready:", realtime?.playbackId)
                }

                if (!isWebMidiSupported()) {
                    console.warn("[MidiPlayground] Web MIDI API not available on navigator")
                    midi = MidiState.Unsupported
                } else {
                    console.log("[MidiPlayground] requesting MIDI access ...")
                    requestMidiAccess()
                        .then { access ->
                            console.log("[MidiPlayground] MIDI access GRANTED", access)
                            midi = MidiState.Ready(access)
                            hookInputs(access)
                            access.onstatechange = { evt ->
                                console.log(
                                    "[MidiPlayground] state change:", evt.port.name, evt.port.type, evt.port.state
                                )
                                hookInputs(access)
                            }
                        }
                        .catch { err ->
                            // NB this also catches exceptions thrown inside the then-handler above,
                            // not only permission rejections — the message tells them apart.
                            console.error("[MidiPlayground] MIDI setup failed:", err)
                            midi = MidiState.Failed(err.message ?: err.toString())
                        }
                }
            }

            onUnmount {
                (midi as? MidiState.Ready)?.access?.let { access ->
                    access.onstatechange = null
                    access.inputs.forEach { input, _ -> input.onmidimessage = null }
                }
            }
        }
    }

    //  MIDI  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** (Re-)hooks all inputs. Runs on every state change, so hot-plugged devices just work. */
    private fun hookInputs(access: MidiAccess) {
        val names = mutableListOf<String>()

        access.inputs.forEach { input, _ ->
            console.log("[MidiPlayground] hooking input:", input.name, "state:", input.state)
            names.add(input.name ?: input.id)
            input.onmidimessage = { evt -> onMidiMessage(input, evt) }
        }

        console.log("[MidiPlayground] ${names.size} input(s) hooked")
        devices = names
    }

    private fun onMidiMessage(input: MidiInput, evt: MidiMessageEvent) {
        val status = evt.byteAt(0)
        val kind = status and 0xF0
        val channel = (status and 0x0F) + 1

        // Log everything except the high-frequency system-realtime spam (clock 0xF8, active sensing 0xFE)
        if (status < 0xF8) {
            console.log(
                "[MidiPlayground] t=${formatTs(evt.timeStamp)} msg status=$status kind=$kind ch=$channel " +
                        "data1=${evt.byteAt(1)} data2=${evt.byteAt(2)} len=${evt.data.length}"
            )
        }

        // A throw inside the state setters would otherwise die silently in the MIDI callback
        try {
            when (kind) {
                // Note-on with velocity 0 is a note-off by convention
                0x90 -> {
                    val note = evt.byteAt(1)
                    val velocity = evt.byteAt(2)
                    if (velocity > 0) {
                        noteOn(input, channel, note, velocity, evt.timeStamp)
                    } else {
                        noteOff(input, channel, note, evt.timeStamp)
                    }
                }

                0x80 -> noteOff(input, channel, evt.byteAt(1), evt.timeStamp)

                // Everything else (CC, pitch bend, clock, ...) is out of scope for this step
                else -> {}
            }
        } catch (e: Throwable) {
            console.error("[MidiPlayground] message handling failed:", e)
        }
    }

    private fun noteOn(input: MidiInput, channel: Int, note: Int, velocity: Int, tsMs: Double) {
        logEvent(tsMs, "ON  ${noteLabel(note)} vel $velocity ch $channel [${input.name ?: input.id}]")
        pressed = pressed + (note to velocity)

        realtime?.startVoice(
            data = VoiceData.empty.copy(
                sound = "supersaw",
                freqHz = Midi.midiToFreq(note.toDouble()),
                velocity = velocity / 127.0,
            ),
            gateDurSec = NOTE_LENGTH_SEC,
        )
    }

    private fun noteOff(input: MidiInput, channel: Int, note: Int, tsMs: Double) {
        logEvent(tsMs, "OFF ${noteLabel(note)} ch $channel [${input.name ?: input.id}]")
        pressed = pressed - note
    }

    private fun logEvent(tsMs: Double, entry: String) {
        eventLog = (listOf("${formatTs(tsMs)}  $entry") + eventLog).take(EVENT_LOG_SIZE)
    }

    /** DOMHighResTimeStamp ms with one decimal, e.g. "12345.7" */
    private fun formatTs(tsMs: Double): String = tsMs.asDynamic().toFixed(1).unsafeCast<String>()

    private fun noteLabel(note: Int): String = "${Midi.midiToNoteName(note.toDouble())} ($note)"

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            key = "midi-playground-page"

            ui.basic.segment {
                ui.header H1 { +"Midi Playground" }

                renderStatus()

                when (midi) {
                    is MidiState.Ready -> {
                        renderPressedKeys()
                        renderEventLog()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun FlowContent.renderStatus() {
        when (val state = midi) {
            MidiState.Unsupported -> ui.warning.message {
                ui.header { +"Web MIDI is not supported in this browser" }
                +"Chrome and Edge support it out of the box. Safari does not; Firefox only via a site-permission add-on."
            }

            MidiState.Requesting -> ui.info.message {
                +"Requesting MIDI access ... the browser may ask for permission."
            }

            is MidiState.Failed -> ui.negative.message {
                ui.header { +"MIDI setup failed" }
                +state.message
                ui.divider()
                +"If access was denied: allow MIDI for this page in the browser settings and reload. "
                +"Known walls: snap-packaged browsers (e.g. Ubuntu's Chromium snap) cannot reach MIDI devices at all, "
                +"and Firefox gates MIDI behind a site-permission add-on that is usually unavailable on localhost — "
                +"use Chrome or another unconfined Chromium build."
            }

            is MidiState.Ready -> {
                if (devices.isEmpty()) {
                    ui.info.message {
                        +"No MIDI devices found. Plug in a keyboard — it will show up here automatically."
                    }
                } else {
                    ui.list {
                        devices.forEach { name ->
                            noui.item {
                                icon.keyboard()
                                noui.content { +name }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderPressedKeys() {
        ui.segment {
            key = "pressed-keys"
            css {
                minHeight = 90.px
            }

            if (pressed.isEmpty()) {
                ui.header H3 {
                    css { fontWeight = FontWeight.normal }
                    +"Play something on your keyboard ..."
                }
            } else {
                ui.labels {
                    pressed.keys.sorted().forEach { note ->
                        ui.big.label {
                            key = "note-$note"
                            css { fontSize = 24.px }
                            +noteLabel(note)
                            noui.detail { +"vel ${pressed[note]}" }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderEventLog() {
        ui.basic.segment {
            key = "event-log"

            ui.header H4 { +"Events" }

            ui.mini.basic.button {
                +"Clear"
                onClick { eventLog = emptyList() }
            }

            ui.list {
                css { fontFamily = "monospace" }

                eventLog.forEachIndexed { idx, entry ->
                    noui.item {
                        key = "evt-$idx"
                        +entry
                    }
                }
            }
        }
    }
}
