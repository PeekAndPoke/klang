// strudel/src/commonTest/kotlin/lang/LangFilterWhenSpec.kt
package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase

class LangFilterWhenSpec : StringSpec({

    "filterWhen() works as pattern extension" {
        // filterWhen(predicate)
        // Keep events starting in the second half of the cycle
        val p = note("a b c d").filterWhen { it >= 0.5 }

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "filterWhen() works as string extension" {
        // "a b c d" -> durations 0.25 each. Starts at 0.0, 0.25, 0.5, 0.75
        // Keep < 0.5 -> "a" and "b"
        val p = "a b c d".filterWhen { it < 0.5 }.note()

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "A"
        events[1].data.note shouldBeEqualIgnoringCase "B"
    }

    "filterWhen() works as top-level function" {
        val p = filterWhen { it >= 0.75 }

        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "filterWhen() with time logic" {
        // oneCycle: s("bd*4").filterWhen((t) => t < 1)
        // We simulate this by checking if it filters correctly across multiple cycles
        // This predicate receives the absolute time
        val p = note("a").filterWhen { it < 1.0 }

        // Cycle 0: Start 0.0 -> Keep
        p.queryArc(0.0, 1.0).size shouldBe 1

        // Cycle 1: Start 1.0 -> Remove (since it is NOT < 1.0)
        p.queryArc(1.0, 2.0).size shouldBe 0
    }
})
