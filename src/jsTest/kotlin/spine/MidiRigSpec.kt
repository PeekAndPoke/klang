/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.midi.ControlEvent
import io.peekandpoke.klang.midi.ControlId
import io.peekandpoke.klang.midi.LaptopKeys
import io.peekandpoke.klang.midi.SourceId

/**
 * The MIDI rig (KLANG-00402, step 4): what it lights, by source, as the drawings show it. A rig of the spec's own, never
 * started: no ports asked for, no engine, the voices it would start dropped.
 */
class MidiRigSpec : StringSpec({

    val keyLab = SourceId("keylab")
    val pad = ControlId("pad-3")

    fun press(source: SourceId, control: ControlId = pad) =
        ControlEvent.Press(source = source, control = control, velocity = 0.8, tsMs = 0.0)

    fun allUp(source: SourceId, reason: ControlEvent.Reason) =
        ControlEvent.AllUp(source = source, reason = reason, tsMs = 0.0)

    "a device's pad lights in its bank's colour on the device's drawing; a pad on the laptop's side lights untinted on its own" {
        val rig = MidiRig()

        rig.handle(press(keyLab))
        rig.handle(press(MidiRig.LAPTOP_SCREEN, ControlId("pad-1")))

        val state = rig.state()

        withClue("the device's pad, tinted in its bank's colour, on the device's drawing") {
            state.pressed(laptopSide = false) shouldBe mapOf("pad-3" to 0.8)
            state.pressedTints shouldBe mapOf("pad-3" to state.padOutlines().getValue("pad-3"))
        }
        withClue("the laptop's pad on the laptop's drawing only, with no tint") {
            state.pressed(laptopSide = true) shouldBe mapOf("pad-1" to 0.8)
        }

        rig.handle(ControlEvent.Release(source = keyLab, control = pad, tsMs = 0.0))

        withClue("released: out, its tint too") {
            rig.state().pressed(laptopSide = false) shouldBe emptyMap()
            rig.state().pressedTints shouldBe emptyMap()
        }
    }

    "a source that lets go puts out its own lights only; a panic puts out every light and every note" {
        val rig = MidiRig()

        rig.handle(press(keyLab))
        rig.handle(press(MidiRig.LAPTOP_SCREEN))
        rig.handle(ControlEvent.NoteDown(source = keyLab, channel = 0, note = 60, velocity = 0.5, tsMs = 0.0))

        rig.handle(allUp(MidiRig.LAPTOP_SCREEN, ControlEvent.Reason.FocusLost))

        withClue("the laptop's side let go: the device's pad, its colour and its note kept") {
            rig.state().pressed(laptopSide = true) shouldBe emptyMap()
            rig.state().pressed(laptopSide = false) shouldBe mapOf("pad-3" to 0.8)
            rig.state().pressedTints.keys shouldBe setOf("pad-3")
            rig.state().heldNotes(laptopSide = false) shouldBe setOf(60)
        }

        rig.handle(allUp(keyLab, ControlEvent.Reason.Panic))

        withClue("a panic: nothing lit, nothing held") {
            rig.state().pressed(laptopSide = false) shouldBe emptyMap()
            rig.state().pressedTints shouldBe emptyMap()
            rig.state().held shouldBe emptyMap()
        }
    }

    "a pad of the same name released on the laptop's side leaves the device's pad lit in its colour" {
        val rig = MidiRig()

        rig.handle(press(keyLab))
        rig.handle(press(MidiRig.LAPTOP_SCREEN))
        rig.handle(ControlEvent.Release(source = MidiRig.LAPTOP_SCREEN, control = pad, tsMs = 0.0))

        withClue("the device's pad-3 still lit and tinted") {
            rig.state().pressed(laptopSide = false) shouldBe mapOf("pad-3" to 0.8)
            rig.state().pressedTints.keys shouldBe setOf("pad-3")
        }
    }

    "the laptop's keys hold a note on the laptop's side, not the device's; its focus lost lets it go" {
        val rig = MidiRig()
        val note = LaptopKeys.noteAt(code = "KeyZ", octave = 0)!!

        withClue("the key is the laptop's: it does not reach the browser") {
            rig.laptopKeyDown(code = "KeyZ", repeat = false, modified = false, shift = false, tsMs = 0.0) shouldBe true
        }
        rig.state().heldNotes(laptopSide = true) shouldBe setOf(note)
        rig.state().heldNotes(laptopSide = false) shouldBe emptySet()

        rig.laptopFocusLost(tsMs = 0.0)

        withClue("the focus gone: let go, though its key never came up") {
            rig.state().held shouldBe emptyMap()
        }
    }

    "the laptop's octave shows as it changes" {
        val rig = MidiRig()
        val before = rig.state().laptopOctave

        rig.laptopKeyDown(code = "ArrowRight", repeat = false, modified = false, shift = false, tsMs = 0.0)

        rig.state().laptopOctave shouldBe before + 1
    }
})
