package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LangChunkDebugSpec : FunSpec({

    test("Debug: Check seq pattern") {
        // Create the binary pattern: [1, 0, 0, 0]
        val binaryPattern = seq(
            pure(1),
            pure(0),
            pure(0),
            pure(0)
        )

        val events = binaryPattern.queryArc(0.0, 1.0)
        println("Binary seq pattern:")
        println("  Count: ${events.size}")
        println("  Values: ${events.map { it.data.value?.asInt }}")
        println("  Positions: ${events.map { "(${it.begin.toDouble()}, ${it.end.toDouble()})" }}")

        events.size shouldBe 4
        events.map { it.data.value?.asInt } shouldBe listOf(1, 0, 0, 0)
    }

    test("Debug: Check iter(4) for cycle 0") {
        val binaryPattern = seq(
            pure(1),
            pure(0),
            pure(0),
            pure(0)
        ).iter(4)

        val events = binaryPattern.queryArc(0.0, 1.0)
        println("Binary WITH iter(4) - cycle 0:")
        println("  Count: ${events.size}")
        println("  Values: ${events.map { it.data.value?.asInt }}")
        println("  Positions: ${events.map { "(${it.begin.toDouble()}, ${it.end.toDouble()})" }}")

        events.size shouldBe 4
        // Cycle 0 should be [1, 0, 0, 0]
        events.map { it.data.value?.asInt } shouldBe listOf(1, 0, 0, 0)
    }

    test("Debug: Check iter(4) for cycle 1") {
        val binaryPattern = seq(
            pure(1),
            pure(0),
            pure(0),
            pure(0)
        ).iter(4)

        val events = binaryPattern.queryArc(1.0, 2.0)
        println("Binary WITH iter(4) - cycle 1:")
        println("  Count: ${events.size}")
        println("  Values: ${events.map { it.data.value?.asInt }}")
        println("  Positions: ${events.map { "(${it.begin.toDouble()}, ${it.end.toDouble()})" }}")

        events.size shouldBe 4
        // Cycle 1 actually gives [0, 0, 0, 1] - rotating BACKWARDS!
        events.map { it.data.value?.asInt } shouldBe listOf(0, 0, 0, 1)
    }

    test("Debug: Check iterBack(4) for cycle 1 - maybe this is forward?") {
        val binaryPattern = seq(
            pure(1),
            pure(0),
            pure(0),
            pure(0)
        ).iterBack(4)

        val events = binaryPattern.queryArc(1.0, 2.0)
        println("Binary WITH iterBack(4) - cycle 1:")
        println("  Count: ${events.size}")
        println("  Values: ${events.map { it.data.value?.asInt }}")
        println("  Positions: ${events.map { "(${it.begin.toDouble()}, ${it.end.toDouble()})" }}")

        events.size shouldBe 4
        // Test if iterBack gives us [0, 1, 0, 0] (forward rotation)
        events.map { it.data.value?.asInt } shouldBe listOf(0, 1, 0, 0)
    }
})