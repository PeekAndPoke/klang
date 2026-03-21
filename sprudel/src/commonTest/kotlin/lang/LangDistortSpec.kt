package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangDistortSpec : StringSpec({

    "distort dsl interface" {
        dslInterfaceTests(
            "pattern.distort(amount)" to note("c").distort(0.5),
            "script pattern.distort(amount)" to SprudelPattern.compile("""note("c").distort(0.5)"""),
            "string.distort(amount)" to "c".distort(0.5),
            "script string.distort(amount)" to SprudelPattern.compile(""""c".distort(0.5)"""),
            "distort(amount)" to note("c").apply(distort(0.5)),
            "script distort(amount)" to SprudelPattern.compile("""note("c").apply(distort(0.5))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "dist dsl interface" {
        dslInterfaceTests(
            "pattern.dist(amount)" to note("c").dist(0.5),
            "script pattern.dist(amount)" to SprudelPattern.compile("""note("c").dist(0.5)"""),
            "string.dist(amount)" to "c".dist(0.5),
            "script string.dist(amount)" to SprudelPattern.compile(""""c".dist(0.5)"""),
            "dist(amount)" to note("c").apply(dist(0.5)),
            "script dist(amount)" to SprudelPattern.compile("""note("c").apply(dist(0.5))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "reinterpret voice data as distort | seq(\"0 0.5\").distort()" {
        val p = seq("0 0.5").distort()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.distort shouldBe 0.0
            events[1].data.distort shouldBe 0.5
        }
    }

    "reinterpret voice data as distort | \"0 0.5\".distort()" {
        val p = "0 0.5".distort()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.distort shouldBe 0.0
            events[1].data.distort shouldBe 0.5
        }
    }

    "reinterpret voice data as distort | seq(\"0 0.5\").apply(distort())" {
        val p = seq("0 0.5").apply(distort())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.distort shouldBe 0.0
            events[1].data.distort shouldBe 0.5
        }
    }

    "distort() sets VoiceData.distort" {
        val p = note("a b").distort("0.5 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.5, 10.0)
    }

    "distort() works as pattern extension" {
        val p = note("c").distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works as string extension" {
        val p = "c".distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").distort("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() with continuous pattern sets distort correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").distort(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.distort shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.distort shouldBe (0.0 plusOrMinus EPSILON)
    }

    // Alias tests

    "dist() is an alias for distort()" {
        val p = note("c").apply(dist("3.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 3.0
    }

    "dist() works as pattern extension" {
        val p = note("c").dist("3.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 3.0
    }

    "dist() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").dist("3.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.distort shouldBe 3.0
    }

    // -- combined "amount:shape" format -----------------------------------------------

    "distort() combined sets amount and shape" {
        val p = note("c").distort("0.5:hard")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "hard"
        }
    }

    "distort() combined with amount only (no colon) preserves backward compat" {
        val p = note("c").distort(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.7
        events[0].data.distortShape shouldBe null
    }

    "distort() combined works as string extension" {
        val p = "c".distort("0.8:fold")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.8
            distortShape shouldBe "fold"
        }
    }

    "distort() combined works in compiled code" {
        val p = SprudelPattern.compile("""note("c").distort("0.5:soft")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "soft"
        }
    }

    "dist() combined works" {
        val p = note("c").dist("0.6:diode")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.6
            distortShape shouldBe "diode"
        }
    }

    "distort() combined works with mini-notation patterns" {
        val p = note("c3 e3").distort("<0.3:soft 0.6:hard>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.distort shouldBe 0.3
            cycle0[0].data.distortShape shouldBe "soft"

            cycle1.size shouldBe 2
            cycle1[0].data.distort shouldBe 0.6
            cycle1[0].data.distortShape shouldBe "hard"
        }
    }

    "distort() combined works chained with other effects" {
        val p = note("c").apply(gain(0.8).distort("0.5:fold"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            distort shouldBe 0.5
            distortShape shouldBe "fold"
        }
    }

    // -- distortshape() / distshape() / dshape() ------------------------------------------

    "distortshape dsl interface" {
        dslInterfaceTests(
            "pattern.distortshape(shape)" to note("c").distortshape("fold"),
            "script pattern.distortshape(shape)" to
                    SprudelPattern.compile("""note("c").distortshape("fold")"""),
            "string.distortshape(shape)" to "c".distortshape("fold"),
            "script string.distortshape(shape)" to
                    SprudelPattern.compile(""""c".distortshape("fold")"""),
            "distortshape(shape)" to note("c").apply(distortshape("fold")),
            "script distortshape(shape)" to
                    SprudelPattern.compile("""note("c").apply(distortshape("fold"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.distortShape shouldBe "fold"
        }
    }

    "distshape dsl interface" {
        dslInterfaceTests(
            "pattern.distshape(shape)" to note("c").distshape("hard"),
            "script pattern.distshape(shape)" to
                    SprudelPattern.compile("""note("c").distshape("hard")"""),
            "string.distshape(shape)" to "c".distshape("hard"),
            "script string.distshape(shape)" to
                    SprudelPattern.compile(""""c".distshape("hard")"""),
            "distshape(shape)" to note("c").apply(distshape("hard")),
            "script distshape(shape)" to
                    SprudelPattern.compile("""note("c").apply(distshape("hard"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.distortShape shouldBe "hard"
        }
    }

    "dshape dsl interface" {
        dslInterfaceTests(
            "pattern.dshape(shape)" to note("c").dshape("exp"),
            "script pattern.dshape(shape)" to
                    SprudelPattern.compile("""note("c").dshape("exp")"""),
            "string.dshape(shape)" to "c".dshape("exp"),
            "script string.dshape(shape)" to
                    SprudelPattern.compile(""""c".dshape("exp")"""),
            "dshape(shape)" to note("c").apply(dshape("exp")),
            "script dshape(shape)" to
                    SprudelPattern.compile("""note("c").apply(dshape("exp"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.distortShape shouldBe "exp"
        }
    }

    "distortshape() sets VoiceData.distortShape" {
        val p = note("c").distortshape("fold")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortShape shouldBe "fold"
    }

    "distortshape() works with control pattern" {
        val p = note("c3 e3").distortshape("<soft hard>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.distortShape shouldBe "soft"

            cycle1.size shouldBe 2
            cycle1[0].data.distortShape shouldBe "hard"
        }
    }

    "distortshape() converts to lowercase" {
        val p = note("c").distortshape("FOLD")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distortShape shouldBe "fold"
    }

    "distortshape() chains with distort()" {
        val p = note("c").distort(0.5).distortshape("hard")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "hard"
        }
    }

    "distortshape() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").distort(0.5).distortshape("fold")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "fold"
        }
    }

    "distshape() as alias works" {
        val p = note("c").distort(0.5).distshape("hard")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "hard"
        }
    }

    "dshape() as alias works" {
        val p = note("c").distort(0.5).dshape("exp")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "exp"
        }
    }

    "PatternMapperFn.distortshape() chains correctly" {
        val p = note("c").apply(distort(0.5).distortshape("fold"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            distort shouldBe 0.5
            distortShape shouldBe "fold"
        }
    }
})
