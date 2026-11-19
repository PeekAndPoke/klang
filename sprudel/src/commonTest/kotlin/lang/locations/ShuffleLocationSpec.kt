/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.locations

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * Live highlighting through `shuffle`: every event keeps the location of the atom it came from,
 * and the slice count adds its own location, the way `fast(n)` does. The events are compared in
 * any order, so the spec does not depend on the permutation.
 */
class ShuffleLocationSpec : StringSpec({

    fun query(expr: String, from: Double, to: Double): List<SprudelPatternEvent> {
        val engine = klangScript { registerLibrary(sprudelLib) }
        val code = "import * from \"stdlib\"\nimport * from \"sprudel\"\n$expr"
        val pattern = engine.execute(code).value as SprudelPattern

        return pattern.queryArc(from, to).filter { it.isOnset }
    }

    /** The start columns of the event's location chain, all on line 3. */
    fun SprudelPatternEvent.columns(): List<Int> {
        val locations = sourceLocations?.locations.orEmpty()
        locations.forEach { it.startLine shouldBe 3 }

        return locations.map { it.startColumn }
    }

    // Line 3:  sound("bd hh").shuffle(2)
    // Columns: 123456789012345678901234
    //                 ^  ^            ^
    //                 8  11           24
    "shuffle(2) keeps the atom location and adds the slice count location" {
        val events = query("sound(\"bd hh\").shuffle(2)", 0.0, 1.0)

        events.map { it.columns() } shouldContainExactlyInAnyOrder listOf(
            listOf(24, 8), listOf(24, 11),
        )
    }

    // Line 3:  sound("bd hh").shuffle("<2 4>")
    // Columns: 1234567890123456789012345678
    //                 ^  ^              ^ ^
    //                 8  11             26 28
    "shuffle with a control pattern adds the location of the slice count of each cycle" {
        query("sound(\"bd hh\").shuffle(\"<2 4>\")", 0.0, 1.0)
            .map { it.columns() } shouldContainExactlyInAnyOrder listOf(listOf(26, 8), listOf(26, 11))

        query("sound(\"bd hh\").shuffle(\"<2 4>\")", 1.0, 2.0)
            .map { it.columns() } shouldContainExactlyInAnyOrder listOf(listOf(28, 8), listOf(28, 11))
    }

    // Line 3:  sound("bd hh").shuffle("<2 ~>")
    // Columns: 1234567890123456789012345678
    //                 ^  ^              ^
    //                 8  11             26
    "a cycle whose slice count is a rest adds no location" {
        query("sound(\"bd hh\").shuffle(\"<2 ~>\")", 1.0, 2.0)
            .map { it.columns() } shouldContainExactlyInAnyOrder listOf(listOf(8), listOf(11))
    }

    // Line 3:  sound("bd hh").apply(shuffle(2))
    // Columns: 1234567890123456789012345678901
    //                 ^  ^                  ^
    //                 8  11                 30
    "the mapper form of shuffle keeps the atom location and adds the slice count location" {
        val events = query("sound(\"bd hh\").apply(shuffle(2))", 0.0, 1.0)

        events.map { it.columns() } shouldContainExactlyInAnyOrder listOf(
            listOf(30, 8), listOf(30, 11),
        )
    }
})
