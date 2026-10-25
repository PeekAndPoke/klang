/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The user's keys as stored text (KLANG-00325): there and back, and what an unreadable text or entry becomes.
 */
class KeymapJsonSpec : StringSpec({

    "there and back, an empty list (every key taken off) included" {
        val keys = mapOf("klang.stop" to listOf("F8", "Mod-Shift-."), "klang.play-or-update" to emptyList())

        KeymapJson.write(keys) shouldBe """{"klang.stop":["F8","Mod-Shift-."],"klang.play-or-update":[]}"""
        KeymapJson.read(KeymapJson.write(keys)) shouldBe keys
    }

    "an unreadable text is no keys at all" {
        KeymapJson.read("not json") shouldBe emptyMap()
        KeymapJson.read("[\"F8\"]") shouldBe emptyMap()
    }

    "an entry that is no list of strings is skipped, the others are kept" {
        withClue("a string, a number in the list, a nested object") {
            KeymapJson.read("""{"a":"F8","b":["F9",3],"c":{"x":1},"d":["F10"]}""") shouldBe mapOf("d" to listOf("F10"))
        }
    }
})
