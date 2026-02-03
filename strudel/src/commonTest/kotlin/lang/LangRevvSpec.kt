package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class LangRevvSpec : StringSpec({

    "revv() reverses the order of cycles" {
        // JavaScript test: fastcat('a', 'b', 'c', 'd').slow(2).revv().fast(2).sortHapsByPart().firstCycle()
        // should equal: fastcat('d', 'c', 'b', 'a').firstCycle()
        //
        // Breakdown:
        // - fastcat('a', 'b', 'c', 'd').slow(2) puts abcd over 2 cycles
        //   cycle 0-1: a[0, 0.5), b[0.5, 1.0)
        //   cycle 1-2: c[1.0, 1.5), d[1.5, 2.0)
        // - revv() reverses globally, mirroring across time 0
        //   cycle -2 to -1: c[-1.5, -1.0), d[-2.0, -1.5)
        //   cycle -1 to 0: a[-0.5, 0.0), b[-1.0, -0.5)
        // - fast(2) compresses back to 1 cycle
        //   cycle 0-1 contains: d, c, b, a

        val pattern = fastcat("a", "b", "c", "d").slow(2.toRational()).revv().fast(2.toRational())
        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events[0].data.value?.asString shouldBeEqualIgnoringCase "d"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "c"
        events[2].data.value?.asString shouldBeEqualIgnoringCase "b"
        events[3].data.value?.asString shouldBeEqualIgnoringCase "a"
    }

    "revv() with slow and fast reverses order" {
        // The JavaScript test uses: fastcat('a', 'b', 'c', 'd').slow(2).revv().fast(2)
        // This spreads abcd over 2 cycles, reverses globally, then speeds back up
        // Result should be: d, c, b, a in cycle 0

        val pattern = fastcat("a", "b", "c", "d")
            .slow(2.toRational())
            .revv()
            .fast(2.toRational())

        val events = pattern.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events[0].data.value?.asString shouldBeEqualIgnoringCase "d"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "c"
        events[2].data.value?.asString shouldBeEqualIgnoringCase "b"
        events[3].data.value?.asString shouldBeEqualIgnoringCase "a"
    }

    "revv() reverses multi-cycle patterns" {
        // Pattern spans 2 cycles: a in cycle 0, b in cycle 1
        val pattern = cat("a", "b").revv()

        // After revv:
        // - Original a at [0, 1) -> reversed to [-1, 0)
        // - Original b at [1, 2) -> reversed to [-2, -1)

        val eventsNeg1 = pattern.queryArc(-1.0, 0.0).sortedBy { it.part.begin }
        eventsNeg1.size shouldBe 1
        eventsNeg1[0].data.value?.asString shouldBeEqualIgnoringCase "a"

        val eventsNeg2 = pattern.queryArc(-2.0, -1.0).sortedBy { it.part.begin }
        eventsNeg2.size shouldBe 1
        eventsNeg2[0].data.value?.asString shouldBeEqualIgnoringCase "b"
    }

    "revv() works as pattern extension" {
        val pattern = note("a b c").revv()
        val events = pattern.queryArc(-1.0, 0.0).sortedBy { it.part.begin }

        // Original: a[0, 1/3), b[1/3, 2/3), c[2/3, 1)
        // After revv (querying [-1, 0)):
        //   c[2/3, 1) → [-1, -2/3)
        //   b[1/3, 2/3) → [-2/3, -1/3)
        //   a[0, 1/3) → [-1/3, 0)

        events.size shouldBe 3
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].part.begin.toDouble() shouldBe (-1.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (-2.0 / 3.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].part.begin.toDouble() shouldBe (-2.0 / 3.0 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (-1.0 / 3.0 plusOrMinus EPSILON)

        events[2].data.note shouldBeEqualIgnoringCase "a"
        events[2].part.begin.toDouble() shouldBe (-1.0 / 3.0 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "revv() works as string extension" {
        val pattern = "a b c".revv().note()
        val events = pattern.queryArc(-1.0, 0.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[2].data.note shouldBeEqualIgnoringCase "a"
    }

    "revv() works in compiled code" {
        val pattern = StrudelPattern.compile("""note("a b c").revv()""")
        val events = pattern?.queryArc(-1.0, 0.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 3
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[2].data.note shouldBeEqualIgnoringCase "a"
    }

    "revv() works as standalone function" {
        val pattern = revv(note("a b"))
        val events = pattern.queryArc(-1.0, 0.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "b"
        events[1].data.note shouldBeEqualIgnoringCase "a"
    }

    "revv() difference from rev()" {
        // rev() reverses within each cycle
        val rev = note("a b c").rev()
        val revEvents = rev.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // After rev: c b a (reversed within cycle 0)
        revEvents.size shouldBe 3
        revEvents[0].data.note shouldBeEqualIgnoringCase "c"
        revEvents[1].data.note shouldBeEqualIgnoringCase "b"
        revEvents[2].data.note shouldBeEqualIgnoringCase "a"

        // revv() reverses globally - but note("a b c") is infinite, so it repeats
        // The pattern at cycle 0 is the same as cycle 0 was originally
        val revv = note("a b c").revv()
        val revvEvents = revv.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // Since note("a b c") is infinite and repeating, revv() mirrors it
        // At cycle 0: We query [-1, 0), which has the same pattern, then mirror back
        // The order within the cycle is reversed (like applying rev to the negative cycle)
        revvEvents.size shouldBe 3
        revvEvents[0].data.note shouldBeEqualIgnoringCase "c"
        revvEvents[1].data.note shouldBeEqualIgnoringCase "b"
        revvEvents[2].data.note shouldBeEqualIgnoringCase "a"
    }

    "revv() preserves event whole spans" {
        val pattern = note("a").revv()
        val events = pattern.queryArc(-1.0, 0.0)

        events.size shouldBe 1

        // Check both part and whole are negated correctly
        events[0].part.begin.toDouble() shouldBe (-1.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (-1.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }
})
