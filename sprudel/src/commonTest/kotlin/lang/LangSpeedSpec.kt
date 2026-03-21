package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangSpeedSpec : StringSpec({

    "speed dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 2.0"
        dslInterfaceTests(
            "pattern.speed(ctrl)" to seq(pat).speed(ctrl),
            "script pattern.speed(ctrl)" to SprudelPattern.compile("""seq("$pat").speed("$ctrl")"""),
            "string.speed(ctrl)" to pat.speed(ctrl),
            "script string.speed(ctrl)" to SprudelPattern.compile(""""$pat".speed("$ctrl")"""),
            "speed(ctrl)" to seq(pat).apply(speed(ctrl)),
            "script speed(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(speed("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.speed shouldBe 0.5
            events[1].data.speed shouldBe 2.0
        }
    }

    "reinterpret voice data as speed | seq(\"0.5 2.0\").speed()" {
        val p = seq("0.5 2.0").speed()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.speed shouldBe 0.5
            events[1].data.speed shouldBe 2.0
        }
    }

    "reinterpret voice data as speed | \"0.5 2.0\".speed()" {
        val p = "0.5 2.0".speed()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.speed shouldBe 0.5
            events[1].data.speed shouldBe 2.0
        }
    }

    "reinterpret voice data as speed | seq(\"0.5 2.0\").apply(speed())" {
        val p = seq("0.5 2.0").apply(speed())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.speed shouldBe 0.5
            events[1].data.speed shouldBe 2.0
        }
    }

    "speed() sets VoiceData.speed correctly" {
        val p = sound("hh hh").apply(speed("0.5 2.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.speed } shouldBe listOf(0.5, 2.0)
    }

    "control pattern speed() sets VoiceData.speed on existing pattern" {
        val base = note("c3 e3")
        val p = base.speed("0.5 2.0")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.speed } shouldBe listOf(0.5, 2.0, 0.5, 2.0)
    }

    "speed() works as string extension" {
        val p = "c3".speed("2.0")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.speed shouldBe 2.0
    }

    "speed() works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").speed("0.5 2.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
        events.map { it.data.speed } shouldBe listOf(0.5, 2.0)
    }
})
