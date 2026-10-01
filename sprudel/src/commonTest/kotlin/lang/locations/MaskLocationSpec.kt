/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.locations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * Live highlighting through the mask and struct family: every kept event keeps the location of the
 * atom it came from, and the mask atom that let it through adds its own location.
 */
class MaskLocationSpec : StringSpec({

    fun query(expr: String): List<SprudelPatternEvent> {
        val engine = klangScript { registerLibrary(sprudelLib) }
        val code = "import * from \"stdlib\"\nimport * from \"sprudel\"\n$expr"
        val pattern = engine.execute(code).value as SprudelPattern

        return pattern.queryArc(0.0, 1.0).filter { it.isOnset }
    }

    /** The start columns of the event's location chain, all on line 3. */
    fun SprudelPatternEvent.columns(): List<Int> {
        val locations = sourceLocations?.locations.orEmpty()
        locations.forEach { it.startLine shouldBe 3 }

        return locations.map { it.startColumn }
    }

    // Line 3:  sound("bd hh").mask("1 1")
    // Columns: 123456789012345678901234
    //                 ^  ^          ^ ^
    //                 8  11        22 24
    "mask keeps the atom location and adds the mask atom location" {
        val events = query("sound(\"bd hh\").mask(\"1 1\")")

        events shouldHaveSize 2
        events.map { it.columns() } shouldBe listOf(
            listOf(22, 8), listOf(24, 11),
        )
    }

    // Line 3:  sound("bd hh").mask("0 1")
    // Columns: 123456789012345678901234
    //                    ^            ^
    //                    11           24
    "mask drops the event under a falsy mask atom and highlights the truthy one" {
        val events = query("sound(\"bd hh\").mask(\"0 1\")")

        events shouldHaveSize 1
        events.map { it.columns() } shouldBe listOf(
            listOf(24, 11),
        )
    }

    // Line 3:  sound("bd hh").maskAll("x x")
    // Columns: 1234567890123456789012345678
    //                 ^  ^             ^ ^
    //                 8  11           25 27
    "maskAll keeps the atom location and adds the mask atom location" {
        val events = query("sound(\"bd hh\").maskAll(\"x x\")")

        events shouldHaveSize 2
        events.map { it.columns() } shouldBe listOf(
            listOf(25, 8), listOf(27, 11),
        )
    }

    // Line 3:  sound("bd hh").struct("x x")
    // Columns: 12345678901234567890123456
    //                 ^  ^           ^ ^
    //                 8  11         24 26
    "struct keeps the atom location and adds the structure atom location" {
        val events = query("sound(\"bd hh\").struct(\"x x\")")

        events shouldHaveSize 2
        events.map { it.columns() } shouldBe listOf(
            listOf(24, 8), listOf(26, 11),
        )
    }
})
