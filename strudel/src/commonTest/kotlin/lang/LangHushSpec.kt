package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHushSpec : StringSpec({

    "hush() returns silence with no arguments" {
        val p = hush()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() returns silence with arguments (ignores them)" {
        val p = hush("a", "b", 123)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() works in compiled code" {
        val p = StrudelPattern.compile("""hush()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 0
    }

    "hush() ignores arguments in compiled code" {
        val p = StrudelPattern.compile("""hush(note("c d"), "anything", 42)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 0
    }

    "hush() works as method on StrudelPattern" {
        // note("c d").hush() -> silence
        val p = note("c d").hush()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() works as method on StrudelPattern with arguments" {
        // note("c d").hush("a", 123) -> silence (ignores all)
        val p = note("c d").hush("a", 123)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() works as extension on String" {
        // "a b c".hush()
        val p = "a b c".hush()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() works as extension on String with arguments" {
        // "a b c".hush("x", "y")
        val p = "a b c".hush("x", "y")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() works as method in compiled code" {
        // note("c d").hush()
        val p = StrudelPattern.compile("""note("c d").hush()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 0
    }

    "hush() works as string extension in compiled code" {
        // "a b c".hush()
        val p = StrudelPattern.compile(""""a b c".hush()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 0
    }
})
