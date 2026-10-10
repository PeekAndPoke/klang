/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.blend
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * `blend(wet, branch)` on both DSLs, both doors (`docs/tasks/in-progress/parallel-serial-bands.md` step 5): a
 * `parallel` of the dry times `1 - wet` and the branch times `wet`, the linear law, with the same value from the script
 * door and the Kotlin door. The sum itself is `parallel`'s, rendered in `IgnitorParallelSpec` and
 * `KatalystParallelEffectSpec`; this spec pins the shape the door writes.
 */
class KlangScriptBlendDoorParitySpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun katalyst(code: String): KatalystDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<KatalystDsl>()!!
    }

    fun errorOf(block: () -> Unit): String = shouldThrowAny { block() }.message ?: ""

    fun c(v: Double) = IgnitorDsl.Constant(v)

    // ── Ignitor ──────────────────────────────────────────────────────────────────────────────────

    "Ignitor: the dry times 1 - wet and the branch times wet, side by side, reading one input" {
        val out = ignitor("Ignitor.saw().blend(0.25, y => y.mul(4))").shouldBeInstanceOf<IgnitorDsl.Parallel>()
        val dry = out.branches[0].shouldBeInstanceOf<IgnitorDsl.Times>()
        val wet = out.branches[1].shouldBeInstanceOf<IgnitorDsl.Times>()

        dry.right shouldBe c(0.75)
        wet.right shouldBe c(0.25)
        wet.left.shouldBeInstanceOf<IgnitorDsl.Times>().right shouldBe c(4.0)
        (dry.left === wet.left.shouldBeInstanceOf<IgnitorDsl.Times>().left) shouldBe true
    }

    "Ignitor: the Kotlin door builds the same node as the script door" {
        val saw = ignitor("Ignitor.saw()")

        saw.blend(0.25) { it.mul(4.0) } shouldBe ignitor("Ignitor.saw().blend(0.25, y => y.mul(4))")
    }

    "Ignitor: a slot as wet moves the blend: the dry share is 1 - the slot" {
        val out = ignitor("""Ignitor.saw().blend(Ignitor.param("mix", 0.3), y => y.mul(4))""").shouldBeInstanceOf<IgnitorDsl.Parallel>()
        val dry = out.branches[0].shouldBeInstanceOf<IgnitorDsl.Times>().right.shouldBeInstanceOf<IgnitorDsl.Minus>()

        dry.left shouldBe c(1.0)
        dry.right.shouldBeInstanceOf<IgnitorDsl.Param>().name shouldBe "mix"
    }

    "Ignitor: a wet that is not finite is 0, the dry signal, on both doors and both Kotlin overloads" {
        val saw = ignitor("Ignitor.saw()")
        val dryOnly = saw.blend(0.0) { it.mul(4.0) }

        saw.blend(Double.NaN) { it.mul(4.0) } shouldBe dryOnly
        saw.blend(IgnitorDsl.Constant(Double.POSITIVE_INFINITY)) { it.mul(4.0) } shouldBe dryOnly
        ignitor("Ignitor.saw().blend(Math.sqrt(-1), y => y.mul(4))") shouldBe dryOnly

        val out = dryOnly.shouldBeInstanceOf<IgnitorDsl.Parallel>()

        out.branches[0].shouldBeInstanceOf<IgnitorDsl.Times>().right shouldBe c(1.0)
        out.branches[1].shouldBeInstanceOf<IgnitorDsl.Times>().right shouldBe c(0.0)
    }

    // ── Katalyst ─────────────────────────────────────────────────────────────────────────────────

    "Katalyst: a parallel of the bus at 1 - wet and the branch's stages at wet" {
        katalyst("Katalyst(k => k.blend(0.3, b => b.distort(0.6)))") shouldBe KatalystDsl.of(
            KatalystStageDsl.Parallel(
                branches = listOf(
                    KatalystDsl.of(KatalystStageDsl.Gain(c(0.7))),
                    KatalystDsl.of(KatalystStageDsl.Distort(amount = c(0.6)), KatalystStageDsl.Gain(c(0.3))),
                ),
            ),
        )
    }

    "Katalyst: the Kotlin door builds the same chain as the script door; a wet that is not finite is 0" {
        val empty = KatalystBuilder(KatalystDsl(emptyList()))

        empty.blend(0.3) { it.distort(0.6) }.node shouldBe katalyst("Katalyst(k => k.blend(0.3, b => b.distort(0.6)))")

        val stage = empty.blend(Double.NaN) { it.distort(0.6) }.node.stages.single().shouldBeInstanceOf<KatalystStageDsl.Parallel>()

        stage.branches[0].stages.single() shouldBe KatalystStageDsl.Gain(c(1.0))
        stage.branches[1].stages.last() shouldBe KatalystStageDsl.Gain(c(0.0))
    }

    // ── A broken branch is named ─────────────────────────────────────────────────────────────────

    "Katalyst: a param or a signal as wet is a script error that says wet is a plain number here" {
        errorOf { katalyst("""Katalyst(k => k.blend(Katalyst.param("mix", 0.3), b => b.distort(0.6)))""") } shouldContain
                "wet is a plain number here"
    }

    "a branch that returns nothing or something else is named, on both DSLs" {
        errorOf { ignitor("Ignitor.saw().blend(0.5, y => { y.mul(2) })") } shouldContain "branch 1 of Ignitor blend returned nothing"
        errorOf { katalyst("Katalyst(k => k.blend(0.5, b => 3))") } shouldContain "branch 1 of Katalyst blend must return builder"
    }
})
