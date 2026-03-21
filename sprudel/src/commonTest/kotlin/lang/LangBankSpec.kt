package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangBankSpec : StringSpec({

    "bank dsl interface" {
        val pat = "bd sd"
        val bankName = "RolandCR78"

        dslInterfaceTests(
            "pattern.bank(name)" to s(pat).bank(bankName),
            "script pattern.bank(name)" to StrudelPattern.compile("""s("$pat").bank("$bankName")"""),
            "string.bank(name)" to pat.bank(bankName),
            "script string.bank(name)" to StrudelPattern.compile(""""$pat".bank("$bankName")"""),
            "bank(name)" to s(pat).apply(bank(bankName)),
            "script bank(name)" to StrudelPattern.compile("""s("$pat").apply(bank("$bankName"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bank shouldBe bankName
        }
    }

    "reinterpret voice data as bank | seq(\"RolandCR78 User1\").bank()" {
        val p = seq("RolandCR78 User1").bank()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bank shouldBe "RolandCR78"
        events[1].data.bank shouldBe "User1"
    }

    "reinterpret voice data as bank | \"RolandCR78 User1\".bank()" {
        val p = "RolandCR78 User1".bank()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bank shouldBe "RolandCR78"
        events[1].data.bank shouldBe "User1"
    }

    "reinterpret voice data as bank | seq(\"RolandCR78 User1\").apply(bank())" {
        val p = seq("RolandCR78 User1").apply(bank())
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bank shouldBe "RolandCR78"
        events[1].data.bank shouldBe "User1"
    }

    "bank() sets VoiceData.bank" {
        val p = s("bd").bank("RolandCR78")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bank shouldBe "RolandCR78"
    }

    "bank() works as pattern extension" {
        val p = note("c").bank("User1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "c"
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
