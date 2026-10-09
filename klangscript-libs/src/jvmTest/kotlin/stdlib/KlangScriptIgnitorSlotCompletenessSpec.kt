/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue
import java.lang.reflect.Modifier

/**
 * Two doors, one DSL, for the flat slots: every flat leaf of the Kotlin door `IgnitorDsl.Slots` (a
 * `Param`, not a stage group such as `Slots.lpf`) is reachable on the script door as `Ignitor.slot.<name>`,
 * and it is the SAME object; and the other way round, every flat property of the script door
 * (`KlangScriptIgnitorSlots`, its `IgnitorDsl` vals) is a Kotlin leaf of the same name. Both sides are read
 * by reflection, so a slot added on one door without its twin on the other is red here (the slot-name check
 * found eight missing on the script door, step 3, 2026-10-09).
 */
class KlangScriptIgnitorSlotCompletenessSpec : StringSpec({

    /** The `IgnitorDsl` vals of a Kotlin `object`, by name: its flat leaves, not its group objects. */
    fun flatLeavesOf(holder: Class<*>): Map<String, IgnitorDsl> =
        holder.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && IgnitorDsl::class.java.isAssignableFrom(it.type) }
            .associate { field ->
                field.isAccessible = true
                field.name to field.get(null) as IgnitorDsl
            }

    fun kotlinFlatLeaves(): Map<String, IgnitorDsl> = flatLeavesOf(IgnitorDsl.Slots::class.java)

    "every flat IgnitorDsl.Slots leaf is Ignitor.slot.<name> on the script door, the same object" {
        val leaves = kotlinFlatLeaves()

        // Not vacuous: the reflection finds the leaves this check is about, the eight it added included.
        leaves.keys shouldContainAll listOf(
            "analog", "pregain", "feedback", "bipolar", "chaos", "color", "declick", "leak", "octaves", "persistence", "tail",
        )

        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        for ((name, leaf) in leaves) {
            withClue("Ignitor.slot.$name") {
                val value = (engine.execute("Ignitor.slot.$name") as NativeObjectValue<*>).value
                value.shouldBeSameInstanceAs(leaf)
            }
        }
    }

    "every flat Ignitor.slot property of the script door is a Kotlin IgnitorDsl.Slots leaf of the same name" {
        val leaves = kotlinFlatLeaves()
        val scriptFlat = flatLeavesOf(KlangScriptIgnitorSlots::class.java)

        // Not vacuous: the script door's own flat properties are found, the eight added in step 3 included.
        scriptFlat.keys shouldContainAll listOf("analog", "pregain", "declick", "leak", "tail")

        for ((name, value) in scriptFlat) {
            withClue("Ignitor.slot.$name") {
                val leaf = leaves[name] ?: error("Ignitor.slot.$name has no IgnitorDsl.Slots leaf of that name")
                value.shouldBeSameInstanceAs(leaf)
            }
        }
    }
})
