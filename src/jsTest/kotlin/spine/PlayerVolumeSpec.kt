/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/**
 * The master volume as a stored section of this machine (KLANG-00330, KLANG-00422): kept once per turn of the knob,
 * found on the next visit, a tab's own while it runs.
 */
class PlayerVolumeSpec : StringSpec({

    fun stores(machine: MemoryKeyValueStore) =
        SectionStores(user = MemoryKeyValueStore(), machine = machine, tab = MemoryKeyValueStore())

    "a first visit starts at full; a volume set is kept when the knob is let go, and found on the next visit" {
        val machine = MemoryKeyValueStore()
        val player = Player(stores = stores(machine))

        player.outputVolume() shouldBe 1.0

        player.setOutputVolume(0.6)
        player.setOutputVolume(0.25)
        withClue("while the knob turns") { machine.all shouldBe emptyMap() }

        player.rememberOutputVolume()

        machine.read(StorageKeys.OUTPUT) shouldBe """{"v":1,"data":{"volume":0.25}}"""
        Player(stores = stores(machine)).outputVolume() shouldBe 0.25
    }

    "a volume kept out of range is held in it" {
        val machine = MemoryKeyValueStore(initial = mapOf(StorageKeys.OUTPUT to """{"v":1,"data":{"volume":7.5}}"""))

        Player(stores = stores(machine)).outputVolume() shouldBe 1.0
    }

    "another tab's turn of the knob is not followed while this tab runs" {
        val machine = MemoryKeyValueStore()
        val player = Player(stores = stores(machine))

        player.setOutputVolume(0.5)
        machine.changeFromElsewhere(key = StorageKeys.OUTPUT, text = """{"v":1,"data":{"volume":0.1}}""")

        player.outputVolume() shouldBe 0.5
    }

    "the volume kept before the section existed is read once, and its old key goes; full stays the user's choice" {
        val machine = MemoryKeyValueStore(initial = mapOf(StorageKeys.MASTER_VOLUME to "0.4"))

        Player(stores = stores(machine)).outputVolume() shouldBe 0.4
        machine.read(StorageKeys.MASTER_VOLUME) shouldBe null
        machine.read(StorageKeys.OUTPUT) shouldBe """{"v":1,"data":{"volume":0.4}}"""

        val full = MemoryKeyValueStore(initial = mapOf(StorageKeys.MASTER_VOLUME to "1.0"))

        Player(stores = stores(full)).outputVolume() shouldBe 1.0
        withClue("kept, though it is what the default says: ${full.read(StorageKeys.OUTPUT)}") {
            full.read(StorageKeys.OUTPUT).orEmpty().contains("\"volume\"") shouldBe true
        }
    }

    "an old volume out of range or unreadable: held in range, or full" {
        Player(stores = stores(MemoryKeyValueStore(initial = mapOf(StorageKeys.MASTER_VOLUME to "-3")))).outputVolume() shouldBe 0.0
        Player(stores = stores(MemoryKeyValueStore(initial = mapOf(StorageKeys.MASTER_VOLUME to "NaN")))).outputVolume() shouldBe 1.0
        Player(stores = stores(MemoryKeyValueStore(initial = mapOf(StorageKeys.MASTER_VOLUME to "loud")))).outputVolume() shouldBe 1.0
    }
})
