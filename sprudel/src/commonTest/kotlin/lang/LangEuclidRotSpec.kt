package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangEuclidRotSpec : StringSpec({

    "euclidRot dsl interface" {
        val pat = "hh"
        dslInterfaceTests(
            "pattern.euclidRot(3, 8, 1)" to s(pat).euclidRot(3, 8, 1),
            "script pattern.euclidRot(3, 8, 1)" to SprudelPattern.compile("""s("$pat").euclidRot(3, 8, 1)"""),
            "string.euclidRot(3, 8, 1)" to pat.euclidRot(3, 8, 1),
            "script string.euclidRot(3, 8, 1)" to SprudelPattern.compile(""""$pat".euclidRot(3, 8, 1)"""),
            "euclidRot(3, 8, 1)" to s(pat).apply(euclidRot(3, 8, 1)),
            "script euclidRot(3, 8, 1)" to SprudelPattern.compile("""s("$pat").apply(euclidRot(3, 8, 1))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 3
        }
    }

    "euclidrot dsl interface" {
        val pat = "hh"
        dslInterfaceTests(
            "pattern.euclidrot(3, 8, 1)" to s(pat).euclidrot(3, 8, 1),
            "script pattern.euclidrot(3, 8, 1)" to SprudelPattern.compile("""s("$pat").euclidrot(3, 8, 1)"""),
            "string.euclidrot(3, 8, 1)" to pat.euclidrot(3, 8, 1),
            "script string.euclidrot(3, 8, 1)" to SprudelPattern.compile(""""$pat".euclidrot(3, 8, 1)"""),
            "euclidrot(3, 8, 1)" to s(pat).apply(euclidrot(3, 8, 1)),
            "script euclidrot(3, 8, 1)" to SprudelPattern.compile("""s("$pat").apply(euclidrot(3, 8, 1))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 3
        }
    }

    "euclidRot(3, 8, 1) rotates the rhythm" {
        // 3,8 is 10010010
        // Rotating by 1 (right shift in Strudel logic usually) -> 01001001
        // Indices: 1, 4, 7
        // Times: 0.125, 0.5, 0.875

        val p = note("a").euclidRot(3, 8, 1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe 0.125
        events[1].part.begin.toDouble() shouldBe 0.5
        events[2].part.begin.toDouble() shouldBe 0.875
    }

    "euclidRot works as top-level function" {
        val p = euclidRot(3, 8, 1, note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe 0.125
    }

    "euclidRot works as string extension" {
        val p = "a".euclidRot(3, 8, 1)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe 0.125
    }
})
