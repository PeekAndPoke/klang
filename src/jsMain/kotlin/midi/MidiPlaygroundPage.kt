/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.spine.MidiRig
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.storage.MidiInstrument
import io.peekandpoke.klang.storage.MidiInstrumentStorage
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.utils.WakeLockComponent
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.FontWeight
import kotlinx.css.Padding
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.minHeight
import kotlinx.css.padding
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

@Suppress("FunctionName")
fun Tag.MidiPlaygroundPage() = comp {
    MidiPlaygroundPage(it)
}

/**
 * Midi Playground: play the engine live from a MIDI keyboard, the laptop's keys, or the drawings.
 *
 * What plays lives in the Spine, the MIDI rig ([Playbacks.live], KLANG-00402 step 4): the ports, the profiles, the
 * routing, the held notes, the realtime playback and the translation. The page brings it up, hands it input and draws
 * what it publishes; leaving the page releases nothing but the laptop's keys, whose drawing goes with it. The drawings
 * are the MIDI tool's too ([DeviceView], [LaptopKeysView]); the page keeps what only it shows: the instrument's editors
 * and the saved instruments, the monitor and the log. See KLANG-00159 (realtime playback controller).
 */
class MidiPlaygroundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////

    private val rig = Playbacks.live

    private val rigState by subscribingTo(rig.state)

    private val ctrlState by subscribingTo(rig.playback)

    private val midiState by subscribingTo(rig.devices)

    /**
     * What the two editors hold right now, and the name it is saved under. Takes effect on
     * [applyInstrument]; until then notes keep playing the last applied version.
     */
    private var draft: MidiInstrument by value(MidiInstrumentStorage.loadDraft() ?: MidiRig.DEFAULT_INSTRUMENT)

    /** The instruments saved in localStorage, in the order they were first saved. */
    private var savedInstruments: List<MidiInstrument> by value(MidiInstrumentStorage.loadAll())

    /** Bumped when a saved instrument is loaded, so the editors remount with its code. */
    private var editorGeneration: Int by value(0)

    companion object {
        private val EDITOR_LIBS = listOf(stdlibLib, sprudelLib)
    }

    init {
        lifecycle {
            onMount {
                // Once for the app's life; the rig plays on when the page goes. The MIDI page asks for the devices
                rig.start()
                rig.connectDevices()
            }
        }
    }

    //  THE INSTRUMENT: two KlangScript inputs, applied by the rig  ////////////////////////////

    /** The rig plays the draft from the next note on, and keeps it for the next visit. */
    private fun applyInstrument() {
        rig.apply(draft)
    }

    /** Saves the draft under its name, replacing a saved instrument of the same name. */
    private fun saveInstrument() {
        val toSave = draft.copy(name = draft.name.trim().ifBlank { "Untitled" })
        val exists = savedInstruments.any { it.name == toSave.name }

        draft = toSave
        savedInstruments = when {
            exists -> savedInstruments.map { if (it.name == toSave.name) toSave else it }
            else -> savedInstruments + toSave
        }
        MidiInstrumentStorage.saveAll(savedInstruments)
        applyInstrument()
    }

    private fun loadInstrument(instrument: MidiInstrument) {
        draft = instrument
        editorGeneration += 1
        applyInstrument()
    }

    private fun deleteInstrument(name: String) {
        savedInstruments = savedInstruments.filter { it.name != name }
        MidiInstrumentStorage.saveAll(savedInstruments)
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            key = "midi-playground-page"

            ui.basic.segment {
                ui.header H1 { +"Midi Playground" }

                // The screen stays awake on this page, always (the maintainer, 2026-10-31: "On the midi page we can have
                // a dedicated wakelock always"): a keyboard played over USB or Bluetooth never touches the screen, and a
                // phone or tablet that locks it stops the sound. The realtime voices do not count for Player.playing.
                // Below the heading: a hidden first child would still take `.ui.segment:first-child`'s place.
                WakeLockComponent()

                when {
                    ctrlState.isReady -> renderPlayground()
                    else -> renderStartEngine()
                }
            }
        }
    }

    /**
     * The engine gate. Browsers only start an AudioContext from a user gesture, so this button IS
     * the gesture: an honest state rather than a silently suspended context.
     */
    private fun FlowContent.renderStartEngine() {
        ui.basic.segment {
            key = "start-engine"

            ui.big.primary.button {
                onClick { rig.startEngine() }

                when {
                    // Clicking retries: Player.ensure() drops its memoized deferred on failure.
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
        renderPanel()
        renderLaptopKeys()
        renderInstrument()
        renderSoundPicker()
        renderPressedKeys()
        renderMonitor()
        renderEventLog()
    }

    private fun FlowContent.renderPanel() {
        ui.basic.segment {
            key = "device-panel"

            DeviceView()
        }
    }

    private fun FlowContent.renderLaptopKeys() {
        ui.basic.segment {
            key = "laptop-keys"

            LaptopKeysView()
        }
    }

    /**
     * Live map of every controller the device emits: the tool that makes wiring knobs possible,
     * since a KeyLab's encoder CCs live in its MIDI Control Center preset rather than in any chart.
     * Twist one control at a time and note which row appears.
     */
    private fun FlowContent.renderMonitor() {
        ui.segment {
            key = "midi-monitor"

            ui.header H4 { +"Controller monitor" }

            if (rigState.observed.isEmpty()) {
                noui.content {
                    +"Move a knob, fader, wheel or pad: every controller it sends shows up here "
                    +"with its number, so we can map them."
                }
            } else {
                ui.mini.basic.button {
                    +"Clear"
                    onClick { rig.clearObserved() }
                }

                ui.very.compact.celled.table Table {
                    thead {
                        tr {
                            th { +"Control" }
                            th { +"Value" }
                            th { +"Ch" }
                            th { +"Msgs" }
                        }
                    }
                    tbody {
                        rigState.observed.entries.sortedBy { it.key }.forEach { (label, c) ->
                            tr {
                                td { +label }
                                td { +"${c.raw}" }
                                td { +"${c.channel}" }
                                td { +"${c.hits}" }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderInstrument() {
        ui.segment {
            key = "instrument"

            ui.header H4 { +"Instrument" }

            if (savedInstruments.isNotEmpty()) {
                noui.tiny.header { +"Saved" }

                ui.basic.segment {
                    css { padding = Padding(0.px, 0.px, 8.px, 0.px) }

                    savedInstruments.forEach { instrument ->
                        ui.small.given(instrument.name == draft.name) { primary }.button {
                            key = "instrument-${instrument.name}"
                            onClick { loadInstrument(instrument) }
                            +instrument.name
                        }
                    }
                }
            }

            ui.form {
                UiInputField(draft.name, { draft = draft.copy(name = it) }) {
                    placeholder("Instrument name")
                    leftLabel {
                        ui.grey.label { +"Name" }
                    }
                }
            }

            ui.basic.segment {
                css { padding = Padding(8.px, 0.px) }

                ui.primary.button {
                    onClick { applyInstrument() }
                    icon.play()
                    +"Apply"
                }

                ui.button {
                    onClick { saveInstrument() }
                    icon.save()
                    +"Save"
                }

                if (savedInstruments.any { it.name == draft.name }) {
                    ui.basic.button {
                        onClick { deleteInstrument(draft.name) }
                        icon.trash()
                        +"Delete"
                    }
                }
            }

            ui.two.column.stackable.grid {
                noui.column {
                    renderCodeInput(
                        id = "ignitor",
                        title = "Ignitor",
                        hint = "Evaluates to an Ignitor, e.g. Ign.saw().lowpass(1800). Blank plays the built-in sound below.",
                        code = draft.ignitorCode,
                        onChange = { draft = draft.copy(ignitorCode = it) },
                        error = rigState.ignitorError,
                    )
                }

                noui.column {
                    renderCodeInput(
                        id = "mapper",
                        title = "Sprudel mapper",
                        hint = "A pattern mapper: reverb(wet = 0.2), or a function, x => x.reverb(wet = 0.2). Blank = none.",
                        code = draft.mapperCode,
                        onChange = { draft = draft.copy(mapperCode = it) },
                        error = rigState.mapperError,
                    )
                }
            }
        }
    }

    private fun FlowContent.renderCodeInput(
        id: String,
        title: String,
        hint: String,
        code: String,
        onChange: (String) -> Unit,
        error: String?,
    ) {
        noui.tiny.header { +title }

        noui.content {
            css { padding = Padding(0.px, 0.px, 4.px, 0.px) }
            +hint
        }

        // Keyed by generation: loading a saved instrument remounts the editor with its code.
        ui.segment {
            key = "$id-editor-$editorGeneration"
            css { padding = Padding(0.px) }

            KlangScriptEditorComp(
                code = code,
                onCodeChanged = onChange,
                availableLibraries = EDITOR_LIBS,
                autoImportedLibraries = EDITOR_LIBS,
            )
        }

        if (error != null) {
            ui.negative.message { +error }
        }
    }

    private fun FlowContent.renderSoundPicker() {
        ui.segment {
            key = "sound-picker"

            ui.header H4 { +"Built-in sound" }

            noui.content {
                css { padding = Padding(0.px, 0.px, 4.px, 0.px) }
                +"Plays while the Ignitor input is blank."
            }

            ui.two.column.grid {
                MidiRig.SOUND_GROUPS.forEach { (groupName, sounds) ->
                    noui.column {
                        key = "sound-group-$groupName"
                        css { padding = Padding(4.px, 0.px) }

                        noui.tiny.header { +groupName }

                        ui.buttons {
                            sounds.forEach { sound ->
                                ui.given(sound == rigState.selectedSound) { primary }.button {
                                    key = "sound-$sound"
                                    onClick { rig.selectSound(sound) }
                                    +sound
                                }
                            }
                        }
                    }
                }
            }
        }
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
                    +"No MIDI devices found. Plug in a keyboard, and it will show up here automatically."
                }
            } else {
                ui.horizontal.list {
                    midiState.devices.forEach { name ->
                        noui.item {
                            ui.label {
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
            css { minHeight = 90.px }

            if (rigState.held.isEmpty()) {
                ui.header H3 {
                    css { fontWeight = FontWeight.normal }
                    +"Play something on your keyboard ..."
                }
            } else {
                ui.labels {
                    rigState.held.values.sortedBy { it.key.note }.forEach { note ->
                        ui.big.label {
                            key = "note-${note.key.source.id}-${note.key.channel}-${note.key.note}"
                            css { fontSize = 24.px }
                            +MidiRig.noteLabel(note.key.note)
                            noui.detail { +"vel ${MidiRig.velocityLabel(note.velocity)}" }
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
                onClick { rig.clearLog() }
            }

            ui.list {
                css { fontFamily = "monospace" }

                rigState.eventLog.forEachIndexed { idx, entry ->
                    noui.item {
                        key = "evt-$idx"
                        +entry
                    }
                }
            }
        }
    }
}
