/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** The laptop's keys (KLANG-00402, D1): the map by position, octave and velocity, repeat, modifiers, the focus lost. */
class LaptopKeysSpec : StringSpec({

    fun LaptopKeys.down(code: String, repeat: Boolean = false, modified: Boolean = false) =
        keyDown(code = code, repeat = repeat, modified = modified, tsMs = 0.0)

    fun LaptopKeys.up(code: String) = keyUp(code = code, tsMs = 0.0)

    fun LaptopKeys.Result.note(): Int = events.single().shouldBeInstanceOf<ControlEvent.NoteDown>().note

    "the bottom row plays C to E from C4 by position, the home row the black keys; the gaps play nothing" {
        val keys = LaptopKeys()

        // The German Y and the English Z are the same key: KeyZ
        keys.down("KeyZ").note() shouldBe 60
        keys.down("KeyS").note() shouldBe 61
        keys.down("KeyM").note() shouldBe 71
        keys.down("Comma").note() shouldBe 72
        keys.down("Semicolon").note() shouldBe 75
        keys.down("Slash").note() shouldBe 76

        // The gaps and the key beside D# (Firefox's quick find on an English board) are ours, and do nothing
        for (gap in listOf("KeyF", "KeyA", "KeyK", "Quote", "IntlBackslash")) {
            keys.down(gap) shouldBe LaptopKeys.Result(events = emptyList(), handled = true)
        }

        keys.down("KeyT") shouldBe LaptopKeys.Result.NotOurs
    }

    "the drawing's letters follow the octave: each key's letter on the note it plays" {
        val labels = LaptopKeys.keyLabels(octave = 1, label = KeyLayout.German::labelOf)

        labels[72] shouldBe "Y"
        labels[87] shouldBe "Ö"
        labels.size shouldBe LaptopKeys.NOTES.size
    }

    "Meta's up lets go of everything held (macOS sends no up for a key let go under Meta), and stays the browser's" {
        val keys = LaptopKeys()

        keys.down("KeyZ")

        val result = keys.up("MetaLeft")

        result.handled shouldBe false
        result.events.single().shouldBeInstanceOf<ControlEvent.AllUp>()
        keys.down("KeyZ").note() shouldBe 60
    }

    "a key's up stops the note it started, also after the octave moved" {
        val keys = LaptopKeys()

        keys.down("KeyZ").note() shouldBe 60
        keys.down("ArrowRight").events.shouldBeEmpty()
        keys.octave shouldBe 1

        keys.up("KeyZ").events.single().shouldBeInstanceOf<ControlEvent.NoteUp>().note shouldBe 60
        keys.down("KeyZ").note() shouldBe 72
    }

    "the octave stops at three each way, the velocity at its softest and loudest; the keys play at the velocity" {
        val keys = LaptopKeys()

        repeat(5) { keys.down("ArrowLeft"); keys.up("ArrowLeft") }
        keys.octave shouldBe -LaptopKeys.OCTAVE_RANGE
        keys.noteOf("KeyZ") shouldBe 24

        repeat(10) { keys.down("ArrowUp"); keys.up("ArrowUp") }
        keys.velocity shouldBe 127
        keys.down("KeyX").events.single().shouldBeInstanceOf<ControlEvent.NoteDown>().velocity shouldBe 1.0

        repeat(10) { keys.down("ArrowDown"); keys.up("ArrowDown") }
        keys.velocity shouldBe LaptopKeys.VELOCITIES.first()
    }

    "a held key's repeat is ours but plays nothing again; a key with Ctrl, Alt or Meta is the browser's" {
        val keys = LaptopKeys()

        keys.down("KeyZ").events.size shouldBe 1
        keys.down("KeyZ", repeat = true) shouldBe LaptopKeys.Result(events = emptyList(), handled = true)
        keys.down("Space", modified = true) shouldBe LaptopKeys.Result.NotOurs
        // Shift+Tab is the way out of the keys, the browser's; Tab alone the sustain
        keys.keyDown(code = "Tab", repeat = false, modified = false, tsMs = 0.0, shift = true) shouldBe LaptopKeys.Result.NotOurs
        keys.down("Tab").events.single().shouldBeInstanceOf<ControlEvent.Press>()
        keys.up("KeyQ") shouldBe LaptopKeys.Result(events = emptyList(), handled = true)
        keys.up("KeyT") shouldBe LaptopKeys.Result.NotOurs
    }

    "the pads press at the velocity, the function keys press their controls; each is released on its up" {
        val keys = LaptopKeys()

        val pad = keys.down("KeyQ").events.single().shouldBeInstanceOf<ControlEvent.Press>()

        pad.control shouldBe ControlId("pad-5")
        pad.velocity shouldBe LaptopKeys.DEFAULT_VELOCITY / 127.0

        keys.down("Digit4").events.single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId("pad-4")
        keys.down("Tab").events.single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId.Sustain
        keys.down("Escape").events.single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe ControlId.Panic
        keys.down("BracketRight").events.single().shouldBeInstanceOf<ControlEvent.Press>().control shouldBe
            ControlId.InstrumentNext

        keys.up("Tab").events.single().shouldBeInstanceOf<ControlEvent.Release>().control shouldBe ControlId.Sustain
    }

    "the focus lost lets go of everything held at once, and says nothing when nothing is held" {
        val keys = LaptopKeys()

        keys.down("KeyZ")
        keys.down("Tab")

        keys.focusLost(tsMs = 0.0).single().shouldBeInstanceOf<ControlEvent.AllUp>().reason shouldBe
            ControlEvent.Reason.FocusLost
        keys.focusLost(tsMs = 0.0).shouldBeEmpty()

        // The key's up after that finds nothing held, and the key plays again on its next down
        keys.up("KeyZ").events.shouldBeEmpty()
        keys.down("KeyZ").note() shouldBe 60
    }
})
