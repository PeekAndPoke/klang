package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangInsideSpec : StringSpec({

    "p.inside(1, x => x)" {

        val p = seq("0 1")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val values = events.map { it.data.value?.asInt }
                    println("Cycle $cycle: $values")

                    events.shouldHaveSize(2)

                    events[0].data.value?.asInt shouldBe 0
                    events[0].begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 1
                    events[1].begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[1].end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "p.inside(2, x => x.add(\"0 10\"))" {

        val p = seq("0 1 2 3")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner.add("0 10") }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val values = events.map { it.data.value?.asInt }

                    // Expected values: 0, 1, 12, 13
                    println("Cycle $cycle: $values")

                    events.shouldHaveSize(4)

                    events[0].data.value?.asInt shouldBe 0
                    events[0].begin.toDouble() shouldBe ((cycleDbl + 0.0) plusOrMinus EPSILON)
                    events[0].end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 1
                    events[1].begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].end.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)

                    events[2].data.value?.asInt shouldBe 12
                    events[2].begin.toDouble() shouldBe ((cycleDbl + 0.5) plusOrMinus EPSILON)
                    events[2].end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)

                    events[3].data.value?.asInt shouldBe 13
                    events[3].begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[3].end.toDouble() shouldBe ((cycleDbl + 1.0) plusOrMinus EPSILON)
                }
            }
        }
    }

    "p.inside(2, x => x.late(0.1))" {

        val p = seq("0 1 2 3")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner.late(0.1) }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val values = events.map { it.data.value?.asInt }

                    println("Cycle $cycle: $values")

                    events.shouldHaveSize(4)

                    events[0].data.value?.asInt shouldBe 0
                    events[0].begin.toDouble() shouldBe ((cycleDbl + 0.0 + 0.1) plusOrMinus EPSILON)
                    events[0].end.toDouble() shouldBe ((cycleDbl + 0.25 + 0.1) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 1
                    events[1].begin.toDouble() shouldBe ((cycleDbl + 0.25 + 0.1) plusOrMinus EPSILON)
                    events[1].end.toDouble() shouldBe ((cycleDbl + 0.5 + 0.1) plusOrMinus EPSILON)

                    events[2].data.value?.asInt shouldBe 2
                    events[2].begin.toDouble() shouldBe ((cycleDbl + 0.5 + 0.1) plusOrMinus EPSILON)
                    events[2].end.toDouble() shouldBe ((cycleDbl + 0.75 + 0.1) plusOrMinus EPSILON)

                    events[3].data.value?.asInt shouldBe 3
                    events[3].begin.toDouble() shouldBe ((cycleDbl + 0.75 + 0.1) plusOrMinus EPSILON)
                    events[3].end.toDouble() shouldBe ((cycleDbl + 1.0 + 0.1) plusOrMinus EPSILON)
                }
            }
        }
    }

    "p.inside(2, x => x.late(\"0 0.1\"))" {

        val p = seq("0 1 2 3")
        val transform: (StrudelPattern) -> StrudelPattern = { inner -> inner.late("0 0.1") }

        val subject = p.inside(1, transform)

        assertSoftly {
            repeat(12) { cycle ->

                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    val values = events.map {
                        listOf(it.begin.toDouble(), it.end.toDouble(), it.data.value?.asInt)
                    }

                    println("Cycle $cycle | ${events.size} events | $values")

                    events.shouldHaveSize(4)

                    events[0].data.value?.asInt shouldBe 0
                    events[0].begin.toDouble() shouldBe ((cycleDbl + 0.0 + 0.0) plusOrMinus EPSILON)
                    events[0].end.toDouble() shouldBe ((cycleDbl + 0.25 + 0.0) plusOrMinus EPSILON)

                    events[1].data.value?.asInt shouldBe 1
                    events[1].begin.toDouble() shouldBe ((cycleDbl + 0.25 + 0.0) plusOrMinus EPSILON)
                    events[1].end.toDouble() shouldBe ((cycleDbl + 0.5 + 0.0) plusOrMinus EPSILON)

                    events[2].data.value?.asInt shouldBe 2
                    events[2].begin.toDouble() shouldBe ((cycleDbl + 0.5 + 0.1) plusOrMinus EPSILON)
                    events[2].end.toDouble() shouldBe ((cycleDbl + 0.75 + 0.1) plusOrMinus EPSILON)

                    events[3].data.value?.asInt shouldBe 3
                    events[3].begin.toDouble() shouldBe ((cycleDbl + 0.75 + 0.1) plusOrMinus EPSILON)
                    events[3].end.toDouble() shouldBe ((cycleDbl + 1.0 + 0.1) plusOrMinus EPSILON)
                }
            }
        }
    }
})
