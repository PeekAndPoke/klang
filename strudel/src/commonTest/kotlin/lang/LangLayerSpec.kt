package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLayerSpec : StringSpec({

    "layer() should stack results of multiple transformations" {
        // layer(original, fast(2)) should be equivalent to superimpose(fast(2))
        val p = note("a").layer(
            { it },
            { it.note("b") }
        )

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.any { it.data.note == "a" } shouldBe true
        events.any { it.data.note == "b" } shouldBe true
    }

    "layer() works with compiled code" {
        // The parser/interpreter needs to handle passing multiple lambdas or a list of lambdas?
        // In KlangScript/JS, arguments are just a list.
        // layer(x=>x.note("b"), x=>x.note("c"))

        val p = StrudelPattern.compile(
            """
            note("a").layer(
                x => x.note("b"),
                x => x.note("c")
            )
        """.trimIndent()
        )

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.any { it.data.note == "b" } shouldBe true
        events.any { it.data.note == "c" } shouldBe true
        // note("a") is NOT in the output because it wasn't one of the layers, unless explicitly added
    }

    "apply() is an alias for layer()" {
        val p = note("a").apply(
            { it.note("b") },
            { it.note("c") }
        )

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.any { it.data.note == "b" } shouldBe true
        events.any { it.data.note == "c" } shouldBe true
    }
})
