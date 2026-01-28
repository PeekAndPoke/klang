package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangFreqSpec : StringSpec({

    "freq() sets the frequency in Hz" {
        val p = freq(440)
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
