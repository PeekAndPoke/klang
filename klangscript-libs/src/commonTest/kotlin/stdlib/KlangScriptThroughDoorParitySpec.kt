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
import io.peekandpoke.klang.audio_bridge.through
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * `through(...)` on both DSLs, both doors (`docs/tasks-archive/2026-10/20261002-through-signal-chains.md`): the script
 * door, the Kotlin door and the hand-nested calls build the same value, the stages run first to last, with no stage
 * `through()` is the receiver itself, and a broken stage is a script error that names it.
 *
 * The stages are chosen so that their order shows in the result: `mul` then `plus` is not `plus` then `mul`, and two
 * different Katalyst stages appended in a different order are a different chain.
 */
class KlangScriptThroughDoorParitySpec : StringSpec({

    fun osc(code: String): IgnitorDsl {
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

    // ── Osc ──────────────────────────────────────────────────────────────────────────────────────

    "Osc: the script door runs the stages first to last, as the nested calls" {
        val saw = osc("Osc.saw()")
        val expected = IgnitorDsl.Plus(left = IgnitorDsl.Times(left = saw, right = c(2.0)), right = c(3.0))

        osc("Osc.saw().through(x => x.mul(2), x => x.plus(3))") shouldBe expected
        osc("Osc.saw().mul(2).plus(3)") shouldBe expected
        // the other order is another tree: the order is the stages', not commutative by accident
        osc("Osc.saw().through(x => x.plus(3), x => x.mul(2))") shouldNotBe expected
    }

    "Osc: the Kotlin door builds the same node as the script door" {
        val saw = osc("Osc.saw()")
        val kotlin = saw.through(
            { IgnitorDsl.Times(left = it, right = c(2.0)) },
            { IgnitorDsl.Plus(left = it, right = c(3.0)) },
        )

        kotlin shouldBe osc("Osc.saw().through(x => x.mul(2), x => x.plus(3))")
    }

    "Osc: with no stage, through() is the signal itself, on both doors" {
        val saw = osc("Osc.saw()")

        osc("Osc.saw().through()") shouldBe saw
        saw.through() shouldBe saw
    }

    "Osc: a rig stored in a let is a stage, and rigs nest" {
        osc(
            """
            let pedal = x => x.mul(2)
            let cab   = x => x.plus(3)
            let rig   = x => x.through(pedal, cab)
            Osc.saw().through(rig, x => x.mul(4))
            """.trimIndent()
        ) shouldBe osc("Osc.saw().mul(2).plus(3).mul(4)")
    }

    // ── Katalyst ─────────────────────────────────────────────────────────────────────────────────

    "Katalyst: the script door runs the stages first to last, as the nested calls" {
        val nested = katalyst("Katalyst(k => k.reverb(0.25, 7).gain(1.4))")

        katalyst("Katalyst(k => k.through(k => k.reverb(0.25, 7), k => k.gain(1.4)))") shouldBe nested
        katalyst("Katalyst(k => k.through(k => k.gain(1.4), k => k.reverb(0.25, 7)))") shouldNotBe nested
        nested.stages.map { it::class } shouldBe listOf(KatalystStageDsl.Reverb::class, KatalystStageDsl.Gain::class)
    }

    "Katalyst: the Kotlin door builds the same chain as the script door" {
        val kotlin = KatalystBuilder(KatalystDsl(emptyList()))
            .through({ it.reverb(0.25, 7.0) }, { it.gain(1.4) })
            .node

        kotlin shouldBe katalyst("Katalyst(k => k.through(k => k.reverb(0.25, 7), k => k.gain(1.4)))")
    }

    "Katalyst: with no stage, through() is the chain itself, on both doors" {
        katalyst("Katalyst(k => k.gain(1.4).through())") shouldBe katalyst("Katalyst(k => k.gain(1.4))")

        val builder = KatalystBuilder(KatalystDsl(emptyList())).gain(1.4)
        builder.through() shouldBe builder
    }

    "Katalyst: a group of stages stored in a let is a stage" {
        katalyst(
            """
            let hall    = k => k.reverb(0.25, 7, 4500)
            let ceiling = k => k.gain(1.4).limiter(threshold = -3.0)
            Katalyst(k => k.through(hall, ceiling))
            """.trimIndent()
        ) shouldBe katalyst("Katalyst(k => k.reverb(0.25, 7, 4500).gain(1.4).limiter(threshold = -3.0))")
    }

    // ── A broken stage is a script error that names it (review round 1, 2026-10-02) ───────────────

    fun errorOf(block: () -> Unit): String = shouldThrowAny { block() }.message ?: ""

    "a stage that returns nothing (a block body without return) is named, on both DSLs" {
        errorOf { osc("Osc.saw().through(x => x.mul(2), x => { x.plus(3) })") } shouldContain
                "stage 2 of Ignitor through returned nothing"
        errorOf { katalyst("Katalyst(k => k.through(k => { k.gain(1.4) }))") } shouldContain
                "stage 1 of Katalyst through returned nothing"
    }

    "a stage that returns something else is named, on both DSLs" {
        errorOf { osc("Osc.saw().through(x => 3)") } shouldContain "stage 1 of Ignitor through must return signal"
        errorOf { katalyst("Katalyst(k => k.through(k => k.gain(1.4), k => 3))") } shouldContain
                "stage 2 of Katalyst through must return builder"
    }

    "a stage that is not a function is a script error, on both DSLs" {
        errorOf { osc("Osc.saw().through(x => x, 0.5)") } shouldContain "expected a function, got a number"
        errorOf { katalyst("Katalyst(k => k.through(true))") } shouldContain "expected a function, got a boolean"
        errorOf { osc("Osc.saw().through(null)") } shouldContain "stage 1 of Ignitor through is null"
    }
})
