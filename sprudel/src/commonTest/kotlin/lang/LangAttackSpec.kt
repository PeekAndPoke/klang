package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangAttackSpec : StringSpec({

    "attack dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.attack(ctrl)" to
                    seq(pat).attack(ctrl),
            "script pattern.attack(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").attack("$ctrl")"""),
            "string.attack(ctrl)" to
                    pat.attack(ctrl),
            "script string.attack(ctrl)" to
                    StrudelPattern.compile(""""$pat".attack("$ctrl")"""),
            "attack(ctrl)" to
                    seq(pat).apply(attack(ctrl)),
            "script attack(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(attack("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.attack shouldBe 0.1
            events[1].data.attack shouldBe 0.5
        }
    }

    "reinterpret voice data as attack | seq(\"0 1\").attack()" {
        val p = seq("0.1 0.5").attack()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.attack shouldBe 0.1
            events[1].data.attack shouldBe 0.5
        }
    }

    "reinterpret voice data as attack | \"0 1\".attack()" {
        val p = "0.1 0.5".attack()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.attack shouldBe 0.1
            events[1].data.attack shouldBe 0.5
        }
    }

    "reinterpret voice data as attack | seq(\"0 1\").apply(attack())" {
        val p = seq("0.1 0.5").apply(attack())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.attack shouldBe 0.1
            events[1].data.attack shouldBe 0.5
        }
    }

    "attack() sets VoiceData.attack" {
        val p = "0 1".apply(attack("0.1 0.5"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.attack } shouldBe listOf(0.1, 0.5)
    }

    "attack() works as pattern extension" {
        val p = note("c").attack("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.attack shouldBe 0.1
    }

    "attack() works as string extension" {
        val p = "c".attack("0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.attack shouldBe 0.1
    }

    "attack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").attack("0.1")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.attack shouldBe (0.1 plusOrMinus EPSILON)
    }

    "attack() with continuous pattern sets attack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").attack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.attack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.attack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.attack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.attack shouldBe (0.0 plusOrMinus EPSILON)
    }
})
