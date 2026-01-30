package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangOffSpec : StringSpec({

    "off() layers a time-shifted transformation" {
        // Original at 0.0
        // Delayed at 0.25 (default time) with transformation (note "e")
        val p = note("c").off(0.25) { it.note("e") }

        // Query enough to see the delayed event
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 3

            // Original "c" at 0.0
            val original = events.find { it.begin.toDouble() == 0.0 }
            original?.data?.note shouldBe "c"

            // Delayed "e" at 0.25
            val delayed = events.find { it.begin.toDouble() == 0.25 }
            delayed?.data?.note shouldBe "e"
        }
    }

    "off() supports custom delay time" {
        val p = note("c").off(0.5) { it }
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 3
        events[0].begin.toDouble() shouldBe -0.5
        events[1].begin.toDouble() shouldBe 0.0
        events[2].begin.toDouble() shouldBe 0.5
    }
})
