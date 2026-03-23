package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.s
import io.peekandpoke.klang.sprudel.lang.seq

class LangAnalogSpec : StringSpec({

    "analog dsl interface" {
        val pat = "0 1"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.analog(ctrl)" to
                    seq(pat).analog(ctrl),
            "script pattern.analog(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").analog("$ctrl")"""),
            "string.analog(ctrl)" to
                    pat.analog(ctrl),
            "script string.analog(ctrl)" to
                    SprudelPattern.compile(""""$pat".analog("$ctrl")"""),
            "analog(ctrl)" to
                    seq(pat).apply(analog(ctrl)),
            "script analog(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(analog("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("analog") shouldBe 0.1
            events[1].data.oscParams?.get("analog") shouldBe 0.5
        }
    }

    "reinterpret voice data as analog | seq(\"0.1 0.5\").analog()" {
        val p = seq("0.1 0.5").analog()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("analog") shouldBe 0.1
            events[1].data.oscParams?.get("analog") shouldBe 0.5
        }
    }

    "reinterpret voice data as analog | \"0.1 0.5\".analog()" {
        val p = "0.1 0.5".analog()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("analog") shouldBe 0.1
            events[1].data.oscParams?.get("analog") shouldBe 0.5
        }
    }

    "analog() sets oscParam correctly" {
        val p = note("c3").s("supersaw").analog("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("analog") shouldBe 0.2
    }

    "analog() works with control pattern" {
        val p = note("c3 e3").analog("0.1 0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.oscParams?.get("analog") shouldBe 0.1
        events[1].data.oscParams?.get("analog") shouldBe 0.3
    }

    "analog() works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").s("supersaw").analog("0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.oscParams?.get("analog") shouldBe 0.2
    }

    "analog() default is null when not set" {
        val p = s("supersaw")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("analog") shouldBe null
    }
})
