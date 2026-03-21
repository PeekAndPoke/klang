package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangFreqSpec : StringSpec({

    "freq dsl interface" {
        val pat = "saw"
        val amount = 440

        dslInterfaceTests(
            "pattern.freq(v)" to s(pat).freq(amount),
            "script pattern.freq(v)" to StrudelPattern.compile("""s("$pat").freq($amount)"""),
            "string.freq(v)" to pat.freq(amount),
            "script string.freq(v)" to StrudelPattern.compile(""""$pat".freq($amount)"""),
            "freq(v)" to s(pat).apply(freq(amount)),
            "script freq(v)" to StrudelPattern.compile("""s("$pat").apply(freq($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.freqHz shouldBe 440.0
        }
    }

    "reinterpret voice data as freq | seq(\"440 880\").freq()" {
        val p = seq("440 880").freq()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.freqHz } shouldBe listOf(440.0, 880.0)
    }

    "reinterpret voice data as freq | \"440 880\".freq()" {
        val p = "440 880".freq()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.freqHz } shouldBe listOf(440.0, 880.0)
    }

    "reinterpret voice data as freq | seq(\"440 880\").apply(freq())" {
        val p = seq("440 880").apply(freq())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.freqHz } shouldBe listOf(440.0, 880.0)
    }

    "freq() sets the frequency in Hz" {
        val p = s("saw").freq(440)
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 1
        events[0].data.freqHz shouldBe 440.0
    }

    "freq() works as pattern extension" {
        val p = s("saw").freq(220)
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 1
        events[0].data.sound shouldBe "saw"
        events[0].data.freqHz shouldBe 220.0
    }

    "freq() supports control patterns" {
        val p = s("saw saw").freq("440 880")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events shouldHaveSize 2
            events[0].data.freqHz shouldBe 440.0
            events[1].data.freqHz shouldBe 880.0
        }
    }

    "freq() works as string extension" {
        val p = "saw".freq(440)
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 1
        events[0].data.freqHz shouldBe 440.0
    }
})
