package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note

class LangHpadsrSpec : StringSpec({

    "hpadsr dsl interface" {
        val pat = "c3"
        val ctrl = "0.01:0.3:0.5:0.5"

        dslInterfaceTests(
            "pattern.hpadsr(ctrl)" to
                    note(pat).hpadsr(ctrl),
            "script pattern.hpadsr(ctrl)" to
                    SprudelPattern.compile("""note("$pat").hpadsr("$ctrl")"""),
            "string.hpadsr(ctrl)" to
                    pat.hpadsr(ctrl),
            "script string.hpadsr(ctrl)" to
                    SprudelPattern.compile(""""$pat".hpadsr("$ctrl")"""),
            "hpadsr(ctrl)" to
                    note(pat).apply(hpadsr(ctrl)),
            "script hpadsr(ctrl)" to
                    SprudelPattern.compile("""note("$pat").apply(hpadsr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.hpattack shouldBe 0.01
                events[0].data.hpdecay shouldBe 0.3
                events[0].data.hpsustain shouldBe 0.5
                events[0].data.hprelease shouldBe 0.5
            }
        }
    }

    "hpadsr() sets all four params" {
        val p = note("c3").hpadsr("0.02:0.4:0.6:0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            hpattack shouldBe 0.02
            hpdecay shouldBe 0.4
            hpsustain shouldBe 0.6
            hprelease shouldBe 0.8
        }
    }

    "hpadsr() with partial params sets only specified fields" {
        val p = note("c3").hpadsr("0.01:0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            hpattack shouldBe 0.01
            hpdecay shouldBe 0.3
            hpsustain shouldBe null
            hprelease shouldBe null
        }
    }

    "hpadsr() works with control pattern" {
        val p = note("c3 e3").hpadsr("0.01:0.2:0.5:0.3 0.05:0.4:0.7:0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hpattack shouldBe 0.01
        events[0].data.hpdecay shouldBe 0.2
        events[1].data.hpattack shouldBe 0.05
        events[1].data.hpdecay shouldBe 0.4
    }

    "hpadsr() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").hpadsr("0.01:0.3:0.5:0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.01
        events[0].data.hpdecay shouldBe 0.3
    }
})
