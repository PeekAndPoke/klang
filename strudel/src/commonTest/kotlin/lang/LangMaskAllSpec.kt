package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern

class LangMaskAllSpec : StringSpec({

    "maskAll() keeps source events regardless of mask truthiness (only checks existence)" {
        // note("c e").maskAll("x 0") -> keeps both because '0' is still an event
        val p = note("c e").maskAll("x 0")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

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

    "maskAll() top-level function works" {
        val p = maskAll("x 0", note("c e"))

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
        val p = StrudelPattern.compile("""note("c e").maskAll("x 0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
    }
})
