/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * The Katalyst arithmetic doors (`docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md`):
 * every door and alias, on the script door and
 * the Kotlin door, against the stage data class written out by hand, so a door wired to the wrong stage cannot look
 * right on both sides. The `min`/`max` crossing is part of the table: `max` ("at most") builds the `Min` stage.
 */
class KlangScriptKatalystArithmeticDoorParitySpec : StringSpec({

    fun ks(code: String): KatalystDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()

        return result.value.shouldBeInstanceOf<KatalystDsl>()
    }

    fun c(value: Double): KatalystKnobDsl = KatalystKnobDsl.Constant(value)

    val empty = KatalystBuilder(KatalystDsl(emptyList()))

    /** One door with an operand: its name, the parameter's name, the Kotlin door, and the stage it must build. */
    data class Door(
        val name: String,
        val param: String,
        val kotlin: (KatalystBuilder, Any) -> KatalystBuilder,
        val stage: (KatalystKnobDsl) -> KatalystStageDsl,
    )

    val doors = listOf(
        Door("plus", "other", { k, v -> k.plus(v) }, { KatalystStageDsl.Plus(other = it) }),
        Door("add", "other", { k, v -> k.add(v) }, { KatalystStageDsl.Plus(other = it) }),
        Door("minus", "other", { k, v -> k.minus(v) }, { KatalystStageDsl.Minus(other = it) }),
        Door("sub", "other", { k, v -> k.sub(v) }, { KatalystStageDsl.Minus(other = it) }),
        Door("times", "other", { k, v -> k.times(v) }, { KatalystStageDsl.Times(other = it) }),
        Door("mul", "other", { k, v -> k.mul(v) }, { KatalystStageDsl.Times(other = it) }),
        Door("div", "other", { k, v -> k.div(v) }, { KatalystStageDsl.Div(other = it) }),
        Door("pow", "exp", { k, v -> k.pow(v) }, { KatalystStageDsl.Pow(exp = it) }),
        Door("power", "exp", { k, v -> k.power(v) }, { KatalystStageDsl.Pow(exp = it) }),
        // The crossing: the doors are clamps, the stages are the mathematical min and max.
        Door("min", "other", { k, v -> k.min(v) }, { KatalystStageDsl.Max(other = it) }),
        Door("max", "other", { k, v -> k.max(v) }, { KatalystStageDsl.Min(other = it) }),
    )

    "every operand door builds its own stage, positionally and named, on both doors" {
        for (door in doors) {
            val expected = KatalystDsl.of(door.stage(c(0.375)))

            withClue("${door.name}, positional") { ks("Katalyst(k => k.${door.name}(0.375))") shouldBe expected }
            withClue("${door.name}, named") { ks("Katalyst(k => k.${door.name}(${door.param} = 0.375))") shouldBe expected }
            withClue("${door.name}, Kotlin") { door.kotlin(empty, 0.375).node shouldBe expected }
        }
    }

    "a Katalyst param is the operand's slot, on every operand door and both doors" {
        for (door in doors) {
            val expected = KatalystDsl.of(door.stage(KatalystKnobDsl.Param("trim", 0.8)))

            withClue("${door.name}, script") { ks("""Katalyst(k => k.${door.name}(Kat.param("trim", 0.8)))""") shouldBe expected }
            withClue("${door.name}, Kotlin") {
                door.kotlin(empty, KlangScriptKatalyst.param("trim", 0.8)).node shouldBe expected
            }
        }
    }

    "neg and negate are a multiply by -1, abs is the rectifier, on both doors" {
        val flip = KatalystDsl.of(KatalystStageDsl.Times(other = c(-1.0)))

        ks("Katalyst(k => k.neg())") shouldBe flip
        ks("Katalyst(k => k.negate())") shouldBe flip
        empty.neg().node shouldBe flip
        empty.negate().node shouldBe flip

        ks("Katalyst(k => k.abs())") shouldBe KatalystDsl.of(KatalystStageDsl.Abs)
        empty.abs().node shouldBe KatalystDsl.of(KatalystStageDsl.Abs)
    }

    "the arithmetic stages append in written order among the others" {
        ks("Katalyst(k => k.abs().distort(0.4).mul(0.5).max(0.9))") shouldBe KatalystDsl.of(
            KatalystStageDsl.Abs,
            KatalystStageDsl.Distort(amount = c(0.4)),
            KatalystStageDsl.Times(other = c(0.5)),
            KatalystStageDsl.Min(other = c(0.9)),
        )
    }
})
