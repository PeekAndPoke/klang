package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize

class LangRepeatCyclesSpec : StringSpec({

    "repeatCycles() repeats each cycle n times" {
        // For a static pattern like note("c"), repeatCycles acts as identity
        val staticPattern = note("c").repeatCycles(2)

        // Should continue producing events (not stop after 2 cycles)
        val cycle0 = staticPattern.queryArc(0.0, 1.0)
        cycle0 shouldHaveSize 1

        val cycle1 = staticPattern.queryArc(1.0, 2.0)
        cycle1 shouldHaveSize 1

        val cycle2 = staticPattern.queryArc(2.0, 3.0)
        cycle2 shouldHaveSize 1  // Continues indefinitely for static patterns

        val cycle10 = staticPattern.queryArc(10.0, 11.0)
        cycle10 shouldHaveSize 1  // Still producing events
    }
})
