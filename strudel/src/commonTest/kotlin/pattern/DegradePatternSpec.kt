@file:Suppress("LocalVariableName")

package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.parser.MiniNotationParser
import io.peekandpoke.klang.strudel.lang.seed

class DegradePatternSpec : StringSpec() {

    fun parse(input: String) = MiniNotationParser(input) { note(it) }.parse()

    init {
        "Degrade (?): Deterministic behavior" {
            // "a?" should play roughly 50% of the time.
            // But crucially, for a specific cycle, it should ALWAYS be either present or absent.
            val pattern = parse("a?").seed(2)

            pattern.shouldBeInstanceOf<ContextModifierPattern>()

            val degrade = pattern.source
            degrade.shouldBeInstanceOf<DegradePattern>()
            degrade.probability shouldBe 0.5 plusOrMinus EPSILON

            // Check cycle 0 repeatedly - should always be consistent
            val eventsCycle0_run1 = pattern.queryArc(0.0, 1.0)
            val eventsCycle0_run2 = pattern.queryArc(0.0, 1.0)

            eventsCycle0_run1.size shouldBe eventsCycle0_run2.size
            if (eventsCycle0_run1.isNotEmpty()) {
                eventsCycle0_run1[0].data.note shouldBe "a"
            }

            // Check across many cycles to ensure roughly 50% distribution
            var count = 0
            val totalCycles = 1000
            for (i in 0 until totalCycles) {
                if (pattern.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                    count++
                }
            }

            // Allow for some variance, but it should be around 500
            count shouldBeInRange 400..600
        }

        "Degrade with probability high of dropping the event (?0.9)" {
            val pattern = parse("a?0.9")

            pattern.shouldBeInstanceOf<DegradePattern>()
            pattern.probability shouldBe 0.9 plusOrMinus EPSILON

            var count = 0
            val totalCycles = 1000
            for (i in 0 until totalCycles) {
                if (pattern.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                    count++
                }
            }
            // Expect around 100. Range 50-150.
            count shouldBeInRange 50..150
        }

        "Degrade with low probability of dropping the event (?0.1)" {
            val pattern = parse("a?0.1")

            pattern.shouldBeInstanceOf<DegradePattern>()
            pattern.probability shouldBe 0.1 plusOrMinus EPSILON

            var count = 0
            val totalCycles = 1000
            for (i in 0 until totalCycles) {
                if (pattern.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                    count++
                }
            }
            // Expect around 900. Range 850-950.
            count shouldBeInRange 850..950
        }
    }
}
