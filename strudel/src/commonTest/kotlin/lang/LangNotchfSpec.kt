package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNotchfSpec : StringSpec({

    "notchf() sets VoiceData.cutoff and adds FilterDef.Notch" {
        // Note: notchf currently sets 'cutoff' in VoiceData, similar to lpf
        val p = notchf("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.cutoff shouldBe 1000.0
        events[0].data.filters.getByType<FilterDef.Notch>()?.cutoffHz shouldBe 1000.0

        events[1].data.cutoff shouldBe 500.0
        events[1].data.filters.getByType<FilterDef.Notch>()?.cutoffHz shouldBe 500.0
    }

    "notchf() works as pattern extension" {
        val p = note("c").notchf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
        events[0].data.filters.getByType<FilterDef.Notch>()?.cutoffHz shouldBe 1000.0
    }

    "notchf() works as string extension" {
        val p = "c".notchf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "notchf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").notchf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }
})
