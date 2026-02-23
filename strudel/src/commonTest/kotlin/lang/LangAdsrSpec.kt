package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangAdsrSpec : StringSpec({

    "adsr dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1:0.2:0.8:0.5"

        dslInterfaceTests(
            "pattern.adsr(ctrl)" to
                    seq(pat).adsr(ctrl),
            "script pattern.adsr(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").adsr("$ctrl")"""),
            "string.adsr(ctrl)" to
                    pat.adsr(ctrl),
            "script string.adsr(ctrl)" to
                    StrudelPattern.compile(""""$pat".adsr("$ctrl")"""),
            "adsr(ctrl)" to
                    seq(pat).apply(adsr(ctrl)),
            "script adsr(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(adsr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.attack shouldBe 0.1
                events[0].data.decay shouldBe 0.2
                events[0].data.sustain shouldBe 0.8
                events[0].data.release shouldBe 0.5
            }
        }
    }

    "adsr() sets VoiceData ADSR components correctly from string" {
        val p = "0 1".apply(adsr("0.1:0.2:0.8:0.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        with(events[0].data) {
            attack shouldBe 0.1
            decay shouldBe 0.2
            sustain shouldBe 0.8
            release shouldBe 0.5
        }
    }

    "adsr() works as pattern extension" {
        val p = note("c").adsr("0.1:0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            attack shouldBe 0.1
            decay shouldBe 0.2
            sustain shouldBe null
            release shouldBe null
        }
    }

    "adsr() works as string extension" {
        val p = "c".adsr("0.1:0.2:0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            attack shouldBe 0.1
            decay shouldBe 0.2
            sustain shouldBe 0.8
            release shouldBe null
        }
    }

    "adsr() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").adsr("0.1:0.2:0.8:0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            attack shouldBe 0.1
            decay shouldBe 0.2
            sustain shouldBe 0.8
            release shouldBe 0.5
        }
    }
})