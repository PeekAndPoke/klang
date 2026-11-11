/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.midi.ControlEvent
import io.peekandpoke.klang.midi.ControlId
import io.peekandpoke.klang.midi.LaptopKeys
import io.peekandpoke.klang.midi.MidiPlayCode
import io.peekandpoke.klang.midi.SourceId
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The MIDI rig (KLANG-00402, step 4): what it lights, by source, as the drawings show it. A rig of the spec's own, started
 * only to read a reload: no ports asked for, no engine, the voices it would start dropped.
 */
class MidiRigSpec : StringSpec({

    /** The engine every rig of this spec plays on: one, as the app has one. Never started: no case calls `startEngine()`. */
    val engine = Player(stores = SectionStores.inMemory())

    /** A rig on [store], a browser's storage of the spec's own: the user's and the machine's in one. */
    fun rigOn(store: MemoryKeyValueStore) = MidiRig(
        engine = engine,
        kept = MidiKept(stores = SectionStores(user = store, machine = store, tab = MemoryKeyValueStore())),
    )

    /** The play code's document as it is kept, holding [code]. */
    fun playKept(code: String) = """{"v":1,"data":{"code":${Json.encodeToString(String.serializer(), code)}}}"""

    /** The next instrument key (the laptop's `]`), pressed and let go. */
    fun step(rig: MidiRig) {
        rig.laptopKeyDown(code = "BracketRight", repeat = false, modified = false, shift = false, tsMs = 0.0)
        rig.laptopKeyUp(code = "BracketRight", tsMs = 0.0)
    }

    val keyLab = SourceId("keylab")
    val pad = ControlId("pad-3")

    fun press(source: SourceId, control: ControlId = pad) =
        ControlEvent.Press(source = source, control = control, velocity = 0.8, tsMs = 0.0)

    fun allUp(source: SourceId, reason: ControlEvent.Reason) =
        ControlEvent.AllUp(source = source, reason = reason, tsMs = 0.0)

    "a device's pad lights in its bank's colour on the device's drawing; a pad on the laptop's side lights untinted on its own" {
        val rig = MidiRig(engine = engine)

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
        val rig = MidiRig(engine = engine)

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
        val rig = MidiRig(engine = engine)

        rig.handle(press(keyLab))
        rig.handle(press(MidiRig.LAPTOP_SCREEN))
        rig.handle(ControlEvent.Release(source = MidiRig.LAPTOP_SCREEN, control = pad, tsMs = 0.0))

        withClue("the device's pad-3 still lit and tinted") {
            rig.state().pressed(laptopSide = false) shouldBe mapOf("pad-3" to 0.8)
            rig.state().pressedTints.keys shouldBe setOf("pad-3")
        }
    }

    "the laptop's keys hold a note on the laptop's side, not the device's; its focus lost lets it go" {
        val rig = MidiRig(engine = engine)
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
        val rig = MidiRig(engine = engine)
        val before = rig.state().laptopOctave

        rig.laptopKeyDown(code = "ArrowRight", repeat = false, modified = false, shift = false, tsMs = 0.0)

        rig.state().laptopOctave shouldBe before + 1
    }

    "the tool's panic lets every source go and puts out every light" {
        val rig = MidiRig(engine = engine)

        rig.handle(press(keyLab))
        rig.handle(ControlEvent.NoteDown(source = keyLab, channel = 0, note = 60, velocity = 0.5, tsMs = 0.0))
        rig.laptopKeyDown(code = "KeyZ", repeat = false, modified = false, shift = false, tsMs = 0.0)

        rig.panic(tsMs = 0.0)

        withClue("nothing held, nothing lit") {
            rig.state().held shouldBe emptyMap()
            rig.state().pressed shouldBe emptyMap()
            rig.state().pressedTints shouldBe emptyMap()
        }
    }

    "the play code: its instruments, what a key plays, one chosen or stepped to; one that fails keeps the one before" {
        val store = MemoryKeyValueStore()
        val rig = rigOn(store)

        rig.apply("let lead = Ign.saw()\nlet n = 3\nlet pad = Ign.supersaw()\n\nx => x.sound(lead)")

        withClue("its instruments: the lets holding an Ignitor; it plays its function's sound") {
            rig.state().instruments shouldBe listOf("lead", "pad")
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("lead")
            rig.state().playCodeError shouldBe null
        }

        rig.apply("x => x.sound(")

        withClue("one that fails: said, the one before plays on") {
            rig.state().playCodeError shouldNotBe null
            rig.state().instruments shouldBe listOf("lead", "pad")
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("lead")
        }

        step(rig)

        withClue("a step after one that failed: from the one that plays; the user's text and its error kept") {
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("pad")
            rig.state().playCodeError shouldNotBe null
            rig.state().playCode shouldBe "x => x.sound("
        }

        rig.apply("let lead = Ign.saw()\n\n3")

        withClue("one that ends in no function: said") { rig.state().playCodeError.shouldNotBeNull() shouldContain "ends in a function" }

        rig.apply("let lead = Ign.saw()\nlet pad = Ign.supersaw()\n\nx => x.sound(lead).gain(0.5)")
        rig.choose(MidiPlayCode.Sound.BuiltIn("pluck"))

        withClue("a built-in chosen: in the place of the sound a key plays, the rest kept") {
            rig.state().playCode.trim().lines().last() shouldBe "x => x.sound(\"pluck\").gain(0.5)"
            rig.state().sound shouldBe MidiPlayCode.Sound.BuiltIn("pluck")
        }

        step(rig)

        withClue("the next instrument key: the one after it") {
            rig.state().sound shouldBe MidiPlayCode.Sound.BuiltIn("superpluck")
        }

        rig.apply("let lead = Ign.saw()\n\nx => x.gain(0.5)")
        rig.choose(MidiPlayCode.Sound.Own("lead"))

        withClue("no sound to choose in: said, the code as it was") {
            rig.state().playCode.trim().lines().last() shouldBe "x => x.gain(0.5)"
            rig.state().playCodeError.shouldNotBeNull() shouldContain "name its sound"
        }
    }

    "a reload plays the play code the user applied; a first visit's is not saved, so it stays the app's" {
        val store = MemoryKeyValueStore()

        val first = rigOn(store)

        first.start()

        withClue("a first visit: the app's play code plays, nothing saved") {
            first.state().sound shouldBe MidiPlayCode.Sound.Own("warmSaw")
            store.read(StorageKeys.MIDI_PLAY) shouldBe null
        }

        store.write(key = StorageKeys.MIDI_PLAY, text = playKept("let lead = Ign.saw()\nlet pad = Ign.supersaw()\n\nx => x.sound(lead)"))

        val rig = rigOn(store)

        rig.start()

        withClue("the play code the user applied plays, and shows") {
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("lead")
            rig.state().playCode shouldBe rig.state().playing
            rig.state().playCodeError shouldBe null
        }
    }

    "what is a gesture that starts the Motor: a laptop key or a press that plays; the panic, the octave, the velocity are not" {
        val rig = MidiRig(engine = engine)

        withClue("a note and a pad play") { listOf("KeyZ", "Digit1").map { rig.laptopKeyPlays(it) } shouldBe listOf(true, true) }
        withClue("the panic, the octave, the velocity and the sustain do not") {
            listOf("Escape", "ArrowLeft", "ArrowUp", "Tab").map { rig.laptopKeyPlays(it) } shouldBe listOf(false, false, false, false)
        }
        withClue("on a drawing, the panic does not, a pad does") {
            MidiRig.plays(listOf(press(MidiRig.LAPTOP_SCREEN, ControlId.Panic))) shouldBe false
            MidiRig.plays(listOf(press(MidiRig.LAPTOP_SCREEN, ControlId("pad-2")))) shouldBe true
        }
    }

    "a reload whose play code fails now: the text comes back, its error said, never an empty editor" {
        val store = MemoryKeyValueStore()
        val failing = "let lead = Ign.saw().lowpas(1800)\n\nx => x.sound(lead)"

        store.write(key = StorageKeys.MIDI_PLAY, text = playKept(failing))

        val rig = rigOn(store)

        rig.start()

        withClue("the text back, its error said") {
            rig.state().playCode shouldBe failing
            rig.state().playCodeError shouldNotBe null
        }
    }

    "a step saves nothing: the stored play code stays the user's, whatever any tab steps to" {
        val store = MemoryKeyValueStore()

        val rig = rigOn(store)
        val applied = "let lead = Ign.saw()\nlet pad = Ign.supersaw()\n\nx => x.sound(lead)"

        rig.apply(applied)
        step(rig)

        withClue("the step plays for the session; the stored play code as the user applied it") {
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("pad")
            store.read(StorageKeys.MIDI_PLAY) shouldBe playKept(applied)
        }
    }

    "a play code that ends in a mapper value is none: it ends in a function" {
        val store = MemoryKeyValueStore()

        val rig = rigOn(store)

        rig.apply("reverb(wet = 0.2)")

        rig.state().playCodeError.shouldNotBeNull() shouldContain "ends in a function"
    }

    "a step keeps the error of the user's play code that fails: after a reload, and after an Apply" {
        val store = MemoryKeyValueStore()

        store.write(key = StorageKeys.MIDI_PLAY, text = playKept("let lead = Ign.saw().lowpas(1800)\n\nx => x.sound(lead)"))

        val reloaded = rigOn(store)

        reloaded.start()
        step(reloaded)

        withClue("nothing plays to step from: the stored code's own error stays") {
            reloaded.state().playCodeError.shouldNotBeNull() shouldContain "lowpas"
        }

        val rig = rigOn(store)

        rig.apply("let lead = Ign.saw()\nlet pad = Ign.supersaw()\n\nx => x.sound(lead)")
        rig.apply("let lead = Ign.saw().lowpas(1800)\n\nx => x.sound(lead)")
        step(rig)

        withClue("a step plays on from what plays; the failed text's error stays") {
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("pad")
            rig.state().playCodeError.shouldNotBeNull() shouldContain "lowpas"
        }
    }

    "a step from a sound not in the list starts at the list's first" {
        val store = MemoryKeyValueStore()

        val rig = rigOn(store)

        rig.apply("let lead = Ign.saw()\nlet pad = Ign.supersaw()\n\nx => x.s(\"bd\")")
        step(rig)

        rig.state().sound shouldBe MidiPlayCode.Sound.Own("lead")
    }

    "an old save that is only the page's own default stays the app's: not stored as the user's play code" {
        val store = MemoryKeyValueStore()

        store.write(
            key = StorageKeys.MIDI_INSTRUMENT_DRAFT,
            text = """{"name":"Warm saw","ignitor":"Ign.saw()\n  .plus(Ign.sine().detune(12).mul(0.3))\n  .lowpass(1800)""" +
                """\n  .adsr(0.01, 0.3, 0.6, 0.4)","mapper":"reverb(wet = 0.2)"}""",
        )

        val rig = rigOn(store)

        rig.start()

        rig.state().sound shouldBe MidiPlayCode.Sound.Own("warmSaw")
        withClue("nothing kept as the user's, the old draft left where it is") {
            store.all.keys shouldBe setOf(StorageKeys.MIDI_INSTRUMENT_DRAFT)
        }
    }

    "a rig that starts after another tab applied a play code plays that one; an Apply here is then kept over it" {
        val mine = "let lead = Ign.saw()\n\nx => x.sound(lead)"
        val theirs = "let pad = Ign.supersaw()\n\nx => x.sound(pad)"
        val store = MemoryKeyValueStore(initial = mapOf(StorageKeys.MIDI_PLAY to playKept(mine)))
        // The page loaded, the rig not started yet
        val rig = rigOn(store)

        store.changeFromElsewhere(key = StorageKeys.MIDI_PLAY, text = playKept(theirs))
        rig.start()

        withClue("the play code applied last, in whichever tab") { rig.state().sound shouldBe MidiPlayCode.Sound.Own("pad") }

        rig.apply(mine)

        withClue("the code this tab loaded with, applied again: kept, the last Apply wins") {
            store.read(StorageKeys.MIDI_PLAY) shouldBe playKept(mine)
        }
    }

    "a rig that runs shows a loadout kept elsewhere at once, and an empty one after a reset" {
        val store = MemoryKeyValueStore()
        val kept = MidiKept(stores = SectionStores(user = store, machine = store, tab = MemoryKeyValueStore()))
        val rig = MidiRig(engine = engine, kept = kept)

        rig.start()
        store.changeFromElsewhere(key = StorageKeys.MIDI_DESK, text = """{"v":1,"data":{"loadout":["\"saw\""]}}""")

        rig.state().loadout.codes shouldBe listOf("\"saw\"")

        kept.desk.reset()

        withClue("the storage page's reset") { rig.state().loadout.codes shouldBe emptyList() }
    }

    "the instrument keys step through the loadout, in its order; it is kept for the next visit" {
        val store = MemoryKeyValueStore()

        val rig = rigOn(store)

        rig.apply("let lead = Ign.saw()\nlet pad = Ign.supersaw()\nlet bass = Ign.square()\n\nx => x.sound(lead)")
        // As the loadout's end takes a drop
        rig.equip(MidiPlayCode.Sound.Own("bass"), slot = rig.state().loadout.sounds.size)
        rig.equip(MidiPlayCode.Sound.Own("lead"), slot = rig.state().loadout.sounds.size)

        withClue("from lead, the loadout's next: round to bass, then lead; pad is not in it") {
            step(rig)
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("bass")
            step(rig)
            rig.state().sound shouldBe MidiPlayCode.Sound.Own("lead")
        }

        withClue("kept, in its order") { store.read(StorageKeys.MIDI_DESK) shouldBe """{"v":1,"data":{"loadout":["bass","lead"]}}""" }

        withClue("read back by the next visit's rig") {
            val next = rigOn(store)

            next.start()
            next.state().loadout.sounds shouldBe listOf(MidiPlayCode.Sound.Own("bass"), MidiPlayCode.Sound.Own("lead"))
        }

        // lead dropped on bass's slot: swapped
        rig.equip(MidiPlayCode.Sound.Own("lead"), slot = 0)

        withClue("two slots swapped: kept so") {
            store.read(StorageKeys.MIDI_DESK) shouldBe """{"v":1,"data":{"loadout":["lead","bass"]}}"""
        }

        rig.unequip(0)

        withClue("a slot out: kept so") { store.read(StorageKeys.MIDI_DESK) shouldBe """{"v":1,"data":{"loadout":["bass"]}}""" }
    }
})
