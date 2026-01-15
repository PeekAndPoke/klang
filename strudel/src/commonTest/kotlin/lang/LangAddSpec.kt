package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangAddSpec : StringSpec({

    "add() adds amount to numeric values" {
        // seq("0 1") -> value=0, value=1
        // add("2") -> value=2, value=3
        val p = seq("0 1").add("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }

    "add() works as string extension" {
        // "0 1".add("2") -> value=2, value=3
        val p = "0 1".add("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }

    "add() works as top-level function " {
        val p = add("2")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "add() concatenates strings" {
        // seq("a").add("b") -> value="ab"
        val p = note("a").add("b")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "a"
        events[0].data.value?.asString shouldBe null
    }

    "add() works with scale logic when placed before scale" {
        // seq("0 2").add("1").scale("C4:major")
        // seq("0 2") -> 0, 2
        // add("1") -> 1, 3
        // scale uses these values as degrees
        // 1 -> D4, 3 -> F4
        val p = seq("0 2").add("1").scale("C4:major")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBe "D4"
        events[1].data.note shouldBe "F4"
    }

    "add() works in compiled code" {
        val p = StrudelPattern.compile("""seq("0 1").add("2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 3
    }
})
