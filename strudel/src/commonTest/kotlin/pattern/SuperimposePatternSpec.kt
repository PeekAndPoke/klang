package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.s
import io.peekandpoke.klang.strudel.lang.superimpose

class SuperimposePatternSpec : StringSpec({

    "superimpose() should layer a transformed pattern over the original" {
        // Given: a pattern "a" superposed with a version of itself that has note "b"
        val p = note("a").superimpose { it.note("b") }

        val events = p.queryArc(0.0, 1.0)

        // Should have 2 events at the same time
        events.size shouldBe 2
        events.any { it.data.note == "a" } shouldBe true
        events.any { it.data.note == "b" } shouldBe true

        events.forEach {
            it.begin.toDouble() shouldBe 0.0
            it.end.toDouble() shouldBe 1.0
        }
    }

    "superimpose() with fast(2) should create more events" {
        val p = s("bd").superimpose { it.fast(2.0) }

        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // 1 original bd + 2 fast bds = 3 events
        events.size shouldBe 3

        // Original
        events.count { it.begin.toDouble() == 0.0 && it.end.toDouble() == 1.0 } shouldBe 1
        // Fast ones
        events.count { it.begin.toDouble() == 0.0 && it.end.toDouble() == 0.5 } shouldBe 1
        events.count { it.begin.toDouble() == 0.5 && it.end.toDouble() == 1.0 } shouldBe 1
    }

    "superimpose() works within compiled code" {
        // We compile code that uses superimpose with a transformation function
        val p = StrudelPattern.compile(
            """
            note("a").superimpose(p => p.note("b"))
        """.trimIndent()
        )

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.any { it.data.note == "a" } shouldBe true
        events.any { it.data.note == "b" } shouldBe true
    }
})
