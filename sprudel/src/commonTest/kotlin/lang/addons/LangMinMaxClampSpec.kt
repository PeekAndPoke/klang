package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.add
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.mul
import io.peekandpoke.klang.sprudel.lang.seq
import io.peekandpoke.klang.sprudel.lang.sub

class LangMinMaxClampSpec : StringSpec({

    // ========== min() tests ==========

    "min dsl interface" {
        val pat = "0 1 2 3 4"
        val cap = "2"

        dslInterfaceTests(
            "pattern.min(cap)" to
                    seq(pat).min(cap),
            "string.min(cap)" to
                    pat.min(cap),
            "min(cap)" to
                    seq(pat).apply(min(cap)),
            "script pattern.min(cap)" to
                    SprudelPattern.compile("""seq("$pat").min("$cap")"""),
            "script string.min(cap)" to
                    SprudelPattern.compile(""""$pat".min("$cap")"""),
            "script min(cap)" to
                    SprudelPattern.compile("""seq("$pat").apply(min("$cap"))"""),
        ) { _, events ->
            events.shouldHaveSize(5)
            events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)   // min(0, 2) = 0
            events[1].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)   // min(1, 2) = 1
            events[2].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // min(2, 2) = 2
            events[3].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // min(3, 2) = 2
            events[4].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // min(4, 2) = 2
        }
    }

    "min() caps numeric values" {
        val p = seq(0.0, 1.0, 2.5, 3.0, 4.5).min(2.0)
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 5
        events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
        events[4].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
    }

    "min() with control pattern varies the cap" {
        // first cycle cap = 1, second cycle cap = 3
        val p = seq("0 1 2 3").min("<1 3>")
        val events = p.queryArc(0.0, 2.0)

        assertSoftly {
            events shouldHaveSize 8
            // cycle 0, cap = 1
            events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[3].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            // cycle 1, cap = 3
            events[4].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[5].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[6].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[7].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        }
    }

    "apply(mul().min())" {
        val p = seq("1 2 3").apply(mul("2").min("4"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 3
            events[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // min(1*2, 4)=2
            events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)   // min(2*2, 4)=4
            events[2].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)   // min(3*2, 4)=4
        }
    }

    "script apply(mul().min())" {
        val p = SprudelPattern.compile("""seq("1 2 3").apply(mul("2").min("4"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 3
            events[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)
        }
    }

    // ========== max() tests ==========

    "max dsl interface" {
        val pat = "0 1 2 3 4"
        val floor = "2"

        dslInterfaceTests(
            "pattern.max(floor)" to
                    seq(pat).max(floor),
            "string.max(floor)" to
                    pat.max(floor),
            "max(floor)" to
                    seq(pat).apply(max(floor)),
            "script pattern.max(floor)" to
                    SprudelPattern.compile("""seq("$pat").max("$floor")"""),
            "script string.max(floor)" to
                    SprudelPattern.compile(""""$pat".max("$floor")"""),
            "script max(floor)" to
                    SprudelPattern.compile("""seq("$pat").apply(max("$floor"))"""),
        ) { _, events ->
            events.shouldHaveSize(5)
            events[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // max(0, 2) = 2
            events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // max(1, 2) = 2
            events[2].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // max(2, 2) = 2
            events[3].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)   // max(3, 2) = 3
            events[4].data.value?.asDouble shouldBe (4.0 plusOrMinus EPSILON)   // max(4, 2) = 4
        }
    }

    "max() floors numeric values" {
        val p = seq(-1.0, 0.0, 0.5, 1.0, 2.0).max(0.0)
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 5
        events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[2].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        events[4].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
    }

    "max() with control pattern varies the floor" {
        // first cycle floor = 2, second cycle floor = 0
        val p = seq("0 1 2 3").max("<2 0>")
        val events = p.queryArc(0.0, 2.0)

        assertSoftly {
            events shouldHaveSize 8
            // cycle 0, floor = 2
            events[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[3].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
            // cycle 1, floor = 0
            events[4].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[5].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[6].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[7].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        }
    }

    "apply(sub().max())" {
        val p = seq("1 2 3").apply(sub("2").max("0"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 3
            events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)   // max(1-2, 0)=0
            events[1].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)   // max(2-2, 0)=0
            events[2].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)   // max(3-2, 0)=1
        }
    }

    "script apply(sub().max())" {
        val p = SprudelPattern.compile("""seq("1 2 3").apply(sub("2").max("0"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 3
            events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    // ========== clamp() tests ==========

    "clamp dsl interface" {
        val pat = "-1 0 0.5 1 2"
        val lo = "0"
        val hi = "1"

        dslInterfaceTests(
            "pattern.clamp(lo, hi)" to
                    seq(pat).clamp(lo, hi),
            "string.clamp(lo, hi)" to
                    pat.clamp(lo, hi),
            "clamp(lo, hi)" to
                    seq(pat).apply(clamp(lo, hi)),
            "script pattern.clamp(lo, hi)" to
                    SprudelPattern.compile("""seq("$pat").clamp("$lo", "$hi")"""),
            "script string.clamp(lo, hi)" to
                    SprudelPattern.compile(""""$pat".clamp("$lo", "$hi")"""),
            "script clamp(lo, hi)" to
                    SprudelPattern.compile("""seq("$pat").apply(clamp("$lo", "$hi"))"""),
        ) { _, events ->
            events.shouldHaveSize(5)
            events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)   // clamp(-1) = 0
            events[1].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)   // clamp(0)  = 0
            events[2].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)   // clamp(.5) = .5
            events[3].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)   // clamp(1)  = 1
            events[4].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)   // clamp(2)  = 1
        }
    }

    "clamp() limits values to [min, max]" {
        val p = seq(-1.0, 0.0, 0.5, 1.0, 2.0).clamp(0.0, 1.0)
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 5
        events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[2].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        events[4].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
    }

    "clamp() with control pattern bounds" {
        // cycle 0: [0,1]; cycle 1: [-1, 0]
        val p = seq("-2 -0.5 0.5 2").clamp("<0 -1>", "<1 0>")
        val events = p.queryArc(0.0, 2.0)

        assertSoftly {
            events shouldHaveSize 8
            // cycle 0, bounds [0, 1]
            events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
            events[3].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            // cycle 1, bounds [-1, 0]
            events[4].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
            events[5].data.value?.asDouble shouldBe (-0.5 plusOrMinus EPSILON)
            events[6].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[7].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "apply(add().clamp())" {
        val p = seq("0 1 2 3").apply(add("1").clamp("0", "3"))
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)   // clamp(0+1, 0, 3) = 1
            events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // clamp(1+1, 0, 3) = 2
            events[2].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)   // clamp(2+1, 0, 3) = 3
            events[3].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)   // clamp(3+1, 0, 3) = 3
        }
    }

    "script apply(add().clamp())" {
        val p = SprudelPattern.compile("""seq("0 1 2 3").apply(add("1").clamp("0", "3"))""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 4
            events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
            events[3].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        }
    }

    "clamp() as string extension" {
        val p = "-3 -1 0 2 5".clamp(-1, 2)
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 5
        events[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[2].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
        events[4].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
    }
})
