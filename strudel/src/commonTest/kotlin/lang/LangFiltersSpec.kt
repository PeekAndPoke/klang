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

        events.map { it.data.cutoff } shouldBe listOf(200.0, 400.0)
        // Verify it converts correctly to VoiceData
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.LowPass).cutoffHz } shouldBe listOf(200.0, 400.0)
    }

    "control pattern lpf() applies LowPass per event" {
        val base = note("c3 e3")
        val p = base.lpf("500 1000")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(500.0, 1000.0, 500.0, 1000.0)
        events.map { it.data.cutoff } shouldBe expected
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.LowPass).cutoffHz } shouldBe expected
    }

    // hpf()
    "top-level hpf() adds/sets HighPass filter with cutoff" {
        val p = hpf("100 250")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { it.data.hcutoff } shouldBe listOf(100.0, 250.0)
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.HighPass).cutoffHz } shouldBe listOf(100.0, 250.0)
    }

    "control pattern hpf() applies HighPass per event" {
        val base = note("c3 e3")
        val p = base.hpf("300 600")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(300.0, 600.0, 300.0, 600.0)
        events.map { it.data.hcutoff } shouldBe expected
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.HighPass).cutoffHz } shouldBe expected
    }

    // bandf()/bpf alias
    "top-level bandf() adds/sets BandPass filter with cutoff" {
        val p = bandf("700 900")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { it.data.bandf } shouldBe listOf(700.0, 900.0)
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.BandPass).cutoffHz } shouldBe listOf(700.0, 900.0)
    }

    "alias bpf behaves like bandf (top-level and modifier)" {
        val pTop = bpf("800 1000")
        val eventsTop = pTop.queryArc(0.0, 1.0)
        eventsTop.size shouldBe 2
        eventsTop.map { it.data.bandf } shouldBe listOf(800.0, 1000.0)
        eventsTop.map { (it.data.toVoiceData().filters[0] as FilterDef.BandPass).cutoffHz } shouldBe listOf(
            800.0,
            1000.0
        )

        val base = note("c3 e3")
        val pCtrl = base.bpf("1100 1200")
        val eventsCtrl = pCtrl.queryArc(0.0, 2.0)
        eventsCtrl.size shouldBe 4
        val expected = listOf(1100.0, 1200.0, 1100.0, 1200.0)
        eventsCtrl.map { it.data.bandf } shouldBe expected
        eventsCtrl.map { (it.data.toVoiceData().filters[0] as FilterDef.BandPass).cutoffHz } shouldBe expected
    }

    // notchf()
    "top-level notchf() adds/sets Notch filter with cutoff" {
        val p = notchf("400 500")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events.map { it.data.notchf } shouldBe listOf(400.0, 500.0)
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.Notch).cutoffHz } shouldBe listOf(400.0, 500.0)
    }

    "control pattern notchf() applies Notch per event" {
        val base = note("c3 e3")
        val p = base.notchf("600 700")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        val expected = listOf(600.0, 700.0, 600.0, 700.0)
        events.map { it.data.notchf } shouldBe expected
        events.map { (it.data.toVoiceData().filters[0] as FilterDef.Notch).cutoffHz } shouldBe expected
    }

    // resonance() behavior - now each filter has its own resonance field
    "resonance() sets LPF resonance specifically" {
        val p = note("c3 e3").lpf("200").resonance("1.5")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        // Check flat fields
        events[0].data.cutoff shouldBe 200.0
        events[0].data.resonance shouldBe 1.5

        // Check converted VoiceData
        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.LowPass).q shouldBe 1.5
    }

    "hresonance() sets HPF resonance specifically" {
        val p = note("c3 e3").hpf("300").hresonance("2.0")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events[0].data.hcutoff shouldBe 300.0
        events[0].data.hresonance shouldBe 2.0

        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.HighPass).q shouldBe 2.0
    }

    "bandq() sets BPF Q specifically" {
        val p = note("c3 e3").bandf("400").bandq("1.2")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events[0].data.bandf shouldBe 400.0
        events[0].data.bandq shouldBe 1.2

        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.BandPass).q shouldBe 1.2
    }

    "nresonance() sets Notch resonance specifically" {
        val p = note("c3 e3").notchf("500").nresonance("0.8")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        events[0].data.notchf shouldBe 500.0
        events[0].data.nresonance shouldBe 0.8

        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.Notch).q shouldBe 0.8
    }

    "each filter can have independent resonance values" {
        // Multiple filters with different resonances
        val p = note("c3").lpf("200").resonance("0.7").hpf("300").hresonance("1.3")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1

        // Check flat fields
        events[0].data.cutoff shouldBe 200.0
        events[0].data.resonance shouldBe 0.7
        events[0].data.hcutoff shouldBe 300.0
        events[0].data.hresonance shouldBe 1.3

        // Check converted VoiceData has both filters with correct Q values
        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.LowPass).q shouldBe 0.7
        (voiceData.filters[1] as FilterDef.HighPass).q shouldBe 1.3
    }

    "lpf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""lpf("200 400")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.cutoff } shouldBe listOf(200.0, 400.0)
    }

    "lpf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").lpf("200 400")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.cutoff } shouldBe listOf(200.0, 400.0)
    }

    "hpf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""hpf("100 250")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hcutoff } shouldBe listOf(100.0, 250.0)
    }

    "hpf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").hpf("100 250")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hcutoff } shouldBe listOf(100.0, 250.0)
    }

    "bandf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""bandf("700 900")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.bandf } shouldBe listOf(700.0, 900.0)
    }

    "bandf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").bandf("700 900")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.bandf } shouldBe listOf(700.0, 900.0)
    }

    "notchf() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""notchf("400 500")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.notchf } shouldBe listOf(400.0, 500.0)
    }

    "notchf() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").notchf("400 500")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.notchf } shouldBe listOf(400.0, 500.0)
    }

    "resonance() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""lpf("200 400").resonance("1.5 2.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.resonance } shouldBe listOf(1.5, 2.5)
    }

    "resonance() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").lpf("200 400").resonance("1.5 2.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.resonance } shouldBe listOf(1.5, 2.5)
    }

    // Alias tests
    "res() alias works like resonance()" {
        val p = note("c3 e3").lpf("200").res("1.5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.resonance shouldBe 1.5
    }

    "hres() alias works like hresonance()" {
        val p = note("c3 e3").hpf("300").hres("2.0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.hresonance shouldBe 2.0
    }

    "nres() alias works like nresonance()" {
        val p = note("c3 e3").notchf("500").nres("0.8")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.nresonance shouldBe 0.8
    }

    // Compiled code tests for new resonance functions
    "hresonance() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").hpf("100 250").hresonance("1.5 2.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.hresonance } shouldBe listOf(1.5, 2.5)
    }

    "bandq() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").bandf("700 900").bandq("1.2 1.8")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.bandq } shouldBe listOf(1.2, 1.8)
    }

    "nresonance() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").notchf("400 500").nresonance("0.5 0.9")""")

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

    "bandf() works as string extension" {
        val p = "c3 e3".bandf("700")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.bandf shouldBe 700.0
    }

    "bpf() works as string extension" {
        val p = "c3 e3".bpf("800")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.bandf shouldBe 800.0
    }

    "notchf() works as string extension" {
        val p = "c3 e3".notchf("400")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.notchf shouldBe 400.0
    }

    "resonance() works as string extension" {
        val p = "c3 e3".lpf("200").resonance("1.5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.resonance shouldBe 1.5
    }

    "hresonance() works as string extension" {
        val p = "c3 e3".hpf("300").hresonance("2.0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.hresonance shouldBe 2.0
    }

    "bandq() works as string extension" {
        val p = "c3 e3".bandf("700").bandq("1.2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.bandq shouldBe 1.2
    }

    "nresonance() works as string extension" {
        val p = "c3 e3".notchf("500").nresonance("0.8")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.nresonance shouldBe 0.8
    }
})
