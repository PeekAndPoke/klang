package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.apply
import io.peekandpoke.klang.strudel.lang.note

class LangTremoloSpec : StringSpec({

    // -- dsl interface tests -----------------------------------------------------------------------------------------

    "tremolo() dsl interface" {
        dslInterfaceTests(
            "pattern.tremolo()" to note("c3").tremolo("0.5:4"),
            "string.tremolo()" to "c3".tremolo("0.5:4"),
            "script pattern.tremolo()" to StrudelPattern.compile("""note("c3").tremolo("0.5:4")"""),
            "script string.tremolo()" to StrudelPattern.compile(""""c3".tremolo("0.5:4")"""),
            "apply(tremolo())" to note("c3").apply(tremolo("0.5:4")),
            "script apply(tremolo())" to StrudelPattern.compile("""note("c3").apply(tremolo("0.5:4"))"""),
        ) { _, events ->
            events.size shouldBe 1
            events[0].data.tremoloDepth shouldBe 0.5
            events[0].data.tremoloSync shouldBe 4.0
        }
    }

    // -- depth only --------------------------------------------------------------------------------------------------

    "tremolo(\"0.8\") sets depth only" {
        val p = note("c3").tremolo("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.8
        events[0].data.tremoloSync shouldBe null
        events[0].data.tremoloShape shouldBe null
    }

    // -- depth:rate --------------------------------------------------------------------------------------------------

    "tremolo(\"0.5:4\") sets depth and rate" {
        val p = note("c3").tremolo("0.5:4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloSync shouldBe 4.0
    }

    // -- depth:rate:shape --------------------------------------------------------------------------------------------

    "tremolo(\"0.8:8:square\") sets depth, rate and shape" {
        val p = note("c3").tremolo("0.8:8:square")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.8
        events[0].data.tremoloSync shouldBe 8.0
        events[0].data.tremoloShape shouldBe "square"
    }

    // -- depth:rate:shape:skew ---------------------------------------------------------------------------------------

    "tremolo(\"0.5:4:sine:0.6\") sets depth, rate, shape and skew" {
        val p = note("c3").tremolo("0.5:4:sine:0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloShape shouldBe "sine"
        events[0].data.tremoloSkew shouldBe 0.6
    }

    // -- all five params ---------------------------------------------------------------------------------------------

    "tremolo(\"0.5:4:sine:0.6:0.25\") sets all five params" {
        val p = note("c3").tremolo("0.5:4:sine:0.6:0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloShape shouldBe "sine"
        events[0].data.tremoloSkew shouldBe 0.6
        events[0].data.tremoloPhase shouldBe 0.25
    }

    // -- control patterns --------------------------------------------------------------------------------------------

    "tremolo() works with control pattern" {
        val p = note("c3 e3").tremolo("0.3:2 0.8:8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.tremoloDepth shouldBe 0.3
        events[0].data.tremoloSync shouldBe 2.0
        events[1].data.tremoloDepth shouldBe 0.8
        events[1].data.tremoloSync shouldBe 8.0
    }

    // -- compiled scripts --------------------------------------------------------------------------------------------

    "tremolo() works in compiled code" {
        val p = StrudelPattern.compile("""note("c3").tremolo("0.5:4:sine")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.5
        events[0].data.tremoloSync shouldBe 4.0
        events[0].data.tremoloShape shouldBe "sine"
    }

    "tremolo() chained with apply() works in compiled code" {
        val p = StrudelPattern.compile("""note("c3").apply(tremolo("0.8:8:square"))""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.tremoloDepth shouldBe 0.8
        events[0].data.tremoloSync shouldBe 8.0
        events[0].data.tremoloShape shouldBe "square"
    }
})
