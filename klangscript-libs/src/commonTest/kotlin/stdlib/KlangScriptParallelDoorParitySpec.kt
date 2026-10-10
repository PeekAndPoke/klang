/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.parallel
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * `parallel(...)` on both DSLs, both doors (`docs/tasks/in-progress/parallel-serial-bands.md`): the script door and the Kotlin
 * door build the same value; no branch is the receiver itself, one branch is its stages in place, two or more are one
 * `Parallel` stage holding the branches in the order written; every branch starts from an empty builder; and a broken
 * branch is a script error that names it.
 */
class KlangScriptParallelDoorParitySpec : StringSpec({

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

    fun errorOf(block: () -> Unit): String = shouldThrowAny { block() }.message ?: ""

    val start = KatalystBuilder(KatalystDsl(emptyList()))

    // ── Ignitor ──────────────────────────────────────────────────────────────────────────────────

    "Ignitor: two branches are one Parallel node, in the order written, every branch reading the SAME input" {
        val out = ignitor("Ignitor.saw().parallel(x => x.mul(2), x => x.mul(3))") as IgnitorDsl.Parallel
        val first = out.branches[0] as IgnitorDsl.Times
        val second = out.branches[1] as IgnitorDsl.Times

        first.right shouldBe c(2.0)
        second.right shouldBe c(3.0)
        // One instance, so the engine builds it once (identity, not only equality).
        (first.left === second.left) shouldBe true
    }

    "Ignitor: the Kotlin door builds the same node as the script door" {
        val saw = ignitor("Ignitor.saw()")

        saw.parallel({ it.mul(2.0) }, { it.mul(3.0) }) shouldBe ignitor("Ignitor.saw().parallel(x => x.mul(2), x => x.mul(3))")
    }

    "Ignitor: with no branch, parallel() is the signal itself; with one, that branch, on both doors" {
        val saw = ignitor("Ignitor.saw()")

        ignitor("Ignitor.saw().parallel()") shouldBe saw
        saw.parallel() shouldBe saw
        ignitor("Ignitor.saw().parallel(x => x.mul(2))") shouldBe ignitor("Ignitor.saw().mul(2)")
        saw.parallel({ it.mul(2.0) }) shouldBe saw.mul(2.0)
    }

    "Ignitor: a broken branch is named" {
        errorOf { ignitor("Ignitor.saw().parallel(x => x, x => { x.mul(2) })") } shouldContain
                "branch 2 of Ignitor parallel returned nothing"
        errorOf { ignitor("Ignitor.saw().parallel(x => 3)") } shouldContain "branch 1 of Ignitor parallel must return signal"
        errorOf { ignitor("Ignitor.saw().parallel(null)") } shouldContain "branch 1 of Ignitor parallel is null"
    }

    // ── Katalyst ─────────────────────────────────────────────────────────────────────────────────

    "Katalyst: two branches are one Parallel stage, in the order written, each from an empty builder" {
        val expected = KatalystDsl.of(
            KatalystStageDsl.Gain(c(2.0)),
            KatalystStageDsl.Parallel(
                branches = listOf(
                    KatalystDsl(emptyList()),
                    KatalystDsl.of(KatalystStageDsl.Gain(c(0.5)), KatalystStageDsl.Gain(c(0.25))),
                ),
            ),
        )

        katalyst("Katalyst(k => k.gain(2.0).parallel(b => b, b => b.gain(0.5).gain(0.25)))") shouldBe expected
    }

    "Katalyst: the Kotlin door builds the same chain as the script door" {
        val kotlin = start.gain(2.0).parallel({ it }, { it.gain(0.5).gain(0.25) }).node

        kotlin shouldBe katalyst("Katalyst(k => k.gain(2.0).parallel(b => b, b => b.gain(0.5).gain(0.25)))")
    }

    "Katalyst: with no branch, parallel() is the chain itself, on both doors" {
        katalyst("Katalyst(k => k.gain(1.4).parallel())") shouldBe katalyst("Katalyst(k => k.gain(1.4))")

        val builder = start.gain(1.4)
        builder.parallel() shouldBe builder
    }

    "Katalyst: one branch is its stages in place, on both doors" {
        katalyst("Katalyst(k => k.gain(1.4).parallel(b => b.reverb(0.25, 7)))") shouldBe
                katalyst("Katalyst(k => k.gain(1.4).reverb(0.25, 7))")

        start.gain(1.4).parallel({ it.reverb(0.25, 7.0) }) shouldBe start.gain(1.4).reverb(0.25, 7.0)
    }

    "Katalyst: a group of stages stored in a let is a branch, and parallel nests" {
        katalyst(
            """
            let crunch = b => b.distort(0.5).gain(0.25)
            Katalyst(k => k.parallel(b => b, b => b.parallel(crunch, b => b)))
            """.trimIndent()
        ) shouldBe KatalystDsl.of(
            KatalystStageDsl.Parallel(
                branches = listOf(
                    KatalystDsl(emptyList()),
                    KatalystDsl.of(
                        KatalystStageDsl.Parallel(
                            branches = listOf(
                                start.distort(0.5).gain(0.25).node,
                                KatalystDsl(emptyList()),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    // ── A broken branch is a script error that names it ────────────────────────────────────────

    "a branch that returns nothing, something else or is null is named" {
        errorOf { katalyst("Katalyst(k => k.parallel(b => b, b => { b.gain(1.4) }))") } shouldContain
                "branch 2 of Katalyst parallel returned nothing"
        errorOf { katalyst("Katalyst(k => k.parallel(b => 3))") } shouldContain
                "branch 1 of Katalyst parallel must return builder"
        errorOf { katalyst("Katalyst(k => k.parallel(null))") } shouldContain "branch 1 of Katalyst parallel is null"
        errorOf { katalyst("Katalyst(k => k.parallel(true))") } shouldContain "expected a function, got a boolean"
    }
})
