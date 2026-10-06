/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.convertToKotlin
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * A generated door left with fewer arguments than it has parameters still gets its Kotlin defaults.
 *
 * Two paths reach a door short. A script call goes through the interpreter, whose parameter specs fill
 * every omitted optional from the default thunk. A native function handed to a Kotlin function slot does
 * not: the slot calls it with its own arity (`convertToKotlin`, the `NativeFunctionValue` branch), so the
 * generated registration itself must supply what is missing. Until 2026-10-06 an arity dispatch on
 * `args.size` did that by calling the Kotlin function without the argument; since then the call pastes
 * the default literal. `binaryN(n, bits = 16)` makes the default visible: the pattern has `bits` steps.
 */
class NativeFunctionSlotDefaultsSpec : StringSpec({

    fun stepsOf(pattern: SprudelPattern): Int = pattern.queryArc(0.0, 1.0).count { it.isOnset }

    "a native function in a one-argument Kotlin slot gets the Kotlin default of the parameter it leaves out" {
        val engine = klangScript { registerLibrary(sprudelLib) }
        val binaryNValue = engine.execute("import * from \"sprudel\"\nbinaryN")

        @Suppress("UNCHECKED_CAST")
        val slot = binaryNValue.convertToKotlin(Function1::class) as (Any?) -> Any?
        val viaSlot = slot(5.0) as SprudelPattern

        stepsOf(viaSlot) shouldBe stepsOf(binaryN(5))
        stepsOf(viaSlot) shouldBe 16
    }

    "a native function in a one-argument Kotlin slot gets null for an omitted `= null` parameter" {
        // `sndSuperSaw(voices = null, spread = null)`: the slot passes `voices`, `spread` is read by `optArg`
        // past the end of the arguments and must be null, as the Kotlin call without it.
        val engine = klangScript { registerLibrary(sprudelLib) }
        val sndSuperSawValue = engine.execute("import * from \"sprudel\"\nsndSuperSaw")

        @Suppress("UNCHECKED_CAST")
        val slot = sndSuperSawValue.convertToKotlin(Function1::class) as (Any?) -> Any?
        val viaSlot = slot(4.0) as PatternMapperFn

        // Every pattern gets its own patternId; the rest of the voice data must be equal.
        fun dataOf(mapper: PatternMapperFn) = mapper(note("c e")).queryArc(0.0, 1.0).map { it.data.copy(patternId = null) }

        dataOf(viaSlot) shouldBe dataOf(sndSuperSaw(4.0))
    }

    "a positional script call that leaves the parameter out gets the same default" {
        val engine = klangScript { registerLibrary(sprudelLib) }
        val viaScript = engine.execute("import * from \"sprudel\"\nbinaryN(5)").value as SprudelPattern

        stepsOf(viaScript) shouldBe 16
    }
})
