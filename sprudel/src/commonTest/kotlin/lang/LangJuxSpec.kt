package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangJuxSpec : StringSpec({

    "jux dsl interface" {
        val pat = "c e"
        val transform: PatternMapperFn = { it.rev() }
        dslInterfaceTests(
            "pattern.jux(fn)" to note(pat).jux(transform),
            "script pattern.jux(fn)" to StrudelPattern.compile("""note("$pat").jux(x => x.rev())"""),
            "string.jux(fn)" to pat.jux(transform),
            "script string.jux(fn)" to StrudelPattern.compile(""""$pat".jux(x => x.rev())"""),
            "jux(fn)" to note(pat).apply(jux(transform)),
            "script jux(fn)" to StrudelPattern.compile("""note("$pat").apply(jux(x => x.rev()))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events shouldHaveSize 4  // 2 notes × 2 (left + right)
        }
    }

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
