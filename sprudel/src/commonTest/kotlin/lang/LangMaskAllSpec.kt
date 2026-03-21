package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangMaskAllSpec : StringSpec({

    "maskAll dsl interface" {
        val pat = "c e"
        val ctrl = "x ~"
        dslInterfaceTests(
            "pattern.maskAll(ctrl)" to note(pat).maskAll(ctrl),
            "script pattern.maskAll(ctrl)" to SprudelPattern.compile("""note("$pat").maskAll("$ctrl")"""),
            "string.maskAll(ctrl)" to pat.maskAll(ctrl),
            "script string.maskAll(ctrl)" to SprudelPattern.compile(""""$pat".maskAll("$ctrl")"""),
            "maskAll(ctrl)" to note(pat).apply(maskAll(ctrl)),
            "script maskAll(ctrl)" to SprudelPattern.compile("""note("$pat").apply(maskAll("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
        }
    }

    "maskAll() keeps source events regardless of mask truthiness (only checks existence)" {
        // note("c e").maskAll("x 0") -> keeps both because '0' is still an event
        val p = note("c e").maskAll("x 0")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "e"
    }

    "maskAll() filters if there is silence (no event) in the mask" {
        val p = note("c e").maskAll("x ~")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "maskAll() as PatternMapperFn works" {
        val p = note("c e").apply(maskAll("x 0"))

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
    }

    "maskAll() as string extension works" {
        val p = "c e".maskAll("x ~")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBeEqualIgnoringCase "c"
    }

    "maskAll() works in compiled code" {
        val p = SprudelPattern.compile("""note("c e").maskAll("x 0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
    }
})
