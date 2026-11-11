/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.midi.KeyLayout
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.state.MidiDesk
import io.peekandpoke.klang.state.MidiPlay
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/**
 * What the MIDI rig and the MIDI settings keep (KLANG-00422): the play code as the user's, the loadout and the laptop
 * keys' layout as this machine's, both following another tab, and what a browser kept before read once.
 */
class MidiKeptSpec : StringSpec({

    fun kept(user: MemoryKeyValueStore = MemoryKeyValueStore(), machine: MemoryKeyValueStore = MemoryKeyValueStore()) =
        MidiKept(stores = SectionStores(user = user, machine = machine, tab = MemoryKeyValueStore()))

    "the play code is the user's, the loadout and the layout this machine's; nothing kept, nothing made up" {
        val user = MemoryKeyValueStore()
        val machine = MemoryKeyValueStore()
        val midi = kept(user = user, machine = machine)

        midi.play() shouldBe MidiPlay()
        midi.desk() shouldBe MidiDesk()
        user.all shouldBe emptyMap()
        machine.all shouldBe emptyMap()

        midi.play.update { it.copy(code = "x => x") }
        midi.desk.update { it.copy(loadout = listOf("lead")) }

        user.all shouldBe mapOf(StorageKeys.MIDI_PLAY to """{"v":1,"data":{"code":"x => x"}}""")
        machine.all shouldBe mapOf(StorageKeys.MIDI_DESK to """{"v":1,"data":{"loadout":["lead"]}}""")
    }

    "what another tab keeps shows here, and what this tab writes leaves the other tab's field alone" {
        val user = MemoryKeyValueStore()
        val machine = MemoryKeyValueStore()
        val midi = kept(user = user, machine = machine)

        midi.play.update { it.copy(code = "mine") }
        midi.desk.update { it.copy(loadout = listOf("lead")) }

        user.changeFromElsewhere(key = StorageKeys.MIDI_PLAY, text = """{"v":1,"data":{"code":"theirs"}}""")
        machine.changeFromElsewhere(key = StorageKeys.MIDI_DESK, text = """{"v":1,"data":{"loadout":["lead"],"keyLayout":"French"}}""")

        withClue("another tab's play code and layout: what is kept now") {
            midi.play().code shouldBe "theirs"
            midi.desk().keyLayout shouldBe "French"
        }

        midi.desk.update { it.copy(loadout = listOf("bass")) }
        withClue("this tab's loadout written, the other tab's layout left") {
            machine.read(StorageKeys.MIDI_DESK) shouldBe """{"v":1,"data":{"loadout":["bass"],"keyLayout":"French"}}"""
        }

        midi.play.update { it.copy(code = "mine") }
        withClue("the play code this tab had before, applied again after the other tab's: kept") {
            user.read(StorageKeys.MIDI_PLAY) shouldBe """{"v":1,"data":{"code":"mine"}}"""
        }
    }

    "the play code a browser kept before, a plain text, is read once, and the old key goes" {
        val code = "let lead = Ign.saw()\n\nx => x.sound(lead)"
        val user = MemoryKeyValueStore(
            initial = mapOf(
                StorageKeys.MIDI_PLAY_CODE to code,
                StorageKeys.MIDI_INSTRUMENTS to """[{"name":"Old","ignitor":"Ign.sine()"}]""",
            )
        )

        kept(user = user).play() shouldBe MidiPlay(code = code)

        withClue("the play code wins; the instruments it was once made from go with its old key") {
            user.all.keys shouldBe setOf(StorageKeys.MIDI_PLAY)
        }
        withClue("instruments that cannot be read stay") {
            val odd = MemoryKeyValueStore(initial = mapOf(StorageKeys.MIDI_PLAY_CODE to code, StorageKeys.MIDI_INSTRUMENTS to "not json"))

            kept(user = odd).play() shouldBe MidiPlay(code = code)
            odd.all.keys shouldBe setOf(StorageKeys.MIDI_PLAY, StorageKeys.MIDI_INSTRUMENTS)
        }
        withClue("the draft beside the play code goes with it too; one that cannot be read stays") {
            val draft = """{"name":"My lead","ignitor":"Ign.saw()","mapper":""}"""
            val both = MemoryKeyValueStore(initial = mapOf(StorageKeys.MIDI_PLAY_CODE to code, StorageKeys.MIDI_INSTRUMENT_DRAFT to draft))

            kept(user = both).play() shouldBe MidiPlay(code = code)
            both.all.keys shouldBe setOf(StorageKeys.MIDI_PLAY)

            val odd = MemoryKeyValueStore(
                initial = mapOf(StorageKeys.MIDI_PLAY_CODE to code, StorageKeys.MIDI_INSTRUMENT_DRAFT to "not json")
            )

            kept(user = odd)
            odd.all.keys shouldBe setOf(StorageKeys.MIDI_PLAY, StorageKeys.MIDI_INSTRUMENT_DRAFT)
        }
    }

    "the instruments saved before the one play code become one, once, and their keys go" {
        val user = MemoryKeyValueStore(
            initial = mapOf(
                StorageKeys.MIDI_INSTRUMENTS to """[{"name":"Glass bell","ignitor":"Ign.sine()","mapper":""},{"ignitor":"no name"}]""",
                StorageKeys.MIDI_INSTRUMENT_DRAFT to """{"name":"My lead","ignitor":"Ign.saw()","mapper":"","more":1}""",
            )
        )

        val code = kept(user = user).play().code.orEmpty()

        withClue("the draft and the saved one with a name, as lets: $code") {
            code shouldContain "Ign.saw()"
            code shouldContain "Ign.sine()"
        }
        user.all.keys shouldBe setOf(StorageKeys.MIDI_PLAY)

        withClue("a draft of the user's with saved instruments that cannot be read: the draft becomes the play code, the rest stays") {
            val half = MemoryKeyValueStore(
                initial = mapOf(
                    StorageKeys.MIDI_INSTRUMENT_DRAFT to """{"name":"My lead","ignitor":"Ign.saw()","mapper":""}""",
                    StorageKeys.MIDI_INSTRUMENTS to "not json",
                )
            )

            kept(user = half).play().code.orEmpty() shouldContain "Ign.saw()"
            half.all.keys shouldBe setOf(StorageKeys.MIDI_PLAY, StorageKeys.MIDI_INSTRUMENTS)
        }
        withClue("saved instruments with a draft that cannot be read: they become the play code, the draft stays") {
            val half = MemoryKeyValueStore(
                initial = mapOf(
                    StorageKeys.MIDI_INSTRUMENTS to """[{"name":"Glass bell","ignitor":"Ign.sine()","mapper":""}]""",
                    StorageKeys.MIDI_INSTRUMENT_DRAFT to "not json",
                )
            )

            kept(user = half).play().code.orEmpty() shouldContain "Ign.sine()"
            half.all.keys shouldBe setOf(StorageKeys.MIDI_PLAY, StorageKeys.MIDI_INSTRUMENT_DRAFT)
        }
        withClue("instruments that cannot be read are none, and stay") {
            val odd = MemoryKeyValueStore(initial = mapOf(StorageKeys.MIDI_INSTRUMENTS to "not json"))

            kept(user = odd).play() shouldBe MidiPlay()
            odd.all shouldBe mapOf(StorageKeys.MIDI_INSTRUMENTS to "not json")
        }
    }

    "the loadout and the layout a browser kept before, a key each, are read once into the one document" {
        val machine = MemoryKeyValueStore(
            initial = mapOf(StorageKeys.MIDI_LOADOUT to """["bass","lead"]""", StorageKeys.MIDI_KEY_LAYOUT to "French")
        )

        kept(machine = machine).desk() shouldBe MidiDesk(loadout = listOf("bass", "lead"), keyLayout = "French")
        machine.all shouldBe mapOf(StorageKeys.MIDI_DESK to """{"v":1,"data":{"loadout":["bass","lead"],"keyLayout":"French"}}""")

        withClue("an empty loadout kept before is a choice; a loadout that cannot be read stays, the layout is still read") {
            val empty = MemoryKeyValueStore(initial = mapOf(StorageKeys.MIDI_LOADOUT to "[]"))
            kept(machine = empty)
            empty.all shouldBe mapOf(StorageKeys.MIDI_DESK to """{"v":1,"data":{"loadout":[]}}""")

            val odd = MemoryKeyValueStore(initial = mapOf(StorageKeys.MIDI_LOADOUT to "not json", StorageKeys.MIDI_KEY_LAYOUT to "German"))
            kept(machine = odd).desk() shouldBe MidiDesk(keyLayout = "German")
            odd.all shouldBe mapOf(
                StorageKeys.MIDI_LOADOUT to "not json",
                StorageKeys.MIDI_DESK to """{"v":1,"data":{"keyLayout":"German"}}""",
            )
        }
    }

    "the Spine's reset of the MIDI keys forgets them" {
        val store = MemoryKeyValueStore()
        val spine = Spine(stores = SectionStores(user = store, machine = store, tab = MemoryKeyValueStore()))

        spine.midiSettings.chooseLayout(KeyLayout.French)
        store.read(StorageKeys.MIDI_DESK) shouldBe """{"v":1,"data":{"keyLayout":"French"}}"""

        spine.resetStored(StorageKeys.MIDI_DESK)

        spine.midiSettings.layout() shouldBe null
        store.read(StorageKeys.MIDI_DESK) shouldBe null

        withClue("the Spine's rig keeps its play code in the Spine's storage") {
            spine.playbacks.live.apply("x => x.sound(\"saw\")")

            store.read(StorageKeys.MIDI_PLAY) shouldBe """{"v":1,"data":{"code":"x => x.sound(\"saw\")"}}"""
        }
    }
})
