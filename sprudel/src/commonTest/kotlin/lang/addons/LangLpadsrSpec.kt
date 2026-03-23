package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note

class LangLpadsrSpec : StringSpec({

    "lpadsr dsl interface" {
        val pat = "c3"
        val ctrl = "0.01:0.3:0.5:0.5"

        dslInterfaceTests(
            "pattern.lpadsr(ctrl)" to
                    note(pat).lpadsr(ctrl),
            "script pattern.lpadsr(ctrl)" to
                    SprudelPattern.compile("""note("$pat").lpadsr("$ctrl")"""),
            "string.lpadsr(ctrl)" to
                    pat.lpadsr(ctrl),
            "script string.lpadsr(ctrl)" to
                    SprudelPattern.compile(""""$pat".lpadsr("$ctrl")"""),
            "lpadsr(ctrl)" to
                    note(pat).apply(lpadsr(ctrl)),
            "script lpadsr(ctrl)" to
                    SprudelPattern.compile("""note("$pat").apply(lpadsr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.lpattack shouldBe 0.01
                events[0].data.lpdecay shouldBe 0.3
                events[0].data.lpsustain shouldBe 0.5
                events[0].data.lprelease shouldBe 0.5
            }
        }
    }

    "lpadsr() sets all four params" {
        val p = note("c3").lpadsr("0.02:0.4:0.6:0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            lpattack shouldBe 0.02
            lpdecay shouldBe 0.4
            lpsustain shouldBe 0.6
            lprelease shouldBe 0.8
        }
    }

    "lpadsr() with partial params sets only specified fields" {
        val p = note("c3").lpadsr("0.01:0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            lpattack shouldBe 0.01
            lpdecay shouldBe 0.3
            lpsustain shouldBe null
            lprelease shouldBe null
        }
    }

    "lpadsr() works with control pattern" {
        val p = note("c3 e3").lpadsr("0.01:0.2:0.5:0.3 0.05:0.4:0.7:0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.lpattack shouldBe 0.01
        events[0].data.lpdecay shouldBe 0.2
        events[1].data.lpattack shouldBe 0.05
        events[1].data.lpdecay shouldBe 0.4
    }

    "lpadsr() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").lpadsr("0.01:0.3:0.5:0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.01
        events[0].data.lpdecay shouldBe 0.3
    }
})
