/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPatternEvent
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.common.logging.LogArea
import io.peekandpoke.klang.common.logging.Logger
import io.peekandpoke.klang.midi.ControlEvent
import io.peekandpoke.klang.midi.ControlId
import io.peekandpoke.klang.midi.DeviceProfile
import io.peekandpoke.klang.midi.LaptopKeys
import io.peekandpoke.klang.midi.MidiConnector
import io.peekandpoke.klang.midi.MidiLoadout
import io.peekandpoke.klang.midi.MidiMessage
import io.peekandpoke.klang.midi.MidiPlayCode
import io.peekandpoke.klang.midi.NoteVoicing
import io.peekandpoke.klang.midi.PlayFunction
import io.peekandpoke.klang.midi.Routing
import io.peekandpoke.klang.midi.SourceId
import io.peekandpoke.klang.script.runtime.FunctionValue
import io.peekandpoke.klang.script.runtime.convertFunctionToKotlin
import io.peekandpoke.klang.spine.storage.MidiLoadoutStorage
import io.peekandpoke.klang.spine.storage.MidiPlayCodeStorage
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.sound
import io.peekandpoke.klang.sprudel.lang.velocity
import io.peekandpoke.klang.tones.midi.Midi
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.browser.window
import kotlin.js.Promise
import kotlin.math.roundToInt

private val log = Logger(area = LogArea.Midi, source = "MidiRig")

/**
 * The MIDI rig (KLANG-00402, step 4, section 7): what plays live, for the app's life, not a page's. It holds the
 * ports ([MidiConnector]), a [DeviceProfile] per port, the laptop's keys ([LaptopKeys]), what each control does
 * ([Routing]), the notes held ([NoteVoicing]) and the realtime playback ([RealtimePlaybackCtrl]), and it owns **the
 * translation**: what a note sounds like ([eventsFor], the applied instrument) and what a pad hits ([padEvents]).
 * A page or a tool hands it input ([handle], [laptopKeyDown]) and draws what it publishes ([state]); leaving a page
 * releases nothing, so a chord held rings on while one goes elsewhere.
 *
 * Live play is no one's stage: the rig plays along with a song and stops none (KLANG-00406, the lanes); only a panic
 * stops it. The app has one, [Playbacks.live]; the specs make their own, and start one only to read a reload.
 */
class MidiRig(private val engine: PlaybackEngine) {

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
        /** Whether the devices were asked for ([connectDevices]): before, a device plays nothing. */
        val devicesAsked: Boolean = false,
        /**
         * The user's text (KLANG-00402, D7): the play code applied last, kept also when it failed, to be corrected; a step
         * of the session goes into it only where it held no changes of the user's. The stored one is what the user applied.
         */
        val playCode: String = "",
        /** The play code that plays: the last one applied that worked; a choice or a step starts from it. */
        val playing: String = "",
        /**
         * What a key plays, the `.sound(...)` of the play code's function ([MidiPlayCode.soundOf]); null where it names none
         * the rig can read. Notes sounding keep the sound they were started with: the voice is built at note-on.
         */
        val sound: MidiPlayCode.Sound? = null,
        /** The play code's own instruments: its top-level `let`s whose value is an Ignitor, in order. */
        val instruments: List<String> = emptyList(),
        /** Why the play code, or a key through it, failed; null while it plays. */
        val playCodeError: String? = null,
        /** The instruments the instrument keys step through ([MidiLoadout]); empty, they step through all. */
        val loadout: MidiLoadout = MidiLoadout(),
    ) {
        /** Every instrument there is to choose: the play code's own, then the built-ins. */
        fun allSounds(): List<MidiPlayCode.Sound> =
            instruments.map { MidiPlayCode.Sound.Own(it) } +
                SOUND_GROUPS.flatMap { (_, names) -> names.map { MidiPlayCode.Sound.BuiltIn(it) } }

        /** What the instrument keys step through: the loadout's instruments that still are, else all of them. */
        fun stepping(): List<MidiPlayCode.Sound> = loadout.stepsThrough(allSounds())

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

        /**
         * Whether a press on a drawing plays (a key, a pad): a gesture that may start the Motor (D4); a press on its
         * panic or another button starts nothing.
         */
        fun plays(events: List<ControlEvent>): Boolean = events.any { evt ->
            evt is ControlEvent.NoteDown || (evt is ControlEvent.Press && evt.control.id.startsWith("pad-"))
        }

        /** The laptop's drawing lights the laptop's keys and what is clicked on it; the device's drawing the rest. */
        fun onLaptopSide(source: SourceId): Boolean = source == LaptopKeys.source || source == LAPTOP_SCREEN

        /** A note's name with its number, as the log and the pressed keys show it: "C4 (60)". */
        fun noteLabel(note: Int): String = "${Midi.midiToNoteName(note.toDouble())} ($note)"

        /** A velocity 0..1 as the MIDI number it came from, 0..127. */
        fun velocityLabel(velocity: Double): String = (velocity * 127).roundToInt().toString()
    }

    private val ctrl = RealtimePlaybackCtrl(playbackName = "midi-playground", engine = engine)

    private val midi = MidiConnector()

    /** The held notes of every source: retrigger, sustain, one source letting go (KLANG-00402, section 5). */
    private val voicing = NoteVoicing(nextLiveId = ctrl::nextLiveId)

    /** What each control does: the v1 defaults (KLANG-00402, section 4). */
    private val routing = Routing.defaults

    /** One profile per port, made on its first message: its bytes to [ControlEvent]s. */
    private val profiles = mutableMapOf<String, DeviceProfile>()

    /** The laptop's keys as a source; they play while a drawing of them has the focus. */
    private val laptop = LaptopKeys()

    /** The play code's function from the key's one-note pattern to what sounds; null before one applied. */
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
     * Brings the rig up, once for the app's life: the stored play code played (or the app's own), its playback started
     * as soon as the Motor runs (started by anything: no gesture is needed then), and the kit loads once the playback is up, so a pad's
     * first hit sounds (D2). Asked by what first shows the rig, and at the app's start where MIDI was granted before
     * ([connectIfGranted]); later calls do nothing. The devices are asked for apart ([connectDevices]), so a plain
     * visit asks the user nothing.
     */
    fun start() {
        if (started) {
            return
        }

        started = true

        // The play code the user applied last plays again (one stored code, the maintainer, 2026-11-02: a step is for
        // the session); one that fails now shows with its error, never as an empty editor. Nothing is saved here: the
        // play code a first visit starts with stays the app's until the user applies one
        run(code = MidiPlayCodeStorage.load() ?: MidiPlayCode.DEFAULT, byUser = true)

        _state { it.copy(loadout = MidiLoadoutStorage.load()) }

        // The Motor running, whoever started it: the keys play along at once
        engine.status.subscribeToStream { status ->
            if (status == Player.Status.READY) {
                ctrl.start()
            }
        }

        ctrl.state.map { it.isReady }.distinct().subscribeToStream { ready ->
            if (ready) {
                ctrl.preload(requests = kitRequests())
                // The notes still held while the Motor came up, by their keys or a sustain, sound now (D4, the maintainer,
                // 2026-11-02: "Only held notes wait"): before, the voicing held them and the playback took none, so
                // these are exactly the ones not started; one let go before is gone, a pad's hit never waits
                voicing.held.values.forEach { held ->
                    ctrl.startEvents(liveId = held.liveId, events = eventsFor(held.key.note, held.velocity), gateDurSec = null)
                }
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
                // A browser that does not know the name (Firefox, Safari): the user asks; expected, so only a note
                log.info("The MIDI permission is not readable", e)
            }
        } catch (e: Throwable) {
            log.info("The MIDI permission is not readable", e)
        }
    }

    /**
     * Starts the engine and the realtime playback. Call it from a user gesture (a click, a key): browsers start an
     * AudioContext only from one. A laptop key or a press on a drawing that plays is one, so the first of them starts the
     * Motor, and its note sounds once it is up while its key or a sustain still holds it (D4); a device's key is none,
     * and before the Motor runs the tool shows its start key.
     */
    fun startEngine() {
        ctrl.start()
    }

    /**
     * Whether a laptop key at [code] plays (a note, a pad): such a key is a gesture that may start the Motor (D4); the
     * panic, the octave and the velocity keys start nothing.
     */
    fun laptopKeyPlays(code: String): Boolean = laptop.noteOf(code) != null || code in LaptopKeys.PADS

    //  THE TRANSLATION: voice shape, sound and params live here  //////////////////////////////

    /**
     * A key becomes a one-note sprudel pattern: the note, its velocity, the built-in [MidiPlayCode.FALLBACK_SOUND]. The
     * play code's function then makes it what sounds, its `.sound(...)` the instrument, and whatever starts at cycle 0
     * sounds now, under the key's liveId. Later onsets (`off`, `fast`) are dropped: a realtime voice has no timeline to
     * land on.
     *
     * Key velocity goes through sprudel's own `velocity`, which folds into the wire's one level word (signal-flow plan
     * section 6).
     */
    private fun eventsFor(note: Int, velocity: Double): List<KlangPatternEvent> {
        val base = note(note.toString())
            .velocity(velocity)
            .sound(MidiPlayCode.FALLBACK_SOUND)

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

    /** Runs the play code's function; a failure shows under its editor and the note plays as the key gave it. */
    private fun mapPattern(mapper: (Any?) -> Any?, base: SprudelPattern): SprudelPattern = try {
        mapper(base) as? SprudelPattern ?: error("The play code's function must return a pattern")
    } catch (e: Throwable) {
        _state { it.copy(playCodeError = e.message ?: e.toString()) }
        base
    }

    //  THE PLAY CODE: one KlangScript input, its instruments and what a key plays (D7)  ///////

    /**
     * Applies [code], the user's text, and keeps it for the next visit: the one play code stored. Its last expression is
     * the function a key goes through; its top-level `let`s holding an Ignitor are its instruments. A play code that
     * fails keeps the one before playing, so a typo never silences the keyboard mid-play.
     */
    fun apply(code: String) {
        MidiPlayCodeStorage.save(code)
        run(code = code, byUser = true)
    }

    /**
     * Plays [code]: the user's text ([byUser]), or a choice or a step from outside the editor, which changes what plays
     * for this session only (nothing saved: every open tab's rig hears a step) and leaves the user's text alone where it
     * differs from what played (the maintainer, 2026-11-02: "Never touch my edits").
     */
    private fun run(code: String, byUser: Boolean) {
        // The user's text holds changes of theirs: it differs from what plays
        val edited = _state().playCode != _state().playing

        val script = engine.scriptEngine()
        script.execute("""import * from "stdlib"""")
        script.execute("""import * from "sprudel"""")

        try {
            val result = script.execute(code + "\n")

            appliedMapper = (result as? FunctionValue)?.convertFunctionToKotlin(arity = 1)
                ?: error("The play code ends in a function, e.g. x => x.sound(warmSaw), got ${result.toDisplayString()}")

            val instruments = MidiPlayCode.namesOf(code).filter { name ->
                runCatching { script.getVariable(name).value is IgnitorDsl }.getOrDefault(false)
            }

            // The text follows what plays where it is the user's, or held no changes of theirs
            val text = if (byUser || !edited) code else _state().playCode

            _state {
                it.copy(
                    playCode = text,
                    playing = code,
                    sound = MidiPlayCode.soundOf(code),
                    instruments = instruments,
                    // A step leaves the error of the user's text that failed where it is: that text stays to be corrected
                    playCodeError = if (byUser || !edited) null else it.playCodeError,
                )
            }
        } catch (e: Throwable) {
            _state { state -> state.copy(playCode = if (byUser) code else state.playCode, playCodeError = e.message ?: e.toString()) }
        }
    }

    /**
     * Plays [sound] from the next key on: the play code that plays, or [code], the user's text (the page gives its
     * editor's), with it in the `.sound(...)` a key plays, applied. Where that names no instrument the rig can change, it
     * says so.
     */
    fun choose(sound: MidiPlayCode.Sound, code: String? = null) {
        val changed = MidiPlayCode.withSound(code ?: _state().playing, sound)

        // Nothing plays yet to step from (a stored play code that fails now): its own error stays
        if (changed == null && code == null && _state().playing.isEmpty()) {
            return
        }

        if (changed == null) {
            _state {
                it.copy(
                    playCodeError = "To choose an instrument, the play code must read and name its sound in its function: " +
                        "x => x.sound(warmSaw)",
                )
            }

            return
        }

        // The page's choice is the user's own Apply; a step plays for this session
        if (code != null) {
            apply(changed)
        } else {
            run(code = changed, byUser = false)
        }
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
            // A one-shot: while the playback is not up it is dropped, never kept to sound later
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
                // While the playback is not up, a start does nothing and the voicing keeps the note, to start once it is
                is NoteVoicing.Out.Start ->
                    ctrl.startEvents(liveId = out.liveId, events = eventsFor(out.key.note, out.velocity), gateDurSec = null)

                is NoteVoicing.Out.Stop -> ctrl.stopVoice(out.liveId)
            }
        }
    }

    /** The instrument [steps] away from the one playing, chosen: through the loadout, or through all where it is empty. */
    private fun browseSound(steps: Int) {
        _state().loadout.next(from = _state().sound, steps = steps, available = _state().allSounds())?.let { choose(it) }
    }

    /**
     * [sound] into the loadout (a drop on the MIDI page) on [slot], as [MidiLoadout.place] puts it (one already in the
     * loadout swaps); kept for the next visit.
     */
    fun equip(sound: MidiPlayCode.Sound, slot: Int) {
        changeLoadout { loadout -> loadout.place(slot, sound) }
    }

    /** Slot [slot] out of the loadout; kept for the next visit. */
    fun unequip(slot: Int) {
        changeLoadout { loadout -> loadout.remove(slot) }
    }

    private fun changeLoadout(change: (MidiLoadout) -> MidiLoadout) {
        val next = change(_state().loadout)

        MidiLoadoutStorage.save(next)
        _state { it.copy(loadout = next) }
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
