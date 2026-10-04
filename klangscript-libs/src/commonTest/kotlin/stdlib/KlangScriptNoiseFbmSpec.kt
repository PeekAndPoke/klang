/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Dual-language equivalence for the fBm knobs (octaves + persistence) on perlin / berlin noise.
 * These knobs are exposed as named params (not chained config methods), so the spec exercises
 * both positional and named-argument forms against the Kotlin builder.
 */
class KlangScriptNoiseFbmSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    fun perlin() = KlangScriptIgnitor.perlin() as IgnitorDsl.PerlinNoise
    fun berlin() = KlangScriptIgnitor.berlin() as IgnitorDsl.BerlinNoise

    // --- perlin -------------------------------------------------------------

    "Ignitor.perlin() — KlangScript == Kotlin builder (octaves=1 perf-neutral default)" {
        ks("Ignitor.perlin()") shouldBe perlin()
    }

    "Ignitor.perlin(octaves = 4) — named arg skips the leading rate param" {
        ks("Ignitor.perlin(octaves = 4)") shouldBe perlin().copy(octaves = IgnitorDsl.Constant(4.0))
    }

    "Ignitor.perlin(persistence = 0.7) — named arg skips rate + octaves" {
        ks("Ignitor.perlin(persistence = 0.7)") shouldBe perlin().copy(persistence = IgnitorDsl.Constant(0.7))
    }

    "Ignitor.perlin(2, 4, 0.6) — positional rate/octaves/persistence" {
        ks("Ignitor.perlin(2, 4, 0.6)") shouldBe perlin().copy(
            rate = IgnitorDsl.Constant(2.0),
            octaves = IgnitorDsl.Constant(4.0),
            persistence = IgnitorDsl.Constant(0.6),
        )
    }

    // --- berlin -------------------------------------------------------------

    "Ignitor.berlin() — KlangScript == Kotlin builder" {
        ks("Ignitor.berlin()") shouldBe berlin()
    }

    "Ignitor.berlin(octaves = 5, persistence = 0.4) — two named args" {
        ks("Ignitor.berlin(octaves = 5, persistence = 0.4)") shouldBe berlin().copy(
            octaves = IgnitorDsl.Constant(5.0),
            persistence = IgnitorDsl.Constant(0.4),
        )
    }
})
