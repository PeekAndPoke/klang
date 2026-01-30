package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangIterSpec : StringSpec({

    "iter() shifts pattern each cycle" {
        val p = note("c d e f").iter(4)

        assertSoftly {
            withClue("Cycle 0") {
                val events = p.queryArc(0.0, 1.0)
                events.map { it.data.note } shouldBe listOf("c", "d", "e", "f")
            }
            withClue("Cycle 1 should be shifted") {
                val events = p.queryArc(1.0, 2.0)
                events.map { it.data.note } shouldBe listOf("d", "e", "f", "c")
            }
            withClue("Cycle 2 should be shifted") {
                val events = p.queryArc(2.0, 3.0)
                events.map { it.data.note } shouldBe listOf("e", "f", "c", "d")
            }
            withClue("Cycle 3 should be shifted") {
                val events = p.queryArc(3.0, 4.0)
                events.map { it.data.note } shouldBe listOf("f", "c", "d", "e")
            }
            // //
            withClue("Cycle 4") {
                val events = p.queryArc(4.0, 5.0)
                events.map { it.data.note } shouldBe listOf("c", "d", "e", "f")
            }
            withClue("Cycle 5 should be shifted") {
                val events = p.queryArc(5.0, 6.0)
                events.map { it.data.note } shouldBe listOf("d", "e", "f", "c")
            }
            withClue("Cycle 6 should be shifted") {
                val events = p.queryArc(6.0, 7.0)
                events.map { it.data.note } shouldBe listOf("e", "f", "c", "d")
            }
            withClue("Cycle 7 should be shifted") {
                val events = p.queryArc(7.0, 8.0)
                events.map { it.data.note } shouldBe listOf("f", "c", "d", "e")
            }
            // //
            withClue("Cycle 8") {
                val events = p.queryArc(8.0, 9.0)
                events.map { it.data.note } shouldBe listOf("c", "d", "e", "f")
            }
        }

    }

    "iterBack() shifts pattern backward each cycle" {
        val p = note("c d e f").iterBack(4)

        // Cycle 0
        val cycle0 = p.queryArc(0.0, 1.0)
        cycle0.map { it.data.note } shouldBe listOf("c", "d", "e", "f")

        // Cycle 1 should be shifted backward
        val cycle1 = p.queryArc(1.0, 2.0)
        cycle1.map { it.data.note } shouldBe listOf("f", "c", "d", "e")
    }

    "iter(4) with binary pattern [1,0,0,0] over 12 cycles" {
        val binary = seq("1 0 0 0")
        val iterated = binary.iter(4)

        println("\n=== Binary pattern [1,0,0,0] with iter(4) ===")
        for (cycle in 0..11) {
            val events = iterated.queryArc(cycle.toDouble(), (cycle + 1).toDouble())
            val values = events.map { it.data.value?.asInt }
            println("Cycle $cycle: $values")

            events.size shouldBe 4

            // Track which position has the 1
            val positionOf1 = values.indexOf(1)
            println("  -> Position of '1': $positionOf1")
        }
    }

    "iterBack(4) with binary pattern [1,0,0,0] over 12 cycles" {
        val binary = seq("1 0 0 0")
        val iterated = binary.iterBack(4)

        println("\n=== Binary pattern [1,0,0,0] with iterBack(4) ===")
        for (cycle in 0..11) {
            val events = iterated.queryArc(cycle.toDouble(), (cycle + 1).toDouble())
            val values = events.map { it.data.value?.asInt }
            println("Cycle $cycle: $values")

            events.size shouldBe 4

            // Track which position has the 1
            val positionOf1 = values.indexOf(1)
            println("  -> Position of '1': $positionOf1")
        }
    }

    "CRITICAL: iter(4) behavior consistency - cycles 0-20" {
        val binary = seq("1 0 0 0")
        val iterated = binary.iter(4)

        println("\n=== CRITICAL TEST: iter(4) over 20 cycles ===")
        val positions = mutableListOf<Int>()

        for (cycle in 0..19) {
            val events = iterated.queryArc(cycle.toDouble(), (cycle + 1).toDouble())
            val values = events.map { it.data.value?.asInt }
            val positionOf1 = values.indexOf(1)
            positions.add(positionOf1)

            println("Cycle $cycle: $values -> Position: $positionOf1")
        }

        // Check if the pattern is consistent every 4 cycles
        println("\n=== Pattern analysis ===")
        for (i in 0..4) {
            val cyclesAtOffset = (0..19 step 4).map { it + i }.filter { it <= 19 }
            val positionsAtOffset = cyclesAtOffset.map { positions[it] }
            println("Cycles at offset $i: $cyclesAtOffset -> Positions: $positionsAtOffset")

            // All cycles at the same offset should have the same position
            val uniquePositions = positionsAtOffset.toSet()
            println("  -> Unique positions: $uniquePositions (should be 1)")
            uniquePositions.size shouldBe 1
        }
    }

    "CRITICAL: iterBack(4) behavior consistency - cycles 0-20" {
        val binary = seq("1 0 0 0")
        val iterated = binary.iterBack(4)

        println("\n=== CRITICAL TEST: iterBack(4) over 20 cycles ===")
        val positions = mutableListOf<Int>()

        for (cycle in 0..19) {
            val events = iterated.queryArc(cycle.toDouble(), (cycle + 1).toDouble())
            val values = events.map { it.data.value?.asInt }
            val positionOf1 = values.indexOf(1)
            positions.add(positionOf1)

            println("Cycle $cycle: $values -> Position: $positionOf1")
        }

        // Check if the pattern is consistent every 4 cycles
        println("\n=== Pattern analysis ===")
        for (i in 0..4) {
            val cyclesAtOffset = (0..19 step 4).map { it + i }.filter { it <= 19 }
            val positionsAtOffset = cyclesAtOffset.map { positions[it] }
            println("Cycles at offset $i: $cyclesAtOffset -> Positions: $positionsAtOffset")

            // All cycles at the same offset should have the same position
            val uniquePositions = positionsAtOffset.toSet()
            println("  -> Unique positions: $uniquePositions (should be 1)")
            uniquePositions.size shouldBe 1
        }
    }
})
