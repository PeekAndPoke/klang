/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangFiltersSpec : StringSpec({

    // lpf()

    "control pattern lpf() applies LowPass per event" {
        val base = note("c3 e3")
        val p = base.lpf("500 1000")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(500.0, 1000.0, 500.0, 1000.0)
        events.map { it.data.cutoff } shouldBe expected
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.LowPass).freq } shouldBe expected
    }

    // hpf()

    "control pattern hpf() applies HighPass per event" {
        val base = note("c3 e3")
        val p = base.hpf("300 600")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(300.0, 600.0, 300.0, 600.0)
        events.map { it.data.hcutoff } shouldBe expected
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.HighPass).freq } shouldBe expected
    }

    // notch()

    "top-level notch() adds/sets Notch filter with cutoff" {
        val p = note("a b").apply(notch("400 500"))

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { it.data.notchf } shouldBe listOf(400.0, 500.0)
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.Notch).freq } shouldBe listOf(400.0, 500.0)
    }

    "control pattern notch() applies Notch per event" {
        val base = note("c3 e3")
        val p = base.notch("600 700")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(600.0, 700.0, 600.0, 700.0)
        events.map { it.data.notchf } shouldBe expected
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.Notch).freq } shouldBe expected
    }

    "lpf() works within compiled code as top-level function" {
        val p = SprudelPattern.compile("""seq("200 400").lpf()""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.cutoff } shouldBe listOf(200.0, 400.0)
    }

    "lpf() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").lpf("200 400")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.cutoff } shouldBe listOf(200.0, 400.0)
    }

    "hpf() works within compiled code as top-level function" {
        val p = SprudelPattern.compile("""seq("100 250").hpf()""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hcutoff } shouldBe listOf(100.0, 250.0)
    }

    "hpf() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").hpf("100 250")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hcutoff } shouldBe listOf(100.0, 250.0)
    }

    "notch() works within compiled code as top-level function" {
        val p = SprudelPattern.compile("""seq("400 500").notch()""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.notchf } shouldBe listOf(400.0, 500.0)
    }

    "notch() works within compiled code as chained-level function" {
        val p = SprudelPattern.compile("""note("a b").notch("400 500")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.notchf } shouldBe listOf(400.0, 500.0)
    }

    // Notch resonance + per-filter independence

    "notch(q = ...) sets Notch resonance specifically" {
        val p = note("c3 e3").notch(freq = "500", q = "0.8")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events[0].data.notchf shouldBe 500.0
        events[0].data.nresonance shouldBe 0.8

        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.Notch).q shouldBe 0.8
    }

    "each filter can have independent resonance values" {
        // Multiple filters with different resonances
        val p = note("c3").lpf(freq = "200", q = "0.7").hpf(freq = "300", q = "1.3")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1

        // Check flat fields
        events[0].data.cutoff shouldBe 200.0
        events[0].data.resonance shouldBe 0.7
        events[0].data.hcutoff shouldBe 300.0
        events[0].data.hresonance shouldBe 1.3

        // Check converted VoiceData carries each filter's resonance independently.
        // toVoiceData() imposes the canonical HPF->...->LPF chain order, so look up by type, not index.
        val voiceData = events[0].data.toVoiceData()
        voiceData.filters.getByType<FilterDef.LowPass>()?.q shouldBe 0.7
        voiceData.filters.getByType<FilterDef.HighPass>()?.q shouldBe 1.3
    }

    // Compiled code tests for resonance functions
    "hpf(q = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").hpf(freq = "100 250", q = "1.5 2.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(1.5, 2.5)
    }

    "notch(q = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").notch(freq = "400 500", q = "0.5 0.9")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.nresonance } shouldBe listOf(0.5, 0.9)
    }

    // String extension tests
    "lpf() works as string extension" {
        val p = "c3 e3".lpf("500")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.cutoff shouldBe 500.0
    }

    "hpf() works as string extension" {
        val p = "c3 e3".hpf("300")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.hcutoff shouldBe 300.0
    }

    "bpf() works as string extension" {
        val p = "c3 e3".bpf("800")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.bandf shouldBe 800.0
    }

    "notch() works as string extension" {
        val p = "c3 e3".notch("400")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.notchf shouldBe 400.0
    }

    "hpf(q = ...) works as string extension" {
        val p = "c3 e3".hpf(freq = "300", q = "2.0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.hresonance shouldBe 2.0
    }

    "notch(q = ...) works as string extension" {
        val p = "c3 e3".notch(freq = "500", q = "0.8")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.nresonance shouldBe 0.8
    }

    // ---- C0 guard: notch(freq, q) ----

    "notch(freq, q) sets both fields" {
        val p = note("c e").notch("200 800", 1.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.notchf shouldBe 200.0
        events[1].data.notchf shouldBe 800.0
        events[0].data.nresonance shouldBe 1.5
        events[1].data.nresonance shouldBe 1.5
    }

    "notch(q = ...) does not clear a previously set freq" {
        val p = SprudelPattern.compile("""note("c3").notch(800).notch(q = 12)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.notchf shouldBe 800.0
        events[0].data.nresonance shouldBe 12.0
    }
})
