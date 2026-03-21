package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangCoarseSpec : StringSpec({

    "coarse dsl interface" {
        dslInterfaceTests(
            "pattern.coarse(amount)" to note("c").coarse(4),
            "script pattern.coarse(amount)" to SprudelPattern.compile("""note("c").coarse(4)"""),
            "string.coarse(amount)" to "c".coarse(4),
            "script string.coarse(amount)" to SprudelPattern.compile(""""c".coarse(4)"""),
            "coarse(amount)" to note("c").apply(coarse(4)),
            "script coarse(amount)" to SprudelPattern.compile("""note("c").apply(coarse(4))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as coarse | seq(\"2 4\").coarse()" {
        val p = seq("2 4").coarse()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.coarse shouldBe 2.0
            events[1].data.coarse shouldBe 4.0
        }
    }

    "reinterpret voice data as coarse | \"2 4\".coarse()" {
        val p = "2 4".coarse()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.coarse shouldBe 2.0
            events[1].data.coarse shouldBe 4.0
        }
    }

    "reinterpret voice data as coarse | seq(\"2 4\").apply(coarse())" {
        val p = seq("2 4").apply(coarse())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.coarse shouldBe 2.0
            events[1].data.coarse shouldBe 4.0
        }
    }

    "coarse() sets VoiceData.coarse" {
        val p = note("a b").coarse("2 4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.coarse } shouldBe listOf(2.0, 4.0)
    }

    "coarse() works as pattern extension" {
        val p = note("c").coarse("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.coarse shouldBe 2.0
    }

    "coarse() works as string extension" {
        val p = "c".coarse("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.coarse shouldBe 2.0
    }

    "coarse() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").coarse("2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.coarse shouldBe 2.0
    }

    "coarse() with continuous pattern sets coarse correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").coarse(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.coarse shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.coarse shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.coarse shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.coarse shouldBe (0.0 plusOrMinus EPSILON)
    }
})
