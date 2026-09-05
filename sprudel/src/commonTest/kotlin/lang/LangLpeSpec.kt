/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangLpeSpec : StringSpec({

    // ---- lpenv ----


    "reinterpret voice data as lpenv | seq(\"0.5 1.0\").lpe()" {
        val p = seq("0.5 1.0").lpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as lpenv | \"0.5 1.0\".lpe()" {
        val p = "0.5 1.0".lpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as lpenv | seq(\"0.5 1.0\").apply(lpe())" {
        val p = seq("0.5 1.0").apply(lpe())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }


    "lpe() works as pattern extension" {
        val p = note("c").lpe("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpe() works as string extension" {
        val p = "c".lpe("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpe() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").lpe("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }


    "lpe() creates FilterEnvDef in FilterDef" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            lpenv = 0.7
        }
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.depth shouldBe 0.7
    }


    "lpe dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.lpe(ctrl)" to seq(pat).lpe(ctrl),
            "script pattern.lpe(ctrl)" to SprudelPattern.compile("""seq("$pat").lpe("$ctrl")"""),
            "string.lpe(ctrl)" to pat.lpe(ctrl),
            "script string.lpe(ctrl)" to SprudelPattern.compile(""""$pat".lpe("$ctrl")"""),
            "lpe(ctrl)" to seq(pat).apply(lpe(ctrl)),
            "script lpe(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(lpe("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.lpenv shouldBe 0.5
            events[1].data.lpenv shouldBe 1.0
        }
    }

    "lpe() with continuous pattern sets lpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpe(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.lpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.lpenv shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.lpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.lpenv shouldBe (0.0 plusOrMinus EPSILON)
    }
})
