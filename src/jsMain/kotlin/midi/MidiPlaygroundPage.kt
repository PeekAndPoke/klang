/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.spine.MidiRig
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import io.peekandpoke.klang.ui.codemirror.KlangScriptEditorComp
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.WakeLockComponent
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
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
 * are the MIDI tool's too ([DeviceView], [LaptopKeysView]); the page keeps what only it shows: the play code's editor and
 * its instruments, the monitor and the log. See KLANG-00159 (realtime playback controller).
 */
class MidiPlaygroundPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////

    private val rig = Playbacks.live

    private val rigState by subscribingTo(rig.state)

    private val ctrlState by subscribingTo(rig.playback)

    private val midiState by subscribingTo(rig.devices)

    /** What the play code's editor holds: the rig's play code, and the user's changes until they apply them. */
    private var draft: String by value("")

    /** Bumped when the play code changes from outside the editor (an instrument chosen, a step), so it shows it. */
    private var editorGeneration: Int by value(0)

    /**
     * The play code that played last, as this page saw it: the editor holds no changes of the user's while its text
     * equals this.
     */
    private var shown: String = ""

    /**
     * The play code that plays, changed by an Apply that worked, a choice or a step (a KeyLab's encoder, on any page).
     * The editor follows it while it holds no changes of the user's; changes not applied yet are never touched from
     * outside (the maintainer, 2026-11-02: "Never touch my edits"), and a note says they do not play.
     */
    @Suppress("unused")
    private val playingShown by subscribingTo(rig.state.map { it.playing }.distinct()) { _ ->
        // The rig's current one, not the value delivered: Kraft hands the first one over a moment late, and by then it
        // may be stale (the page's mount has applied the kept play code meanwhile)
        val code = rig.state().playing

        if (draft == shown && draft != code) {
            draft = code
            editorGeneration += 1
        }

        shown = code
    }

    companion object {
        private val EDITOR_LIBS = listOf(stdlibLib, sprudelLib)
    }

    init {
        lifecycle {
            onMount {
                // Once for the app's life; the rig plays on when the page goes. The MIDI page asks for the devices
                rig.start()
                rig.connectDevices()

                // The play code as the rig has it, also one kept that failed: to be corrected here
                val state = rig.state()

                if (draft != state.playCode) {
                    draft = state.playCode
                    editorGeneration += 1
                }

                shown = state.playing
            }
        }
    }

    //  THE PLAY CODE: one KlangScript input, applied by the rig (D7)  /////////////////////////

    /** A new instrument, a `let` at the top of the editor's play code under a name it does not have yet. */
    private fun newInstrument() {
        val taken = MidiPlayCode.namesOf(draft)
        val name = generateSequence(1) { it + 1 }.map { "instrument$it" }.first { it !in taken }

        // Ended by ";": a play code that begins `(x) =>` would else read as a call of the Ignitor
        draft = "let $name = Ign.saw();\n\n$draft"
        editorGeneration += 1
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
        renderPlayCode()
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

    /**
     * The play code (D7, the maintainer's proposal: "should all be in the one input"): its editor on the left, with
     * Apply; on the right its instruments, a view of the code: yours (its `let`s holding an Ignitor) and the built-ins.
     * A click plays one: the `.sound(...)` a key plays names it in the editor's code, applied.
     */
    private fun FlowContent.renderPlayCode() {
        ui.segment {
            key = "play-code"

            ui.two.column.stackable.grid {
                noui.column {
                    ui.header H4 { +"Play code" }

                    noui.content {
                        css { padding = Padding(0.px, 0.px, 4.px, 0.px) }
                        +"Your instruments as lets at the top, then what a key plays: x => x.sound(warmSaw).reverb(wet = 0.2). "
                        +"A built-in is a name in quotes, x.sound(\"supersaw\"). After a let, write x =>, not (x) =>."
                    }

                    // Keyed by generation: a play code changed from outside remounts the editor with it
                    ui.segment {
                        key = "play-code-editor-$editorGeneration"
                        css { padding = Padding(0.px) }

                        KlangScriptEditorComp(
                            code = draft,
                            onCodeChanged = { draft = it },
                            availableLibraries = EDITOR_LIBS,
                            autoImportedLibraries = EDITOR_LIBS,
                        )
                    }

                    ui.primary.button {
                        onClick { rig.apply(draft) }
                        icon.play()
                        +"Apply"
                    }

                    // The user's changes do not play until applied; an applied text that fails does not play either
                    when {
                        draft != rigState.playCode && draft != rigState.playing ->
                            noui.small.text { +"Not applied yet: the keys play the play code before these changes." }

                        draft != rigState.playing ->
                            noui.small.text { +"This play code does not play: the keys play the one before it, or the plain built-in." }
                    }

                    rigState.playCodeError?.let { error ->
                        ui.negative.message { +error }
                    }
                }

                noui.column {
                    renderInstruments()
                }
            }
        }
    }

    /** The instruments to choose: the play code's own and a key for a new one, then the built-ins by group. */
    private fun FlowContent.renderInstruments() {
        ui.header H4 { +"Instruments" }

        noui.tiny.header { +"Yours" }

        ui.basic.segment {
            css { padding = Padding(0.px, 0.px, 8.px, 0.px) }

            if (rigState.instruments.isEmpty()) {
                noui.content { +"No let of the play code holds an Ignitor yet." }
            }

            rigState.instruments.forEach { name -> soundButton(sound = MidiPlayCode.Sound.Own(name), label = name) }

            ui.small.basic.button {
                onClick { newInstrument() }
                icon.plus()
                +"New instrument"
            }
        }

        noui.tiny.header { +"Built-in" }

        ui.two.column.grid {
            MidiRig.SOUND_GROUPS.forEach { (groupName, sounds) ->
                noui.column {
                    key = "sound-group-$groupName"
                    css { padding = Padding(4.px, 0.px) }

                    noui.tiny.header { +groupName }

                    sounds.forEach { name -> soundButton(sound = MidiPlayCode.Sound.BuiltIn(name), label = name) }
                }
            }
        }
    }

    /**
     * A key that plays [sound], lit while it plays: the user's own choice, so it goes into the editor's text, changes and
     * all, in the `.sound(...)` a key plays, and that text is applied.
     */
    private fun FlowContent.soundButton(sound: MidiPlayCode.Sound, label: String) {
        ui.small.given(sound == rigState.sound) { primary }.button {
            key = "sound-${sound.code}"
            onClick { chooseHere(sound) }
            +label
        }
    }

    private fun chooseHere(sound: MidiPlayCode.Sound) {
        val changed = MidiPlayCode.withSound(draft, sound)

        if (changed != null && changed != draft) {
            draft = changed
            editorGeneration += 1
        }

        // The rig says so where the text names no sound to change
        rig.choose(sound = sound, code = draft)
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
