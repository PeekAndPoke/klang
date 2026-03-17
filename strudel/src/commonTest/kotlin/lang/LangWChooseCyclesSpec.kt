package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangWChooseCyclesSpec : StringSpec({

    "wchooseCycles works" {
        // Extension form: "a" is the base pattern (weight 1.0), "b" weight 0 → always picks "a"
        val p = "a".wchooseCycles(listOf("b", 0)).seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "a"
    }

    "wrandcat alias works" {
        // Extension form: "a" is the base pattern (weight 1.0), "b" weight 0 → always picks "a"
        val p = "a".wrandcat(listOf("b", 0)).seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "a"
    }

    "wchooseCycles pattern extension" {
        // p("c") is added as first choice with weight 1.
        // "a" weight 0, "b" weight 0.
        // Should pick "c"
        val p = seq("c").wchooseCycles(listOf("a", 0), listOf("b", 0)).seed(123)
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "c"
    }

    "wchooseCycles string extension" {
        val p = "c".wchooseCycles(listOf("a", 0), listOf("b", 0)).seed(123)
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "c"
    }

    "wrandcat string extension" {
        val p = "c".wrandcat(listOf("a", 0), listOf("b", 0)).seed(123)
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "c"
    }
})
