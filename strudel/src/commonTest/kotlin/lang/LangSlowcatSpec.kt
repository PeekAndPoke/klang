package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile

class LangSlowcatSpec : FunSpec({
    test("slowcat cycles through 2 patterns") {
        // Create 2 simple patterns
        val pat1 = compile("""pure(1).struct("x ~")""")!! // 1 at 0.0-0.5
        val pat2 = compile("""pure(2).struct("x ~")""")!! // 2 at 0.0-0.5

        // Use applySlowcat
        val result = applySlowcat(listOf(pat1, pat2))

        // Query 4 cycles
        val events = result.queryArc(0.0, 4.0)

        // Cycle 0: should use pat1 (value=1)
        // Cycle 1: should use pat2 (value=2)
        // Cycle 2: should use pat1 (value=1) ← cycles back!
        // Cycle 3: should use pat2 (value=2)

        events shouldHaveSize 4
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
        events[2].data.value?.asInt shouldBe 1 // Key test: cycles back!
        events[3].data.value?.asInt shouldBe 2
    }

    test("slowcat with 3 patterns cycles correctly") {
        val pat1 = compile("""pure(1).struct("x ~ ~")""")!!
        val pat2 = compile("""pure(2).struct("x ~ ~")""")!!
        val pat3 = compile("""pure(3).struct("x ~ ~")""")!!

        val result = applySlowcat(listOf(pat1, pat2, pat3))
        val events = result.queryArc(0.0, 6.0)

        // Should cycle: 1, 2, 3, 1, 2, 3
        events.map { it.data.value?.asInt } shouldBe listOf(1, 2, 3, 1, 2, 3)
    }

    test("slowcat handles single pattern") {
        val pat = compile("""pure(42).struct("x ~")""")!!
        val result = applySlowcat(listOf(pat))

        val events = result.queryArc(0.0, 2.0)

        events shouldHaveSize 2
        events.all { it.data.value?.asInt == 42 } shouldBe true
    }

    test("slowcat handles empty list") {
        val result = applySlowcat(emptyList())

        val events = result.queryArc(0.0, 2.0)

        events shouldHaveSize 0
    }
})
