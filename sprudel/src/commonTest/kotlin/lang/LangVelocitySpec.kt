package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangVelocitySpec : StringSpec({

    "velocity dsl interface" {
        val pat = "0 1"
        val ctrl = "0 0.25"

        dslInterfaceTests(
            "pattern.velocity(ctrl)" to
                    seq(pat).velocity(ctrl),
            "script pattern.velocity(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").velocity("$ctrl")"""),
            "string.velocity(ctrl)" to
                    pat.velocity(ctrl),
            "script string.velocity(ctrl)" to
                    StrudelPattern.compile(""""$pat".velocity("$ctrl")"""),
            "velocity(ctrl)" to
                    seq(pat).apply(velocity(ctrl)),
            "script velocity(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(velocity("$ctrl"))"""),
            // vel alias
            "pattern.vel(ctrl)" to
                    seq(pat).vel(ctrl),
            "script pattern.vel(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").vel("$ctrl")"""),
            "string.vel(ctrl)" to
                    pat.vel(ctrl),
            "script string.vel(ctrl)" to
                    StrudelPattern.compile(""""$pat".vel("$ctrl")"""),
            "vel(ctrl)" to
                    seq(pat).apply(vel(ctrl)),
            "script vel(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(vel("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.velocity shouldBe 0.0
            events[1].data.velocity shouldBe 0.25
        }
    }

    "reinterpret voice data as velocity | seq(\"0 1\").velocity()" {
        val p = seq("0 1").velocity()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.velocity shouldBe 0.0
            events[1].data.velocity shouldBe 1.0
        }
    }

    "reinterpret voice data as velocity | \"0 1\".velocity()" {
        val p = "0 1".velocity()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.velocity shouldBe 0.0
            events[1].data.velocity shouldBe 1.0
        }
    }

    "reinterpret voice data as velocity | seq(\"0 1\").apply(velocity())" {
        val p = seq("0 1").apply(velocity())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.velocity shouldBe 0.0
            events[1].data.velocity shouldBe 1.0
        }
    }

    "top-level velocity() sets VoiceData.velocity correctly" {
        // Given a simple sequence of velocity values within one cycle
        val p = "1 0".apply(velocity("0.5 1.0"))

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the velocity values in order
        events.size shouldBe 2
        events.map { it.data.velocity } shouldBe listOf(0.5, 1.0)
    }

    "control pattern velocity() sets VoiceData.velocity on existing pattern" {
        // Given a base note pattern producing two events per cycle
        val base = note("c3 e3")

        // When applying a control pattern that sets the velocity per step
        val p = base.velocity("0.1 0.2")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the velocity values in order
        events.map { it.data.velocity } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "velocity() works as string extension" {
        val p = "c3".velocity("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.velocity shouldBe 0.5
    }

    "velocity() works within compiled code as top-level function" {
        val p = StrudelPattern.compile(""""1 0".apply(velocity("0.5 1.0"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.velocity } shouldBe listOf(0.5, 1.0)
    }

    "velocity() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").velocity("0.5 1.0")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.velocity } shouldBe listOf(0.5, 1.0)
    }

    "velocity() with continuous pattern sets velocity correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").velocity(sine)
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 4
            // t=0.0: sine(0) = 0.5
            events[0].data.velocity shouldBe (0.5 plusOrMinus EPSILON)
            // t=0.25: sine(0.25) = 1.0
            events[1].data.velocity shouldBe (1.0 plusOrMinus EPSILON)
            // t=0.5: sine(0.5) = 0.5
            events[2].data.velocity shouldBe (0.5 plusOrMinus EPSILON)
            // t=0.75: sine(0.75) = 0.0
            events[3].data.velocity shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "vel() alias works as top-level function" {
        val p = "0 1".apply(vel("0.3 0.7"))

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.velocity } shouldBe listOf(0.3, 0.7)
    }

    "vel() alias works as pattern extension" {
        val p = note("c d").vel("0.4 0.6")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.velocity } shouldBe listOf(0.4, 0.6)
    }

    "vel() alias works as string extension" {
        val p = "e3".vel("0.8")

        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.velocity shouldBe 0.8
    }

    "vel() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").vel("0.2 0.9")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.velocity } shouldBe listOf(0.2, 0.9)
    }
})
