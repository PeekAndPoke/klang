package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangOffSpec : StringSpec({

    "off dsl interface" {
        val pat = "c e"
        val transform: PatternMapperFn = { it.note("e") }
        dslInterfaceTests(
            "pattern.off(0.25, fn)" to note(pat).off(0.25, transform = transform),
            "script pattern.off(0.25, fn)" to SprudelPattern.compile("""note("$pat").off(0.25, x => x.note("e"))"""),
            "string.off(0.25, fn)" to pat.off(0.25, transform = transform),
            "script string.off(0.25, fn)" to SprudelPattern.compile(""""$pat".off(0.25, x => x.note("e"))"""),
            "off(0.25, fn)" to note(pat).apply(off(0.25, transform = transform)),
            "script off(0.25, fn)" to SprudelPattern.compile("""note("$pat").apply(off(0.25, x => x.note("e")))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 5  // original 2 + delayed copy 2
        }
    }

    "off() layers a time-shifted transformation" {
        // Original at 0.0
        // Delayed at 0.25 (default time) with transformation (note "e")
        val p = note("c").off(0.25, transform = { it.note("e") })

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
        val subject = note("c").off(0.5, transform = { it })

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
                // Order by whole.begin — distinct (cycle-0.5, cycle, cycle+0.5) and deterministic.
                // (Two events share part.begin == cycle once the delayed copy is clipped, so sorting
                // by part.begin would be ambiguous.)
                val sorted = events.sortedBy { it.whole.begin }

                // [0] Delayed copy whose onset is in the PREVIOUS cycle. Its part is clipped to the
                // query window, so part.begin (cycle) != whole.begin (cycle-0.5): NOT an onset here —
                // it already triggered in the previous cycle's query (no double-trigger).
                sorted[0].data.note shouldBe "c"
                sorted[0].whole.begin.toDouble() shouldBe (cycleDbl - 0.5)
                sorted[0].whole.end.toDouble() shouldBe (cycleDbl + 0.5)
                sorted[0].part.begin.toDouble() shouldBe (cycleDbl + 0.0)
                sorted[0].part.end.toDouble() shouldBe (cycleDbl + 0.5)
                sorted[0].isOnset shouldBe false

                // [1] The original (unshifted) event: full cycle, onset.
                sorted[1].data.note shouldBe "c"
                sorted[1].whole.begin.toDouble() shouldBe (cycleDbl + 0.0)
                sorted[1].whole.end.toDouble() shouldBe (cycleDbl + 1.0)
                sorted[1].part.begin.toDouble() shouldBe (cycleDbl + 0.0)
                sorted[1].part.end.toDouble() shouldBe (cycleDbl + 1.0)
                sorted[1].isOnset shouldBe true

                // [2] Delayed copy whose onset is in THIS cycle: onset; part clipped to the query end.
                sorted[2].data.note shouldBe "c"
                sorted[2].whole.begin.toDouble() shouldBe (cycleDbl + 0.5)
                sorted[2].whole.end.toDouble() shouldBe (cycleDbl + 1.5)
                sorted[2].part.begin.toDouble() shouldBe (cycleDbl + 0.5)
                sorted[2].part.end.toDouble() shouldBe (cycleDbl + 1.0)
                sorted[2].isOnset shouldBe true
            }
        }
    }
})
