/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.addons.notchf

/**
 * C1 parity pin (docs/plans/filter-unification.md): ONE default q = 0.707 for every filter
 * on EVERY surface. The default drifted once before (sprudel built q = 1.0 while the
 * ignitor built 0.707 — an audible difference nobody decided); this spec is the tripwire.
 */
class LangDefaultQSpec : StringSpec({

    val q = 0.707

    fun firstFilter(p: SprudelPattern?): FilterDef {
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        return events.first().data.toVoiceData().filters[0]
    }

    "sprudel Kotlin door: bare filters build q = 0.707" {
        (firstFilter(note("c").lpf(800)) as FilterDef.LowPass).q shouldBe q
        (firstFilter(note("c").hpf(200)) as FilterDef.HighPass).q shouldBe q
        (firstFilter(note("c").bpf(1000)) as FilterDef.BandPass).q shouldBe q
        (firstFilter(note("c").notchf(1000)) as FilterDef.Notch).q shouldBe q
    }

    "sprudel script door: bare filters build q = 0.707" {
        (firstFilter(SprudelPattern.compile("""note("c").lpf(800)""")) as FilterDef.LowPass).q shouldBe q
        (firstFilter(SprudelPattern.compile("""note("c").hpf(200)""")) as FilterDef.HighPass).q shouldBe q
        (firstFilter(SprudelPattern.compile("""note("c").bpf(1000)""")) as FilterDef.BandPass).q shouldBe q
        (firstFilter(SprudelPattern.compile("""note("c").notchf(1000)""")) as FilterDef.Notch).q shouldBe q
    }

    "ignitor DSL door: every filter/eq default is Constant(0.707)" {
        (IgnitorDsl.Lowpass(inner = IgnitorDsl.Constant(0.0), cutoffHz = IgnitorDsl.Constant(800.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.Highpass(inner = IgnitorDsl.Constant(0.0), cutoffHz = IgnitorDsl.Constant(200.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.Bandpass(inner = IgnitorDsl.Constant(0.0), cutoffHz = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.Notch(inner = IgnitorDsl.Constant(0.0), cutoffHz = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.EqSection.Bandpass(freqHz = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.EqSection.Notch(freqHz = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.EqSection.RawTap(freqHz = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
    }

    "ignitor scalar doors: band() and tap() default to q = 0.707" {
        val eq = IgnitorDsl.Sine().eq()
        val band = eq.band(1000.0).sections.last() as IgnitorDsl.EqSection.Bell
        band.q shouldBe IgnitorDsl.Constant(0.707)
        val tap = eq.tap(1000.0).sections.last() as IgnitorDsl.EqSection.RawTap
        tap.q shouldBe IgnitorDsl.Constant(0.707)
    }

    "KlangScript interpreter doors: Osc bandpass/notch/tap default to q = 0.707" {
        // The guard hole that let C1 miss the script stdlib doors once: drive the actual
        // interpreter, not just sprudel compilation.
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        val bp = eval("""Osc.saw().bandpass(1000)""") as IgnitorDsl.Bandpass
        bp.q shouldBe IgnitorDsl.Constant(0.707)

        val nt = eval("""Osc.saw().notch(1000)""") as IgnitorDsl.Notch
        nt.q shouldBe IgnitorDsl.Constant(0.707)

        val eqd = eval("""Osc.saw().eq().tap(850)""") as IgnitorDsl.Eq
        (eqd.sections.single() as IgnitorDsl.EqSection.RawTap).q shouldBe IgnitorDsl.Constant(0.707)
    }
})
