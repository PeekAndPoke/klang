package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangRoomSizeSpec : StringSpec({

    "roomsize dsl interface" {
        dslInterfaceTests(
            "pattern.roomsize(amount)" to note("c").roomsize(4.0),
            "script pattern.roomsize(amount)" to StrudelPattern.compile("""note("c").roomsize(4)"""),
            "string.roomsize(amount)" to "c".roomsize(4.0),
            "script string.roomsize(amount)" to StrudelPattern.compile(""""c".roomsize(4)"""),
            "roomsize(amount)" to note("c").apply(roomsize(4.0)),
            "script roomsize(amount)" to StrudelPattern.compile("""note("c").apply(roomsize(4))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "rsize dsl interface" {
        dslInterfaceTests(
            "pattern.rsize(amount)" to note("c").rsize(4.0),
            "script pattern.rsize(amount)" to StrudelPattern.compile("""note("c").rsize(4)"""),
            "string.rsize(amount)" to "c".rsize(4.0),
            "script string.rsize(amount)" to StrudelPattern.compile(""""c".rsize(4)"""),
            "rsize(amount)" to note("c").apply(rsize(4.0)),
            "script rsize(amount)" to StrudelPattern.compile("""note("c").apply(rsize(4))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "sz dsl interface" {
        dslInterfaceTests(
            "pattern.sz(amount)" to note("c").sz(4.0),
            "script pattern.sz(amount)" to StrudelPattern.compile("""note("c").sz(4)"""),
            "string.sz(amount)" to "c".sz(4.0),
            "script string.sz(amount)" to StrudelPattern.compile(""""c".sz(4)"""),
            "sz(amount)" to note("c").apply(sz(4.0)),
            "script sz(amount)" to StrudelPattern.compile("""note("c").apply(sz(4))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "size dsl interface" {
        dslInterfaceTests(
            "pattern.size(amount)" to note("c").size(4.0),
            "script pattern.size(amount)" to StrudelPattern.compile("""note("c").size(4)"""),
            "string.size(amount)" to "c".size(4.0),
            "script string.size(amount)" to StrudelPattern.compile(""""c".size(4)"""),
            "size(amount)" to note("c").apply(size(4.0)),
            "script size(amount)" to StrudelPattern.compile("""note("c").apply(size(4))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as roomSize | seq(\"2.0 4.0\").roomsize()" {
        val p = seq("2.0 4.0").roomsize()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.roomSize shouldBe 2.0
            events[1].data.roomSize shouldBe 4.0
        }
    }

    "reinterpret voice data as roomSize | \"2.0 4.0\".roomsize()" {
        val p = "2.0 4.0".roomsize()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.roomSize shouldBe 2.0
            events[1].data.roomSize shouldBe 4.0
        }
    }

    "reinterpret voice data as roomSize | seq(\"2.0 4.0\").apply(roomsize())" {
        val p = seq("2.0 4.0").apply(roomsize())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.roomSize shouldBe 2.0
            events[1].data.roomSize shouldBe 4.0
        }
    }

    "roomsize() sets VoiceData.roomSize" {
        val p = note("a b").roomsize("2.0 4.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.roomSize } shouldBe listOf(2.0, 4.0)
    }

    "rsize() alias works" {
        val p = note("c").apply(rsize("2.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "roomsize() works as pattern extension" {
        val p = note("c").roomsize("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "roomsize() works as string extension" {
        val p = "c".roomsize("2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "roomsize() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").roomsize("2.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "roomsize() with continuous pattern sets roomSize correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").roomsize(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.roomSize shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.roomSize shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.roomSize shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.roomSize shouldBe (0.0 plusOrMinus EPSILON)
    }
})
