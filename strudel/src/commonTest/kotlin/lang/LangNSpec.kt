package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNSpec : StringSpec({

    "top-level n() sets VoiceData.soundIndex correctly" {
        // Given a simple sequence of n values
        val p = n("0 1 2")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then soundIndex is set
        events.size shouldBe 3
        events.map { it.data.soundIndex } shouldBe listOf(0, 1, 2)
    }

    "top-level n() sets VoiceData.note if scale is present in context (via resolveNote logic)" {
        // When n is used inside a block that has scale set? Or we manually set scale.
        // Actually top-level n() without context just sets soundIndex.
        // But if we chain .scale()...
        // Let's test basic assignment first.
        val p = n("0")
        val event = p.queryArc(0.0, 1.0).first()
        event.data.soundIndex shouldBe 0
        event.data.note shouldBe null // No scale, no note yet unless explicitly set
    }

    "control pattern n() sets soundIndex on existing pattern" {
        val base = note("c3")
        val p = base.n("5 7")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.soundIndex } shouldBe listOf(5, 7)
    }

    "n() re-interprets value as soundIndex when called without args" {
        // Given a pattern that has 'value' set (e.g. from seq or purely numeric input)
        // seq("0 2") sets value=0.0, value=2.0
        val p = seq("0 2").n()

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.soundIndex shouldBe 0
        events[1].data.soundIndex shouldBe 2
    }

    "n() re-interpretation respects scale if present" {
        // seq("0").scale("C:major").n()
        // 1. seq("0") -> value=0
        // 2. .scale("C:major") -> scale="C major"
        // 3. .n() -> re-interprets. resolveNote checks soundIndex OR value.
        //    It sees value=0. It sees scale. It should resolve to Note "C".
        //    AND it should CLEAR soundIndex if it resolves to a note?
        //    Let's check resolveNote implementation:
        //    if (n != null && !effectiveScale.isNullOrEmpty()) ... returns copy(note=..., soundIndex=null, value=null)

        val p = seq("0 1").scale("C4:major").n()

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2

        // 0 in C4 major -> C4
        events[0].data.note shouldBe "C4"
        events[0].data.soundIndex shouldBe null

        // 1 in C4 major -> D4
        events[1].data.note shouldBe "D4"
        events[1].data.soundIndex shouldBe null
    }

    "n() works within compiled code" {
        val p = StrudelPattern.compile("""n("0 5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.soundIndex } shouldBe listOf(0, 5)
    }

    "n() re-interpretation works within compiled code" {
        val p = StrudelPattern.compile("""seq("0 2").n()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.soundIndex shouldBe 0
        events[1].data.soundIndex shouldBe 2
    }
})
