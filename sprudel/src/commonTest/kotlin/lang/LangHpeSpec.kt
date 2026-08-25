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

class LangHpeSpec : StringSpec({

    // ---- hpenv ----


    "reinterpret voice data as hpenv | seq(\"0.5 1.0\").hpe()" {
        val p = seq("0.5 1.0").hpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as hpenv | \"0.5 1.0\".hpe()" {
        val p = "0.5 1.0".hpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as hpenv | seq(\"0.5 1.0\").apply(hpe())" {
        val p = seq("0.5 1.0").apply(hpe())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }


    "hpe() works as pattern extension" {
        val p = note("c").hpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpe() works as string extension" {
        val p = "c".hpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpe() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").hpe("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }


    "hpe() creates FilterEnvDef in FilterDef" {
        val data = createSprudelVoiceData {
            hcutoff = 2000.0
            hpenv = 0.7
        }
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.depth shouldBe 0.7
    }


    "hpe dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpe(ctrl)" to seq(pat).hpe(ctrl),
            "script pattern.hpe(ctrl)" to SprudelPattern.compile("""seq("$pat").hpe("$ctrl")"""),
            "string.hpe(ctrl)" to pat.hpe(ctrl),
            "script string.hpe(ctrl)" to SprudelPattern.compile(""""$pat".hpe("$ctrl")"""),
            "hpe(ctrl)" to seq(pat).apply(hpe(ctrl)),
            "script hpe(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpe("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "hpe() with continuous pattern sets hpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpe(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hpenv shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hpenv shouldBe (0.0 plusOrMinus EPSILON)
    }
})
