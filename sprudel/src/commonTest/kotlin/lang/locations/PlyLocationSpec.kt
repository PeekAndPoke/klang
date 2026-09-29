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
 * Live highlighting through the ply family: every copy keeps the location of the atom it came from,
 * and the factor adds its own location, the way `fast(n)` does.
 */
class PlyLocationSpec : StringSpec({

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

    // Line 3:  sound("bd hh").ply(2)
    // Columns: 123456789012345678901
    //                 ^  ^       ^
    //                 8  11      20
    "ply(2) keeps the atom location and adds the factor location" {
        val events = query("sound(\"bd hh\").ply(2)")

        events shouldHaveSize 4
        events.map { it.columns() } shouldBe listOf(
            listOf(20, 8), listOf(20, 8), listOf(20, 11), listOf(20, 11),
        )
    }

    // Line 3:  sound("bd hh").ply("<2 3>")
    // Columns: 1234567890123456789012
    //                 ^  ^         ^
    //                 8  11        22
    "ply with a control pattern keeps the atom location and adds the factor atom location" {
        val events = query("sound(\"bd hh\").ply(\"<2 3>\")")

        events shouldHaveSize 4
        events.map { it.columns() } shouldBe listOf(
            listOf(22, 8), listOf(22, 8), listOf(22, 11), listOf(22, 11),
        )
    }

    // Line 3:  sound("bd hh").plyWith(2, x => x.fast(1))
    // Columns: 123456789012345678901234567890123456789
    //                 ^  ^           ^              ^
    //                 8  11          24             39 (the transform's factor, copy 1 only)
    "plyWith keeps the atom location and adds the factor location" {
        val events = query("sound(\"bd hh\").plyWith(2, x => x.fast(1))")

        events shouldHaveSize 4
        events.map { it.columns() } shouldBe listOf(
            listOf(24, 8), listOf(24, 39, 8), listOf(24, 11), listOf(24, 39, 11),
        )
    }

    // Line 3:  sound("bd hh").plyForEach(2, (p, i) => p.fast(1))
    // Columns: 12345678901234567890123456789012345678901234567
    //                 ^  ^              ^                   ^
    //                 8  11             27                  47 (the transform's factor, copy 1 only)
    "plyForEach keeps the atom location and adds the factor location" {
        val events = query("sound(\"bd hh\").plyForEach(2, (p, i) => p.fast(1))")

        events shouldHaveSize 4
        events.map { it.columns() } shouldBe listOf(
            listOf(27, 8), listOf(27, 47, 8), listOf(27, 11), listOf(27, 47, 11),
        )
    }

    // Line 3:  sound("bd hh").apply(ply(2))
    // Columns: 12345678901234567890123456
    //                 ^  ^              ^
    //                 8  11             26
    "the mapper form of ply keeps the atom location and adds the factor location" {
        val events = query("sound(\"bd hh\").apply(ply(2))")

        events shouldHaveSize 4
        events.map { it.columns() } shouldBe listOf(
            listOf(26, 8), listOf(26, 8), listOf(26, 11), listOf(26, 11),
        )
    }
})
