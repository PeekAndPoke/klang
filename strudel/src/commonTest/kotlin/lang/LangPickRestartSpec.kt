package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe

class LangPickRestartSpec : StringSpec({

    "pickRestart() restarts pattern at event start" {
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

        val result = pickRestart(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        // Event 1 at 0.0:
        // inner restarted at 0.0.
        // inner 0.0-0.25 is "0".
        events[0].part.begin.toDouble() shouldBeExactly 0.0
        events[0].part.end.toDouble() shouldBeExactly 0.25
        events[0].data.value?.asString shouldBe "0"

        // Event 2 at 0.5:
        // inner restarted at 0.0 (relative to 0.5).
        // so we get inner's 0.0-0.25 again, which is "0".
        // If it was standard 'pick', at 0.5 we would get inner's 0.5-0.75 which is "2".
        events[1].part.begin.toDouble() shouldBeExactly 0.5
        events[1].part.end.toDouble() shouldBeExactly 0.75
        events[1].data.value?.asString shouldBe "0"
    }

    "pickRestart() supports spread arguments (varargs)" {
        // pickRestart(pat1, pat2, selector)
        // selector "0 1" -> 0->pat1, 1->pat2
        // Both patterns restarted.
        val pat1 = seq("0 1 2 3") // 0.25 each
        val pat2 = seq("a b c d") // 0.25 each

        // selector "0 1". "0" at 0.0-0.5. "1" at 0.5-1.0.
        val result = pickRestart(pat1, pat2, "0 1")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4 // pat1 contributes 2 events (0.0-0.5), pat2 contributes 2 events (0.5-1.0)

        // At 0.0 (selector 0): pat1 restarted. duration 0.5 (clipped to selector).
        // pat1 (0 1 2 3): 0 at 0.0, 1 at 0.25. (2 at 0.5 - clipped out).
        events[0].data.value?.asString shouldBe "0"
        events[1].data.value?.asString shouldBe "1"

        // At 0.5 (selector 1): pat2 restarted. duration 0.5.
        // pat2 (a b c d): a at 0.0, b at 0.25. (relative to 0.5).
        events[2].data.value?.asString shouldBe "a"
        events[3].data.value?.asString shouldBe "b"
    }

    "pickRestart() supports Map lookup" {
        val lookup = mapOf(
            "a" to seq("0 1"),
            "b" to seq("2 3")
        )
        // selector "a b" -> 0.0-0.5 "a", 0.5-1.0 "b"
        val result = pickRestart(lookup, "a b")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2 // each pattern is 2 events (0.5 each). selector is 0.5 long. 1 event fits.

        // "a" (0 1) restarted at 0.0. Duration 0.5.
        // "0" at 0.0-0.5.
        events[0].data.value?.asString shouldBe "0"

        // "b" (2 3) restarted at 0.5. Duration 0.5.
        // "2" at 0.0-0.5 (relative).
        events[1].data.value?.asString shouldBe "2"
    }

    "pickRestart() works as pattern extension" {
        // Selector 0.0 and 0.5
        val selector = seq("0 ~ 0 ~")
        val result = selector.pickRestart(listOf(seq("0 1 2 3")))
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events[0].data.value?.asString shouldBe "0"
        events[1].data.value?.asString shouldBe "0" // Restarted
    }

    // -- pickmodRestart tests --

    "pickmodRestart() wraps indices and restarts" {
        val inner = seq("0 1") // 0.5 each
        val lookup = listOf(inner)
        // selector "0 1 2 3". 0.25 each.
        // 0 -> inner. Restart. Inner 0.0-0.25 is "0" (clipped).
        // 1 -> inner. Restart. Inner 0.0-0.25 is "0".
        // 2 -> inner (wrap). Restart. Inner 0.0-0.25 is "0".
        // 3 -> inner (wrap). Restart. Inner 0.0-0.25 is "0".
        val selector = seq("0 1 2 3")

        val result = pickmodRestart(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4

        events.forEach {
            it.data.value?.asString shouldBe "0"
        }
    }

    "pickmodRestart() supports spread arguments" {
        // pickmodRestart(pat1, selector) -> selector picks from [pat1] with modulo
        val pat = seq("a b c d")
        // selector "0 1 2 3" (0.25 each).
        // 0->pat, 1->pat, 2->pat, 3->pat.
        // Each restarts pat. pat duration 1.0. selector duration 0.25.
        // We get first 0.25 of pat ("a") 4 times.
        val result = pickmodRestart(pat, "0 1 2 3")
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 4
        events.forEach {
            it.data.value?.asString shouldBe "a"
        }
    }

    "pick() (standard) does NOT restart (contrast test)" {
        val inner = seq("0 1 2 3")
        val lookup = listOf(inner)
        val selector = seq("0 ~ 0 ~")

        val result = pick(lookup, selector)
        val events = result.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        // Event 1 at 0.0: "0"
        events[0].part.begin.toDouble() shouldBeExactly 0.0
        events[0].data.value?.asString shouldBe "0"

        // Event 2 at 0.5:
        // Standard pick queries inner at 0.5. Inner at 0.5-0.75 is "2".
        events[1].part.begin.toDouble() shouldBeExactly 0.5
        events[1].data.value?.asString shouldBe "2"
    }
})
