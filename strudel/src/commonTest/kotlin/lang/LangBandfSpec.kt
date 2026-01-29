package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef.BandPass
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBandfSpec : StringSpec({

    "bandf() sets StrudelVoiceData.bandf and converts to FilterDef.BandPass" {
        val p = bandf("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bandf shouldBe 1000.0
        events[0].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe 1000.0

        events[1].data.bandf shouldBe 500.0
        events[1].data.toVoiceData().filters.getByType<BandPass>()?.cutoffHz shouldBe 500.0
    }

    "bpf() alias works" {
        val p = bpf("1000")
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

    "bp() is an alias for bandf()" {
        val p = bp("1500")
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
