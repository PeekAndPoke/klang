package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note

class LangStretchBySpec : StringSpec({

    "stretchBy dsl interface" {
        dslInterfaceTests(
            "pattern.stretchBy(factor)" to
                    note("c3").stretchBy(2.0),
            "script pattern.stretchBy(factor)" to
                    SprudelPattern.compile("""note("c3").stretchBy(2.0)"""),
            "string.stretchBy(factor)" to
                    "c3".stretchBy(2.0),
            "script string.stretchBy(factor)" to
                    SprudelPattern.compile(""""c3".stretchBy(2.0)"""),
            "stretchBy(factor)" to
                    note("c3").apply(stretchBy(2.0)),
            "script stretchBy(factor)" to
                    SprudelPattern.compile("""note("c3").apply(stretchBy(2.0))"""),
        ) { _, events ->
            val onsets = events.filter { it.isOnset }
            onsets shouldHaveSize 1
            onsets[0].part.duration.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        }
    }

    "stretchBy(2.0) doubles each event's duration" {
        val p = note("c3 d3").stretchBy(2.0)
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            events shouldHaveSize 2

            events[0].data.note shouldBe "c3"
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

            events[1].data.note shouldBe "d3"
            events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[1].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "stretchBy(0.5) halves each event's duration" {
        val p = note("c3 d3").stretchBy(0.5)
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            events shouldHaveSize 2

            events[0].data.note shouldBe "c3"
            events[0].part.duration.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

            events[1].data.note shouldBe "d3"
            events[1].part.duration.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        }
    }

    "stretchBy(1.0) is a no-op" {
        val p = note("c3 d3").stretchBy(1.0)
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            events shouldHaveSize 2
            events[0].part.duration.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[1].part.duration.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "stretchBy onset time is not affected by stretching" {
        val p = note("c3 d3").stretchBy(3.0)
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            events shouldHaveSize 2
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "stretchBy with control pattern alternates factor per cycle" {
        val p = note("c3").stretchBy("<1 2>")

        assertSoftly {
            kotlin.repeat(8) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = p.queryArc(cycleDbl, cycleDbl + 1.0).filter { it.isOnset }

                    events shouldHaveSize 1
                    val expectedDuration = if (cycle % 2 == 0) 1.0 else 2.0
                    events[0].part.duration.toDouble() shouldBe (expectedDuration plusOrMinus EPSILON)
                }
            }
        }
    }

    "stretchBy as string extension" {
        val p = "c3 d3".stretchBy(2.0)
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            events shouldHaveSize 2
            events[0].data.value?.asString shouldBe "c3"
            events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
            events[1].data.value?.asString shouldBe "d3"
            events[1].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "apply(stretchBy()) works as PatternMapperFn" {
        val p = note("c3 d3").apply(stretchBy(2.0))
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            events shouldHaveSize 2
            events[0].data.note shouldBe "c3"
            events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
            events[1].data.note shouldBe "d3"
            events[1].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "apply(stretchBy().stretchBy()) chains two stretchBy mappers" {
        // stretchBy(2) doubles duration; stretchBy(2) again doubles it again -> 4x total
        val p = note("c3").apply(stretchBy(2.0).stretchBy(2.0))
        val events = p.queryArc(0.0, 1.0).filter { it.isOnset }

        assertSoftly {
            events shouldHaveSize 1
            events[0].part.duration.toDouble() shouldBe (4.0 plusOrMinus EPSILON)
        }
    }

    "script apply(stretchBy()) works in compiled code" {
        val p = SprudelPattern.compile("""note("c3 d3").apply(stretchBy(2.0))""")
        val events = p?.queryArc(0.0, 1.0)?.filter { it.isOnset } ?: emptyList()

        assertSoftly {
            events shouldHaveSize 2
            events[0].data.note shouldBe "c3"
            events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "stretchBy in compiled code" {
        val p = SprudelPattern.compile("""note("c3 d3").stretchBy(2.0)""")
        val events = p?.queryArc(0.0, 1.0)?.filter { it.isOnset } ?: emptyList()

        assertSoftly {
            events shouldHaveSize 2
            events[0].data.note shouldBe "c3"
            events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }
})
