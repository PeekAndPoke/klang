package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLpfSpec : StringSpec({

    "lpf() sets VoiceData.cutoff and adds FilterDef.LowPass" {
        val p = lpf("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.cutoff shouldBe 1000.0
        events[0].data.filters.getByType<FilterDef.LowPass>()?.cutoffHz shouldBe 1000.0

        events[1].data.cutoff shouldBe 500.0
        events[1].data.filters.getByType<FilterDef.LowPass>()?.cutoffHz shouldBe 500.0
    }

    "lpf() works as pattern extension" {
        val p = note("c").lpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
        events[0].data.filters.getByType<FilterDef.LowPass>()?.cutoffHz shouldBe 1000.0
    }

    "lpf() works as string extension" {
        val p = "c".lpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }
})
