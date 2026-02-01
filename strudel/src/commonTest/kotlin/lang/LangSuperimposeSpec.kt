package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSuperimposeSpec : StringSpec({

    "superimpose() should layer a transformed pattern over the original" {
        // Given: a pattern "a" superposed with a version of itself that has note "b"
        val p = note("a").superimpose { it.note("b") }

        val events = p.queryArc(0.0, 1.0)

        // Should have 2 events at the same time
        events.size shouldBe 2
        events.any { it.data.note?.lowercase() == "a" } shouldBe true
        events.any { it.data.note?.lowercase() == "b" } shouldBe true

        events.forEach {
            it.part.begin.toDouble() shouldBe 0.0
            it.part.end.toDouble() shouldBe 1.0
        }
    }

    "superimpose() with fast(2) should create more events" {
        val p = s("bd").superimpose { it.fast(2.0) }

        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // 1 original bd + 2 fast bds = 3 events
        events.size shouldBe 3

        // Original
        events.count { it.part.begin.toDouble() == 0.0 && it.part.end.toDouble() == 1.0 } shouldBe 1
        // Fast ones
        events.count { it.part.begin.toDouble() == 0.0 && it.part.end.toDouble() == 0.5 } shouldBe 1
        events.count { it.part.begin.toDouble() == 0.5 && it.part.end.toDouble() == 1.0 } shouldBe 1
    }

    "superimpose() works within compiled code" {
        // We compile code that uses superimpose with a transformation function
        val p = StrudelPattern.Companion.compile(
            """
            note("a").superimpose(p => p.note("b"))
        """.trimIndent()
        )

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.any { it.data.note?.lowercase() == "a" } shouldBe true
        events.any { it.data.note?.lowercase() == "b" } shouldBe true
    }
})
