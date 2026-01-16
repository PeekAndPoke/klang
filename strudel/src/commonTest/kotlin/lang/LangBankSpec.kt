package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBankSpec : StringSpec({

    "bank() sets VoiceData.bank" {
        val p = bank("RolandCR78")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bank shouldBe "RolandCR78"
    }

    "bank() works as pattern extension" {
        val p = note("c").bank("User1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "C"
        events[0].data.bank shouldBe "User1"
    }

    "bank() works as string extension" {
        val p = "c".bank("User1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c"
        events[0].data.bank shouldBe "User1"
    }

    "bank() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bank("RolandCR78")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.bank shouldBe "RolandCR78"
    }
})
