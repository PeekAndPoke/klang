package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.lang.note

class MiniNotationIntervalSpec : StringSpec({

    fun parse(input: String) = MiniNotationParser(input) { note(it) }.parse()

    "Parsing negative intervals '-2M'" {
        val pattern = parse("-2M")
        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "-2M"
    }

    "Parsing alternation with intervals '<1P -2M>'" {
        val pattern = parse("<1P -2M>")

        // Cycle 0: 1P
        val events0 = pattern.queryArc(0.0, 1.0)
        events0.size shouldBe 1
        events0[0].data.note shouldBe "1P"

        // Cycle 1: -2M
        val events1 = pattern.queryArc(1.0, 2.0)
        events1.size shouldBe 1
        events1[0].data.note shouldBe "-2M"
    }

    "Parsing sequence with intervals '1P -2M'" {
        val pattern = parse("1P -2M")

        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.note shouldBe "1P"
        events[1].data.note shouldBe "-2M"
    }
})
