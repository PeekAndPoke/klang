/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.WireFilter
import io.peekandpoke.klang.sprudel.wireFilters

/**
 * C1 parity pin (docs/tasks-archive/2026-09/20260927-filter-unification.md): ONE default q = 0.707 for every filter
 * on EVERY surface. The default drifted once before (sprudel built q = 1.0 while the
 * ignitor built 0.707 — an audible difference nobody decided); this spec is the tripwire.
 */
class LangDefaultQSpec : StringSpec({

    val q = 0.707

    // Since phase 3 step 8 a bare filter sends no `q`: the instrument's `classic()` slot default builds it. So the
    // q a bare filter BUILDS is the wire's, or else that slot's default; both halves are asserted below.
    val slotQ = IgnitorDsl.Slots.let { s -> listOf(s.lpf.q, s.hpf.q, s.bpf.q, s.notch.q) }
        .map { (it as IgnitorDsl.Param).default }

    fun firstFilter(p: SprudelPattern?): WireFilter {
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        val wire = events.first().data.toVoiceData().wireFilters()[0]

        return when (wire) {
            is WireFilter.LowPass -> wire.copy(q = wire.q ?: slotQ[0])
            is WireFilter.HighPass -> wire.copy(q = wire.q ?: slotQ[1])
            is WireFilter.BandPass -> wire.copy(q = wire.q ?: slotQ[2])
            is WireFilter.Notch -> wire.copy(q = wire.q ?: slotQ[3])
        }
    }

    "a bare filter sends no q: the classic() slot default builds it" {
        note("c").lpf(800).queryArc(0.0, 1.0).first().data.toVoiceData().ignitorParams?.containsKey("lpf.q") shouldBe false
        slotQ shouldBe listOf(q, q, q, q)
    }

    "sprudel Kotlin door: bare filters build q = 0.707" {
        (firstFilter(note("c").lpf(800)) as WireFilter.LowPass).q shouldBe q
        (firstFilter(note("c").hpf(200)) as WireFilter.HighPass).q shouldBe q
        (firstFilter(note("c").bpf(1000)) as WireFilter.BandPass).q shouldBe q
        (firstFilter(note("c").notch(1000)) as WireFilter.Notch).q shouldBe q
    }

    "sprudel script door: bare filters build q = 0.707" {
        (firstFilter(SprudelPattern.compile("""note("c").lpf(800)""")) as WireFilter.LowPass).q shouldBe q
        (firstFilter(SprudelPattern.compile("""note("c").hpf(200)""")) as WireFilter.HighPass).q shouldBe q
        (firstFilter(SprudelPattern.compile("""note("c").bpf(1000)""")) as WireFilter.BandPass).q shouldBe q
        (firstFilter(SprudelPattern.compile("""note("c").notch(1000)""")) as WireFilter.Notch).q shouldBe q
    }

    "ignitor DSL door: every filter/eq default is Constant(0.707)" {
        (IgnitorDsl.Lowpass(inner = IgnitorDsl.Constant(0.0), freq = IgnitorDsl.Constant(800.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.Highpass(inner = IgnitorDsl.Constant(0.0), freq = IgnitorDsl.Constant(200.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.Bandpass(inner = IgnitorDsl.Constant(0.0), freq = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.Notch(inner = IgnitorDsl.Constant(0.0), freq = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.EqSection.Bandpass(freq = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.EqSection.Notch(freq = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
        (IgnitorDsl.EqSection.RawTap(freq = IgnitorDsl.Constant(1000.0)).q)
            .shouldBe(IgnitorDsl.Constant(0.707))
    }

    "ignitor scalar doors: band() and tap() default to q = 0.707" {
        val eq = IgnitorDsl.Sine().eq()
        val band = eq.band(1000.0).sections.last() as IgnitorDsl.EqSection.Bell
        band.q shouldBe IgnitorDsl.Constant(0.707)
        val tap = eq.tap(1000.0).sections.last() as IgnitorDsl.EqSection.RawTap
        tap.q shouldBe IgnitorDsl.Constant(0.707)
    }

    "KlangScript interpreter doors: Ignitor bandpass/notch/tap default to q = 0.707" {
        // The guard hole that let C1 miss the script stdlib doors once: drive the actual
        // interpreter, not just sprudel compilation.
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        val bp = eval("""Ignitor.saw().bandpass(1000)""") as IgnitorDsl.Bandpass
        bp.q shouldBe IgnitorDsl.Constant(0.707)

        val nt = eval("""Ignitor.saw().notch(1000)""") as IgnitorDsl.Notch
        nt.q shouldBe IgnitorDsl.Constant(0.707)

        val eqd = eval("""Ignitor.saw().eq(e => e.tap(850))""") as IgnitorDsl.Eq
        (eqd.sections.single() as IgnitorDsl.EqSection.RawTap).q shouldBe IgnitorDsl.Constant(0.707)
    }
})
