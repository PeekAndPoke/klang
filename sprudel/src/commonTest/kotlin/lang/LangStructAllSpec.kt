package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangStructAllSpec : StringSpec({

    "structAll dsl interface" {
        val pat = "c e"
        val ctrl = "x x"
        dslInterfaceTests(
            "pattern.structAll(ctrl)" to note(pat).structAll(ctrl),
            "script pattern.structAll(ctrl)" to SprudelPattern.compile("""note("$pat").structAll("$ctrl")"""),
            "string.structAll(ctrl)" to pat.structAll(ctrl),
            "script string.structAll(ctrl)" to SprudelPattern.compile(""""$pat".structAll("$ctrl")"""),
            "structAll(ctrl)" to note(pat).apply(structAll(ctrl)),
            "script structAll(ctrl)" to SprudelPattern.compile("""note("$pat").apply(structAll("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "structAll() keeps multiple source events within one structure step" {
        // Given: source "c e" (c at 0-0.5, e at 0.5-1.0)
        // Struct: "x" (active 0-1.0)
        val p = "c e".structAll("x")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // Unlike struct() which would pick only 'c', structAll keeps both
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "c"
        events[1].data.value?.asString shouldBe "e"
    }

    "structAll() clips source events to structure boundaries" {
        // Source: "c" (0-1)
        // Struct: "x ~" (active 0-0.5)
        val p = "c".structAll("x ~")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c"
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "structAll() works as PatternMapperFn" {
        val p = note("a b").apply(structAll("x"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
    }

    "structAll() works in compiled code" {
        val p = SprudelPattern.compile("""note("a b").structAll("x")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
    }
})
