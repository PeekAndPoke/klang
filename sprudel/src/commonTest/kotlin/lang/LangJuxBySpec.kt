package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangJuxBySpec : StringSpec({

    "juxBy dsl interface" {
        val pat = "c e"
        val transform: PatternMapperFn = { it.rev() }
        dslInterfaceTests(
            "pattern.juxBy(0.5, fn)" to note(pat).juxBy(0.5, transform),
            "script pattern.juxBy(0.5, fn)" to StrudelPattern.compile("""note("$pat").juxBy(0.5, x => x.rev())"""),
            "string.juxBy(0.5, fn)" to pat.juxBy(0.5, transform),
            "script string.juxBy(0.5, fn)" to StrudelPattern.compile(""""$pat".juxBy(0.5, x => x.rev())"""),
            "juxBy(0.5, fn)" to note(pat).apply(juxBy(0.5, transform)),
            "script juxBy(0.5, fn)" to StrudelPattern.compile("""note("$pat").apply(juxBy(0.5, x => x.rev()))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events shouldHaveSize 4  // 2 notes × 2 (left + right)
        }
    }

    "juxBy() allows adjustable stereo width" {
        // Width 0.5 -> Left: -0.5, Right: 0.5
        val p = note("c").juxBy(0.5) { it.note("e") }
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        val left = events.find { it.data.pan == 0.25 }
        left?.data?.note shouldBeEqualIgnoringCase "c"

        val right = events.find { it.data.pan == 0.75 }
        right?.data?.note shouldBeEqualIgnoringCase "e"
    }
})
