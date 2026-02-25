package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef.BandPass
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangBandfSpec : StringSpec({

    // ---- bandf ----

    "bandf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"
        dslInterfaceTests(
            "pattern.bandf(ctrl)" to seq(pat).bandf(ctrl),
            "script pattern.bandf(ctrl)" to StrudelPattern.compile("""seq("$pat").bandf("$ctrl")"""),
            "string.bandf(ctrl)" to pat.bandf(ctrl),
            "script string.bandf(ctrl)" to StrudelPattern.compile(""""$pat".bandf("$ctrl")"""),
            "bandf(ctrl)" to seq(pat).apply(bandf(ctrl)),
            "script bandf(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bandf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "reinterpret voice data as bandf | seq(\"1000 500\").bandf()" {
        val p = seq("1000 500").bandf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "reinterpret voice data as bandf | \"1000 500\".bandf()" {
        val p = "1000 500".bandf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "reinterpret voice data as bandf | seq(\"1000 500\").apply(bandf())" {
        val p = seq("1000 500").apply(bandf())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "bandf() sets StrudelVoiceData.bandf and converts to FilterDef.BandPass" {
        val p = note("a b").apply(bandf("1000 500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bandf shouldBe 1000.0
        events[0].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe 1000.0

        events[1].data.bandf shouldBe 500.0
        events[1].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe 500.0
    }

    // ---- bpf (alias) ----

    "bpf dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"
        dslInterfaceTests(
            "pattern.bpf(ctrl)" to seq(pat).bpf(ctrl),
            "script pattern.bpf(ctrl)" to StrudelPattern.compile("""seq("$pat").bpf("$ctrl")"""),
            "string.bpf(ctrl)" to pat.bpf(ctrl),
            "script string.bpf(ctrl)" to StrudelPattern.compile(""""$pat".bpf("$ctrl")"""),
            "bpf(ctrl)" to seq(pat).apply(bpf(ctrl)),
            "script bpf(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpf("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "reinterpret voice data as bandf | seq(\"1000 500\").bpf()" {
        val p = seq("1000 500").bpf()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "bpf() alias works" {
        val p = note("a").apply(bpf("1000"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.bandf shouldBe 1000.0
    }

    "bandf() works as pattern extension" {
        val p = note("c").bandf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandf shouldBe 1000.0
        events[0].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe 1000.0
    }

    "bandf() works as string extension" {
        val p = "c".bandf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandf shouldBe 1000.0
    }

    "bandf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bandf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bandf shouldBe 1000.0
    }

    "bandf() with continuous pattern sets cutoffHz correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bandf(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // Check flat fields
        events[0].data.bandf shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.bandf shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.bandf shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.bandf shouldBe (0.0 plusOrMinus EPSILON)

        // Also check converted VoiceData
        events[0].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe (0.0 plusOrMinus EPSILON)
    }

    // ---- bp (alias) ----

    "bp dsl interface" {
        val pat = "a b"
        val ctrl = "1000 500"
        dslInterfaceTests(
            "pattern.bp(ctrl)" to seq(pat).bp(ctrl),
            "script pattern.bp(ctrl)" to StrudelPattern.compile("""seq("$pat").bp("$ctrl")"""),
            "string.bp(ctrl)" to pat.bp(ctrl),
            "script string.bp(ctrl)" to StrudelPattern.compile(""""$pat".bp("$ctrl")"""),
            "bp(ctrl)" to seq(pat).apply(bp(ctrl)),
            "script bp(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bp("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "reinterpret voice data as bandf | seq(\"1000 500\").bp()" {
        val p = seq("1000 500").bp()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bandf shouldBe 1000.0
            events[1].data.bandf shouldBe 500.0
        }
    }

    "bp() is an alias for bandf()" {
        val p = note("a").apply(bp("1500"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandf shouldBe 1500.0
    }

    "bp() works as pattern extension" {
        val p = note("c").bp("1500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandf shouldBe 1500.0
    }

    "bp() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bp("1500")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bandf shouldBe 1500.0
    }
})
