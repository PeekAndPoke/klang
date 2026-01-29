package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LangWhenSimpleSpec : FunSpec({

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

    test("chunk() works with direct function calls") {
        val pat = seq("0 1 2 3").chunk(4) { it.add(12) }

        val events0 = pat.queryArc(0.0, 1.0)
        events0.size shouldBe 4

        // First note should be transformed (first quarter in cycle 0)
        events0[0].data.value?.asInt shouldBe 12  // 0 + 12
        events0[1].data.value?.asInt shouldBe 1
        events0[2].data.value?.asInt shouldBe 2
        events0[3].data.value?.asInt shouldBe 3
    }
})
