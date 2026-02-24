package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.*

class LangMergeSpec : StringSpec({

    "merge dsl interface" {
        // Control sets only 'sound', leaving 'value' null — so source value is preserved after merge.
        dslInterfaceTests(
            "pattern.merge(ctrl)" to
                    seq("1.0 2.0").merge(s("sine supersaw")),
            "string.merge(ctrl)" to
                    "1.0 2.0".merge(s("sine supersaw")),
            "merge(ctrl)" to
                    seq("1.0 2.0").apply(merge(s("sine supersaw"))),
            "script pattern.merge(ctrl)" to
                    StrudelPattern.compile("""seq("1.0 2.0").merge(s("sine supersaw"))"""),
            "script string.merge(ctrl)" to
                    StrudelPattern.compile(""""1.0 2.0".merge(s("sine supersaw"))"""),
            "script merge(ctrl)" to
                    StrudelPattern.compile("""seq("1.0 2.0").apply(merge(s("sine supersaw")))"""),
        ) { _, events ->
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)  // source value preserved
            events[0].data.sound shouldBe "sine"                                // sound from control
            events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[1].data.sound shouldBe "supersaw"
        }
    }

    "merge() overlays non-null voice fields from control" {
        // source: note pattern;  control: sound pattern
        // after merge: each event has both note and sound
        val p = note("c3 d3").merge(s("sine supersaw"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.note shouldBe "c3"
            events[0].data.sound shouldBe "sine"
            events[1].data.note shouldBe "d3"
            events[1].data.sound shouldBe "supersaw"
        }
    }

    "merge() preserves source fields that control leaves null" {
        // source has warmth set; control (s()) has no warmth field -> source warmth is kept
        val p = seq("0.3 0.7").warmth().merge(s("sine supersaw"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.warmth shouldBe (0.3 plusOrMinus EPSILON)  // source warmth preserved
            events[0].data.sound shouldBe "sine"                        // sound from control
            events[1].data.warmth shouldBe (0.7 plusOrMinus EPSILON)
            events[1].data.sound shouldBe "supersaw"
        }
    }

    "merge() as string extension" {
        // string parsed as value sequence; control overlays sound without touching value
        val p = "1.0 2.0".merge(s("sine supersaw"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[0].data.sound shouldBe "sine"
            events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[1].data.sound shouldBe "supersaw"
        }
    }

    "apply(mul().merge())" {
        // mul doubles the value; merge then overlays sound from control (no value field -> value kept)
        val p = seq("1.0 2.0").apply(mul("2").merge(s("sine supersaw")))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)  // 1.0 * 2 = 2.0
            events[0].data.sound shouldBe "sine"                                 // sound from control
            events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)  // 2.0 * 2 = 4.0
            events[1].data.sound shouldBe "supersaw"
        }
    }

    "script apply(mul().merge())" {
        val p = StrudelPattern.compile(
            """seq("1.0 2.0").apply(mul("2").merge(s("sine supersaw")))"""
        )!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[0].data.sound shouldBe "sine"
            events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)
            events[1].data.sound shouldBe "supersaw"
        }
    }
})