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
            val original = events.find { it.part.begin.toDouble() == 0.0 }
            original?.data?.note shouldBe "c"

            // Delayed "e" at 0.25
            val delayed = events.find { it.part.begin.toDouble() == 0.25 }
            delayed?.data?.note shouldBe "e"
        }
    }

    "off() supports custom delay time" {
        val subject = note("c").off(0.5) { it }

        assertSoftly {

            repeat(12) { cycle ->
                val cycleDbl = cycle.toDouble()
                val events = subject.queryArc(cycleDbl, cycleDbl + 1.0)

                events.forEachIndexed { index, event ->
                    println(
                        "${index + 1}: note: ${event.data.note} | " +
                                "part: ${event.part.begin} ${event.part.end} | " +
                                "whole: ${event.whole.begin} ${event.whole.end}"
                    )
                }

                events shouldHaveSize 3

                // Event 0: Original event from cycle 0
                events[0].data.note shouldBe "c"
                events[0].part.begin.toDouble() shouldBe (cycleDbl - 0.5)
                events[0].part.end.toDouble() shouldBe (cycleDbl + 0.5)
                events[0].whole.begin.toDouble() shouldBe (cycleDbl - 0.5)
                events[0].whole.end.toDouble() shouldBe (cycleDbl + 0.5)

                // Event 1: Delayed copy from cycle -1, clipped to query range
                events[0].data.note shouldBe "c"
                events[1].part.begin.toDouble() shouldBe (cycleDbl + 0.0)
                events[1].part.end.toDouble() shouldBe (cycleDbl + 1.0)
                events[1].whole.begin.toDouble() shouldBe (cycleDbl + 0.0)
                events[1].whole.end.toDouble() shouldBe (cycleDbl + 1.0)

                // Event 2: Delayed copy from cycle 0
                events[0].data.note shouldBe "c"
                events[2].part.begin.toDouble() shouldBe (cycleDbl + 0.5)
                events[2].part.end.toDouble() shouldBe (cycleDbl + 1.5)
                events[2].whole.begin.toDouble() shouldBe (cycleDbl + 0.5)
                events[2].whole.end.toDouble() shouldBe (cycleDbl + 1.5)
            }
        }
    }
})
