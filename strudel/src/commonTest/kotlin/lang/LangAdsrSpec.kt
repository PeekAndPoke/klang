package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangAdsrSpec : StringSpec({

    "adsr() sets StrudelVoiceData ADSR components correctly from string" {
        val p = adsr("0.1:0.2:0.8:0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
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
            // Others should be null/unchanged
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
