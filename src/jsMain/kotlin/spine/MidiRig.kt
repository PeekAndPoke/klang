/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.midi.ControlEvent
import io.peekandpoke.klang.midi.ControlId
import io.peekandpoke.klang.midi.DeviceProfile
import io.peekandpoke.klang.midi.LaptopKeys
import io.peekandpoke.klang.midi.MidiConnector
import io.peekandpoke.klang.midi.MidiMessage
import io.peekandpoke.klang.midi.NoteVoicing
import io.peekandpoke.klang.midi.PlayFunction
import io.peekandpoke.klang.midi.Routing
import io.peekandpoke.klang.midi.SourceId
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.runtime.FunctionValue
import io.peekandpoke.klang.script.runtime.convertFunctionToKotlin
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.sound
import io.peekandpoke.klang.sprudel.lang.velocity
import io.peekandpoke.klang.storage.MidiInstrument
import io.peekandpoke.klang.storage.MidiInstrumentStorage
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.browser.window
import kotlin.js.Promise
import kotlin.math.roundToInt

/**
 * The MIDI rig (KLANG-00402, step 4, section 7): what plays live, for the app's life, not a page's. It holds the
 * ports ([MidiConnector]), a [DeviceProfile] per port, the laptop's keys ([LaptopKeys]), what each control does
 * ([Routing]), the notes held ([NoteVoicing]) and the realtime playback ([RealtimePlaybackCtrl]), and it owns **the
 * translation**: what a note sounds like ([eventsFor], the applied instrument) and what a pad hits ([padEvents]).
 * A page or a tool hands it input ([handle], [laptopKeyDown]) and draws what it publishes ([state]); leaving a page
 * releases nothing, so a chord held rings on while one goes elsewhere.
 *
 * Live play is no one's stage: the rig plays along with a song and stops none (KLANG-00406, the lanes); only a panic
 * stops it. The app has one, [Playbacks.live]; the specs make their own and never [start] it.
 */
class MidiRig {

    /** One row per distinct controller seen, for discovering a device's map. */
    data class ObservedControl(val raw: Int, val channel: Int, val hits: Int)

    /** What the rig shows: what is held and pressed, the controls' positions, the laptop's octave, the log, the sound. */
    data class State(
        /** The voices sounding, as the voicing holds them, for the drawings and the pressed keys. */
        val held: Map<NoteVoicing.Key, NoteVoicing.Held> = emptyMap(),
        /** The momentary controls held down (pads, buttons) by source and id, each with its velocity. */
        val pressed: Map<Pair<SourceId, String>, Double> = emptyMap(),
        /** A device's pad held down lights in its bank's colour. */
        val pressedTints: Map<String, String> = emptyMap(),
        /** The laptop's octave and velocity (KLANG-00402, step 2). */
        val laptopOctave: Int = 0,
        val laptopVelocity: Int = LaptopKeys.DEFAULT_VELOCITY,
        /** The last events, newest first. */
        val eventLog: List<String> = emptyList(),
        /** Every controller seen, by label: the map of a device's knobs, found by turning them. */
        val observed: Map<String, ObservedControl> = emptyMap(),
        /** The live positions of the device's controls, `0..1`, by panel control id: the drawn faders follow them. */
        val controlValues: Map<String, Double> = emptyMap(),
        /**
         * Which pad bank is believed live. The device never announces it: the Bank button only says it was pressed. So
         * it starts as a guess ("A", the power-on default) and is kept honest two ways: the Bank button turns it, and
         * any pad note corrects it, because the note says which bank sent it.
         */
        val activeBank: String = "A",
        /**
         * The built-in sound the next note plays while no Ignitor is applied. Notes sounding keep the sound they were
         * started with: the voice is built at note-on.
         */
        val selectedSound: String = "supersaw",
        /** Whether the devices were asked for ([connectDevices]): before, a device plays nothing. */
        val devicesAsked: Boolean = false,
        /** The applied instrument's name while its Ignitor plays; null while the built-in [selectedSound] plays. */
        val instrument: String? = null,
        val ignitorError: String? = null,
        val mapperError: String? = null,
    ) {
        /** The notes held on the laptop's side, or on the devices' keybeds: each drawing lights its own. */
        fun heldNotes(laptopSide: Boolean): Set<Int> =
            held.keys.filter { onLaptopSide(it.source) == laptopSide }.map { it.note }.toSet()

        /** The controls held down on the laptop's side, or on the devices, by id: each drawing lights its own. */
        fun pressed(laptopSide: Boolean): Map<String, Double> =
            pressed.filterKeys { (source, _) -> onLaptopSide(source) == laptopSide }.mapKeys { it.key.second }

        /** Every pad outlined in the active bank's colour, so the bank is readable at rest. */
        fun padOutlines(): Map<String, String> {
            val color = if (activeBank == "A") BANK_A_COLOR else BANK_B_COLOR

            return (1..8).associate { "pad-$it" to color }
        }
    }

    companion object {
        /** The drawings as sources: what is clicked or touched on each (KLANG-00402, step 3). */
        val DEVICE_SCREEN = SourceId("screen:device")
        val LAPTOP_SCREEN = SourceId("screen:laptop")

        /** The panic key drawn by the MIDI tool, a source of its own. */
        private val PANIC_KEY = SourceId("screen:panic")

        /**
         * The playable built-ins, grouped for the picker. Names must match the backend's default ignitor registry
         * (`IgnitorDefaults`); aliases (`sin`, `sqr`, `tri`, `ks`, ...) are left out so the picker shows each sound once.
         */
        val SOUND_GROUPS: List<Pair<String, List<String>>> = listOf(
            "Waves" to listOf("sine", "sawtooth", "square", "triangle", "ramp", "pulze"),
            "Raw (aliased)" to listOf("zaw", "zamp"),
            "Unison" to listOf("supersaw", "superramp", "supersquare", "supertri", "supersine", "superpulse"),
            "Plucked" to listOf("pluck", "superpluck"),
            "Noise" to listOf("whitenoise", "pinknoise", "brownnoise", "dust", "crackle", "perlinnoise", "berlinnoise"),
        )

        /** The instrument played before any is applied. */
        val DEFAULT_INSTRUMENT = MidiInstrument(
            name = "Warm saw",
            ignitorCode = "Ign.saw()\n  .plus(Ign.sine().detune(12).mul(0.3))\n  .lowpass(1800)\n  .adsr(0.01, 0.3, 0.6, 0.4)",
            mapperCode = "reverb(wet = 0.2)",
        )

        /** Pad bank A / B colours: distinct hues, since the bank is otherwise invisible. */
        private const val BANK_A_COLOR = "#4a9eff"
        private const val BANK_B_COLOR = "#f0a33c"

        private const val EVENT_LOG_SIZE = 16

        /** The tempo a mapper sees: 30 rpm, the song page's default. Only time-aware mappers notice. */
        private const val MAPPER_CPS = 0.5

        /** What the pads hit, pad 1 to 8: bd sd hh oh / cp rim lt cr (KLANG-00402, D2). */
        private val KIT = listOf("bd", "sd", "hh", "oh", "cp", "rim", "lt", "cr")

        /** A pad's hit is a one-shot: its release does not cut it, the gate ends it. */
        private const val PAD_GATE_SEC = 1.0

        /** The laptop's drawing lights the laptop's keys and what is clicked on it; the device's drawing the rest. */
        fun onLaptopSide(source: SourceId): Boolean = source == LaptopKeys.source || source == LAPTOP_SCREEN

        /** A note's name with its number, as the log and the pressed keys show it: "C4 (60)". */
        fun noteLabel(note: Int): String = "${Midi.midiToNoteName(note.toDouble())} ($note)"

        /** A velocity 0..1 as the MIDI number it came from, 0..127. */
        fun velocityLabel(velocity: Double): String = (velocity * 127).roundToInt().toString()
    }

    private val ctrl = RealtimePlaybackCtrl(playbackName = "midi-playground")

    private val midi = MidiConnector()

    /** The held notes of every source: retrigger, sustain, one source letting go (KLANG-00402, section 5). */
    private val voicing = NoteVoicing(nextLiveId = ctrl::nextLiveId)

    /** What each control does: the v1 defaults (KLANG-00402, section 4). */
    private val routing = Routing.defaults

    /** One profile per port, made on its first message: its bytes to [ControlEvent]s. */
    private val profiles = mutableMapOf<String, DeviceProfile>()

    /** The laptop's keys as a source; they play while a drawing of them has the focus. */
    private val laptop = LaptopKeys()

    /** The applied ignitor; null plays the built-in [State.selectedSound]. */
    private var appliedIgnitor: IgnitorDsl? = null

    /** The name of the instrument whose Ignitor was applied last. */
    private var appliedName: String? = null

    /** The applied sprudel mapper (`reverb(wet = 0.2)`, or a function as `x => x.reverb(wet = 0.2)`); null for none. */
    private var appliedMapper: ((Any?) -> Any?)? = null

    private var started = false

    private val _state = StreamSource(State(laptopOctave = laptop.octave, laptopVelocity = laptop.velocity))

    /** What the rig shows, for the drawings, the log and the monitor. */
    val state: Stream<State> = _state.readonly

    /** The realtime playback: the engine's state, whether it takes voices. */
    val playback: Stream<RealtimePlaybackCtrl.State> = ctrl.state

    /** The MIDI ports: whether the browser gives MIDI, and the devices plugged in. */
    val devices: Stream<MidiConnector.State> = midi.state

    /**
     * Brings the rig up, once for the app's life: the saved instrument applied, and the kit loads as soon as the engine
     * is up, so a pad's first hit sounds (D2). Asked by what first shows the rig; later calls do nothing. The devices
     * are asked for apart ([connectDevices]), so a plain visit asks the user nothing.
     */
    fun start() {
        if (started) {
            return
        }

        started = true

        apply(MidiInstrumentStorage.loadDraft() ?: DEFAULT_INSTRUMENT)

        ctrl.state.map { it.isReady }.distinct().subscribeToStream { ready ->
            if (ready) {
                ctrl.preload(requests = kitRequests())
            }
        }
    }

    /**
     * Asks the browser for MIDI and hooks the devices, once for the app's life, the rig brought up with them; the browser
     * may ask the user then.
     */
    fun connectDevices() {
        if (_state().devicesAsked) {
            return
        }

        start()
        _state { it.copy(devicesAsked = true) }
        midi.start()
        midi.events.subscribeToStream { evt -> evt?.let { onMidi(it) } }
    }

    /**
     * Hooks the devices without a question: only where the user granted MIDI before (the browser's Permissions API
     * says so); else nothing, and the user asks for them ([connectDevices]).
     */
    fun connectIfGranted() {
        val permissions = window.navigator.asDynamic().permissions ?: return

        try {
            val query: Promise<dynamic> = permissions.query(js("({ name: 'midi' })")).unsafeCast<Promise<dynamic>>()

            query.then<Unit> { status ->
                if (status.state == "granted") {
                    connectDevices()
                }
            }.catch { e ->
                // A browser that does not know the name (Firefox, Safari): the user asks
                console.warn("[MidiRig] the MIDI permission is not readable", e)
            }
        } catch (e: Throwable) {
            console.warn("[MidiRig] the MIDI permission is not readable", e)
        }
    }

    /**
     * Starts the engine and the realtime playback. Call it from a user gesture (a click, a key): browsers start an
     * AudioContext only from one.
     */
    fun startEngine() {
        ctrl.start()
    }

    //  THE TRANSLATION: voice shape, sound and params live here  //////////////////////////////

    /**
     * A key becomes a one-note sprudel pattern: the note, its velocity, the applied ignitor (or the built-in sound). The
     * applied mapper then shapes it like any sprudel pattern, and whatever starts at cycle 0 sounds now, under the key's
     * liveId. Later onsets (a mapper with `off` or `fast`) are dropped: a realtime voice has no timeline to land on.
     *
     * Key velocity goes through sprudel's own `velocity`, which folds into the wire's one level word (signal-flow plan
     * section 6).
     */
    private fun eventsFor(note: Int, velocity: Double): List<KlangPatternEvent> {
        val base = note(note.toString())
            .velocity(velocity)
            .sound(appliedIgnitor ?: _state().selectedSound)

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
        _state { it.copy(mapperError = e.message ?: e.toString()) }
        base
    }

    //  THE INSTRUMENT: two KlangScript inputs, compiled on apply  /////////////////////////////

    /**
     * Applies [instrument] and keeps it as the draft for the next visit. An input that fails keeps its previous applied
     * version, so a typo never silences the keyboard mid-play.
     */
    fun apply(instrument: MidiInstrument) {
        MidiInstrumentStorage.saveDraft(instrument)

        val engine = Player.createEngine()
        engine.execute("""import * from "stdlib"""")
        engine.execute("""import * from "sprudel"""")

        var ignitorError: String? = null
        var mapperError: String? = null

        try {
            appliedIgnitor = compileIgnitor(engine, instrument.ignitorCode)
            // The name of what plays: an Ignitor that fails keeps the one before, and its name with it
            appliedName = instrument.name.trim().ifBlank { "Untitled" }
        } catch (e: Throwable) {
            ignitorError = e.message ?: e.toString()
        }

        try {
            appliedMapper = compileMapper(engine, instrument.mapperCode)
        } catch (e: Throwable) {
            mapperError = e.message ?: e.toString()
        }

        _state {
            it.copy(
                instrument = appliedName.takeIf { appliedIgnitor != null },
                ignitorError = ignitorError,
                mapperError = mapperError,
            )
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

    /** Plays the built-in [sound] from the next note on, while no Ignitor is applied. */
    fun selectSound(sound: String) {
        _state { it.copy(selectedSound = sound) }
    }

    //  INPUT  //////////////////////////////////////////////////////////////////////////////////

    /** A port's word: a message goes to the monitor and through the port's profile; a port gone lets its keys go. */
    private fun onMidi(evt: MidiConnector.Event): Unit = when (evt) {
        is MidiConnector.Event.Message -> {
            observe(evt.message)

            val profile = profiles.getOrPut(evt.message.port) { DeviceProfile.forPort(evt.message.port) }
            val events = profile.translate(evt.message)

            // The bank first, so a pad lights in the bank its own note said; and only from a pad or the Bank button, so
            // another port of the device (or another device) cannot put it back to its own guess
            if (events.any { it is ControlEvent.Press && (it.control.id.startsWith("pad-") || it.control.id == DeviceProfile.BANK) }) {
                _state { it.copy(activeBank = profile.bank) }
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

    /**
     * A control's event, from a port, the laptop's keys or a drawing: logged, lit, and played as the routing says.
     * Exhaustive by design: a new control event fails the build until the rig decides what it does.
     */
    fun handle(evt: ControlEvent) {
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
                    ControlEvent.Reason.Panic -> _state { it.copy(pressed = emptyMap(), pressedTints = emptyMap()) }

                    ControlEvent.Reason.Disconnected, ControlEvent.Reason.FocusLost -> _state { state ->
                        state.copy(
                            pressed = state.pressed.filterKeys { (source, _) -> source != evt.source },
                            // The tints are the devices' pads only
                            pressedTints = if (onLaptopSide(evt.source)) state.pressedTints else emptyMap(),
                        )
                    }
                }

                if (outs.isNotEmpty()) {
                    logEvent(evt.tsMs, "ALL UP (${evt.reason}): ${outs.size} voice(s) released")
                }

                play(outs)
            }

            is ControlEvent.Press -> {
                logEvent(evt.tsMs, "PRESS ${evt.control.id} vel ${velocityLabel(evt.velocity)}")
                _state { state ->
                    state.copy(
                        pressed = state.pressed + ((evt.source to evt.control.id) to evt.velocity),
                        pressedTints = padTint(state, evt)?.let { state.pressedTints + (evt.control.id to it) } ?: state.pressedTints,
                    )
                }
                routing.functionOf(source = evt.source, control = evt.control)?.let { onPressed(it, evt) }
            }

            is ControlEvent.Release -> {
                _state { state ->
                    state.copy(
                        pressed = state.pressed - (evt.source to evt.control.id),
                        // The tints are the devices' pads only: a laptop pad's release leaves a KeyLab pad's colour alone
                        pressedTints = if (onLaptopSide(evt.source)) state.pressedTints else state.pressedTints - evt.control.id,
                    )
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

        _state { it.copy(held = voicing.held) }
    }

    /** Every note of every source lets go and every light goes out, as a device's panic does. */
    fun panic(tsMs: Double) {
        handle(ControlEvent.AllUp(source = PANIC_KEY, reason = ControlEvent.Reason.Panic, tsMs = tsMs))
    }

    /**
     * A laptop key went down, from a drawing of the laptop's keys that has the focus: through [LaptopKeys]. True when the
     * key is the laptop's, so it does not reach the browser too.
     */
    fun laptopKeyDown(code: String, repeat: Boolean, modified: Boolean, shift: Boolean, tsMs: Double): Boolean =
        laptopResult(laptop.keyDown(code = code, repeat = repeat, modified = modified, tsMs = tsMs, shift = shift))

    /** A laptop key came up; true when it is the laptop's. */
    fun laptopKeyUp(code: String, tsMs: Double): Boolean = laptopResult(laptop.keyUp(code = code, tsMs = tsMs))

    /** The laptop keys let go of everything they hold: a key held when the focus goes never sends its up. */
    fun laptopFocusLost(tsMs: Double) {
        laptop.focusLost(tsMs = tsMs).forEach { handle(it) }
    }

    private fun laptopResult(result: LaptopKeys.Result): Boolean {
        result.events.forEach { handle(it) }
        _state { it.copy(laptopOctave = laptop.octave, laptopVelocity = laptop.velocity) }

        return result.handled
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
     * applied Ignitor goes, so the sound browsed is the one heard; the Ignitor's code stays in its editor for the next
     * Apply.
     */
    private fun browseSound(steps: Int) {
        val all = SOUND_GROUPS.flatMap { it.second }
        val at = all.indexOf(_state().selectedSound).coerceAtLeast(0)

        appliedIgnitor = null
        _state { it.copy(instrument = null, selectedSound = all[((at + steps) % all.size + all.size) % all.size]) }
    }

    /** Moves an on-screen control to [value] (`0..1`). */
    private fun moveControl(controlId: String, value: Double) {
        _state { it.copy(controlValues = it.controlValues + (controlId to value)) }
    }

    /** A device's pad struck lights in its bank's colour, the bank its note said; null for any other control. */
    private fun padTint(state: State, evt: ControlEvent.Press): String? = when {
        onLaptopSide(evt.source) || !evt.control.id.startsWith("pad-") -> null
        state.activeBank == "A" -> BANK_A_COLOR
        else -> BANK_B_COLOR
    }

    //  THE MONITOR AND THE LOG  ////////////////////////////////////////////////////////////////

    /**
     * Records a controller movement. This is how a device's knob and fader map gets discovered: twist something and
     * watch which row moves. Keyed by label, so each control is one row however many messages it sends.
     *
     * It has to be empirical: a KeyLab's encoders send whatever its MIDI Control Center preset says, and the manual
     * publishes no defaults for them, so no chart can tell us.
     */
    private fun observe(message: MidiMessage) {
        when (message.kind) {
            // Notes are the keys' and the pads': the event log shows them
            0x80, 0x90 -> Unit
            0xB0 -> observeControl(label = "CC ${message.data1}", raw = message.data2, channel = message.channel)
            else -> observeControl(label = otherLabel(message.kind, message.data1), raw = message.data2, channel = message.channel)
        }
    }

    private fun observeControl(label: String, raw: Int, channel: Int) {
        _state { state ->
            state.copy(
                observed = state.observed + (
                    label to ObservedControl(raw = raw, channel = channel, hits = (state.observed[label]?.hits ?: 0) + 1)
                    ),
            )
        }
    }

    private fun otherLabel(kind: Int, data1: Int): String = when (kind) {
        0xA0 -> "Poly aftertouch note $data1"
        0xC0 -> "Program change"
        0xD0 -> "Channel pressure"
        0xE0 -> "Pitch bend"
        else -> "Status 0x${kind.toString(16)} d1=$data1"
    }

    /** Clears the controller monitor. */
    fun clearObserved() {
        _state { it.copy(observed = emptyMap()) }
    }

    /** Clears the event log. */
    fun clearLog() {
        _state { it.copy(eventLog = emptyList()) }
    }

    private fun logEvent(tsMs: Double, entry: String) {
        _state { it.copy(eventLog = (listOf("${formatTs(tsMs)}  $entry") + it.eventLog).take(EVENT_LOG_SIZE)) }
    }

    /** DOMHighResTimeStamp ms with one decimal, e.g. "12345.7" */
    private fun formatTs(tsMs: Double): String = tsMs.asDynamic().toFixed(1).unsafeCast<String>()
}
