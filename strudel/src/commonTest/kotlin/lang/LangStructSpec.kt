package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangStructSpec : StringSpec({

    "struct() with mini-notation 'x ~ x' structures a pattern" {
        // Given: note "c e g" (3 items in cycle)
        // Struct: "x ~ x" (x at 0.0-0.33, silence, x at 0.66-1.0)
        val p = note("c e g").struct("x ~ x")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // We expect 2 events:
        // 1. 'c' from the first 'x' slot (0.0 to 0.33)
        // 2. 'g' from the second 'x' slot (0.66 to 1.0)
        events.size shouldBe 2

        events[0].data.note shouldBe "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)

        events[1].data.note shouldBe "g"
        events[1].begin.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "struct() as a standalone function" {
        val p = struct("x x", note("c e"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.note } shouldBe listOf("c", "e")
    }

    "struct() as extension on String" {
        val p = "c e".struct("x")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "c"
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.value?.asString shouldBe "e"
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "struct() works in compiled code" {
        val p = StrudelPattern.compile("""note("c e g").struct("x ~ x")""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBe "c"
        events[1].data.note shouldBe "g"
    }
})
