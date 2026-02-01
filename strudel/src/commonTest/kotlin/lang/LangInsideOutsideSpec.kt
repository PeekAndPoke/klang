package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON

class LangInsideOutsideSpec : StringSpec({

    "inside() is equivalent to slow().transform().fast()" {
        val original = note("c d e f")

        val withInside = original.inside(2) { it.rev() }
        val manually = original.slow(2).rev().fast(2)

        val insideEvents = withInside.queryArc(0.0, 1.0)
        val manualEvents = manually.queryArc(0.0, 1.0)

        insideEvents.size shouldBe manualEvents.size
        insideEvents.zip(manualEvents).forEach { (ie, me) ->
            ie.data.note shouldBeEqualIgnoringCase me.data.note!!
            ie.part.begin.toDouble() shouldBe (me.part.begin.toDouble() plusOrMinus EPSILON)
            ie.part.end.toDouble() shouldBe (me.part.end.toDouble() plusOrMinus EPSILON)
        }
    }

    "inside() with fast transformation" {
        val p = note("c d").inside(2) { it.fast(2) }
        val events = p.queryArc(0.0, 1.0)

        // Pattern is slowed by 2, sped up by 2, then sped back up by 2
        // Net effect: 2x faster than original
        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[2].data.note shouldBeEqualIgnoringCase "c"
        events[3].data.note shouldBeEqualIgnoringCase "d"
    }

    "outside() is equivalent to fast().transform().slow()" {
        val original = note("c d e f")

        val withOutside = original.outside(2) { it.rev() }
        val manually = original.fast(2).rev().slow(2)

        val outsideEvents = withOutside.queryArc(0.0, 1.0)
        val manualEvents = manually.queryArc(0.0, 1.0)

        outsideEvents.size shouldBe manualEvents.size
        outsideEvents.zip(manualEvents).forEach { (oe, me) ->
            oe.data.note shouldBeEqualIgnoringCase me.data.note!!
            oe.part.begin.toDouble() shouldBe (me.part.begin.toDouble() plusOrMinus EPSILON)
            oe.part.end.toDouble() shouldBe (me.part.end.toDouble() plusOrMinus EPSILON)
        }
    }

    "inside() with identity function does nothing" {
        val p = note("c d e f").inside(2) { it }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[2].data.note shouldBeEqualIgnoringCase "e"
        events[3].data.note shouldBeEqualIgnoringCase "f"
    }

    "outside() with identity function does nothing" {
        val p = note("c d e f").outside(2) { it }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[2].data.note shouldBeEqualIgnoringCase "e"
        events[3].data.note shouldBeEqualIgnoringCase "f"
    }
})
