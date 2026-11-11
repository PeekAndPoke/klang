/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.midi.KeyLayout
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/**
 * The MIDI settings' letters (KLANG-00402, D9): what a drawing of the laptop keys prints on a key, shared by the MIDI
 * page and the MIDI tool. The layout chosen is this machine's, kept as a stored section (KLANG-00422).
 */
class MidiSettingsSpec : StringSpec({

    "a key's letter: the layout chosen first, else the one learned, else the English one; a key learns its first letter only" {
        val machine = MemoryKeyValueStore()
        val stores = SectionStores(user = MemoryKeyValueStore(), machine = machine, tab = MemoryKeyValueStore())
        val settings = MidiSettings(kept = MidiKept(stores = stores))

        settings.chooseLayout(null)

        withClue("nothing learned, no layout chosen: the English letter") {
            settings.letters()("KeyZ") shouldBe KeyLayout.English.labelOf("KeyZ")
        }

        settings.learn(code = "KeyZ", letter = "Ä")
        settings.learn(code = "KeyZ", letter = "Ö")

        withClue("learned: the first letter it pressed, not a later one") { settings.letters()("KeyZ") shouldBe "Ä" }

        settings.chooseLayout(KeyLayout.French)

        withClue("a layout chosen by hand wins over what was learned") {
            settings.letters()("KeyZ") shouldBe KeyLayout.French.labelOf("KeyZ")
        }

        withClue("kept as this machine's, and read back by the next visit's settings") {
            machine.read(StorageKeys.MIDI_DESK) shouldBe """{"v":1,"data":{"keyLayout":"French"}}"""
            MidiSettings(kept = MidiKept(stores = stores)).layout() shouldBe KeyLayout.French
        }

        settings.chooseLayout(null)
        withClue("back to Automatic") {
            settings.layout() shouldBe null
            machine.read(StorageKeys.MIDI_DESK) shouldBe """{"v":1,"data":{"keyLayout":null}}"""
        }
    }

    "a layout's name no layout has, one of another version, is Automatic" {
        val machine = MemoryKeyValueStore(initial = mapOf(StorageKeys.MIDI_DESK to """{"v":1,"data":{"keyLayout":"Klingon"}}"""))
        val stores = SectionStores(user = MemoryKeyValueStore(), machine = machine, tab = MemoryKeyValueStore())

        MidiSettings(kept = MidiKept(stores = stores)).layout() shouldBe null
    }
})
