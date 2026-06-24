/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.createSprudelVoiceData

class RepeatCyclesPatternSpec : StringSpec({

    "RepeatCyclesPattern repeats each cycle n times" {
        val source = AtomicPattern(createSprudelVoiceData { note = "c" })

        val pattern = RepeatCyclesPattern(source, 2.0)

        // For a static pattern (every cycle is "c"), repeatCycles acts as identity
        // Query 4 cycles - should get all 4 (not stop after 2)
        val events = pattern.queryArc(0.0, 4.0)

        events shouldHaveSize 4  // All 4 cycles
        events.all { it.data.note == "c" } shouldBe true
    }
})
