package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangDensitySpec : StringSpec({

    "density dsl interface" {
        val pat = "0 1"
        val ctrl = "10 20"

        dslInterfaceTests(
            "pattern.density(ctrl)" to
                    seq(pat).density(ctrl),
            "script pattern.density(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").density("$ctrl")"""),
            "string.density(ctrl)" to
                    pat.density(ctrl),
            "script string.density(ctrl)" to
                    StrudelPattern.compile(""""$pat".density("$ctrl")"""),
            "density(ctrl)" to
                    seq(pat).apply(density(ctrl)),
            "script density(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(density("$ctrl"))"""),
            // comp alias
            "pattern.d(ctrl)" to
                    seq(pat).d(ctrl),
            "script pattern.d(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").d("$ctrl")"""),
            "string.d(ctrl)" to
                    pat.d(ctrl),
            "script string.d(ctrl)" to
                    StrudelPattern.compile(""""$pat".d("$ctrl")"""),
            "d(ctrl)" to
                    seq(pat).apply(d(ctrl)),
            "script d(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(d("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("density") shouldBe 10.0
            events[1].data.oscParams?.get("density") shouldBe 20.0
        }
    }

    "reinterpret voice data as density | seq(\"0 1\").density()" {
        val p = seq("10 20").density()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("density") shouldBe 10.0
            events[1].data.oscParams?.get("density") shouldBe 20.0
        }
    }

    "reinterpret voice data as density | \"0 1\".density()" {
        val p = "10 20".density()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("density") shouldBe 10.0
            events[1].data.oscParams?.get("density") shouldBe 20.0
        }
    }

    "reinterpret voice data as density | seq(\"0 1\").apply(density())" {
        val p = seq("10 20").apply(density())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("density") shouldBe 10.0
            events[1].data.oscParams?.get("density") shouldBe 20.0
        }
    }

    "density() sets VoiceData.density" {
        val p = "0 1".apply(density("0.2 0.8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("density") } shouldBe listOf(0.2, 0.8)
    }

    "d() alias sets VoiceData.density" {
        val p = "0 1".apply(d("0.2 0.8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("density") } shouldBe listOf(0.2, 0.8)
    }

    "density() works as pattern extension" {
        val p = note("c").density("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("density") shouldBe 0.5
    }

    "density() works as string extension" {
        val p = "c".density("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("density") shouldBe 0.5
    }

    "density() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").density("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.oscParams?.get("density") shouldBe 0.5
    }

    "density() with continuous pattern sets density correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").density(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.oscParams?.get("density") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.oscParams?.get("density") shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.oscParams?.get("density") shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.oscParams?.get("density") shouldBe (0.0 plusOrMinus EPSILON)
    }
})
