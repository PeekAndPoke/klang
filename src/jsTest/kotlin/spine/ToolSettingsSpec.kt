/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.state.ToolSettings
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/** The code tools' auto-update switch (KLANG-00422): the widgets' switch tied to what the Spine keeps. */
class ToolSettingsSpec : StringSpec({

    fun settings(user: MemoryKeyValueStore) =
        Settings(stores = SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore()))

    /** The widgets' switch as a page finds it, before and after [block]. */
    suspend fun withSwitch(block: suspend () -> Unit) {
        val was = KlangToolAutoUpdate()
        KlangToolAutoUpdate.set(true)

        try {
            block()
        } finally {
            KlangToolAutoUpdate.set(was)
        }
    }

    "the switch shows what is kept, a press is kept, and another tab's press shows at once" {
        withSwitch {
            val user = MemoryKeyValueStore(initial = mapOf(StorageKeys.TOOLS to """{"v":1,"data":{"autoUpdate":false}}"""))
            val settings = settings(user)
            val detach = settings.attachTools()

            try {
                withClue("what is kept") { KlangToolAutoUpdate() shouldBe false }

                KlangToolAutoUpdate.toggle()
                settings.tools() shouldBe ToolSettings(autoUpdate = true)
                user.read(StorageKeys.TOOLS) shouldBe """{"v":1,"data":{"autoUpdate":true}}"""

                user.changeFromElsewhere(key = StorageKeys.TOOLS, text = """{"v":1,"data":{"autoUpdate":false}}""")
                withClue("another tab's press") { KlangToolAutoUpdate() shouldBe false }

                settings.tools.reset()
                withClue("a reset: on, as for one who never chose") { KlangToolAutoUpdate() shouldBe true }
                user.all shouldBe emptyMap()
            } finally {
                detach()
            }

            withClue("untied: a press is no longer kept, and what is kept no longer shows") {
                KlangToolAutoUpdate.toggle()
                user.all shouldBe emptyMap()

                settings.tools.update { it.copy(autoUpdate = true) }
                KlangToolAutoUpdate() shouldBe false
            }
        }
    }

    "never chosen: on, and nothing is kept" {
        withSwitch {
            val user = MemoryKeyValueStore()
            val detach = settings(user).attachTools()

            try {
                KlangToolAutoUpdate() shouldBe true
                user.all shouldBe emptyMap()
            } finally {
                detach()
            }
        }
    }

    "what the widgets kept themselves before is read once, and the old key goes" {
        val off = MemoryKeyValueStore(initial = mapOf(StorageKeys.TOOL_AUTO_UPDATE to "false"))
        settings(off).tools() shouldBe ToolSettings(autoUpdate = false)
        off.all shouldBe mapOf(StorageKeys.TOOLS to """{"v":1,"data":{"autoUpdate":false}}""")

        withClue("kept as on: a choice, also where it says what the default says") {
            val on = MemoryKeyValueStore(initial = mapOf(StorageKeys.TOOL_AUTO_UPDATE to "true"))
            settings(on).tools() shouldBe ToolSettings(autoUpdate = true)
            on.all shouldBe mapOf(StorageKeys.TOOLS to """{"v":1,"data":{"autoUpdate":true}}""")
        }
    }
})
