package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangAccelerateSpec : StringSpec({

    "accelerate dsl interface" {
        val pat = "c4 e4"
        val amount = 2.0

        dslInterfaceTests(
            "pattern.accelerate(v)" to note(pat).accelerate(amount),
            "script pattern.accelerate(v)" to StrudelPattern.compile("""note("$pat").accelerate($amount)"""),
            "string.accelerate(v)" to pat.accelerate(amount),
            "script string.accelerate(v)" to StrudelPattern.compile(""""$pat".accelerate($amount)"""),
            "accelerate(v)" to note(pat).apply(accelerate(amount)),
            "script accelerate(v)" to StrudelPattern.compile("""note("$pat").apply(accelerate($amount))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.accelerate shouldBe amount
        }
    }

    "reinterpret voice data as accelerate | seq(\"-0.5 0.75\").accelerate()" {
        val p = seq("-0.5 0.75").accelerate()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(-0.5, 0.75)
    }

    "accelerate() sets VoiceData.accelerate correctly" {
        val p = note("a b").accelerate("-0.5 0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(-0.5, 0.75)
    }

    "control pattern accelerate() sets VoiceData.accelerate on existing pattern" {
        val base = note("c3 e3")
        val p = base.accelerate("0.1 -0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.accelerate } shouldBe listOf(0.1, -0.2, 0.1, -0.2)
    }

    "accelerate() works as string extension" {
        val p = "c3".accelerate("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.accelerate shouldBe 0.5
    }

    "accelerate() works within compiled code as chained function" {
        val p = StrudelPattern.compile("""note("a b").accelerate("0 1")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.accelerate } shouldBe listOf(0.0, 1.0)
    }

    "accelerate() with continuous pattern sets accelerate correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").accelerate(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.accelerate shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.accelerate shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.accelerate shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.accelerate shouldBe (0.0 plusOrMinus EPSILON)
    }
})
