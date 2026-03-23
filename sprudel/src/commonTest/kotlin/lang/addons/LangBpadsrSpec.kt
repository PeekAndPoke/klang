package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note

class LangBpadsrSpec : StringSpec({

    "bpadsr dsl interface" {
        val pat = "c3"
        val ctrl = "0.01:0.3:0.5:0.5"

        dslInterfaceTests(
            "pattern.bpadsr(ctrl)" to
                    note(pat).bpadsr(ctrl),
            "script pattern.bpadsr(ctrl)" to
                    SprudelPattern.compile("""note("$pat").bpadsr("$ctrl")"""),
            "string.bpadsr(ctrl)" to
                    pat.bpadsr(ctrl),
            "script string.bpadsr(ctrl)" to
                    SprudelPattern.compile(""""$pat".bpadsr("$ctrl")"""),
            "bpadsr(ctrl)" to
                    note(pat).apply(bpadsr(ctrl)),
            "script bpadsr(ctrl)" to
                    SprudelPattern.compile("""note("$pat").apply(bpadsr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            assertSoftly {
                events[0].data.bpattack shouldBe 0.01
                events[0].data.bpdecay shouldBe 0.3
                events[0].data.bpsustain shouldBe 0.5
                events[0].data.bprelease shouldBe 0.5
            }
        }
    }

    "bpadsr() sets all four params" {
        val p = note("c3").bpadsr("0.02:0.4:0.6:0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            bpattack shouldBe 0.02
            bpdecay shouldBe 0.4
            bpsustain shouldBe 0.6
            bprelease shouldBe 0.8
        }
    }

    "bpadsr() with partial params sets only specified fields" {
        val p = note("c3").bpadsr("0.01:0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            bpattack shouldBe 0.01
            bpdecay shouldBe 0.3
            bpsustain shouldBe null
            bprelease shouldBe null
        }
    }

    "bpadsr() works with control pattern" {
        val p = note("c3 e3").bpadsr("0.01:0.2:0.5:0.3 0.05:0.4:0.7:0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bpattack shouldBe 0.01
        events[0].data.bpdecay shouldBe 0.2
        events[1].data.bpattack shouldBe 0.05
        events[1].data.bpdecay shouldBe 0.4
    }

    "bpadsr() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").bpadsr("0.01:0.3:0.5:0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.01
        events[0].data.bpdecay shouldBe 0.3
    }
})
