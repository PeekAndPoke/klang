package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFiltersSpec : StringSpec({

    // lpf()
    "top-level lpf() adds/sets LowPass filter with cutoff" {
        val p = lpf("200 400")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { (it.data.filters[0] as FilterDef.LowPass).cutoffHz } shouldBe listOf(200.0, 400.0)
    }

    "control pattern lpf() applies LowPass per event" {
        val base = note("c3 e3")
        val p = base.lpf("500 1000")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(500.0, 1000.0, 500.0, 1000.0)
        events.map { (it.data.filters[0] as FilterDef.LowPass).cutoffHz } shouldBe expected
    }

    // hpf()
    "top-level hpf() adds/sets HighPass filter with cutoff" {
        val p = hpf("100 250")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { (it.data.filters[0] as FilterDef.HighPass).cutoffHz } shouldBe listOf(100.0, 250.0)
    }

    "control pattern hpf() applies HighPass per event" {
        val base = note("c3 e3")
        val p = base.hpf("300 600")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(300.0, 600.0, 300.0, 600.0)

        events.forEach { println(it.data.filters) }

        events.map { (it.data.filters[0] as FilterDef.HighPass).cutoffHz } shouldBe expected
    }

    // bandf()/bpf alias
    "top-level bandf() adds/sets BandPass filter with cutoff" {
        val p = bandf("700 900")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { (it.data.filters[0] as FilterDef.BandPass).cutoffHz } shouldBe listOf(700.0, 900.0)
    }

    "alias bpf behaves like bandf (top-level and modifier)" {
        val pTop = bpf("800 1000")
        val eventsTop = pTop.queryArc(0.0, 1.0)
        eventsTop.size shouldBe 2
        eventsTop.map { (it.data.filters[0] as FilterDef.BandPass).cutoffHz } shouldBe listOf(800.0, 1000.0)

        val base = note("c3 e3")
        val pCtrl = base.bpf("1100 1200")
        val eventsCtrl = pCtrl.queryArc(0.0, 2.0)
        eventsCtrl.size shouldBe 4
        val expected = listOf(1100.0, 1200.0, 1100.0, 1200.0)
        eventsCtrl.map { (it.data.filters[0] as FilterDef.BandPass).cutoffHz } shouldBe expected
    }

    // notchf()
    "top-level notchf() adds/sets Notch filter with cutoff" {
        val p = notchf("400 500")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { (it.data.filters[0] as FilterDef.Notch).cutoffHz } shouldBe listOf(400.0, 500.0)
    }

    "control pattern notchf() applies Notch per event" {
        val base = note("c3 e3")
        val p = base.notchf("600 700")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(600.0, 700.0, 600.0, 700.0)
        events.map { (it.data.filters[0] as FilterDef.Notch).cutoffHz } shouldBe expected
    }

    // resonance() behavior
    "resonance() updates Q on all existing filters (LPF, HPF, BandPass, Notch)" {
        // Given: stack of four events each carrying a different filter type
        val p = note("c3 e3 c3 e3")
            .lpf("200")
            .hpf("300")
            .bandf("400")
            .notchf("500")

        // When: apply a single resonance value as control
        val withRes = p.resonance("1.5")

        val events = withRes.queryArc(0.0, 1.0)
        events.size shouldBe 4

        // Then: every existing filter instance must have q updated to 1.5
        (events[0].data.filters[0] as FilterDef.LowPass).q shouldBe 1.5
        (events[0].data.filters[1] as FilterDef.HighPass).q shouldBe 1.5
        (events[0].data.filters[2] as FilterDef.BandPass).q shouldBe 1.5
        (events[0].data.filters[3] as FilterDef.Notch).q shouldBe 1.5

        // And: changing resonance per step also updates per corresponding event
        val stepped = p.resonance("0.5 1.0 2.0 3.0")
        val steppedEvents = stepped.queryArc(0.0, 1.0)
        steppedEvents.size shouldBe 4
        (steppedEvents[0].data.filters[0] as FilterDef.LowPass).q shouldBe 0.5
        (steppedEvents[1].data.filters[1] as FilterDef.HighPass).q shouldBe 1.0
        (steppedEvents[2].data.filters[2] as FilterDef.BandPass).q shouldBe 2.0
        (steppedEvents[3].data.filters[3] as FilterDef.Notch).q shouldBe 3.0
    }

    "filters added after resonance() use the current resonance value for all types" {
        // Given: a base pattern that sets resonance per event
        val base = note("c3 e3 c3 e3").resonance("0.7 1.3 2.2 0.9")

        // When: add different filter types afterwards; each should pick up the current resonance per event
        val withLpf = base.lpf("200 200 200 200")
        val lpfEvents = withLpf.queryArc(0.0, 1.0)
        lpfEvents.map { (it.data.filters[0] as FilterDef.LowPass).q } shouldBe listOf(0.7, 1.3, 2.2, 0.9)

        val withHpf = base.hpf("300 300 300 300")
        val hpfEvents = withHpf.queryArc(0.0, 1.0)
        hpfEvents.map { (it.data.filters[0] as FilterDef.HighPass).q } shouldBe listOf(0.7, 1.3, 2.2, 0.9)

        val withBand = base.bandf("400 400 400 400")
        val bandEvents = withBand.queryArc(0.0, 1.0)
        bandEvents.map { (it.data.filters[0] as FilterDef.BandPass).q } shouldBe listOf(0.7, 1.3, 2.2, 0.9)

        val withNotch = base.notchf("500 500 500 500")
        val notchEvents = withNotch.queryArc(0.0, 1.0)
        notchEvents.map { (it.data.filters[0] as FilterDef.Notch).q } shouldBe listOf(0.7, 1.3, 2.2, 0.9)
    }

    "lpf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""lpf("200 400")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.LowPass).cutoffHz } shouldBe listOf(200.0, 400.0)
    }

    "lpf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").lpf("200 400")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.LowPass).cutoffHz } shouldBe listOf(200.0, 400.0)
    }

    "hpf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""hpf("100 250")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.HighPass).cutoffHz } shouldBe listOf(100.0, 250.0)
    }

    "hpf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").hpf("100 250")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.HighPass).cutoffHz } shouldBe listOf(100.0, 250.0)
    }

    "bandf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""bandf("700 900")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.BandPass).cutoffHz } shouldBe listOf(700.0, 900.0)
    }

    "bandf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").bandf("700 900")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.BandPass).cutoffHz } shouldBe listOf(700.0, 900.0)
    }

    "notchf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""notchf("400 500")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.Notch).cutoffHz } shouldBe listOf(400.0, 500.0)
    }

    "notchf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").notchf("400 500")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.Notch).cutoffHz } shouldBe listOf(400.0, 500.0)
    }

    "resonance() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""lpf("200 400").resonance("1.5 2.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.LowPass).q } shouldBe listOf(1.5, 2.5)
    }

    "resonance() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").lpf("200 400").resonance("1.5 2.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { (it.data.filters[0] as FilterDef.LowPass).q } shouldBe listOf(1.5, 2.5)
    }
})
