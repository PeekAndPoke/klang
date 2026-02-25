package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangMaskSpec : StringSpec({

    "mask dsl interface" {
        val pat = "c e"
        val ctrl = "1 0"
        dslInterfaceTests(
            "pattern.mask(ctrl)" to note(pat).mask(ctrl),
            "script pattern.mask(ctrl)" to StrudelPattern.compile("""note("$pat").mask("$ctrl")"""),
            "string.mask(ctrl)" to pat.mask(ctrl),
            "script string.mask(ctrl)" to StrudelPattern.compile(""""$pat".mask("$ctrl")"""),
            "mask(ctrl)" to note(pat).apply(mask(ctrl)),
            "script mask(ctrl)" to StrudelPattern.compile("""note("$pat").apply(mask("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 1  // only the truthy "1" slot passes
        }
    }

    "mask() filters source events based on mask truthiness" {
        // note("c e").mask("x ~") -> should only keep "c"
        val p = note("c e").mask("x ~")

        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "mask() works with boolean patterns" {
        // note("c [eb,g]").mask("<1 [0 1]>")
        // We use a shorter pattern to match the expectation of 5 events (3 in cycle 1, 2 in cycle 2)
        val p = note("c [eb,g]").mask("<1 [0 1]>")

        // Cycle 1 (Mask "1"): Keeps "c" (0.0-0.5) and "[eb,g]" (0.5-1.0) -> 3 events
        // Cycle 2 (Mask "[0 1]"):
        //   - First half "0": drops "c" (1.0-1.5)
        //   - Second half "1": keeps "[eb,g]" (1.5-2.0) -> 2 events
        // Total should be 5 events
        val events = p.queryArc(0.0, 2.0).sortedBy { it.part.begin }

        events.size shouldBe 5
        events.filter { it.part.begin.toDouble() < 1.0 }.size shouldBe 3
        events.filter { it.part.begin.toDouble() >= 1.0 }.size shouldBe 2
    }

    "mask() as PatternMapperFn works" {
        val p = note("c e").apply(mask("x ~"))

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "mask() as string extension works" {
        val p = "c e".mask("x ~")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c"
    }

    "mask() works in compiled code" {
        // We use "1 0" to strictly test half-cycle masking in a single cycle
        val p = StrudelPattern.compile("""note("c [eb,g]").mask("1 0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // "1 0" means keep first half, silence second half.
        // note("c [eb,g]") has 'c' in first half (0.0-0.5) and 'eb,g' in second half (0.5-1.0).
        // Result: only 'c' remains.
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "mask() with continuous pattern" {
        // note("c*8").mask(square.fast(4))
        // square goes 0, 1, 0, 1 ... every cycle. fast(4) makes it 4 times per cycle.
        // This should effectively silence every other note.
        val p = note("c*8").mask(square.fast(4))

        val events = p.queryArc(0.0, 1.0)
        // c*8 = 8 events. square fast 4 has 4 truthy blocks.
        // 8 / 2 = 4 events should remain.
        events.size shouldBe 4
    }
})
