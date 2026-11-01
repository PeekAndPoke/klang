/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Bytes to [ControlEvent]s (KLANG-00402, section 3): the generic profile, and the KeyLab's by its drawing. */
class DeviceProfileSpec : StringSpec({

    fun noteOn(channel: Int, note: Int, velocity: Int) =
        MidiMessage(port = "p", status = 0x90 or (channel - 1), data1 = note, data2 = velocity, tsMs = 1.0)

    fun noteOff(channel: Int, note: Int) =
        MidiMessage(port = "p", status = 0x80 or (channel - 1), data1 = note, data2 = 0, tsMs = 1.0)

    fun cc(number: Int, value: Int, channel: Int = 1) =
        MidiMessage(port = "p", status = 0xB0 or (channel - 1), data1 = number, data2 = value, tsMs = 1.0)

    fun bend(raw: Int) = MidiMessage(port = "p", status = 0xE0, data1 = raw and 0x7F, data2 = raw shr 7, tsMs = 1.0)

    "the generic profile: notes are notes, a note on at velocity 0 is a note off" {
        val p = DeviceProfile.forPort("Some Synth")

        p.source shouldBe SourceId("midi:Some Synth")
        p.translate(noteOn(channel = 2, note = 60, velocity = 127)) shouldBe
            listOf(ControlEvent.NoteDown(source = p.source, channel = 2, note = 60, velocity = 1.0, tsMs = 1.0))
        p.translate(noteOn(channel = 2, note = 60, velocity = 0)) shouldBe
            listOf(ControlEvent.NoteUp(source = p.source, channel = 2, note = 60, tsMs = 1.0))
        p.translate(noteOff(channel = 2, note = 60)) shouldBe
            listOf(ControlEvent.NoteUp(source = p.source, channel = 2, note = 60, tsMs = 1.0))
    }

    "the generic profile: CC 64 is the sustain, 120 and 123 the panic, any other CC moves cc-n" {
        val p = DeviceProfile.forPort("Some Synth")

        p.translate(cc(64, 127)).single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId.Sustain
        p.translate(cc(64, 64)).single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId.Sustain
        p.translate(cc(64, 63)).single().shouldBeInstanceOf<ControlEvent.Release>().control shouldBe ControlId.Sustain
        p.translate(cc(120, 0)).single().shouldBeInstanceOf<ControlEvent.AllUp>().reason shouldBe ControlEvent.Reason.Panic
        p.translate(cc(123, 0)).single().shouldBeInstanceOf<ControlEvent.AllUp>().reason shouldBe ControlEvent.Reason.Panic

        val move = p.translate(cc(7, 127)).single().shouldBeInstanceOf<ControlEvent.Move>()

        move.control shouldBe ControlId("cc-7")
        move.value shouldBe 1.0
    }

    "the bend: 14 bits, centred, -1 to 1" {
        val p = DeviceProfile.forPort("Some Synth")

        fun value(raw: Int) = p.translate(bend(raw)).single().shouldBeInstanceOf<ControlEvent.Move>().value

        value(8192) shouldBe 0.0
        value(0) shouldBe -1.0
        value(16383) shouldBe (1.0 plusOrMinus 0.001)
        p.translate(bend(8192)).single().shouldBeInstanceOf<ControlEvent.Move>().control shouldBe ControlId.PitchBend
    }

    "the KeyLab: a pad note presses its pad and says the bank; the same note on the keybed's channel is a note" {
        val p = DeviceProfile.forPort("KeyLab Essential 49 mk3 MIDI")

        p.source shouldBe SourceId("keylab-49-mk3")
        // A guess before the first pad note: the power-on default
        p.bank shouldBe "A"

        val press = p.translate(noteOn(channel = 11, note = PAD_NOTES_BANK_A[0], velocity = 127)).single()

        press shouldBe ControlEvent.Press(source = p.source, control = ControlId("pad-1"), velocity = 1.0, tsMs = 1.0)
        p.bank shouldBe "A"

        p.translate(noteOn(channel = 11, note = PAD_NOTES_BANK_B[2], velocity = 64)).single()
            .shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId("pad-3")
        p.bank shouldBe "B"

        // The Bank button turns it on its press, not on its release; a pad note corrects it
        p.translate(cc(118, 127))
        p.bank shouldBe "A"
        p.translate(cc(118, 0))
        p.bank shouldBe "A"
        p.translate(noteOn(channel = 11, note = PAD_NOTES_BANK_B[2], velocity = 64))
        p.bank shouldBe "B"

        p.translate(noteOff(channel = 11, note = PAD_NOTES_BANK_B[2])) shouldBe
            listOf(ControlEvent.Release(source = p.source, control = ControlId("pad-3"), tsMs = 1.0))

        p.translate(noteOn(channel = 1, note = PAD_NOTES_BANK_A[0], velocity = 127)).single()
            .shouldBeInstanceOf<ControlEvent.NoteDown>().note shouldBe PAD_NOTES_BANK_A[0]
    }

    "the KeyLab: a knob and a fader move, a button presses and releases, the encoder nudges around 64" {
        val p = DeviceProfile.forPort("KeyLab Essential 49 mk3 MIDI")

        p.translate(cc(96, 127)).single().shouldBeInstanceOf<ControlEvent.Move>().control shouldBe ControlId("knob-1")
        p.translate(cc(113, 0)).single().shouldBeInstanceOf<ControlEvent.Move>().control shouldBe ControlId("fader-9")
        p.translate(cc(1, 64)).single().shouldBeInstanceOf<ControlEvent.Move>().control shouldBe ControlId.ModWheel

        p.translate(cc(21, 127)).single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId("play")
        p.translate(cc(21, 0)).single().shouldBeInstanceOf<ControlEvent.Release>().control shouldBe ControlId("play")
        // The Bank button, drawn as one, and the encoder's click, drawn as none: buttons both
        p.translate(cc(118, 127)).single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId("bank")
        p.translate(cc(117, 127)).single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId("main-encoder-press")

        p.translate(cc(116, 65)).single().shouldBeInstanceOf<ControlEvent.Nudge>().steps shouldBe 1
        p.translate(cc(116, 62)).single().shouldBeInstanceOf<ControlEvent.Nudge>().steps shouldBe -2
        p.translate(cc(116, 64)).shouldBeEmpty()
    }
})
