package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangVibratoModSpec : StringSpec({

    "vibratoMod dsl interface" {
        val pat = "c4 e4"
        val amount = 0.5

        dslInterfaceTests(
            "pattern.vibratoMod(depth)" to note(pat).vibratoMod(amount),
            "script pattern.vibratoMod(depth)" to StrudelPattern.compile("""note("$pat").vibratoMod($amount)"""),
            "string.vibratoMod(depth)" to pat.vibratoMod(amount),
            "script string.vibratoMod(depth)" to StrudelPattern.compile(""""$pat".vibratoMod($amount)"""),
            "vibratoMod(depth)" to note(pat).apply(vibratoMod(amount)),
            "script vibratoMod(depth)" to StrudelPattern.compile("""note("$pat").apply(vibratoMod($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.vibratoMod shouldBe amount
        }
    }

    "vibmod dsl interface" {
        val pat = "c4 e4"
        val amount = 0.5

        dslInterfaceTests(
            "pattern.vibmod(depth)" to note(pat).vibmod(amount),
            "script pattern.vibmod(depth)" to StrudelPattern.compile("""note("$pat").vibmod($amount)"""),
            "string.vibmod(depth)" to pat.vibmod(amount),
            "script string.vibmod(depth)" to StrudelPattern.compile(""""$pat".vibmod($amount)"""),
            "vibmod(depth)" to note(pat).apply(vibmod(amount)),
            "script vibmod(depth)" to StrudelPattern.compile("""note("$pat").apply(vibmod($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.vibratoMod shouldBe amount
        }
    }

    "reinterpret voice data as vibratoMod | seq(\"0.1 0.5\").vibratoMod()" {
        val p = seq("0.1 0.5").vibratoMod()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "reinterpret voice data as vibratoMod | \"0.1 0.5\".vibratoMod()" {
        val p = "0.1 0.5".vibratoMod()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "reinterpret voice data as vibratoMod | seq(\"0.1 0.5\").apply(vibratoMod())" {
        val p = seq("0.1 0.5").apply(vibratoMod())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "vibratoMod() sets VoiceData.vibratoMod depth" {
        val p = note("a b").vibratoMod("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "vibmod() alias sets VoiceData.vibratoMod depth" {
        val p = note("a b").vibmod("0.1 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(0.1, 0.5)
    }

    "vibratoMod() works as pattern extension" {
        val p = note("c").vibratoMod("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibratoMod() works as string extension" {
        val p = "c".vibratoMod("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibratoMod() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").vibratoMod("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.vibratoMod shouldBe 0.1
    }

    "vibratoMod() with continuous pattern sets vibratoMod correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").vibratoMod(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.vibratoMod shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.vibratoMod shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.vibratoMod shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.vibratoMod shouldBe (0.0 plusOrMinus EPSILON)
    }
})
