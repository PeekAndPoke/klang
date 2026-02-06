package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile

class LangWhenSpec : FunSpec({

    test("when() works with direct function calls") {
        val pat = note("c3 d3 e3 f3")
            .`when`(pure(1)) { it.transpose(12) }

        val events = pat.queryArc(0.0, 1.0)
        println("Events count: ${events.size}")
        println("Events: ${events.map { it.data.note }}")
        events.size shouldBe 4

        // All notes should be transformed (condition always true)
        events.map { it.data.note?.lowercase() } shouldBe listOf("c4", "d4", "e4", "f4")
    }

    test("when() works with mini-notation") {
        val pat = note("c3 d3 e3 f3")
            .`when`("1 0 1 0") { it.transpose(12) }

        val events = pat.queryArc(0.0, 1.0)
        println("Events count: ${events.size}")
        println("Events: ${events.map { it.data.note }}")
        events.size shouldBe 4

        // All notes should be transformed (condition always true)
        events.map { it.data.note?.lowercase() } shouldBe listOf("c4", "d3", "e4", "f3")
    }

    test("when() should apply transformation when condition is truthy") {
        val pat = note("c3 d3 e3 f3")
            .`when`(seq("1 0 1 0")) { it.transpose(12) }

        val events = pat.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 4
            // First and third notes should be transformed
            events[0].data.note shouldBeEqualIgnoringCase "c4" // c + 12
            events[1].data.note shouldBeEqualIgnoringCase "d3" // d
            events[2].data.note shouldBeEqualIgnoringCase "e4" // e + 12
            events[3].data.note shouldBeEqualIgnoringCase "f3" // f
        }
    }

    test("chunk() works with direct function calls") {
        val pat = seq("0 1 2 3").chunk(4) { it.add(12) }

        val events0 = pat.queryArc(0.0, 1.0)
        events0.size shouldBe 4

        // First note should be transformed (first quarter in cycle 0)
        events0[0].data.value?.asInt shouldBe 12 // 0 + 12
        events0[1].data.value?.asInt shouldBe 1
        events0[2].data.value?.asInt shouldBe 2
        events0[3].data.value?.asInt shouldBe 3
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

    test("when() with cycling binary control [1,0,0,0].iter(4) over 12 cycles") {
        val source = seq("0 1 2 3").repeatCycles(4)
        val binaryControl = seq("1 0 0 0").iter(4)
        val transformed = source.`when`(binaryControl) { it.add(10) }

        println("\n=== when() with cycling binary control ===")
        for (cycle in 0..11) {
            val events = transformed.queryArc(cycle.toDouble(), (cycle + 1).toDouble())
            val values = events.map { it.data.value?.asInt }
            println("Cycle $cycle: $values")

            events.size shouldBe 4

            // Identify which position was transformed
            val transformedPositions = values.mapIndexed { idx, value ->
                if (value != null && value >= 10) idx else null
            }.filterNotNull()

            println("  -> Transformed positions: $transformedPositions")
        }
    }

    test("when() with cycling binary control [1,0,0,0].iterBack(4) over 12 cycles") {
        val source = seq("0 1 2 3").repeatCycles(4)
        val binaryControl = seq("1 0 0 0").iterBack(4)
        val transformed = source.`when`(binaryControl) { it.add(10) }

        println("\n=== when() with iterBack binary control ===")
        for (cycle in 0..11) {
            val events = transformed.queryArc(cycle.toDouble(), (cycle + 1).toDouble())
            val values = events.map { it.data.value?.asInt }
            println("Cycle $cycle: $values")

            events.size shouldBe 4

            // Identify which position was transformed
            val transformedPositions = values.mapIndexed { idx, value ->
                if (value != null && value >= 10) idx else null
            }.filterNotNull()

            println("  -> Transformed positions: $transformedPositions")
        }
    }
})
