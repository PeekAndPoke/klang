package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangPickResetSpec : StringSpec({

    "pickReset() resets pattern phase to event start" {
        // Inner: sequence "0 1 2 3" (0.25 each)
        val inner = seq("0 1 2 3")
        val lookup = listOf(inner)

        // Selector: "0 ~ 0 ~" creates 4 steps in 1 cycle.
        // Step 0 ("0"): 0.0 - 0.25 -> Event 1
        // Step 1 ("~"): 0.25 - 0.5
        // Step 2 ("0"): 0.5 - 0.75 -> Event 2
        // Step 3 ("~"): 0.75 - 1.0
        // Total 2 events.
        val selector = seq("0 ~ 0 ~")

        val result = pickReset(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        // Event 1 at 0.0:
        // inner reset (aligned) at 0.0.
        // inner at 0.0-0.25 is "0".
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[0].data.value?.asString shouldBe "0"

        // Event 2 at 0.5:
        // inner reset (aligned) at 0.5.
        // This means inner's cycle position 0.5 aligns with event's cycle position.
        // But since this is `pickReset`, it aligns the pattern structure such that
        // the inner pattern's cycle start matches the event's start.
        // Wait, let's verify "reset" definition from Strudel docs/JS code.
        // JS: _opReset -> resetJoin -> align the inner pattern cycle start to outer pattern haps
        // So at 0.5, the inner pattern should start from its beginning (relative to the event).
        // BUT, `resetJoin` implementation usually does:
        // inner_hap.late(outer_hap.whole.begin.cyclePos())
        // which shifts the inner pattern so its 0 matches outer event's begin (within cycle).

        // So for Event 2 (start 0.5):
        // Inner pattern is shifted by 0.5.
        // We query at global 0.5-0.75.
        // Local query time = global - shift = 0.5 - 0.5 = 0.0.
        // Inner at 0.0-0.25 is "0".
        // So we should get "0".

        events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[1].data.value?.asString shouldBe "0"
    }

    "pickReset() supports spread arguments (varargs)" {
        // pickReset(pat1, pat2, selector)
        // selector "0 1" -> 0->pat1, 1->pat2
        // Both patterns reset.
        val pat1 = seq("0 1 2 3") // 0.25 each
        val pat2 = seq("a b c d") // 0.25 each

        // selector "0 1". "0" at 0.0-0.5. "1" at 0.5-1.0.
        val result = pickReset(pat1, pat2, "0 1")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4 // pat1 contributes 2 events (0.0-0.5), pat2 contributes 2 events (0.5-1.0)

        // At 0.0 (selector 0): pat1 reset. Shift 0.0.
        // Query 0.0-0.5. pat1(0.0-0.5) -> "0" "1".
        events[0].data.value?.asString shouldBe "0"
        events[1].data.value?.asString shouldBe "1"

        // At 0.5 (selector 1): pat2 reset. Shift 0.5.
        // Query global 0.5-1.0.
        // Local query = global - 0.5 = 0.0 - 0.5.
        // pat2(0.0-0.5) -> "a" "b".
        events[2].data.value?.asString shouldBe "a"
        events[3].data.value?.asString shouldBe "b"
    }

    "pickReset() supports Map lookup" {
        val lookup = mapOf(
            "a" to seq("0 1"),
            "b" to seq("2 3")
        )
        // selector "a b" -> 0.0-0.5 "a", 0.5-1.0 "b"
        val result = pickReset(lookup, "a b")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2 // each pattern is 2 events (0.5 each). selector is 0.5 long. 1 event fits.

        // "a" (0 1) reset at 0.0. Shift 0.0.
        // Query 0.0-0.5. Inner "0".
        events[0].data.value?.asString shouldBe "0"

        // "b" (2 3) reset at 0.5. Shift 0.5.
        // Query global 0.5-1.0. Local 0.0-0.5.
        // Inner "2".
        events[1].data.value?.asString shouldBe "2"
    }

    "pickReset() works as pattern extension" {
        // Selector 0.0 and 0.5
        val selector = seq("0 ~ 0 ~")
        val result = selector.pickReset(listOf(seq("0 1 2 3")))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "0"
        events[1].data.value?.asString shouldBe "0" // Reset
    }

    // -- pickmodReset tests --

    "pickmodReset() wraps indices and resets" {
        val inner = seq("0 1") // 0.5 each
        val lookup = listOf(inner)
        // selector "0 1 2 3". 0.25 each.
        // 0 -> inner. Reset. Shift 0.0. Inner 0.0-0.25 is "0".
        // 1 -> inner. Reset. Shift 0.25. Inner 0.0-0.25 is "0".
        // 2 -> inner (wrap). Reset. Shift 0.5. Inner 0.0-0.25 is "0".
        // 3 -> inner (wrap). Reset. Shift 0.75. Inner 0.0-0.25 is "0".
        val selector = seq("0 1 2 3")

        val result = pickmodReset(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4

        events.forEach {
            it.data.value?.asString shouldBe "0"
        }
    }

    "pickmodReset() supports spread arguments" {
        // pickmodReset(pat1, selector) -> selector picks from [pat1] with modulo
        val pat = seq("a b c d")
        // selector "0 1 2 3" (0.25 each).
        // 0->pat, 1->pat, 2->pat, 3->pat.
        // Each resets pat. pat duration 1.0. selector duration 0.25.
        // We get first 0.25 of pat ("a") 4 times.
        val result = pickmodReset(pat, "0 1 2 3")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events.forEach {
            it.data.value?.asString shouldBe "a"
        }
    }
})
