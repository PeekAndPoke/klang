package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile
import io.peekandpoke.klang.strudel.lang.*

class WhenPatternSpec : FunSpec({

    test("when() should apply transformation when condition is truthy") {
        val pat = note("c3 d3 e3 f3")
            .`when`(pure(1).struct("t ~ t ~")) { it.transpose(12) }

        val events = pat.queryArc(0.0, 1.0)
        events.size shouldBe 4

        // First and third notes should be transformed
        events[0].data.note shouldBeEqualIgnoringCase "c4"  // c + 12
        events[1].data.note shouldBeEqualIgnoringCase "d3"   // d (unchanged)
        events[2].data.note shouldBeEqualIgnoringCase "e4"  // e + 12
        events[3].data.note shouldBeEqualIgnoringCase "f3"   // f (unchanged)
    }

    test("when() should keep events unchanged when condition is falsy") {
        val pat = note("c d e f").`when`(pure(0)) { it.transpose(12) }

        val events = pat.queryArc(0.0, 1.0)
        events.size shouldBe 4

        // All notes should be unchanged (condition always false)
        events.map { it.data.note } shouldBe listOf("c", "d", "e", "f")
    }

    test("when() should work with alternating condition") {
        val pat = compile(
            """
                note("c3 d3 e3 f3").when(pure(1).slowcat(pure(0)), x => x.transpose(12))
            """.trimIndent()
        )!!

        // Cycle 0: condition is true
        val events0 = pat.queryArc(0.0, 1.0)
        events0.map { it.data.note?.lowercase() } shouldBe listOf("c4", "d4", "e4", "f4")

        // Cycle 1: condition is false
        val events1 = pat.queryArc(1.0, 2.0)
        events1.map { it.data.note?.lowercase() } shouldBe listOf("c3", "d3", "e3", "f3")
    }
})
