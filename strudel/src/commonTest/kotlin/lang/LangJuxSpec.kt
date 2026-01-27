package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldBeEqualIgnoringCase

class LangJuxSpec : StringSpec({

    "jux() creates stereo effect with transformations" {
        // Original: hard left (-1)
        // Transformed: hard right (1), reversed
        val p = note("c e").jux { it.rev() }
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 4

        // Left channel events (original order: c, e)
        val leftEvents = events.filter { it.data.pan == 0.0 }
        leftEvents shouldHaveSize 2
        leftEvents[0].data.note shouldBeEqualIgnoringCase "c"
        leftEvents[1].data.note shouldBeEqualIgnoringCase "e"

        // Right channel events (reversed order: e, c)
        val rightEvents = events.filter { it.data.pan == 1.0 }
        rightEvents shouldHaveSize 2
        rightEvents[0].data.note shouldBeEqualIgnoringCase "e"
        rightEvents[1].data.note shouldBeEqualIgnoringCase "c"
    }

    "jux() defaults to identity transform if no function provided (just splitting)" {
        val p = note("c").jux { it }
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 2
        events.find { it.data.pan == 0.0 }?.data?.note shouldBeEqualIgnoringCase "c"
        events.find { it.data.pan == 1.0 }?.data?.note shouldBeEqualIgnoringCase "c"
    }
})
