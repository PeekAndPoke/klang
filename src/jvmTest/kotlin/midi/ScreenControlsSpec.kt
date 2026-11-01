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
import io.peekandpoke.klang.midi.ScreenControls.Grip

/** The drawn controls as a source (KLANG-00402, step 3): what is at a point, and what a pointer does there. */
class ScreenControlsSpec : StringSpec({

    val screen = SourceId("screen:test")

    /** A keybed of one octave and a third from C4, 170 wide: ten whites of 17. */
    val bed = KeyBed(fromNote = 60, toNote = 76, x = 0, y = 100, width = 170, height = 100)

    "the keybed: a black key on top where it lies, a white key below it and beside it, nothing outside" {
        val layout = KeyBedLayout(bed = bed, offset = 0)

        layout.whites.size shouldBe 10
        // C# lies across the line between C and D, at 17 - 30 % of a white
        layout.keyAt(x = 16.0, y = 110.0) shouldBe 61
        layout.keyAt(x = 16.0, y = 190.0) shouldBe 60
        layout.keyAt(x = 5.0, y = 110.0) shouldBe 60
        layout.keyAt(x = 168.0, y = 190.0) shouldBe 76
        layout.keyAt(x = 50.0, y = 90.0) shouldBe null
        layout.keyAt(x = 171.0, y = 190.0) shouldBe null
    }

    "the keybed drawn an octave up offers the octave up" {
        KeyBedLayout(bed = bed, offset = 12).keyAt(x = 5.0, y = 190.0) shouldBe 72
    }

    "a panel offers its keys, pads, buttons, knobs, the encoder, faders and wheels; the display nothing" {
        fun grip(x: Int, y: Int) = ScreenControls.gripAt(
            panel = KeyLabEssential49Mk3,
            x = x.toDouble(),
            y = y.toDouble(),
            noteOffset = 0,
            valueOf = { if (it == "knob-1") 0.25 else null },
        )

        grip(207 + 10, 32 + 10) shouldBe Grip.Momentary(control = ControlId("pad-1"))
        grip(556 + 5, 136 + 5) shouldBe Grip.Momentary(control = ControlId("play"))
        grip(1085, 45) shouldBe Grip.Dial(control = ControlId("knob-1"), from = 0.25)
        grip(795, 158) shouldBe Grip.Encoder(ControlId.MainEncoder)
        grip(1085, 120).shouldBeInstanceOf<Grip.Track>().control shouldBe ControlId("fader-1")
        grip(42 + 5, 300).shouldBeInstanceOf<Grip.Track>().bipolar shouldBe true
        grip(82 + 5, 300).shouldBeInstanceOf<Grip.Track>().bipolar shouldBe false
        grip(200, 420).shouldBeInstanceOf<Grip.Key>()
        grip(10, 10) shouldBe null
    }

    "a key and a pad go down and up with their pointer, wherever the finger slides" {
        val controls = ScreenControls(source = screen)

        controls.down(pointer = 1, grip = Grip.Key(64), y = 150.0, tsMs = 0.0).single()
            .shouldBeInstanceOf<ControlEvent.NoteDown>().note shouldBe 64
        controls.down(pointer = 2, grip = Grip.Momentary(control = ControlId("pad-2")), y = 0.0, tsMs = 0.0).single()
            .shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId("pad-2")

        controls.move(pointer = 1, y = 90.0, tsMs = 0.0).shouldBeEmpty()

        controls.up(pointer = 2, tsMs = 0.0).single().shouldBeInstanceOf<ControlEvent.Release>().control shouldBe ControlId("pad-2")
        controls.up(pointer = 1, tsMs = 0.0).single().shouldBeInstanceOf<ControlEvent.NoteUp>().note shouldBe 64
        controls.up(pointer = 1, tsMs = 0.0).shouldBeEmpty()
    }

    "a fader follows the finger's height; the pitch wheel goes -1 to 1 and springs back to the middle on the up" {
        val controls = ScreenControls(source = screen)
        val fader = Grip.Track(control = ControlId("fader-1"), top = 100.0, height = 100.0, bipolar = false)
        val bend = Grip.Track(control = ControlId.PitchBend, top = 100.0, height = 100.0, bipolar = true)

        fun List<ControlEvent>.value() = single().shouldBeInstanceOf<ControlEvent.Move>().value

        controls.down(pointer = 1, grip = fader, y = 125.0, tsMs = 0.0).value() shouldBe 0.75
        controls.move(pointer = 1, y = 50.0, tsMs = 0.0).value() shouldBe 1.0
        controls.up(pointer = 1, tsMs = 0.0).shouldBeEmpty()

        controls.down(pointer = 2, grip = bend, y = 200.0, tsMs = 0.0).value() shouldBe -1.0
        controls.move(pointer = 2, y = 100.0, tsMs = 0.0).value() shouldBe 1.0
        controls.up(pointer = 2, tsMs = 0.0).value() shouldBe 0.0
    }

    "a knob turns from where it was as the finger drags up, within 0 and 1" {
        val controls = ScreenControls(source = screen)
        val knob = Grip.Dial(control = ControlId("knob-3"), from = 0.5)

        fun List<ControlEvent>.value() = single().shouldBeInstanceOf<ControlEvent.Move>().value

        controls.down(pointer = 1, grip = knob, y = 300.0, tsMs = 0.0).shouldBeEmpty()
        controls.move(pointer = 1, y = 300.0 - ScreenControls.DIAL_RANGE / 4, tsMs = 0.0).value() shouldBe (0.75 plusOrMinus 1e-9)
        controls.move(pointer = 1, y = 0.0, tsMs = 0.0).value() shouldBe 1.0
        controls.move(pointer = 1, y = 900.0, tsMs = 0.0).value() shouldBe 0.0
    }

    "the encoder steps as it is dragged, each step once" {
        val controls = ScreenControls(source = screen)
        val step = ScreenControls.ENCODER_STEP

        fun List<ControlEvent>.steps() = single().shouldBeInstanceOf<ControlEvent.Nudge>().steps

        controls.down(pointer = 1, grip = Grip.Encoder(ControlId.MainEncoder), y = 100.0, tsMs = 0.0).shouldBeEmpty()
        controls.move(pointer = 1, y = 100.0 - step * 0.5, tsMs = 0.0).shouldBeEmpty()
        controls.move(pointer = 1, y = 100.0 - step * 2, tsMs = 0.0).steps() shouldBe 2
        controls.move(pointer = 1, y = 100.0 - step * 2.5, tsMs = 0.0).shouldBeEmpty()
        controls.move(pointer = 1, y = 100.0 + step, tsMs = 0.0).steps() shouldBe -3
    }

    "the encoder steps a whole step each way: a click's wobble of a pixel down steps nothing" {
        val controls = ScreenControls(source = screen)

        controls.down(pointer = 1, grip = Grip.Encoder(ControlId.MainEncoder), y = 100.0, tsMs = 0.0).shouldBeEmpty()
        controls.move(pointer = 1, y = 100.5, tsMs = 0.0).shouldBeEmpty()
        controls.move(pointer = 1, y = 99.5, tsMs = 0.0).shouldBeEmpty()
    }

    "two fingers on one key hold it as one: down with the first, up with the last" {
        val controls = ScreenControls(source = screen)

        controls.down(pointer = 1, grip = Grip.Key(60), y = 0.0, tsMs = 0.0).size shouldBe 1
        controls.down(pointer = 2, grip = Grip.Key(60), y = 0.0, tsMs = 0.0).shouldBeEmpty()
        controls.up(pointer = 1, tsMs = 0.0).shouldBeEmpty()
        controls.up(pointer = 2, tsMs = 0.0).single().shouldBeInstanceOf<ControlEvent.NoteUp>().note shouldBe 60
    }

    "two fingers on one button hold it as one too: one press, one release, the sustain kept until the last lets go" {
        val controls = ScreenControls(source = screen)
        val sustain = Grip.Momentary(ControlId.Sustain)

        controls.down(pointer = 1, grip = sustain, y = 0.0, tsMs = 0.0).single().shouldBeInstanceOf<ControlEvent.Press>()
        controls.down(pointer = 2, grip = sustain, y = 0.0, tsMs = 0.0).shouldBeEmpty()
        controls.up(pointer = 2, tsMs = 0.0).shouldBeEmpty()
        controls.up(pointer = 1, tsMs = 0.0).single().shouldBeInstanceOf<ControlEvent.Release>()
    }

    "the pitch wheel springs back only when the last finger on it lets go" {
        val controls = ScreenControls(source = screen)
        val bend = Grip.Track(control = ControlId.PitchBend, top = 100.0, height = 100.0, bipolar = true)

        controls.down(pointer = 1, grip = bend, y = 100.0, tsMs = 0.0)
        controls.down(pointer = 2, grip = bend, y = 150.0, tsMs = 0.0)

        controls.up(pointer = 2, tsMs = 0.0).shouldBeEmpty()
        controls.up(pointer = 1, tsMs = 0.0).single().shouldBeInstanceOf<ControlEvent.Move>().value shouldBe 0.0
    }

    "a click on a fader's cap where it stands moves it nowhere" {
        val panel = KeyLabEssential49Mk3
        val fader = panel.controls.filterIsInstance<PanelControl.Fader>().first()
        val grip = ScreenControls.gripAt(
            panel = panel,
            x = fader.x.toDouble(),
            y = fader.y + 5.0,
            noteOffset = 0,
            valueOf = { null },
        ).shouldBeInstanceOf<Grip.Track>()

        // The cap at the top is drawn from the fader's top, 10 high: its middle is the top of the range
        ScreenControls(source = screen).down(pointer = 1, grip = grip, y = fader.y + 5.0, tsMs = 0.0).single()
            .shouldBeInstanceOf<ControlEvent.Move>().value shouldBe 1.0
    }

    "a down on a pointer still holding (its up lost) lets the old hold go first" {
        val controls = ScreenControls(source = screen)

        controls.down(pointer = 1, grip = Grip.Key(60), y = 0.0, tsMs = 0.0)

        val next = controls.down(pointer = 1, grip = Grip.Key(62), y = 0.0, tsMs = 0.0)

        next.map { it::class.simpleName } shouldBe listOf("NoteUp", "NoteDown")
    }

    "the drawing going away lets every pointer go" {
        val controls = ScreenControls(source = screen)

        controls.down(pointer = 1, grip = Grip.Key(60), y = 0.0, tsMs = 0.0)
        controls.down(pointer = 2, grip = Grip.Momentary(ControlId("pad-1")), y = 0.0, tsMs = 0.0)

        controls.releaseAll(tsMs = 0.0).map { it::class.simpleName }.toSet() shouldBe setOf("NoteUp", "Release")
        controls.releaseAll(tsMs = 0.0).shouldBeEmpty()
    }
})
