package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
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
})
