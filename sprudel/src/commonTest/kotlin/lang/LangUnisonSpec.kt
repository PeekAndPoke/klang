package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangUnisonSpec : StringSpec({

    "unison dsl interface" {
        val pat = "0 1"
        val ctrl = "2 4"

        dslInterfaceTests(
            "pattern.unison(ctrl)" to
                    seq(pat).unison(ctrl),
            "script pattern.unison(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").unison("$ctrl")"""),
            "string.unison(ctrl)" to
                    pat.unison(ctrl),
            "script string.unison(ctrl)" to
                    StrudelPattern.compile(""""$pat".unison("$ctrl")"""),
            "unison(ctrl)" to
                    seq(pat).apply(unison(ctrl)),
            "script unison(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(unison("$ctrl"))"""),
            // comp alias
            "pattern.uni(ctrl)" to
                    seq(pat).uni(ctrl),
            "script pattern.uni(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").uni("$ctrl")"""),
            "string.uni(ctrl)" to
                    pat.uni(ctrl),
            "script string.uni(ctrl)" to
                    StrudelPattern.compile(""""$pat".uni("$ctrl")"""),
            "uni(ctrl)" to
                    seq(pat).apply(uni(ctrl)),
            "script uni(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(uni("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("voices") shouldBe 2
            events[1].data.oscParams?.get("voices") shouldBe 4
        }
    }

    "reinterpret voice data as velocity | seq(\"0 1\").velocity()" {
        val p = seq("0 1").unison()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("voices") shouldBe 0.0
            events[1].data.oscParams?.get("voices") shouldBe 1.0
        }
    }

    "reinterpret voice data as velocity | \"0 1\".velocity()" {
        val p = "0 1".unison()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("voices") shouldBe 0.0
            events[1].data.oscParams?.get("voices") shouldBe 1.0
        }
    }

    "reinterpret voice data as velocity | seq(\"0 1\").apply(velocity())" {
        val p = seq("0 1").apply(unison())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("voices") shouldBe 0.0
            events[1].data.oscParams?.get("voices") shouldBe 1.0
        }
    }

    "unison() sets VoiceData.voices" {
        val p = "1 0".apply(unison("4 8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("voices") } shouldBe listOf(4.0, 8.0)
    }

    "uni() alias sets VoiceData.voices" {
        val p = "1 0".apply(uni("4 8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("voices") } shouldBe listOf(4.0, 8.0)
    }

    "unison() works as pattern extension" {
        val p = note("c").unison("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 4.0
    }

    "uni() works as pattern extension" {
        val p = note("c").uni("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 4.0
    }

    "unison() works as string extension" {
        val p = "c".unison("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 4.0
    }

    "uni() works as string extension" {
        val p = "c".uni("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 4.0
    }

    "unison() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").unison("4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.oscParams?.get("voices") shouldBe 4.0
    }

    "unison() with continuous pattern sets voices correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").unison(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.oscParams?.get("voices") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.oscParams?.get("voices") shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.oscParams?.get("voices") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.oscParams?.get("voices") shouldBe (0.0 plusOrMinus EPSILON)
    }
})
