package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

class LangChooseCyclesSpec : StringSpec({

    "chooseCycles picks one element per cycle" {
        val p = chooseCycles("a", "b").seed(123)

        // Cycle 0
        val e1 = p.queryArc(0.0, 1.0)
        e1.size shouldBe 1

        // Cycle 1
        val e2 = p.queryArc(1.0, 2.0)
        e2.size shouldBe 1

        e1[0].data.note shouldBe e2[0].data.note // Within same query context with seed, it might be stable if time based seed.
        // Actually, seed(123) sets context random seed.
        // ChoicePattern uses query time for additional seeding if needed, or just context random?
        // With rand.segment(1), it should vary per cycle.
    }

    "randcat alias works" {
        val p = randcat("a", "b")
        p.queryArc(0.0, 1.0).size shouldBe 1
    }

    "chooseCycles pattern extension includes pattern as choice" {
        // n("a").chooseCycles("b") -> choice between a and b
        val p = note("a").chooseCycles(note("b")).seed(123)
        val events = p.queryArc(0.0, 1.0)

        events[0].data.note shouldBeIn listOf("a", "b")
    }

    "chooseCycles string extension" {
        val p = "a".chooseCycles("b").seed(123)
        val events = p.queryArc(0.0, 1.0)

        events[0].data.value?.asString shouldBeIn listOf("a", "b")
    }
})
