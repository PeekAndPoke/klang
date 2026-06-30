/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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
 * Dual-language equivalence for the typed supersaw DSL (Phase 2).
 *
 * Each case expresses the SAME thing two ways — as KlangScript source run through the
 * full engine (parse → interpret → native interop), and as the Kotlin builder + data
 * class `.copy()` — then asserts the resulting [IgnitorDsl.SuperSaw] objects are
 * structurally equal. One `shouldBe` validates the whole binding chain: that each
 * fluent config method binds to the right field, in the right order, with the right
 * coercion (number → [IgnitorDsl.Constant]) and defaults.
 *
 * Comparing against `.copy()` (rather than re-calling the extension functions) makes the
 * check independent: a bug where e.g. `.spreadPower()` wrote `sideAtten` would be caught.
 *
 * This is the template every other typed oscillator subtype will copy.
 */
class KlangScriptSuperSawSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    // Kotlin side: the same builder the KlangScript interpreter dispatches `Osc.supersaw()` to.
    fun superSaw() = KlangScriptOsc.supersaw()

    // ── default ──────────────────────────────────────────────────────────────────

    "Osc.supersaw() — KlangScript == Kotlin builder" {
        ks("Osc.supersaw()") shouldBe superSaw()
    }

    // ── audio-rate params (number → Constant) ────────────────────────────────────

    "freq(220)" {
        ks("Osc.supersaw().freq(220)") shouldBe superSaw().copy(freq = IgnitorDsl.Constant(220.0))
    }

    "voices(9)" {
        ks("Osc.supersaw().voices(9)") shouldBe superSaw().copy(voices = IgnitorDsl.Constant(9.0))
    }

    "spread(0.3)" {
        ks("Osc.supersaw().spread(0.3)") shouldBe superSaw().copy(spread = IgnitorDsl.Constant(0.3))
    }

    "analog(5.0)" {
        ks("Osc.supersaw().analog(5.0)") shouldBe superSaw().copy(analog = IgnitorDsl.Constant(5.0))
    }

    // ── character knobs (plain Double scalars) ───────────────────────────────────

    "spreadPower(1.5)" {
        ks("Osc.supersaw().spreadPower(1.5)") shouldBe superSaw().copy(spreadPower = 1.5)
    }

    "sideAtten(0.25)" {
        ks("Osc.supersaw().sideAtten(0.25)") shouldBe superSaw().copy(sideAtten = 0.25)
    }

    "gainJitter(0.0)" {
        ks("Osc.supersaw().gainJitter(0.0)") shouldBe superSaw().copy(gainJitter = 0.0)
    }

    "centerJitter(1.0) maps to centerJitterScale" {
        ks("Osc.supersaw().centerJitter(1.0)") shouldBe superSaw().copy(centerJitterScale = 1.0)
    }

    // ── full chain ───────────────────────────────────────────────────────────────

    "every typed config method in one chain" {
        val code = "Osc.supersaw().freq(110).voices(11).spread(0.12).analog(4.0)" +
                ".spreadPower(1.4).sideAtten(0.2).gainJitter(0.1).centerJitter(0.6)"

        ks(code) shouldBe superSaw().copy(
            freq = IgnitorDsl.Constant(110.0),
            voices = IgnitorDsl.Constant(11.0),
            spread = IgnitorDsl.Constant(0.12),
            analog = IgnitorDsl.Constant(4.0),
            spreadPower = 1.4,
            sideAtten = 0.2,
            gainJitter = 0.1,
            centerJitterScale = 0.6,
        )
    }

    // ── base IgnitorDsl methods still chain off the narrowed subtype ─────────────

    "supersaw().lowpass(2000) — base wrapper applies to the narrowed subtype" {
        val dsl = ks("Osc.supersaw().spreadPower(1.5).lowpass(2000)")
        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        val inner = dsl.inner
        inner.shouldBeInstanceOf<IgnitorDsl.SuperSaw>()
        inner.spreadPower shouldBe 1.5
    }
})
