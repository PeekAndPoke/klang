package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBandfSpec : StringSpec({

    "bandf() sets VoiceData.bandf and adds FilterDef.BandPass" {
        val p = bandf("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bandf shouldBe 1000.0
        events[0].data.filters.getByType<FilterDef.BandPass>()?.cutoffHz shouldBe 1000.0

        events[1].data.bandf shouldBe 500.0
        events[1].data.filters.getByType<FilterDef.BandPass>()?.cutoffHz shouldBe 500.0
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
        events[0].data.filters.getByType<FilterDef.BandPass>()?.cutoffHz shouldBe 1000.0
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
        // t=0.0: sine(0) = 0.5
        events[0].data.filters.getByType<FilterDef.BandPass>()?.cutoffHz shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.filters.getByType<FilterDef.BandPass>()?.cutoffHz shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.filters.getByType<FilterDef.BandPass>()?.cutoffHz shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.filters.getByType<FilterDef.BandPass>()?.cutoffHz shouldBe (0.0 plusOrMinus EPSILON)
    }
})
