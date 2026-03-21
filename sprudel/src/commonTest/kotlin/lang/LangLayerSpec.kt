package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangLayerSpec : StringSpec({

    "layer dsl interface" {
        val pat = "c e"
        val transform: PatternMapperFn = { it.note("e") }
        dslInterfaceTests(
            "pattern.layer(fn)" to note(pat).layer(transform),
            "script pattern.layer(fn)" to StrudelPattern.compile("""note("$pat").layer(x => x.note("e"))"""),
            "string.layer(fn)" to pat.layer(transform),
            "script string.layer(fn)" to StrudelPattern.compile(""""$pat".layer(x => x.note("e"))"""),
            "layer(fn)" to note(pat).apply(layer(transform)),
            "script layer(fn)" to StrudelPattern.compile("""note("$pat").apply(layer(x => x.note("e")))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 2  // layer with one fn returns just the transformed pattern
        }
    }

    "apply dsl interface" {
        val pat = "c e"
        val transform: PatternMapperFn = { it.note("e") }
        dslInterfaceTests(
            "pattern.apply(fn)" to note(pat).apply(transform),
            "script pattern.apply(fn)" to StrudelPattern.compile("""note("$pat").apply(x => x.note("e"))"""),
            "string.apply(fn)" to pat.apply(transform),
            "script string.apply(fn)" to StrudelPattern.compile(""""$pat".apply(x => x.note("e"))"""),
            "apply(fn)" to note(pat).apply(apply(transform)),
            "script apply(fn)" to StrudelPattern.compile("""note("$pat").apply(apply(x => x.note("e")))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 2
        }
    }

    "layer() should stack results of multiple transformations" {
        // layer(original, fast(2)) should be equivalent to superimpose(fast(2))
        val p = note("a").layer(
            { it },
            { it.note("b") }
        )

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.any { it.data.note?.lowercase() == "a" } shouldBe true
        events.any { it.data.note?.lowercase() == "b" } shouldBe true
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
