/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.comp.RealtimePlaybackCtrl
import io.peekandpoke.klang.midi.MidiConnector
import io.peekandpoke.klang.midi.NoteKey
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
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
 * Midi Playground — play the engine live from a MIDI keyboard.
 *
 * The page owns only two things: **the translation** ([voiceFor] — what a note sounds like) and
 * the wiring ([handle]). Engine lifecycle lives in [RealtimePlaybackCtrl]; devices, byte parsing,
 * liveIds and note bookkeeping live in [MidiConnector]. See
 * docs/tasks/realtime-playback-controller.md.
 */
class MidiPlaygroundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////

    private val ctrl = RealtimePlaybackCtrl(playbackName = "midi-playground")

    private val midi = MidiConnector(nextLiveId = ctrl::nextLiveId)

    private val ctrlState by subscribingTo(ctrl.state)

    private val midiState by subscribingTo(midi.state)

    /** Last events, newest first — pure UI, so it lives here. */
    private var eventLog: List<String> by value(emptyList())

    companion object {
        private const val EVENT_LOG_SIZE = 16
    }

    init {
        lifecycle {
            onMount {
                midi.start()
                midi.events.subscribeToStream { evt -> evt?.let { handle(it) } }
            }

            onUnmount {
                midi.tearDown() // releases held notes first, then unhooks
                ctrl.tearDown()
            }
        }
    }

    //  THE TRANSLATION — voice shape, sound and params live here  //////////////////////////////

    private fun voiceFor(evt: MidiConnector.Event.NoteOn): VoiceData = VoiceData.empty.copy(
        sound = "supersaw",
        freqHz = Midi.midiToFreq(evt.key.note.toDouble()),
        velocity = evt.velocity / 127.0,
    )

    /** Exhaustive by design: a new connector event fails the build until this page decides. */
    private fun handle(evt: MidiConnector.Event): Unit = when (evt) {
        is MidiConnector.Event.NoteOn -> {
            logEvent(evt.tsMs, "ON  ${noteLabel(evt.key)} vel ${evt.velocity} ch ${evt.key.channel}")
            ctrl.startVoice(liveId = evt.liveId, data = voiceFor(evt), gateDurSec = null)
        }

        is MidiConnector.Event.NoteOff -> {
            logEvent(evt.tsMs, "OFF ${noteLabel(evt.key)} ch ${evt.key.channel}")
            ctrl.stopVoice(evt.liveId)
        }

        is MidiConnector.Event.AllNotesOff -> {
            logEvent(0.0, "PANIC (${evt.reason}) — releasing ${evt.liveIds.size} voice(s)")
            evt.liveIds.forEach { ctrl.stopVoice(it) }
        }

        // v1 will map these onto oscparams.
        is MidiConnector.Event.ControlChange -> Unit
    }

    //  HELPERS  ////////////////////////////////////////////////////////////////////////////////

    private fun logEvent(tsMs: Double, entry: String) {
        eventLog = (listOf("${formatTs(tsMs)}  $entry") + eventLog).take(EVENT_LOG_SIZE)
    }

    /** DOMHighResTimeStamp ms with one decimal, e.g. "12345.7" */
    private fun formatTs(tsMs: Double): String = tsMs.asDynamic().toFixed(1).unsafeCast<String>()

    private fun noteLabel(key: NoteKey): String =
        "${Midi.midiToNoteName(key.note.toDouble())} (${key.note})"

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            key = "midi-playground-page"

            ui.basic.segment {
                ui.header H1 { +"Midi Playground" }

                when {
                    ctrlState.isReady -> renderPlayground()
                    else -> renderStartEngine()
                }
            }
        }
    }

    /**
     * The engine gate. Browsers only start an AudioContext from a user gesture, so this button IS
     * the gesture — an honest state rather than a silently suspended context.
     */
    private fun FlowContent.renderStartEngine() {
        ui.basic.segment {
            key = "start-engine"

            ui.big.primary.button {
                onClick { ctrl.start() }

                when {
                    // Clicking retries: Player.ensure() drops its memoized deferred on failure.
                    ctrlState.isFailed -> {
                        icon.exclamation_triangle()
                        +"Retry"
                    }

                    ctrlState.isLoading -> {
                        icon.loading.spinner()
                        +"Starting the Motör ..."
                    }

                    else -> {
                        icon.power_off()
                        +"Start Engine"
                    }
                }
            }

            if (ctrlState.isFailed) {
                ui.negative.message {
                    ui.header { +"The engine did not start" }
                    +(ctrlState.error ?: "Unknown error, see the browser console.")
                }
            }
        }
    }

    private fun FlowContent.renderPlayground() {
        renderDevices()
        renderPressedKeys()
        renderEventLog()
    }

    private fun FlowContent.renderDevices() {
        when (midiState.status) {
            MidiConnector.Status.Unsupported -> ui.warning.message {
                ui.header { +"Web MIDI is not supported in this browser" }
                +"Chrome and Edge support it out of the box. Safari does not; Firefox only via a site-permission add-on."
            }

            MidiConnector.Status.Requesting -> ui.info.message {
                +"Requesting MIDI access ... the browser may ask for permission."
            }

            MidiConnector.Status.Denied -> ui.negative.message {
                ui.header { +"MIDI setup failed" }
                +(midiState.error ?: "")
                ui.divider()
                +"Known walls: snap-packaged browsers (e.g. Ubuntu's Chromium snap) cannot reach MIDI devices at all, "
                +"and Firefox gates MIDI behind a site-permission add-on that is usually unavailable on localhost."
            }

            MidiConnector.Status.Ready -> if (midiState.devices.isEmpty()) {
                ui.info.message {
                    +"No MIDI devices found. Plug in a keyboard — it will show up here automatically."
                }
            } else {
                ui.list {
                    midiState.devices.forEach { name ->
                        noui.item {
                            icon.keyboard()
                            noui.content { +name }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderPressedKeys() {
        ui.segment {
            key = "pressed-keys"
            css { minHeight = 90.px }

            val held = midiState.held

            if (held.isEmpty()) {
                ui.header H3 {
                    css { fontWeight = FontWeight.normal }
                    +"Play something on your keyboard ..."
                }
            } else {
                ui.labels {
                    held.values.sortedBy { it.key.note }.forEach { note ->
                        ui.big.label {
                            key = "note-${note.key.channel}-${note.key.note}"
                            css { fontSize = 24.px }
                            +noteLabel(note.key)
                            noui.detail { +"vel ${note.velocity}" }
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
