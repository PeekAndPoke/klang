package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangSuperimposeSpec : StringSpec({

    "superimpose dsl interface" {
        val pat = "c e"
        val transform: PatternMapperFn = { it.note("e") }
        dslInterfaceTests(
            "pattern.superimpose(fn)" to note(pat).superimpose(transform),
            "script pattern.superimpose(fn)" to SprudelPattern.compile("""note("$pat").superimpose(x => x.note("e"))"""),
            "string.superimpose(fn)" to pat.superimpose(transform),
            "script string.superimpose(fn)" to SprudelPattern.compile(""""$pat".superimpose(x => x.note("e"))"""),
            "superimpose(fn)" to note(pat).apply(superimpose(transform)),
            "script superimpose(fn)" to SprudelPattern.compile("""note("$pat").apply(superimpose(x => x.note("e")))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 4  // original 2 + transformed copy 2
        }
    }

    "superimpose() should layer a transformed pattern over the original" {
        // Given: a pattern "a" superposed with a version of itself that has note "b"
        val p = note("a").superimpose({ it.note("b") })

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
        val p = s("bd").superimpose({ it.fast(2.0) })

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
        val p = SprudelPattern.compile(
            """
                note("a").superimpose(p => p.note("b"))
            """.trimIndent()
        )

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.any { it.data.note?.lowercase() == "a" } shouldBe true
        events.any { it.data.note?.lowercase() == "b" } shouldBe true
    }

    "superimpose(rev()) and superimpose(x => x.rev()) produce the same events" {
        val pMapper = note("a b c d").superimpose(rev())
        val pLambda = note("a b c d").superimpose({ it.rev() })
        val pScript = SprudelPattern.compile("""note("a b c d").superimpose(x => x.rev())""")
        val pScriptMapper = SprudelPattern.compile("""note("a b c d").superimpose(rev())""")

        val expected = pMapper.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        expected.size shouldBe 8

        for ((label, p) in listOf("lambda" to pLambda, "script-lambda" to pScript, "script-mapper" to pScriptMapper)) {
            val events = (p ?: error("$label: compile failed")).queryArc(0.0, 1.0).sortedBy { it.part.begin }
            events.map { it.data.note } shouldBe expected.map { it.data.note }
        }
    }

    "superimpose(x => x.rev()) with slow() works correctly in script" {
        val pDirect = SprudelPattern.compile("""note("a b c d e f g a").slow(4).superimpose(rev())""")
        val pLambda = SprudelPattern.compile("""note("a b c d e f g a").slow(4).superimpose(x => x.rev())""")

        val expected = pDirect!!.queryArc(0.0, 4.0).sortedBy { it.part.begin }
        val actual = pLambda!!.queryArc(0.0, 4.0).sortedBy { it.part.begin }

        actual.map { "${it.data.note}@${it.part.begin}" } shouldBe expected.map { "${it.data.note}@${it.part.begin}" }
    }
})
