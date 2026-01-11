package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHpfSpec : StringSpec({

    "hpf() sets VoiceData.hcutoff and adds FilterDef.HighPass" {
        val p = hpf("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hcutoff shouldBe 1000.0
        events[0].data.filters.getByType<FilterDef.HighPass>()?.cutoffHz shouldBe 1000.0

        events[1].data.hcutoff shouldBe 500.0
        events[1].data.filters.getByType<FilterDef.HighPass>()?.cutoffHz shouldBe 500.0
    }

    "hpf() works as pattern extension" {
        val p = note("c").hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
        events[0].data.filters.getByType<FilterDef.HighPass>()?.cutoffHz shouldBe 1000.0
    }

    "hpf() works as string extension" {
        val p = "c".hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }
})
