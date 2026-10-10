/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.serial
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * `serial(...)` on both DSLs, both doors (built as `through` in `docs/tasks-archive/2026-10/20261002-through-signal-chains.md`,
 * renamed 2026-10-10): the script door, the Kotlin door and the hand-nested calls build the same value, the stages run
 * first to last, with no stage `serial()` is the receiver itself, and a broken stage is a script error that names it.
 *
 * The stages are chosen so that their order shows in the result: `mul` then `plus` is not `plus` then `mul`, and two
 * different Katalyst stages appended in a different order are a different chain.
 */
class KlangScriptSerialDoorParitySpec : StringSpec({

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

    fun c(v: Double) = IgnitorDsl.Constant(v)

    // ── Ignitor ──────────────────────────────────────────────────────────────────────────────────────

    "Ignitor: the script door runs the stages first to last, as the nested calls" {
        val saw = ignitor("Ignitor.saw()")
        val expected = IgnitorDsl.Plus(left = IgnitorDsl.Times(left = saw, right = c(2.0)), right = c(3.0))

        ignitor("Ignitor.saw().serial(x => x.mul(2), x => x.plus(3))") shouldBe expected
        ignitor("Ignitor.saw().mul(2).plus(3)") shouldBe expected
        // the other order is another tree: the order is the stages', not commutative by accident
        ignitor("Ignitor.saw().serial(x => x.plus(3), x => x.mul(2))") shouldNotBe expected
    }

    "Ignitor: the Kotlin door builds the same node as the script door" {
        val saw = ignitor("Ignitor.saw()")
        val kotlin = saw.serial(
            { IgnitorDsl.Times(left = it, right = c(2.0)) },
            { IgnitorDsl.Plus(left = it, right = c(3.0)) },
        )

        kotlin shouldBe ignitor("Ignitor.saw().serial(x => x.mul(2), x => x.plus(3))")
    }

    "Ignitor: with no stage, serial() is the signal itself, on both doors" {
        val saw = ignitor("Ignitor.saw()")

        ignitor("Ignitor.saw().serial()") shouldBe saw
        saw.serial() shouldBe saw
    }

    "Ignitor: a rig stored in a let is a stage, and rigs nest" {
        ignitor(
            """
            let pedal = x => x.mul(2)
            let cab   = x => x.plus(3)
            let rig   = x => x.serial(pedal, cab)
            Ignitor.saw().serial(rig, x => x.mul(4))
            """.trimIndent()
        ) shouldBe ignitor("Ignitor.saw().mul(2).plus(3).mul(4)")
    }

    // ── Katalyst ─────────────────────────────────────────────────────────────────────────────────

    "Katalyst: the script door runs the stages first to last, as the nested calls" {
        val nested = katalyst("Katalyst(k => k.reverb(0.25, 7).gain(1.4))")

        katalyst("Katalyst(k => k.serial(k => k.reverb(0.25, 7), k => k.gain(1.4)))") shouldBe nested
        katalyst("Katalyst(k => k.serial(k => k.gain(1.4), k => k.reverb(0.25, 7)))") shouldNotBe nested
        nested.stages.map { it::class } shouldBe listOf(KatalystStageDsl.Reverb::class, KatalystStageDsl.Gain::class)
    }

    "Katalyst: the Kotlin door builds the same chain as the script door" {
        val kotlin = KatalystBuilder(KatalystDsl(emptyList()))
            .serial({ it.reverb(0.25, 7.0) }, { it.gain(1.4) })
            .node

        kotlin shouldBe katalyst("Katalyst(k => k.serial(k => k.reverb(0.25, 7), k => k.gain(1.4)))")
    }

    "Katalyst: with no stage, serial() is the chain itself, on both doors" {
        katalyst("Katalyst(k => k.gain(1.4).serial())") shouldBe katalyst("Katalyst(k => k.gain(1.4))")

        val builder = KatalystBuilder(KatalystDsl(emptyList())).gain(1.4)
        builder.serial() shouldBe builder
    }

    "Katalyst: a group of stages stored in a let is a stage" {
        katalyst(
            """
            let hall    = k => k.reverb(0.25, 7, 4500)
            let ceiling = k => k.gain(1.4).limiter(threshold = -3.0)
            Katalyst(k => k.serial(hall, ceiling))
            """.trimIndent()
        ) shouldBe katalyst("Katalyst(k => k.reverb(0.25, 7, 4500).gain(1.4).limiter(threshold = -3.0))")
    }

    // ── A broken stage is a script error that names it (review round 1, 2026-10-02) ───────────────

    fun errorOf(block: () -> Unit): String = shouldThrowAny { block() }.message ?: ""

    "a stage that returns nothing (a block body without return) is named, on both DSLs" {
        errorOf { ignitor("Ignitor.saw().serial(x => x.mul(2), x => { x.plus(3) })") } shouldContain
                "stage 2 of Ignitor serial returned nothing"
        errorOf { katalyst("Katalyst(k => k.serial(k => { k.gain(1.4) }))") } shouldContain
                "stage 1 of Katalyst serial returned nothing"
    }

    "a stage that returns something else is named, on both DSLs" {
        errorOf { ignitor("Ignitor.saw().serial(x => 3)") } shouldContain "stage 1 of Ignitor serial must return signal"
        errorOf { katalyst("Katalyst(k => k.serial(k => k.gain(1.4), k => 3))") } shouldContain
                "stage 2 of Katalyst serial must return builder"
    }

    "a stage that is not a function is a script error, on both DSLs" {
        errorOf { ignitor("Ignitor.saw().serial(x => x, 0.5)") } shouldContain "expected a function, got a number"
        errorOf { katalyst("Katalyst(k => k.serial(true))") } shouldContain "expected a function, got a boolean"
        errorOf { ignitor("Ignitor.saw().serial(null)") } shouldContain "stage 1 of Ignitor serial is null"
    }
})
