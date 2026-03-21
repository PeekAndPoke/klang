package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangVibratoSpec : StringSpec({

    "vibrato dsl interface" {
        val pat = "c4 e4"
        val amount = 5.0

        dslInterfaceTests(
            "pattern.vibrato(hz)" to note(pat).vibrato(amount),
            "script pattern.vibrato(hz)" to StrudelPattern.compile("""note("$pat").vibrato($amount)"""),
            "string.vibrato(hz)" to pat.vibrato(amount),
            "script string.vibrato(hz)" to StrudelPattern.compile(""""$pat".vibrato($amount)"""),
            "vibrato(hz)" to note(pat).apply(vibrato(amount)),
            "script vibrato(hz)" to StrudelPattern.compile("""note("$pat").apply(vibrato($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.vibrato shouldBe amount
        }
    }

    "vib dsl interface" {
        val pat = "c4 e4"
        val amount = 5.0

        dslInterfaceTests(
            "pattern.vib(hz)" to note(pat).vib(amount),
            "script pattern.vib(hz)" to StrudelPattern.compile("""note("$pat").vib($amount)"""),
            "string.vib(hz)" to pat.vib(amount),
            "script string.vib(hz)" to StrudelPattern.compile(""""$pat".vib($amount)"""),
            "vib(hz)" to note(pat).apply(vib(amount)),
            "script vib(hz)" to StrudelPattern.compile("""note("$pat").apply(vib($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.vibrato shouldBe amount
        }
    }

    "reinterpret voice data as vibrato | seq(\"5.0 10.0\").vibrato()" {
        val p = seq("5.0 10.0").vibrato()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "reinterpret voice data as vibrato | \"5.0 10.0\".vibrato()" {
        val p = "5.0 10.0".vibrato()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "reinterpret voice data as vibrato | seq(\"5.0 10.0\").apply(vibrato())" {
        val p = seq("5.0 10.0").apply(vibrato())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "vibrato() sets VoiceData.vibrato rate" {
        val p = note("a b").vibrato("5.0 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "vib() alias sets VoiceData.vibrato rate" {
        val p = note("a b").vib("5.0 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "vibrato() works as pattern extension" {
        val p = note("c").vibrato("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() works as string extension" {
        val p = "c".vibrato("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").vibrato("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() with continuous pattern sets vibrato correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").vibrato(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.vibrato shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.vibrato shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.vibrato shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.vibrato shouldBe (0.0 plusOrMinus EPSILON)
    }
})
