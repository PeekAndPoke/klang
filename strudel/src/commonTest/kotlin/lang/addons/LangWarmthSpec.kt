package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.*

class LangWarmthSpec : StringSpec({

    "warmth dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.warmth(ctrl)" to
                    seq(pat).warmth(ctrl),
            "script pattern.warmth(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").warmth("$ctrl")"""),
            "string.warmth(ctrl)" to
                    pat.warmth(ctrl),
            "script string.warmth(ctrl)" to
                    StrudelPattern.compile(""""$pat".warmth("$ctrl")"""),
            "warmth(ctrl)" to
                    seq(pat).apply(warmth(ctrl)),
            "script warmth(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(warmth("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.warmth shouldBe 0.1
            events[1].data.warmth shouldBe 0.5
        }
    }

    "reinterpret voice data as warmth | seq(\"0 1\").warmth()" {
        val p = seq("0.1 0.5").warmth()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.warmth shouldBe 0.1
            events[1].data.warmth shouldBe 0.5
        }
    }

    "reinterpret voice data as warmth | \"0 1\".warmth()" {
        val p = "0.1 0.5".warmth()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.warmth shouldBe 0.1
            events[1].data.warmth shouldBe 0.5
        }
    }

    "reinterpret voice data as warmth | seq(\"0 1\").apply(warmth())" {
        val p = seq("0.1 0.5").apply(warmth())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.warmth shouldBe 0.1
            events[1].data.warmth shouldBe 0.5
        }
    }

    "warmth() sets VoiceData.warmth" {
        val p = "0 1".apply(warmth("0.25 0.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.warmth } shouldBe listOf(0.25, 0.5)
    }

    "warmth() works as pattern extension" {
        val p = s("supersaw").warmth("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.warmth shouldBe 0.5
    }

    "warmth() works as string extension" {
        val p = "supersaw".warmth("0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.warmth shouldBe 0.3
    }

    "warmth() works in compiled code" {
        val p = StrudelPattern.compile("""s("supersaw").warmth("0.4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.warmth shouldBe 0.4
    }

    "warmth() with continuous pattern sets warmth correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").warmth(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.warmth shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.warmth shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.warmth shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.warmth shouldBe (0.0 plusOrMinus EPSILON)
    }

    "warmth() can be chained with other functions" {
        val p = s("supersaw").warmth("0.25").note("c3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.sound shouldBe "supersaw"
        events[0].data.warmth shouldBe 0.25
        events[0].data.note shouldBe "c3"
    }

    "warmth() default is null when not set" {
        val p = s("supersaw")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.warmth shouldBe null
    }

    "apply(mul().warmth())" {
        val p = seq("0.2 0.4").apply(mul("2").warmth())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.warmth shouldBe (0.4 plusOrMinus EPSILON)  // 0.2*2=0.4 -> warmth=0.4
            events[1].data.warmth shouldBe (0.8 plusOrMinus EPSILON)  // 0.4*2=0.8 -> warmth=0.8
        }
    }

    "script apply(mul().warmth())" {
        val p = StrudelPattern.compile("""seq("0.2 0.4").apply(mul("2").warmth())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.warmth shouldBe (0.4 plusOrMinus EPSILON)  // 0.2*2=0.4 -> warmth=0.4
            events[1].data.warmth shouldBe (0.8 plusOrMinus EPSILON)  // 0.4*2=0.8 -> warmth=0.8
        }
    }

    "warmth() can be applied to different oscillators" {
        val p = s("sine triangle square").warmth("0.3 0.5 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.sound shouldBe "sine"
        events[0].data.warmth shouldBe 0.3
        events[1].data.sound shouldBe "triangle"
        events[1].data.warmth shouldBe 0.5
        events[2].data.sound shouldBe "square"
        events[2].data.warmth shouldBe 0.7
    }
})
