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
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * `bands(...)` on both DSLs, both doors (`docs/tasks/in-progress/parallel-serial-bands.md` step 4): the script door and
 * the Kotlin door build the same value, and the shape of the split: no cut is the one band in place, one `band()` is
 * its processor, two on one band are summed, an untouched band is only its crossover, and a broken processor is named.
 * What the split SOUNDS like (flat when untouched, a band acting on its range) is `BandsCrossoverSpec`.
 */
class KlangScriptBandsDoorParitySpec : StringSpec({

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

    val empty = KatalystBuilder(KatalystDsl(emptyList()))

    // ── Ignitor ──────────────────────────────────────────────────────────────────────────────────

    "Ignitor: the Kotlin door builds the same split as the script door" {
        val saw = ignitor("Ignitor.saw()")
        val script = ignitor("Ignitor.saw().bands(b => b.band(x => x.mul(2)).cut(200).cut(2000).band(x => x.mul(3)))")
        val kotlin = KlangScriptIgnitorExtensions.bands(saw) {
            it.band { x -> x.mul(2.0) }.cut(200.0).cut(2000.0).band { x -> x.mul(3.0) }
        }

        kotlin shouldBe script
    }

    "Ignitor: no configure, or no cut, is the one band: the signal, or its processor" {
        val saw = ignitor("Ignitor.saw()")

        ignitor("Ignitor.saw().bands()") shouldBe saw
        ignitor("Ignitor.saw().bands(b => b.band(x => x.mul(2)))") shouldBe ignitor("Ignitor.saw().mul(2)")
    }

    "Ignitor: three bands are one Parallel of three; two processors on one band are summed" {
        val split = ignitor("Ignitor.saw().bands(b => b.cut(200).band(x => x.mul(2)).band(x => x.mul(3)).cut(2000))")
            .shouldBeInstanceOf<IgnitorDsl.Parallel>()

        split.branches.size shouldBe 3
        split.branches[1].shouldBeInstanceOf<IgnitorDsl.Parallel>().branches.size shouldBe 2
    }

    "Ignitor: the bands share the split tree by identity: a high side is one node for the band and the split above it" {
        val split = ignitor("Ignitor.saw().bands(b => b.cut(200).cut(2000))").shouldBeInstanceOf<IgnitorDsl.Parallel>()
        val middle = split.branches[1].shouldBeInstanceOf<IgnitorDsl.Eq>()
        val top = split.branches[2].shouldBeInstanceOf<IgnitorDsl.Eq>()

        (middle.inner === top.inner) shouldBe true
    }

    // ── Katalyst ─────────────────────────────────────────────────────────────────────────────────

    "Katalyst: the Kotlin door builds the same chain as the script door" {
        val script = katalyst("Katalyst(k => k.bands(b => b.cut(150).band(m => m.distort(0.15)).cut(5000)).gain(1.2))")
        val kotlin = empty.bands { it.cut(150.0).band { m -> m.distort(0.15) }.cut(5000.0) }.gain(1.2).node

        kotlin shouldBe script
    }

    "Katalyst: no configure, or no cut, is the one band, its stages in place" {
        katalyst("Katalyst(k => k.gain(1.2).bands())") shouldBe katalyst("Katalyst(k => k.gain(1.2))")
        katalyst("Katalyst(k => k.bands(b => b.band(m => m.gain(0.5))))") shouldBe katalyst("Katalyst(k => k.gain(0.5))")
    }

    "Katalyst: an untouched band is its crossover only; the low band of three carries the upper cut's all-pass" {
        val stage = katalyst("Katalyst(k => k.bands(b => b.cut(200).cut(2000)))").stages.single()
            .shouldBeInstanceOf<KatalystStageDsl.Parallel>()
        val low = stage.branches[0].stages
        val high = stage.branches[2].stages

        low.size shouldBe 2
        low[0].shouldBeInstanceOf<KatalystStageDsl.Eq>().sections.size shouldBe 2
        // The upper cut's all-pass: one raw tap at gain -2, in an EQ of its own (a tap reads its EQ's input).
        low[1].shouldBeInstanceOf<KatalystStageDsl.Eq>().sections.single()
            .shouldBeInstanceOf<IgnitorDsl.EqSection.RawTap>().gain shouldBe IgnitorDsl.Constant(-2.0)
        high.single().shouldBeInstanceOf<KatalystStageDsl.Eq>().sections.size shouldBe 4
    }

    // ── A broken processor is named ─────────────────────────────────────────────────────────────

    "a processor that returns nothing or something else is named with its band, on both DSLs" {
        errorOf { ignitor("Ignitor.saw().bands(b => b.cut(200).band(x => { x.mul(2) }))") } shouldContain
                "processor 1 of band 2 of Ignitor bands returned nothing"
        errorOf { katalyst("Katalyst(k => k.bands(b => b.band(m => 3)))") } shouldContain
                "processor 1 of band 1 of Katalyst bands must return builder"
    }
})
