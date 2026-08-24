/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.mul
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.s
import io.peekandpoke.klang.sprudel.lang.seq
import io.peekandpoke.klang.sprudel.lang.sine
import io.peekandpoke.klang.sprudel.soundName

class LangOnepoleSpec : StringSpec({

    "onepole dsl interface" {
        val pat = "0 1"
        val ctrl = "12000 3700"

        dslInterfaceTests(
            "pattern.onepole(ctrl)" to
                    seq(pat).onepole(ctrl),
            "script pattern.onepole(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").onepole("$ctrl")"""),
            "string.onepole(ctrl)" to
                    pat.onepole(ctrl),
            "script string.onepole(ctrl)" to
                    SprudelPattern.compile(""""$pat".onepole("$ctrl")"""),
            "onepole(ctrl)" to
                    seq(pat).apply(onepole(ctrl)),
            "script onepole(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(onepole("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.oscParams?.get("onepole") shouldBe 12000.0
            events[1].data.oscParams?.get("onepole") shouldBe 3700.0
        }
    }

    "reinterpret voice data as onepole | seq(\"0 1\").onepole()" {
        val p = seq("12000 3700").onepole()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("onepole") shouldBe 12000.0
            events[1].data.oscParams?.get("onepole") shouldBe 3700.0
        }
    }

    "reinterpret voice data as onepole | \"0 1\".onepole()" {
        val p = "12000 3700".onepole()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("onepole") shouldBe 12000.0
            events[1].data.oscParams?.get("onepole") shouldBe 3700.0
        }
    }

    "reinterpret voice data as onepole | seq(\"0 1\").apply(onepole())" {
        val p = seq("12000 3700").apply(onepole())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("onepole") shouldBe 12000.0
            events[1].data.oscParams?.get("onepole") shouldBe 3700.0
        }
    }

    "onepole() sets the oscParam via a mapper" {
        val p = "0 1".apply(onepole("19084 12000"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.oscParams?.get("onepole") } shouldBe listOf(19084.0, 12000.0)
    }

    "onepole() works as pattern extension" {
        val p = s("supersaw").onepole("3700")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("onepole") shouldBe 3700.0
    }

    "onepole() works as string extension" {
        val p = "supersaw".onepole("17814")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("onepole") shouldBe 17814.0
    }

    "onepole() works in compiled code" {
        val p = SprudelPattern.compile("""s("supersaw").onepole("4916")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.oscParams?.get("onepole") shouldBe 4916.0
    }

    "onepole() with continuous pattern samples the signal per event" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75);
        // a signal-valued freq passes through raw (Hz-scaled signals via sine.range(...))
        val p = note("a b c d").onepole(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.oscParams?.get("onepole") shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.oscParams?.get("onepole") shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.oscParams?.get("onepole") shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.oscParams?.get("onepole") shouldBe (0.0 plusOrMinus EPSILON)
    }

    "onepole() can be chained with other functions" {
        val p = s("supersaw").onepole("19084").note("c3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundName shouldBe "supersaw"
        events[0].data.oscParams?.get("onepole") shouldBe 19084.0
        events[0].data.note shouldBe "c3"
    }

    "onepole() default is null when not set" {
        val p = s("supersaw")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.oscParams?.get("onepole") shouldBe null
    }

    "apply(mul().onepole())" {
        val p = seq("2000 4000").apply(mul("2").onepole())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("onepole") shouldBe (4000.0 plusOrMinus EPSILON)  // 2000*2
            events[1].data.oscParams?.get("onepole") shouldBe (8000.0 plusOrMinus EPSILON)  // 4000*2
        }
    }

    "script apply(mul().onepole())" {
        val p = SprudelPattern.compile("""seq("2000 4000").apply(mul("2").onepole())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.oscParams?.get("onepole") shouldBe (4000.0 plusOrMinus EPSILON)  // 2000*2
            events[1].data.oscParams?.get("onepole") shouldBe (8000.0 plusOrMinus EPSILON)  // 4000*2
        }
    }

    "onepole() can be applied to different oscillators" {
        val p = s("sine triangle square").onepole("17814 3700 4916")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.soundName shouldBe "sine"
        events[0].data.oscParams?.get("onepole") shouldBe 17814.0
        events[1].data.soundName shouldBe "triangle"
        events[1].data.oscParams?.get("onepole") shouldBe 3700.0
        events[2].data.soundName shouldBe "square"
        events[2].data.oscParams?.get("onepole") shouldBe 4916.0
    }
})
