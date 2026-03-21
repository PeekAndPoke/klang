package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.seq

class LangTimeLoopSpec : StringSpec({

    "timeLoop dsl interface" {
        // seq("1 2") with timeLoop(0.5): window [0, 0.5] captures only event "1"
        // querying [cycle, cycle+1] always gives 2 events of value 1.0
        val pat = "1 2"
        val duration = 0.5

        dslInterfaceTests(
            "pattern.timeLoop(duration)" to
                    seq(pat).timeLoop(duration),
            "script pattern.timeLoop(duration)" to
                    SprudelPattern.compile("""seq("$pat").timeLoop($duration)"""),
            "string.timeLoop(duration)" to
                    pat.timeLoop(duration),
            "script string.timeLoop(duration)" to
                    SprudelPattern.compile(""""$pat".timeLoop($duration)"""),
            "timeLoop(duration)" to
                    seq(pat).apply(timeLoop(duration)),
            "script timeLoop(duration)" to
                    SprudelPattern.compile("""seq("$pat").apply(timeLoop($duration))"""),
        ) { _, events ->
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe 1.0
            events[1].data.value?.asDouble shouldBe 1.0
        }
    }

    "timeLoop() repeats window [0, duration] indefinitely" {
        // note("c3 d3 e3 f3") with timeLoop(0.5):
        // c3=[0, 0.25], d3=[0.25, 0.5], e3=[0.5, 0.75], f3=[0.75, 1.0]
        // window [0, 0.5] captures c3 and d3 only
        // querying [0, 1] gives: c3, d3, c3, d3 (4 events)
        val p = note("c3 d3 e3 f3").timeLoop(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4

            events[0].data.note shouldBe "c3"
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

            events[1].data.note shouldBe "d3"
            events[1].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].data.note shouldBe "c3"
            events[2].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

            events[3].data.note shouldBe "d3"
            events[3].part.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[3].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "timeLoop() is stable across multiple cycles" {
        val p = note("c3 d3").timeLoop(0.5)

        kotlin.repeat(8) { cycle ->
            val cycleDbl = cycle.toDouble()
            val events = p.queryArc(cycleDbl, cycleDbl + 1.0).sortedBy { it.part.begin }

            events shouldHaveSize 2
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "c3"
        }
    }

    "timeLoop() as string extension" {
        val p = "c3 d3 e3 f3".timeLoop(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.value?.asString shouldBe "c3"
            events[1].data.value?.asString shouldBe "d3"
            events[2].data.value?.asString shouldBe "c3"
            events[3].data.value?.asString shouldBe "d3"
        }
    }

    "timeLoop() as top-level PatternMapperFn" {
        val p = note("c3 d3 e3 f3").apply(timeLoop(0.5))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "d3"
            events[2].data.note shouldBe "c3"
            events[3].data.note shouldBe "d3"
        }
    }

    "timeLoop() returns silence for zero duration" {
        val p = note("c3 d3").timeLoop(0)
        val events = p.queryArc(0.0, 10.0)
        events shouldHaveSize 0
    }

    "timeLoop() with full-cycle duration loops the whole pattern" {
        // timeLoop(1.0) on a 1-cycle pattern is effectively a no-op
        val p = note("c3 d3").timeLoop(1.0)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "d3"
            events[2].data.note shouldBe "c3"
            events[3].data.note shouldBe "d3"
        }
    }

    "timeLoop() in compiled code" {
        val p = SprudelPattern.compile("""note("c3 d3 e3 f3").timeLoop(0.5)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.part.begin } ?: emptyList()

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "d3"
            events[2].data.note shouldBe "c3"
            events[3].data.note shouldBe "d3"
        }
    }

    "timeLoop() top-level PatternMapperFn in compiled code" {
        val p = SprudelPattern.compile("""note("c3 d3 e3 f3").apply(timeLoop(0.5))""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.part.begin } ?: emptyList()

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "d3"
            events[2].data.note shouldBe "c3"
            events[3].data.note shouldBe "d3"
        }
    }

    "apply(timeLoop()) chains correctly" {
        // seq("c3 d3 e3 f3"): [0,0.25]=c3, [0.25,0.5]=d3, [0.5,0.75]=e3, [0.75,1]=f3
        // timeLoop(0.5): window [0,0.5] -> loops c3 and d3
        // querying [0,1]: c3, d3, c3, d3
        val p = note("c3 d3 e3 f3").apply(timeLoop(0.5))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "d3"
            events[2].data.note shouldBe "c3"
            events[3].data.note shouldBe "d3"
        }
    }

    "apply(timeLoop().timeLoop()) chains two timeLoop mappers" {
        // timeLoop(1.0) on a 1-cycle pattern is a no-op; then timeLoop(0.5) loops within 0.5 cycles
        // result is identical to plain timeLoop(0.5)
        val p = note("c3 d3 e3 f3").apply(timeLoop(1.0).timeLoop(0.5))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "d3"
            events[2].data.note shouldBe "c3"
            events[3].data.note shouldBe "d3"
        }
    }

    "script apply(PatternMapperFn.timeLoop()) chains" {
        val p = SprudelPattern.compile("""note("c3 d3 e3 f3").apply(timeLoop(0.5))""")!!
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "d3"
            events[2].data.note shouldBe "c3"
            events[3].data.note shouldBe "d3"
        }
    }
})
