package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.slow

class LangRepeatSpec : StringSpec({

    "repeat dsl interface" {
        // note("a b").repeat(2): "a b" spans 1 cycle; repeat(2) produces a 2-cycle pattern.
        // Each 1-cycle query window yields 2 events (a, b), stable across all cycles.
        val pat = "a b"
        val times = 2

        dslInterfaceTests(
            "pattern.repeat(times)" to
                    note(pat).repeat(times),
            "script pattern.repeat(times)" to
                    SprudelPattern.compile("""note("$pat").repeat($times)"""),
            "string.repeat(times)" to
                    pat.repeat(times).note(),
            "script string.repeat(times)" to
                    SprudelPattern.compile(""""$pat".repeat($times).note()"""),
            "repeat(times)" to
                    note(pat).apply(repeat(times)),
            "script repeat(times)" to
                    SprudelPattern.compile("""note("$pat").apply(repeat($times))"""),
        ) { _, events ->
            events.shouldHaveSize(2)
            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[1].data.note shouldBeEqualIgnoringCase "b"
        }
    }

    "repeat() plays sequence twice sequentially" {
        val p = note("a b").repeat(2)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 4

        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[2].data.note shouldBeEqualIgnoringCase "a"
        events[2].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (1.5 plusOrMinus EPSILON)

        events[3].data.note shouldBeEqualIgnoringCase "b"
        events[3].part.begin.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
        events[3].part.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "repeat(3) extends duration to 3 cycles" {
        val p = note("a").repeat(3)

        p.estimateCycleDuration().toDouble() shouldBe (3.0 plusOrMinus EPSILON)

        val events = p.queryArc(0.0, 3.0).sortedBy { it.part.begin }
        events.size shouldBe 3

        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[2].part.begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "repeat(1) returns original pattern" {
        val p = note("a").repeat(1)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "repeat(0) returns silence" {
        val p = note("a").repeat(0)
        val events = p.queryArc(0.0, 10.0)
        events shouldHaveSize 0
    }

    "repeat() respects internal duration of source pattern" {
        val source = note("a b").slow(2)
        val p = source.repeat(2)
        val events = p.queryArc(0.0, 4.0).sortedBy { it.part.begin }

        events.size shouldBe 4

        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].part.begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].part.duration.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[2].data.note shouldBeEqualIgnoringCase "a"
        events[2].part.begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        events[3].data.note shouldBeEqualIgnoringCase "b"
        events[3].part.begin.toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "repeat() works as a string extension" {
        val p = "a b".repeat(2)
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        events[0].data.value?.asString shouldBeEqualIgnoringCase "a"
        events[2].data.value?.asString shouldBeEqualIgnoringCase "a"
    }

    "apply(repeat()) works as PatternMapperFn" {
        val p = note("a b").apply(repeat(2))
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[1].data.note shouldBeEqualIgnoringCase "b"
            events[2].data.note shouldBeEqualIgnoringCase "a"
            events[3].data.note shouldBeEqualIgnoringCase "b"
        }
    }

    "apply(repeat().repeat()) chains two repeat mappers" {
        // repeat(1) is a no-op; repeat(2) then duplicates over 2 cycles
        // result is identical to plain repeat(2)
        val p = note("a b").apply(repeat(1).repeat(2))
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[1].data.note shouldBeEqualIgnoringCase "b"
            events[2].data.note shouldBeEqualIgnoringCase "a"
            events[3].data.note shouldBeEqualIgnoringCase "b"
        }
    }

    "script apply(repeat()) works in compiled code" {
        val p = SprudelPattern.compile("""note("a b").apply(repeat(2))""")!!
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.note shouldBeEqualIgnoringCase "a"
            events[1].data.note shouldBeEqualIgnoringCase "b"
            events[2].data.note shouldBeEqualIgnoringCase "a"
            events[3].data.note shouldBeEqualIgnoringCase "b"
        }
    }
})
