package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangSegmentSpec : StringSpec({

    "segment(n) samples a continuous pattern n times per cycle" {
        // sine is continuous. segment(4) should create 4 discrete events per cycle
        val p = sine.segment(4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        // Check timing
        events[0].begin.toDouble() shouldBe 0.0
        events[0].end.toDouble() shouldBe 0.25

        events[3].begin.toDouble() shouldBe 0.75
        events[3].end.toDouble() shouldBe 1.0
    }

    "segment(n) works on discrete patterns" {
        // seq("0") is one event (0..1). segment(4) should chop it into 4
        val p = seq("0").segment(4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.forEach {
            it.data.value?.asInt shouldBe 0
        }
    }

    "seg() alias works" {
        val p = seq("0").seg(2)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
    }

    "segment works as string extension" {
        val p = "0".segment(2)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
    }

    "seg() alias works as string extension" {
        val p = "0".seg(2)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
    }
})
