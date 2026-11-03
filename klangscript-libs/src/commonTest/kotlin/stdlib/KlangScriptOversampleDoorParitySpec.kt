/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.Ign
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.audio_bridge.oversampleAtLeast
import io.peekandpoke.klang.audio_bridge.oversampleBy
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The oversampling regions on both doors (KLANG-00214 (oversampling regions)). The Ignitor's (step 1):
 * `oversampleBy(factor, body)` and `oversampleAtLeast(factor, body)` build their node with the signal as `inner`, the
 * body applied to [IgnitorDsl.RegionInput] and the factor as written. The Katalyst's (step 3a): the same two words
 * append a stage holding the factor and the body's stages, the body given an empty builder. Each the same value from
 * the script door and the Kotlin door. What a region does is rendered in `OversampleRegionSpec` and
 * `KatalystRegionEffectSpec` (audio_be); this spec pins the shape the doors write.
 */
class KlangScriptOversampleDoorParitySpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun errorOf(block: () -> Unit): String = shouldThrowAny { block() }.message ?: ""

    fun c(v: Double) = IgnitorDsl.Constant(v)

    "oversampleBy: the node written out, the body on the region's input, from both doors" {
        val saw = ignitor("Ignitor.saw()")
        val expected = IgnitorDsl.OversampleBy(
            inner = saw,
            body = IgnitorDsl.RegionInput(up = 0).highpass(100.ign),
            factor = c(8.0),
        )

        saw.oversampleBy(8.ign) { it.highpass(100.ign) } shouldBe expected
        ignitor("Ignitor.saw().oversampleBy(8, y => y.highpass(100))") shouldBe expected
    }

    "oversampleAtLeast: the node written out, from both doors" {
        val saw = ignitor("Ignitor.saw()")
        val expected = IgnitorDsl.OversampleAtLeast(inner = saw, body = IgnitorDsl.RegionInput(up = 0), factor = c(4.0))

        saw.oversampleAtLeast(4.ign) { it } shouldBe expected
        ignitor("Ignitor.saw().oversampleAtLeast(4, y => y)") shouldBe expected
    }

    "the Kotlin door builds the same node as the script door, a distorting body and a slot as factor included" {
        val saw = ignitor("Ignitor.saw()")

        saw.oversampleBy(4.ign) { it.distort(0.3.ign, "tube").highpass(90.ign) } shouldBe
            ignitor("""Ignitor.saw().oversampleBy(4, y => y.distort(0.3, "tube").highpass(90))""")
        saw.oversampleAtLeast(Ign.param("os", 2.0)) { it.distort(0.3.ign, "tube") } shouldBe
            ignitor("""Ignitor.saw().oversampleAtLeast(Ignitor.param("os", 2), y => y.distort(0.3, "tube"))""")
    }

    "nesting: an inner region's input is its own, its signal the outer region's input" {
        val saw = ignitor("Ignitor.saw()")
        val expected = IgnitorDsl.OversampleBy(
            inner = saw,
            body = IgnitorDsl.OversampleBy(inner = IgnitorDsl.RegionInput(up = 0), body = IgnitorDsl.RegionInput(up = 0), factor = c(2.0)),
            factor = c(2.0),
        )

        ignitor("Ignitor.saw().oversampleBy(2, y => y.oversampleBy(2, z => z))") shouldBe expected
        saw.oversampleBy(2.ign) { y -> y.oversampleBy(2.ign) { it } } shouldBe expected
    }

    "nesting: the outer lambda's parameter inside a nested body points one region out, from both doors" {
        val saw = ignitor("Ignitor.saw()")
        val expected = IgnitorDsl.OversampleBy(
            inner = saw,
            body = IgnitorDsl.OversampleBy(
                inner = IgnitorDsl.Silence,
                body = IgnitorDsl.Plus(left = IgnitorDsl.RegionInput(up = 0), right = IgnitorDsl.RegionInput(up = 1)),
                factor = c(2.0),
            ),
            factor = c(2.0),
        )

        ignitor("Ignitor.saw().oversampleBy(2, y => Ignitor.silence().oversampleBy(2, z => z.plus(y)))") shouldBe expected
        saw.oversampleBy(2.ign) { y -> Ign.silence().oversampleBy(2.ign) { z -> z.plus(y) } } shouldBe expected
    }

    "a body that returns nothing or something else is named" {
        errorOf { ignitor("Ignitor.saw().oversampleBy(4, y => { y.highpass(100) })") } shouldContain "body 1 of Ignitor oversampleBy returned nothing"
        errorOf { ignitor("Ignitor.saw().oversampleAtLeast(4, y => 3)") } shouldContain "body 1 of Ignitor oversampleAtLeast"
    }

    // ── The Katalyst's regions (step 3a) ────────────────────────────────────────────────────────

    fun katalyst(code: String): KatalystDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<KatalystDsl>()!!
    }

    "Katalyst oversampleBy: the stage written out, the body's stages from an empty builder, from both doors" {
        val expected = KatalystDsl.of(
            KatalystStageDsl.Gain(gain = 0.5.kat),
            KatalystStageDsl.OversampleBy(
                factor = 4,
                body = KatalystDsl.of(KatalystStageDsl.Distort(amount = 0.3.kat, shape = DistortionShapes.indexOf("tube").toInt())),
            ),
        )

        katalyst("""Katalyst(k => k.gain(0.5).oversampleBy(4, r => r.distort(0.3, "tube")))""") shouldBe expected
        KatalystBuilder().gain(0.5.kat).oversampleBy(4) { it.distort(amount = 0.3.kat, shape = "tube") }.node shouldBe expected
    }

    "Katalyst oversampleAtLeast: the floor's stage, nested in a region, from both doors" {
        val expected = KatalystDsl.of(
            KatalystStageDsl.OversampleBy(
                factor = 2,
                body = KatalystDsl.of(KatalystStageDsl.OversampleAtLeast(factor = 8, body = KatalystDsl.of(KatalystStageDsl.Gain(gain = 2.0.kat)))),
            ),
        )

        katalyst("Katalyst(k => k.oversampleBy(2, r => r.oversampleAtLeast(8, s => s.gain(2))))") shouldBe expected
        KatalystBuilder().oversampleBy(2) { r -> r.oversampleAtLeast(8) { it.gain(2.0.kat) } }.node shouldBe expected
    }

    "a Katalyst region's body that returns nothing or something else is named" {
        errorOf { katalyst("Katalyst(k => k.oversampleBy(4, r => { r.gain(2) }))") } shouldContain "body 1 of Katalyst oversampleBy returned nothing"
        errorOf { katalyst("Katalyst(k => k.oversampleAtLeast(4, r => 3))") } shouldContain "body 1 of Katalyst oversampleAtLeast"
    }
})
