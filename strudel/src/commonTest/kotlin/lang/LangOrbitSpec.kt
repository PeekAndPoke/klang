package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangOrbitSpec : StringSpec({

    "orbit dsl interface" {
        val pat = "0 1"
        val ctrl = "1 2"

        dslInterfaceTests(
            "pattern.orbit(ctrl)" to
                    seq(pat).orbit(ctrl),
            "script pattern.orbit(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").orbit("$ctrl")"""),
            "string.orbit(ctrl)" to
                    pat.orbit(ctrl),
            "script string.orbit(ctrl)" to
                    StrudelPattern.compile(""""$pat".orbit("$ctrl")"""),
            "orbit(ctrl)" to
                    seq(pat).apply(orbit(ctrl)),
            "script orbit(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(orbit("$ctrl"))"""),
            // o alias
            "pattern.o(ctrl)" to
                    seq(pat).o(ctrl),
            "script pattern.o(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").o("$ctrl")"""),
            "string.o(ctrl)" to
                    pat.o(ctrl),
            "script string.o(ctrl)" to
                    StrudelPattern.compile(""""$pat".o("$ctrl")"""),
            "o(ctrl)" to
                    seq(pat).apply(o(ctrl)),
            "script o(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(o("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.orbit shouldBe 1
            events[1].data.orbit shouldBe 2
        }
    }

    "orbit() sets VoiceData.orbit" {
        val p = note("a b c").apply(orbit("0 1 2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.orbit shouldBe 0
        events[1].data.orbit shouldBe 1
        events[2].data.orbit shouldBe 2
    }

    "o() alias sets VoiceData.orbit" {
        val p = note("a b c").apply(o("0 1 2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.orbit shouldBe 0
        events[1].data.orbit shouldBe 1
        events[2].data.orbit shouldBe 2
    }

    "orbit() sets orbit on existing pattern" {
        val base = sound("bd hh sn")
        val p = base.orbit("0 1 2")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        events[0].data.sound shouldBe "bd"
        events[0].data.orbit shouldBe 0
        events[1].data.sound shouldBe "hh"
        events[1].data.orbit shouldBe 1
        events[2].data.sound shouldBe "sn"
        events[2].data.orbit shouldBe 2
    }

    "orbit() works as string extension" {
        val p = "bd".orbit("1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "bd"
        events[0].data.orbit shouldBe 1
    }

    "orbit() works in compiled code" {
        val p = StrudelPattern.compile("""note("a b c d").orbit("0 1 2 3")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.orbit shouldBe 0
        events[1].data.orbit shouldBe 1
        events[2].data.orbit shouldBe 2
        events[3].data.orbit shouldBe 3
    }

    "orbit() as modifier works in compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").orbit("0 2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.orbit shouldBe 0
        events[1].data.sound shouldBe "hh"
        events[1].data.orbit shouldBe 2
    }

    "orbit() with continuous pattern sets orbit correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").orbit(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5 -> 0
        events[0].data.orbit shouldBe 0
        // t=0.25: sine(0.25) = 1.0 -> 1
        events[1].data.orbit shouldBe 1
        // t=0.5: sine(0.5) = 0.5 -> 0
        events[2].data.orbit shouldBe 0
        // t=0.75: sine(0.75) = 0.0 -> 0
        events[3].data.orbit shouldBe 0
    }
})
