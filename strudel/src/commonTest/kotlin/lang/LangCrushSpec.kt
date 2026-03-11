package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangCrushSpec : StringSpec({

    "crush dsl interface" {
        dslInterfaceTests(
            "pattern.crush(amount)" to note("c").crush(4),
            "script pattern.crush(amount)" to StrudelPattern.compile("""note("c").crush(4)"""),
            "string.crush(amount)" to "c".crush(4),
            "script string.crush(amount)" to StrudelPattern.compile(""""c".crush(4)"""),
            "crush(amount)" to note("c").apply(crush(4)),
            "script crush(amount)" to StrudelPattern.compile("""note("c").apply(crush(4))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as crush | seq(\"4 8\").crush()" {
        val p = seq("4 8").crush()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.crush shouldBe 4.0
            events[1].data.crush shouldBe 8.0
        }
    }

    "reinterpret voice data as crush | \"4 8\".crush()" {
        val p = "4 8".crush()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.crush shouldBe 4.0
            events[1].data.crush shouldBe 8.0
        }
    }

    "reinterpret voice data as crush | seq(\"4 8\").apply(crush())" {
        val p = seq("4 8").apply(crush())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.crush shouldBe 4.0
            events[1].data.crush shouldBe 8.0
        }
    }

    "crush() sets VoiceData.crush" {
        val p = note("a b").crush("4 8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.crush } shouldBe listOf(4.0, 8.0)
    }

    "crush() works as pattern extension" {
        val p = note("c").crush("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.crush shouldBe 4.0
    }

    "crush() works as string extension" {
        val p = "c".crush("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.crush shouldBe 4.0
    }

    "crush() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").crush("4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.crush shouldBe 4.0
    }

    "crush() with continuous pattern sets crush correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").crush(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.crush shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.crush shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.crush shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.crush shouldBe (0.0 plusOrMinus EPSILON)
    }
})
