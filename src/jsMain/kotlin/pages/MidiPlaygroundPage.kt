/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.comp.MidiPanel
import io.peekandpoke.klang.comp.RealtimePlaybackCtrl
import io.peekandpoke.klang.midi.KeyLabEssential49Mk3
import io.peekandpoke.klang.midi.MidiConnector
import io.peekandpoke.klang.midi.MidiInstrument
import io.peekandpoke.klang.midi.MidiInstrumentStorage
import io.peekandpoke.klang.midi.NoteKey
import io.peekandpoke.klang.midi.padBankOf
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.runtime.FunctionValue
import io.peekandpoke.klang.script.runtime.convertFunctionToKotlin
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.sound
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.sprudel.lang.velocity
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.semanticui.forms.UiInputField
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
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
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

    /** One row per distinct controller seen, for discovering a device's map. */
    data class ObservedControl(val raw: Int, val channel: Int, val hits: Int)

    private var observed: Map<String, ObservedControl> by value(emptyMap())

    /**
     * Live positions of the device's controls, `0..1`, keyed by panel control id — this is what
     * makes the on-screen faders follow the physical ones. Fed by the device's [MidiBindings];
     * anything unbound simply never appears here and stays at its resting position.
     */
    private var controlValues: Map<String, Double> by value(emptyMap())

    /**
     * Which pad bank we believe is live.
     *
     * The device never announces this — the Bank button (CC 118) only reports that it was pressed.
     * So this starts as a GUESS ("A", the power-on default) and is kept honest two ways: pressing
     * Bank toggles it, and any pad note CORRECTS it, because the note number says unambiguously
     * which bank produced it. The correction is what makes a wrong guess self-healing — one pad hit
     * and the display is right again.
     */
    private var activeBank: String by value("A")

    /**
     * Semitone shift of the drawn keybed, matching the device's Transpose/Octave setting.
     *
     * MANUAL, and it cannot be otherwise: those buttons change what the keys transmit and send no
     * MIDI at all, and a note transposed down a semitone is byte-for-byte identical to the
     * untransposed note below it. Nothing in the stream distinguishes them.
     *
     * An earlier version guessed this from notes landing outside the drawn range. That was not
     * inference — an out-of-range note only says the drawing cannot show it, not why (a second
     * device on another range does the same thing) — and once this became settable it silently
     * overrode the user's own choice. Removed deliberately; do not reintroduce it.
     */
    private var keyBedOffset: Int by value(0)

    /**
     * The built-in oscillator the NEXT note will use. Notes already sounding keep the sound they
     * were started with — the voice is built at note-on, and re-sounding a held key would be a
     * glitch, not a feature.
     */
    private var selectedSound: String by value("supersaw")

    /**
     * What the two editors hold right now, and the name it is saved under. Takes effect on
     * [applyInstrument]; until then notes keep playing the last applied version.
     */
    private var draft: MidiInstrument by value(MidiInstrumentStorage.loadDraft() ?: DEFAULT_INSTRUMENT)

    /** The instruments saved in localStorage, in the order they were first saved. */
    private var savedInstruments: List<MidiInstrument> by value(MidiInstrumentStorage.loadAll())

    /** Bumped when a saved instrument is loaded, so the editors remount with its code. */
    private var editorGeneration: Int by value(0)

    /** The applied ignitor; null = the built-in [selectedSound]. */
    private var appliedIgnitor: IgnitorDsl? by value(null)

    /** The applied sprudel mapper (`x => x.reverb(wet = 0.2)`); null = none. */
    private var appliedMapper: ((Any?) -> Any?)? by value(null)

    private var ignitorError: String? by value(null)

    private var mapperError: String? by value(null)

    companion object {
        /** The device this page draws and binds. One value per supported controller. */
        private val DEVICE = KeyLabEssential49Mk3

        private const val EVENT_LOG_SIZE = 16

        /** The tempo a mapper sees: 30 rpm, the song page's default. Only time-aware mappers notice. */
        private const val MAPPER_CPS = 0.5

        private val EDITOR_LIBS = listOf(stdlibLib, sprudelLib)

        private val DEFAULT_INSTRUMENT = MidiInstrument(
            name = "Warm saw",
            ignitorCode = "Ign.saw()\n  .plus(Ign.sine().detune(12).mul(0.3))\n  .lowpass(1800)\n  .adsr(0.01, 0.3, 0.6, 0.4)",
            mapperCode = "x => x.reverb(wet = 0.2)",
        )

        /** Pad bank A / B highlight colours — distinct hues, since the bank is otherwise invisible. */
        private const val BANK_A_COLOR = "#4a9eff"
        private const val BANK_B_COLOR = "#f0a33c"

        /** The Bank button. Reports presses only, never which bank went live. */
        private const val CC_BANK = 118

        /**
         * The playable built-ins, grouped for the picker. Names must match the backend's default
         * ignitor registry (`IgnitorDefaults`); aliases (`sin`, `sqr`, `tri`, `ks`, …) are left out
         * so the picker shows each sound once.
         */
        private val SOUND_GROUPS: List<Pair<String, List<String>>> = listOf(
            "Waves" to listOf("sine", "sawtooth", "square", "triangle", "ramp", "pulze"),
            "Raw (aliased)" to listOf("zaw", "zamp"),
            "Unison" to listOf("supersaw", "superramp", "supersquare", "supertri", "supersine", "superpulse"),
            "Plucked" to listOf("pluck", "superpluck"),
            "Noise" to listOf("whitenoise", "pinknoise", "brownnoise", "dust", "crackle", "perlinnoise", "berlinnoise"),
        )
    }

    init {
        lifecycle {
            onMount {
                applyInstrument()
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

    /**
     * A key becomes a one-note sprudel pattern: the note, its velocity, the applied ignitor (or the
     * built-in sound). The applied mapper then shapes it like any sprudel pattern, and whatever
     * starts at cycle 0 sounds now, under the key's liveId. Later onsets (a mapper with `off` or
     * `fast`) are dropped: a realtime voice has no timeline to land on.
     *
     * Key velocity goes through sprudel's own `velocity`, which folds into the wire's one level word
     * (signal-flow plan section 6).
     */
    private fun eventsFor(evt: MidiConnector.Event.NoteOn): List<KlangPatternEvent> {
        val base = note(evt.key.note.toString())
            .velocity(evt.velocity / 127.0)
            .sound(appliedIgnitor ?: selectedSound)

        val pattern = appliedMapper?.let { mapper -> mapPattern(mapper, base) } ?: base

        return pattern.queryEvents(fromCycles = 0.0, toCycles = 1.0, cps = MAPPER_CPS)
            .filter { it.startCycles == 0.0 }
    }

    /** Runs the user's mapper; a failure shows under its editor and the note plays unmapped. */
    private fun mapPattern(mapper: (Any?) -> Any?, base: SprudelPattern): SprudelPattern = try {
        mapper(base) as? SprudelPattern ?: error("The mapper must return a pattern")
    } catch (e: Throwable) {
        mapperError = e.message ?: e.toString()
        base
    }

    //  THE INSTRUMENT — two KlangScript inputs, compiled on apply  /////////////////////////////

    /**
     * Compiles both inputs and stores the draft. An input that fails keeps its previous applied
     * version, so a typo never silences the keyboard mid-play.
     */
    private fun applyInstrument() {
        MidiInstrumentStorage.saveDraft(draft)

        val engine = Player.createEngine()
        engine.execute("""import * from "stdlib"""")
        engine.execute("""import * from "sprudel"""")

        ignitorError = null
        mapperError = null

        try {
            appliedIgnitor = compileIgnitor(engine, draft.ignitorCode)
        } catch (e: Throwable) {
            ignitorError = e.message ?: e.toString()
        }

        try {
            appliedMapper = compileMapper(engine, draft.mapperCode)
        } catch (e: Throwable) {
            mapperError = e.message ?: e.toString()
        }
    }

    private fun compileIgnitor(engine: KlangScriptEngine, code: String): IgnitorDsl? {
        if (code.isBlank()) {
            return null
        }

        val result = engine.execute(code + "\n")

        return result.value as? IgnitorDsl
            ?: error("Expected an Ignitor, e.g. Ign.saw(), got ${result.toDisplayString()}")
    }

    private fun compileMapper(engine: KlangScriptEngine, code: String): ((Any?) -> Any?)? {
        if (code.isBlank()) {
            return null
        }

        val result = engine.execute(code + "\n")

        @Suppress("UNCHECKED_CAST")
        return when (result) {
            is FunctionValue -> result.convertFunctionToKotlin(arity = 1)
            // A native mapper value, e.g. one sprudel hands out.
            else -> result.value as? Function1<Any?, Any?>
        } ?: error("Expected a function, e.g. x => x.reverb(wet = 0.2), got ${result.toDisplayString()}")
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

    /** Exhaustive by design: a new connector event fails the build until this page decides. */
    private fun handle(evt: MidiConnector.Event): Unit = when (evt) {
        is MidiConnector.Event.NoteOn -> {
            logEvent(evt.tsMs, "ON  ${noteLabel(evt.key)} vel ${evt.velocity} ch ${evt.key.channel}")
            // A pad note is ground truth about the bank — the note number says which bank sent it.
            // Note there is NO equivalent for the keybed: nothing in a note message reveals the
            // device's transpose/octave setting, so keybed alignment stays a manual control.
            if (DEVICE.bindings.notes[evt.key.channel to evt.key.note] != null) {
                padBankOf(evt.key.note)?.let { activeBank = it }
            }
            ctrl.startEvents(liveId = evt.liveId, events = eventsFor(evt), gateDurSec = null)
        }

        is MidiConnector.Event.NoteOff -> {
            logEvent(evt.tsMs, "OFF ${noteLabel(evt.key)} ch ${evt.key.channel}")
            ctrl.stopVoice(evt.liveId)
        }

        is MidiConnector.Event.AllNotesOff -> {
            logEvent(0.0, "PANIC (${evt.reason}) — releasing ${evt.liveIds.size} voice(s)")
            evt.liveIds.forEach { ctrl.stopVoice(it) }
        }

        is MidiConnector.Event.ControlChange -> {
            observeControl("CC ${evt.cc}", evt.value, evt.channel)
            DEVICE.bindings.cc[evt.cc]?.let { moveControl(it, evt.value / 127.0) }
            // Only on PRESS: a button that also reports its release would otherwise toggle twice
            // and appear to do nothing.
            if (evt.cc == CC_BANK && evt.value > 0) {
                activeBank = if (activeBank == "A") "B" else "A"
            }
            Unit
        }

        is MidiConnector.Event.Other -> {
            observeControl(otherLabel(evt.kind, evt.data1), evt.data2, evt.channel)
            // Pitch bend is 14-bit across both data bytes, unlike everything else here.
            if (evt.kind == 0xE0) {
                DEVICE.bindings.pitchBend?.let {
                    moveControl(it, ((evt.data2 shl 7) or evt.data1) / 16383.0)
                }
            }
            Unit
        }
    }

    /** Moves an on-screen control to [value] (`0..1`). */
    private fun moveControl(controlId: String, value: Double) {
        controlValues = controlValues + (controlId to value)
    }

    /**
     * Held notes that belong on the KEYBED.
     *
     * The pads send notes too (channel 10, notes 36-51 on a KeyLab), and those numbers land inside
     * the drawn key range — so without this filter, hitting a pad lights a piano key. The device's
     * note bindings decide which is which.
     */
    private fun heldKeyNotes(): Set<Int> = midiState.held.keys
        .filter { DEVICE.bindings.notes[it.channel to it.note] == null }
        .map { it.note }
        .toSet()

    /** Pads currently struck, as panel controls, lit at their strike velocity. */
    private fun heldPads(): Map<String, Double> = midiState.held.entries
        .mapNotNull { (key, note) ->
            DEVICE.bindings.notes[key.channel to key.note]?.let { it to note.velocity / 127.0 }
        }
        .toMap()

    /**
     * Colours struck pads by bank: the Bank button only reports that it was pressed, never which
     * bank went live, so the note itself is what says which one you are on — and colour shows it
     * without a readout.
     */
    /** Every pad outlined in the active bank's colour, so the bank is readable at rest. */
    private fun padOutlines(): Map<String, String> {
        val color = if (activeBank == "A") BANK_A_COLOR else BANK_B_COLOR

        return (1..8).associate { "pad-$it" to color }
    }

    private fun padTints(): Map<String, String> = midiState.held.entries
        .mapNotNull { (key, _) ->
            val control = DEVICE.bindings.notes[key.channel to key.note] ?: return@mapNotNull null
            val color = when (padBankOf(key.note)) {
                "A" -> BANK_A_COLOR
                "B" -> BANK_B_COLOR
                else -> return@mapNotNull null
            }
            control to color
        }
        .toMap()

    /**
     * Records a controller movement. This is how a device's knob/fader map gets discovered: twist
     * something and watch which row moves. Keyed by label, so each control is one row however many
     * messages it sends.
     *
     * It has to be empirical — a KeyLab's encoders send whatever its MIDI Control Center preset
     * says, and the manual publishes no defaults for them, so no chart can tell us.
     */
    private fun observeControl(label: String, raw: Int, channel: Int) {
        observed = observed + (
            label to ObservedControl(
                raw = raw,
                channel = channel,
                hits = (observed[label]?.hits ?: 0) + 1,
            )
            )
    }

    private fun otherLabel(kind: Int, data1: Int): String = when (kind) {
        0xA0 -> "Poly aftertouch note $data1"
        0xC0 -> "Program change"
        0xD0 -> "Channel pressure"
        0xE0 -> "Pitch bend"
        else -> "Status 0x${kind.toString(16)} d1=$data1"
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
        renderPanel()
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
     * shifts DO get detected — they eventually push notes off the drawn range, which is visible.)
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
                        keyBedOffset > 0 -> "+$keyBedOffset — reset"
                        else -> "$keyBedOffset — reset"
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
                heldNotes = heldKeyNotes(),
                activeControls = controlValues + heldPads(),
                tints = padTints(),
                outlines = padOutlines(),
                noteOffset = keyBedOffset,
            )
        }
    }

    /**
     * Live map of every controller the device emits — the tool that makes wiring knobs possible,
     * since a KeyLab's encoder CCs live in its MIDI Control Center preset rather than in any chart.
     * Twist one control at a time and note which row appears.
     */
    private fun FlowContent.renderMonitor() {
        ui.segment {
            key = "midi-monitor"

            ui.header H4 { +"Controller monitor" }

            if (observed.isEmpty()) {
                noui.content {
                    +"Move a knob, fader, wheel or pad — every controller it sends shows up here "
                    +"with its number, so we can map them."
                }
            } else {
                ui.mini.basic.button {
                    +"Clear"
                    onClick { observed = emptyMap() }
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
                        observed.entries.sortedBy { it.key }.forEach { (label, c) ->
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
                        error = ignitorError,
                    )
                }

                noui.column {
                    renderCodeInput(
                        id = "mapper",
                        title = "Sprudel mapper",
                        hint = "Evaluates to a function from pattern to pattern, e.g. x => x.reverb(wet = 0.2). Blank = none.",
                        code = draft.mapperCode,
                        onChange = { draft = draft.copy(mapperCode = it) },
                        error = mapperError,
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
                SOUND_GROUPS.forEach { (groupName, sounds) ->
                    noui.column {
                        key = "sound-group-$groupName"
                        css { padding = Padding(4.px, 0.px) }

                        noui.tiny.header { +groupName }

                        ui.buttons {
                            sounds.forEach { sound ->
                                ui.given(sound == selectedSound) { primary }.button {
                                    key = "sound-$sound"
                                    onClick { selectedSound = sound }
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
                    +"No MIDI devices found. Plug in a keyboard — it will show up here automatically."
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
