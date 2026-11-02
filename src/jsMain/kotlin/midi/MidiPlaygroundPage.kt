/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.spine.MidiRig
import io.peekandpoke.klang.spine.MidiSettings
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
import io.peekandpoke.ultra.html.onBlur
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onFocus
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.html.onKeyUp
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.browser.window
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
import kotlinx.html.div
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.Promise

@Suppress("FunctionName")
fun Tag.MidiPlaygroundPage() = comp {
    MidiPlaygroundPage(it)
}

/**
 * Midi Playground: play the engine live from a MIDI keyboard, the laptop's keys, or the drawings.
 *
 * What plays lives in the Spine, the MIDI rig ([Playbacks.live], KLANG-00402 step 4): the ports, the profiles, the
 * routing, the held notes, the realtime playback and the translation. The page brings it up, hands it input and draws
 * what it publishes; leaving the page releases nothing but the laptop's keys, whose drawing goes with it. The page keeps
 * what only it shows: the instrument's editors and the saved instruments, the keybed's alignment, the laptop drawing's
 * focus and letters. See KLANG-00159 (realtime playback controller).
 */
class MidiPlaygroundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////

    private val rig = Playbacks.live

    private val rigState by subscribingTo(rig.state)

    private val ctrlState by subscribingTo(rig.playback)

    private val midiState by subscribingTo(rig.devices)

    private var laptopFocused: Boolean by value(false)

    /** Opened by hand while a KeyLab is connected, when the laptop keys fold to a strip (D5). */
    private var laptopOpen: Boolean by value(false)

    /** The layout chosen by hand; null for "Automatic" (D9). */
    private val keyLayout by subscribingTo(MidiSettings.layout)

    /** The browser's own letters by key position, where it tells them (Chrome, Edge); null elsewhere. */
    private var browserLetters: Map<String, String>? by value(null)

    /** The letters learned from the keys pressed, by position: what "Automatic" shows where the browser tells nothing. */
    private var learnedLetters: Map<String, String> by value(emptyMap())

    /** A KeyLab plugged in folds the laptop keys away (D5): a removed element sends no blur, so they let go here. */
    @Suppress("unused")
    private val keyLabPlugged by subscribingTo(
        rig.devices.map { state -> state.devices.any { it.contains("KeyLab", ignoreCase = true) } }.distinct()
    ) { connected ->
        if (connected && !laptopOpen) {
            releaseLaptop()
        }
    }

    /**
     * Semitone shift of the drawn keybed, matching the device's Transpose/Octave setting.
     *
     * MANUAL, and it cannot be otherwise: those buttons change what the keys transmit and send no
     * MIDI at all, and a note transposed down a semitone is byte-for-byte identical to the
     * untransposed note below it. Nothing in the stream distinguishes them.
     *
     * An earlier version guessed this from notes landing outside the drawn range. That was not
     * inference: an out-of-range note only says the drawing cannot show it, not why (a second
     * device on another range does the same thing), and once this became settable it silently
     * overrode the user's own choice. Removed deliberately; do not reintroduce it.
     */
    private var keyBedOffset: Int by value(0)

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
        /** The device this page draws and binds. One value per supported controller. */
        private val DEVICE = KeyLabEssential49Mk3

        private val EDITOR_LIBS = listOf(stdlibLib, sprudelLib)
    }

    init {
        lifecycle {
            onMount {
                rig.start()
                readBrowserLetters()
            }

            onUnmount {
                // The rig plays on; the laptop's keys go with their drawing, and a removed element sends no blur
                releaseLaptop()
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

    /**
     * Aligns the drawing to the device's Transpose setting.
     *
     * Has to be manual: Transp -/+ changes what the keys send WITHOUT emitting anything, and a
     * note transposed down a semitone is byte-for-byte identical to the untransposed note below
     * it. Nothing in the stream distinguishes them, so the display can only be told. (Octave
     * shifts DO get detected: they eventually push notes off the drawn range, which is visible.)
     */
    private fun FlowContent.renderTransposeControl() {
        ui.basic.segment {
            key = "transpose"
            css { padding = Padding(0.px, 0.px, 4.px, 0.px) }

            noui.tiny.header { +"Keybed alignment" }

            ui.mini.buttons {
                ui.button {
                    onClick { keyBedOffset -= 1 }
                    +"semitone -"
                }
                ui.button {
                    onClick { keyBedOffset -= 12 }
                    +"oct -"
                }
                ui.basic.button {
                    onClick { keyBedOffset = 0 }
                    +when {
                        keyBedOffset == 0 -> "aligned"
                        keyBedOffset > 0 -> "+$keyBedOffset: reset"
                        else -> "$keyBedOffset: reset"
                    }
                }
                ui.button {
                    onClick { keyBedOffset += 12 }
                    +"oct +"
                }
                ui.button {
                    onClick { keyBedOffset += 1 }
                    +"semitone +"
                }
            }
        }
    }

    private fun FlowContent.renderPanel() {
        ui.basic.segment {
            key = "device-panel"

            renderTransposeControl()
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
     * The laptop's keys (KLANG-00402, step 2): drawn as a panel with each key's letter, playing while the drawing has
     * the focus (a click into it, D8), so an editor or a page's own key never meets them. Folded to a strip while a
     * KeyLab is connected (D5), and opened from it by hand.
     */
    private fun FlowContent.renderLaptopKeys() {
        val keyLabConnected = midiState.devices.any { it.contains("KeyLab", ignoreCase = true) }
        val shown = !keyLabConnected || laptopOpen
        val octave = rigState.laptopOctave
        val velocity = rigState.laptopVelocity

        ui.basic.segment {
            key = "laptop-keys"

            ui.horizontal.list {
                noui.item { noui.header { +"Laptop keys" } }

                noui.item {
                    +when {
                        !shown -> "Folded while a KeyLab is connected"
                        laptopFocused -> "Playing: octave C${4 + octave}, velocity $velocity"
                        else -> "Click the keyboard, then play"
                    }
                }

                noui.item {
                    ui.mini.buttons {
                        layoutButton(layout = null, title = "Automatic")

                        KeyLayout.entries.forEach { layoutButton(layout = it, title = it.title) }
                    }
                }

                if (keyLabConnected) {
                    noui.item {
                        ui.mini.basic.button {
                            onClick {
                                // Folding takes the drawing away, and a removed element sends no blur
                                if (laptopOpen) {
                                    releaseLaptop()
                                }

                                laptopOpen = !laptopOpen
                            }
                            +(if (laptopOpen) "Fold" else "Open")
                        }
                    }
                }
            }

            if (shown) {
                div {
                    attributes["tabindex"] = "0"
                    attributes["role"] = "application"
                    attributes["aria-label"] = "Laptop keys: click, then play"
                    css {
                        put("outline", if (laptopFocused) "2px solid #4a9eff" else "1px dashed #55555c")
                        put("border-radius", "4px")
                        put("max-width", "1000px")
                    }

                    onKeyDown { e -> laptopKey(e, down = true) }
                    onKeyUp { e -> laptopKey(e, down = false) }
                    onFocus { laptopFocused = true }
                    // A key held when the focus goes never sends its up: everything the keys hold lets go
                    onBlur { releaseLaptop() }

                    val panel = laptopKeyboardPanel(label = ::letterOf)

                    MidiPanel(
                        panel = panel,
                        heldNotes = rigState.heldNotes(laptopSide = true),
                        activeControls = rigState.pressed(laptopSide = true),
                        noteOffset = octave * 12,
                        keyLabels = LaptopKeys.keyLabels(octave = octave, label = ::letterOf),
                        screen = MidiRig.LAPTOP_SCREEN,
                        onInput = { events -> events.forEach { rig.handle(it) } },
                    )
                }

                noui.small.text {
                    +"Shift+Tab leaves the keyboard (Tab is the sustain, Esc the panic). Many laptop keyboards drop the third or "
                    +"fourth key of some chords: that is the keyboard, not Klang."
                }
            }
        }
    }

    /** The laptop keys let go of everything they hold: a key held when the focus goes never sends its up. */
    private fun releaseLaptop() {
        laptopFocused = false
        rig.laptopFocusLost(tsMs = window.performance.now())
    }

    private fun FlowContent.layoutButton(layout: KeyLayout?, title: String) {
        ui.given(keyLayout == layout) { primary }.button {
            key = "layout-${layout?.name ?: "auto"}"
            onClick { MidiSettings.chooseLayout(layout) }
            +title
        }
    }

    /** A key on the laptop's drawing: through the rig; a key that is the laptop's does not reach the browser too. */
    private fun laptopKey(e: KeyboardEvent, down: Boolean) {
        val handled = when (down) {
            true -> rig.laptopKeyDown(
                code = e.code,
                repeat = e.repeat,
                modified = e.ctrlKey || e.altKey || e.metaKey,
                shift = e.shiftKey,
                tsMs = e.timeStamp.toDouble(),
            )

            false -> rig.laptopKeyUp(code = e.code, tsMs = e.timeStamp.toDouble())
        }

        if (handled) {
            e.preventDefault()

            // "Automatic" learns each key's letter as it is first pressed, where the browser does not tell the layout; not
            // with Shift, which prints another sign (Shift and the comma is "<" on an English board)
            if (down && !e.shiftKey && e.key.length == 1 && e.code !in learnedLetters) {
                learnedLetters = learnedLetters + (e.code to e.key.uppercase())
            }
        }
    }

    /** The letter on the key at [code]: the layout chosen, else the browser's, else learned, else the English one. */
    private fun letterOf(code: String): String =
        keyLayout?.labelOf(code) ?: browserLetters?.get(code) ?: learnedLetters[code] ?: KeyLayout.English.labelOf(code)

    /** Asks the browser for its layout's letters, where it tells them (Chrome, Edge: the Keyboard Map API). */
    private fun readBrowserLetters() {
        val keyboard = window.navigator.asDynamic().keyboard ?: return

        try {
            val read: Promise<dynamic> = keyboard.getLayoutMap().unsafeCast<Promise<dynamic>>()

            read.then<Unit> { map ->
                val letters = mutableMapOf<String, String>()
                val note: (String, String) -> Unit = { letter, code -> letters[code] = letter.uppercase() }

                map.forEach(note)
                browserLetters = letters
            }.catch { e ->
                // Refused (a frame without the keyboard-map permission): the letters are learned instead
                console.warn("[MidiPlayground] the browser's keyboard map is not readable", e)
            }
        } catch (e: Throwable) {
            console.warn("[MidiPlayground] the browser's keyboard map is not readable", e)
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
