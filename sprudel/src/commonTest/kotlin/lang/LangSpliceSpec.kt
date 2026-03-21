package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSpliceSpec : StringSpec({

    "splice() divides sample and adjusts speed" {
        val p = sound("bd").splice(4, 1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.begin shouldBe 0.25  // 1/4
        events[0].data.end shouldBe 0.5     // 2/4
        events[0].data.speed shouldBe 4.0
    }

    "splice() with first slice" {
        val p = sound("bd").splice(4, 0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.begin shouldBe 0.0   // 0/4
        events[0].data.end shouldBe 0.25    // 1/4
        events[0].data.speed shouldBe 4.0
    }

    "splice() with last slice" {
        val p = sound("bd").splice(4, 3)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.begin shouldBe 0.75  // 3/4
        events[0].data.end shouldBe 1.0     // 4/4
        events[0].data.speed shouldBe 4.0
    }

    "splice() with 2 slices" {
        val p = sound("bd").splice(2, 0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.begin shouldBe 0.0   // 0/2
        events[0].data.end shouldBe 0.5     // 1/2
        events[0].data.speed shouldBe 2.0
    }

    "splice() works as string extension" {
        val p = "bd".splice(4, 1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.begin shouldBe 0.25
        events[0].data.end shouldBe 0.5
        events[0].data.speed shouldBe 4.0
    }

    "splice() works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd").splice(4, 1)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.begin shouldBe 0.25
        events[0].data.end shouldBe 0.5
        events[0].data.speed shouldBe 4.0
    }

    "splice() maintains timing compared to slice()" {
        // slice() would play the slice at the original sample's speed
        val sliced = sound("bd").slice(4, 1)
        val slicedEvents = sliced.queryArc(0.0, 1.0)

        // splice() should play at 4x speed to maintain the original duration
        val spliced = sound("bd").splice(4, 1)
        val splicedEvents = spliced.queryArc(0.0, 1.0)

        // Both should have the same begin/end range
        slicedEvents[0].data.begin shouldBe splicedEvents[0].data.begin
        slicedEvents[0].data.end shouldBe splicedEvents[0].data.end

        // But splice should have speed adjusted
        splicedEvents[0].data.speed shouldBe 4.0
    }

    "splice() with minimum value of 1" {
        // Even with n=0 or negative, should default to 1
        val p = sound("bd").splice(0, 0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        // With n=1, the full sample should play at normal speed
        events[0].data.begin shouldBe 0.0
        events[0].data.end shouldBe 1.0
        events[0].data.speed shouldBe 1.0
    }
})
