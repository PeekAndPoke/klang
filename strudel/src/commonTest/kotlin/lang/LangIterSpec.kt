package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangIterSpec : StringSpec({

    "iter() shifts pattern each cycle" {
        val p = note("c d e f").iter(4)

        // Cycle 0
        val cycle0 = p.queryArc(0.0, 1.0)
        cycle0.map { it.data.note } shouldBe listOf("c", "d", "e", "f")

        // Cycle 1 should be shifted
        val cycle1 = p.queryArc(1.0, 2.0)
        cycle1.map { it.data.note } shouldBe listOf("d", "e", "f", "c")
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
})
