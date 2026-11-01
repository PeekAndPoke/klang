/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.comp.RealtimePlaybackCtrl
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.runtime.FunctionValue
import io.peekandpoke.klang.script.runtime.convertFunctionToKotlin
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.spine.MidiSettings
import io.peekandpoke.klang.spine.Player
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.sound
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.sprudel.lang.velocity
import io.peekandpoke.klang.storage.MidiInstrument
import io.peekandpoke.klang.storage.MidiInstrumentStorage
import io.peekandpoke.klang.tones.midi.Midi
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
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.MidiPlaygroundPage() = comp {
    MidiPlaygroundPage(it)
}

/**
 * Midi Playground: play the engine live from a MIDI keyboard.
 *
 * The page owns **the translation** ([eventsFor], what a note sounds like; [padEvents], what a pad hits) and the
 * wiring ([handle]). Engine lifecycle lives in [RealtimePlaybackCtrl]; the ports and bytes in [MidiConnector]; what
 * the bytes mean in a [DeviceProfile] per port; the held notes in [NoteVoicing]; what a control does in [Routing]
 * (KLANG-00402, step 1). See KLANG-00159 (realtime playback controller).
 */
class MidiPlaygroundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////

    private val ctrl = RealtimePlaybackCtrl(playbackName = "midi-playground")

    private val midi = MidiConnector()

    /** The held notes of every source: retrigger, sustain, one source letting go (KLANG-00402, section 5). */
    private val voicing = NoteVoicing(nextLiveId = ctrl::nextLiveId)

    /** What each control does: the v1 defaults (KLANG-00402, section 4). */
    private val routing = Routing.defaults

    /** One profile per port, made on its first message: its bytes to [ControlEvent]s. */
    private val profiles = mutableMapOf<String, DeviceProfile>()

    /** The voices sounding, as [voicing] holds them, for the drawing and the pressed keys. */
    private var held: Map<NoteVoicing.Key, NoteVoicing.Held> by value(emptyMap())

    /** The momentary controls held down (pads, buttons) by source and id, each with its velocity. */
    private var pressed: Map<Pair<SourceId, String>, Double> by value(emptyMap())

    /** A KeyLab pad held down lights in its bank's colour. */
    private var pressedTints: Map<String, String> by value(emptyMap())

    /** The laptop's keys as a source (KLANG-00402, step 2); they play while their drawing has the focus. */
    private val laptop = LaptopKeys()

    /** The laptop's octave and velocity, mirrored after each key so the page redraws them. */
    private var laptopState: Pair<Int, Int> by value(laptop.octave to laptop.velocity)

    private var laptopFocused: Boolean by value(false)

    /** Opened by hand while a KeyLab is connected, when the laptop keys fold to a strip (D5). */
    private var laptopOpen: Boolean by value(false)

    /** The layout chosen by hand; null for "Automatic" (D9). */
    private val keyLayout by subscribingTo(MidiSettings.layout)

    /** The browser's own letters by key position, where it tells them (Chrome, Edge); null elsewhere. */
    private var browserLetters: Map<String, String>? by value(null)

    /** The letters learned from the keys pressed, by position: what "Automatic" shows where the browser tells nothing. */
    private var learnedLetters: Map<String, String> by value(emptyMap())

    private val ctrlState by subscribingTo(ctrl.state)

    private val midiState by subscribingTo(midi.state)

    /** A KeyLab plugged in folds the laptop keys away (D5): a removed element sends no blur, so they let go here. */
    @Suppress("unused")
    private val keyLabPlugged by subscribingTo(
        midi.state.map { state -> state.devices.any { it.contains("KeyLab", ignoreCase = true) } }.distinct()
    ) { connected ->
        if (connected && !laptopOpen) {
            releaseLaptop()
        }
    }

    /** The kit loads as soon as the engine is up, so a pad's first hit sounds (D2). */
    @Suppress("unused")
    private val kitLoad by subscribingTo(ctrl.state.map { it.isReady }.distinct()) { ready ->
        if (ready) {
            ctrl.preload(requests = kitRequests())
        }
    }

    /** Last events, newest first; pure UI, so it lives here. */
    private var eventLog: List<String> by value(emptyList())

    /** One row per distinct controller seen, for discovering a device's map. */
    data class ObservedControl(val raw: Int, val channel: Int, val hits: Int)

    private var observed: Map<String, ObservedControl> by value(emptyMap())

    /**
     * Live positions of the device's controls, `0..1`, keyed by panel control id; this is what
     * makes the on-screen faders follow the physical ones. Fed by the device's [MidiBindings];
     * anything unbound simply never appears here and stays at its resting position.
     */
    private var controlValues: Map<String, Double> by value(emptyMap())

    /**
     * Which pad bank we believe is live.
     *
     * The device never announces this: the Bank button (CC 118) only reports that it was pressed.
     * So this starts as a GUESS ("A", the power-on default) and is kept honest two ways: pressing
     * Bank toggles it, and any pad note CORRECTS it, because the note number says unambiguously
     * which bank produced it. The correction is what makes a wrong guess self-healing: one pad hit
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
     * inference: an out-of-range note only says the drawing cannot show it, not why (a second
     * device on another range does the same thing), and once this became settable it silently
     * overrode the user's own choice. Removed deliberately; do not reintroduce it.
     */
    private var keyBedOffset: Int by value(0)

    /**
     * The built-in oscillator the NEXT note will use. Notes already sounding keep the sound they
     * were started with: the voice is built at note-on, and re-sounding a held key would be a
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

    /** The applied sprudel mapper (`reverb(wet = 0.2)`, or a function as `x => x.reverb(wet = 0.2)`); null = none. */
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
            mapperCode = "reverb(wet = 0.2)",
        )

        /** Pad bank A / B highlight colours: distinct hues, since the bank is otherwise invisible. */
        private const val BANK_A_COLOR = "#4a9eff"
        private const val BANK_B_COLOR = "#f0a33c"

        /** The drawings as sources: what is clicked or touched on each (KLANG-00402, step 3). */
        private val DEVICE_SCREEN = SourceId("screen:device")
        private val LAPTOP_SCREEN = SourceId("screen:laptop")

        /** What the pads hit, pad 1 to 8: bd sd hh oh / cp rim lt cr (KLANG-00402, D2). */
        private val KIT = listOf("bd", "sd", "hh", "oh", "cp", "rim", "lt", "cr")

        /** A pad's hit is a one-shot: its release does not cut it, the gate ends it. */
        private const val PAD_GATE_SEC = 1.0

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
                readBrowserLetters()
                midi.start()
                midi.events.subscribeToStream { evt -> evt?.let { onMidi(it) } }
            }

            onUnmount {
                midi.tearDown()
                // Its Cleanup releases every voice the playback still holds, sustained ones included
                ctrl.tearDown()
            }
        }
    }

    //  THE TRANSLATION: voice shape, sound and params live here  //////////////////////////////

    /**
     * A key becomes a one-note sprudel pattern: the note, its velocity, the applied ignitor (or the
     * built-in sound). The applied mapper then shapes it like any sprudel pattern, and whatever
     * starts at cycle 0 sounds now, under the key's liveId. Later onsets (a mapper with `off` or
     * `fast`) are dropped: a realtime voice has no timeline to land on.
     *
     * Key velocity goes through sprudel's own `velocity`, which folds into the wire's one level word
     * (signal-flow plan section 6).
     */
    private fun eventsFor(note: Int, velocity: Double): List<KlangPatternEvent> {
        val base = note(note.toString())
            .velocity(velocity)
            .sound(appliedIgnitor ?: selectedSound)

        val pattern = appliedMapper?.let { mapper -> mapPattern(mapper, base) } ?: base

        return pattern.queryEvents(fromCycles = 0.0, toCycles = 1.0, cps = MAPPER_CPS)
            .filter { it.startCycles == 0.0 }
    }

    /** Pad [pad]'s hit at [velocity]: its sound of the [KIT], as sprudel plays it, unmapped. */
    private fun padEvents(pad: Int, velocity: Double): List<KlangPatternEvent> {
        val name = KIT.getOrNull(pad - 1) ?: return emptyList()

        return sound(name).velocity(velocity)
            .queryEvents(fromCycles = 0.0, toCycles = 1.0, cps = MAPPER_CPS)
            .filter { it.startCycles == 0.0 }
    }

    /** The kit's samples, asked for exactly as the pads' hits will ask. */
    private fun kitRequests(): Set<SampleRequest> =
        KIT.indices.flatMap { padEvents(pad = it + 1, velocity = 1.0) }.map { it.toVoiceData().asSampleRequest() }.toSet()

    /** Runs the user's mapper; a failure shows under its editor and the note plays unmapped. */
    private fun mapPattern(mapper: (Any?) -> Any?, base: SprudelPattern): SprudelPattern = try {
        mapper(base) as? SprudelPattern ?: error("The mapper must return a pattern")
    } catch (e: Throwable) {
        mapperError = e.message ?: e.toString()
        base
    }

    //  THE INSTRUMENT: two KlangScript inputs, compiled on apply  /////////////////////////////

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
        } ?: error("Expected a mapper, e.g. reverb(wet = 0.2) or x => x.reverb(wet = 0.2), got ${result.toDisplayString()}")
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

    /** A port's word: a message goes to the monitor and through the port's profile; a port gone lets its keys go. */
    private fun onMidi(evt: MidiConnector.Event): Unit = when (evt) {
        is MidiConnector.Event.Message -> {
            observe(evt.message)

            val profile = profiles.getOrPut(evt.message.port) { DeviceProfile.forPort(evt.message.port) }
            val events = profile.translate(evt.message)

            // The bank first, so a pad lights in the bank its own note said; and only from a pad or the Bank button, so
            // another port of the device (or another device) cannot put it back to its own guess
            if (events.any { it is ControlEvent.Press && (it.control.id.startsWith("pad-") || it.control.id == DeviceProfile.BANK) }) {
                activeBank = profile.bank
            }

            events.forEach { handle(it) }
        }

        is MidiConnector.Event.PortGone -> {
            profiles.remove(evt.port)?.let { profile ->
                handle(ControlEvent.AllUp(source = profile.source, reason = ControlEvent.Reason.Disconnected, tsMs = 0.0))
            }

            Unit
        }
    }

    /** Exhaustive by design: a new control event fails the build until this page decides what it does. */
    private fun handle(evt: ControlEvent) {
        when (evt) {
            is ControlEvent.NoteDown -> {
                logEvent(evt.tsMs, "ON  ${noteLabel(evt.note)} vel ${velocityLabel(evt.velocity)} ch ${evt.channel}")
                play(voicing.down(evt))
            }

            is ControlEvent.NoteUp -> {
                logEvent(evt.tsMs, "OFF ${noteLabel(evt.note)} ch ${evt.channel}")
                play(voicing.up(evt))
            }

            is ControlEvent.AllUp -> {
                // A MIDI panic releases every source's notes, as it always did; a port gone or keys unfocused only their own
                val outs = when (evt.reason) {
                    ControlEvent.Reason.Panic -> voicing.panic()
                    ControlEvent.Reason.Disconnected, ControlEvent.Reason.FocusLost -> voicing.allUp(evt)
                }

                // The source's controls held light no more: their releases may never come (a device unplugged, the keys
                // unfocused); a panic puts out every light
                when (evt.reason) {
                    ControlEvent.Reason.Panic -> {
                        pressed = emptyMap()
                        pressedTints = emptyMap()
                    }

                    ControlEvent.Reason.Disconnected, ControlEvent.Reason.FocusLost -> {
                        pressed = pressed.filterKeys { (source, _) -> source != evt.source }

                        // The tints are the devices' pads only
                        if (!onLaptopSide(evt.source)) {
                            pressedTints = emptyMap()
                        }
                    }
                }

                if (outs.isNotEmpty()) {
                    logEvent(evt.tsMs, "ALL UP (${evt.reason}): ${outs.size} voice(s) released")
                }

                play(outs)
            }

            is ControlEvent.Press -> {
                logEvent(evt.tsMs, "PRESS ${evt.control.id} vel ${velocityLabel(evt.velocity)}")
                pressed = pressed + ((evt.source to evt.control.id) to evt.velocity)
                padTint(evt)?.let { pressedTints = pressedTints + (evt.control.id to it) }
                routing.functionOf(source = evt.source, control = evt.control)?.let { onPressed(it, evt) }
            }

            is ControlEvent.Release -> {
                pressed = pressed - (evt.source to evt.control.id)

                // The tints are the devices' pads only: a laptop pad's release leaves a KeyLab pad's colour alone
                if (!onLaptopSide(evt.source)) {
                    pressedTints = pressedTints - evt.control.id
                }
                routing.functionOf(source = evt.source, control = evt.control)?.let { onReleased(it, evt) }
            }

            is ControlEvent.Move -> {
                // The bend is drawn 0..1 like every other control: -1..1 shifted into it. A moved value is read by the
                // next note once notes carry it (KLANG-00160): drawn for now
                val drawn = if (evt.control == ControlId.PitchBend) (evt.value + 1) / 2 else evt.value

                moveControl(evt.control.id, drawn)
            }

            is ControlEvent.Nudge -> {
                logEvent(evt.tsMs, "TURN ${evt.control.id} ${evt.steps}")
                // Drawn as the raw byte was, a direction around the middle
                moveControl(evt.control.id, (64 + evt.steps) / 127.0)
                routing.functionOf(source = evt.source, control = evt.control)?.let { onTurned(it, evt) }
            }
        }

        held = voicing.held
    }

    /** What a routed control does when it goes down. */
    private fun onPressed(function: PlayFunction, evt: ControlEvent.Press) {
        when (function) {
            is PlayFunction.HitPad -> ctrl.startEvents(
                liveId = ctrl.nextLiveId(),
                events = padEvents(pad = function.pad, velocity = evt.velocity),
                gateDurSec = PAD_GATE_SEC,
            )

            PlayFunction.Sustain -> play(voicing.sustain(source = evt.source, on = true))

            PlayFunction.Panic -> {
                logEvent(evt.tsMs, "PANIC")
                play(voicing.panic())
            }

            is PlayFunction.BrowseInstrument -> browseSound(function.direction)

            // Read when it moves, not pressed; nothing yet for Play and Stop (the light only)
            PlayFunction.Value, PlayFunction.PlayStop -> Unit
        }
    }

    /** What a routed control does when it comes up: only the sustain cares (a pad's hit is a one-shot). */
    private fun onReleased(function: PlayFunction, evt: ControlEvent.Release) {
        when (function) {
            PlayFunction.Sustain -> play(voicing.sustain(source = evt.source, on = false))

            is PlayFunction.HitPad, PlayFunction.Panic, is PlayFunction.BrowseInstrument, PlayFunction.Value,
            PlayFunction.PlayStop,
            -> Unit
        }
    }

    /** What a routed endless control does when it turns: only the browsing, by its steps. */
    private fun onTurned(function: PlayFunction, evt: ControlEvent.Nudge) {
        when (function) {
            is PlayFunction.BrowseInstrument -> browseSound(function.direction * evt.steps)

            is PlayFunction.HitPad, PlayFunction.Sustain, PlayFunction.Panic, PlayFunction.Value, PlayFunction.PlayStop -> Unit
        }
    }

    /** The playback does what the voicing says. */
    private fun play(outs: List<NoteVoicing.Out>) {
        for (out in outs) {
            when (out) {
                is NoteVoicing.Out.Start ->
                    ctrl.startEvents(liveId = out.liveId, events = eventsFor(out.key.note, out.velocity), gateDurSec = null)

                is NoteVoicing.Out.Stop -> ctrl.stopVoice(out.liveId)
            }
        }
    }

    /**
     * The built-in sound [steps] away from the selected one, round the list (the instrument loadout comes later). The
     * applied Ignitor goes, so the sound browsed is the one heard; the Ignitor's code stays in its editor for the
     * next Apply.
     */
    private fun browseSound(steps: Int) {
        val all = SOUND_GROUPS.flatMap { it.second }
        val at = all.indexOf(selectedSound).coerceAtLeast(0)

        appliedIgnitor = null
        selectedSound = all[((at + steps) % all.size + all.size) % all.size]
    }

    /** Moves an on-screen control to [value] (`0..1`). */
    private fun moveControl(controlId: String, value: Double) {
        controlValues = controlValues + (controlId to value)
    }

    /** The laptop's drawing lights the laptop's keys and what is clicked on it; the device's drawing the rest. */
    private fun onLaptopSide(source: SourceId): Boolean = source == LaptopKeys.source || source == LAPTOP_SCREEN

    /** The notes held on the laptop's keys, or on the devices' keybeds: each drawing lights its own. */
    private fun heldNotesOf(laptopKeys: Boolean): Set<Int> =
        held.keys.filter { onLaptopSide(it.source) == laptopKeys }.map { it.note }.toSet()

    /** The controls held down on the laptop, or on the devices, by id: each drawing lights its own. */
    private fun pressedOf(laptopKeys: Boolean): Map<String, Double> =
        pressed.filterKeys { (source, _) -> onLaptopSide(source) == laptopKeys }.mapKeys { it.key.second }

    /** Every pad outlined in the active bank's colour, so the bank is readable at rest. */
    private fun padOutlines(): Map<String, String> {
        val color = if (activeBank == "A") BANK_A_COLOR else BANK_B_COLOR

        return (1..8).associate { "pad-$it" to color }
    }

    /** A device's pad struck lights in its bank's colour, the bank its note said; null for any other control. */
    private fun padTint(evt: ControlEvent.Press): String? = when {
        onLaptopSide(evt.source) || !evt.control.id.startsWith("pad-") -> null
        activeBank == "A" -> BANK_A_COLOR
        else -> BANK_B_COLOR
    }

    /**
     * Records a controller movement. This is how a device's knob/fader map gets discovered: twist
     * something and watch which row moves. Keyed by label, so each control is one row however many
     * messages it sends.
     *
     * It has to be empirical: a KeyLab's encoders send whatever its MIDI Control Center preset
     * says, and the manual publishes no defaults for them, so no chart can tell us.
     */
    private fun observe(message: MidiMessage) {
        when (message.kind) {
            // Notes are the keys' and the pads': the event log shows them
            0x80, 0x90 -> Unit
            0xB0 -> observeControl("CC ${message.data1}", message.data2, message.channel)
            else -> observeControl(otherLabel(message.kind, message.data1), message.data2, message.channel)
        }
    }

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

    private fun noteLabel(note: Int): String =
        "${Midi.midiToNoteName(note.toDouble())} ($note)"

    /** A velocity 0..1 as the MIDI number it came from, 0..127. */
    private fun velocityLabel(velocity: Double): String = (velocity * 127).roundToInt().toString()

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
                onClick { ctrl.start() }

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
                heldNotes = heldNotesOf(laptopKeys = false),
                activeControls = controlValues + pressedOf(laptopKeys = false),
                tints = pressedTints,
                outlines = padOutlines(),
                noteOffset = keyBedOffset,
                screen = DEVICE_SCREEN,
                onInput = { events -> events.forEach { handle(it) } },
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
        val (octave, velocity) = laptopState

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
                        heldNotes = heldNotesOf(laptopKeys = true),
                        activeControls = pressedOf(laptopKeys = true),
                        noteOffset = octave * 12,
                        keyLabels = LaptopKeys.keyLabels(octave = octave, label = ::letterOf),
                        screen = LAPTOP_SCREEN,
                        onInput = { events -> events.forEach { handle(it) } },
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
        laptop.focusLost(tsMs = window.performance.now()).forEach { handle(it) }
    }

    private fun FlowContent.layoutButton(layout: KeyLayout?, title: String) {
        ui.given(keyLayout == layout) { primary }.button {
            key = "layout-${layout?.name ?: "auto"}"
            onClick { MidiSettings.chooseLayout(layout) }
            +title
        }
    }

    /** A key on the laptop's drawing: through [laptop]; a key that is the laptop's does not reach the browser too. */
    private fun laptopKey(e: KeyboardEvent, down: Boolean) {
        val result = when (down) {
            true -> laptop.keyDown(
                code = e.code,
                repeat = e.repeat,
                modified = e.ctrlKey || e.altKey || e.metaKey,
                tsMs = e.timeStamp.toDouble(),
                shift = e.shiftKey,
            )

            false -> laptop.keyUp(code = e.code, tsMs = e.timeStamp.toDouble())
        }

        if (result.handled) {
            e.preventDefault()

            // "Automatic" learns each key's letter as it is first pressed, where the browser does not tell the layout; not
            // with Shift, which prints another sign (Shift and the comma is "<" on an English board)
            if (down && !e.shiftKey && e.key.length == 1 && e.code !in learnedLetters) {
                learnedLetters = learnedLetters + (e.code to e.key.uppercase())
            }
        }

        result.events.forEach { handle(it) }
        laptopState = laptop.octave to laptop.velocity
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

            if (observed.isEmpty()) {
                noui.content {
                    +"Move a knob, fader, wheel or pad: every controller it sends shows up here "
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
                        hint = "A pattern mapper: reverb(wet = 0.2), or a function, x => x.reverb(wet = 0.2). Blank = none.",
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

            if (held.isEmpty()) {
                ui.header H3 {
                    css { fontWeight = FontWeight.normal }
                    +"Play something on your keyboard ..."
                }
            } else {
                ui.labels {
                    held.values.sortedBy { it.key.note }.forEach { note ->
                        ui.big.label {
                            key = "note-${note.key.source.id}-${note.key.channel}-${note.key.note}"
                            css { fontSize = 24.px }
                            +noteLabel(note.key.note)
                            noui.detail { +"vel ${velocityLabel(note.velocity)}" }
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
