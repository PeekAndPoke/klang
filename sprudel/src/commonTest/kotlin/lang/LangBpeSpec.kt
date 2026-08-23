/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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

class LangBpeSpec : StringSpec({

    // ---- bpenv ----


    "reinterpret voice data as bpenv | seq(\"0.5 1.0\").bpe()" {
        val p = seq("0.5 1.0").bpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as bpenv | \"0.5 1.0\".bpe()" {
        val p = "0.5 1.0".bpe()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "reinterpret voice data as bpenv | seq(\"0.5 1.0\").apply(bpe())" {
        val p = seq("0.5 1.0").apply(bpe())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }


    "bpe() works as pattern extension" {
        val p = note("c").bpe("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpe() works as string extension" {
        val p = "c".bpe("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpe() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").bpe("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }


    "bpe() creates FilterEnvDef in FilterDef" {
        val data = createSprudelVoiceData {
            bandf = 1000.0
            bpenv = 0.5
        }
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.depth shouldBe 0.5
    }


    "bpe dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpe(ctrl)" to seq(pat).bpe(ctrl),
            "script pattern.bpe(ctrl)" to SprudelPattern.compile("""seq("$pat").bpe("$ctrl")"""),
            "string.bpe(ctrl)" to pat.bpe(ctrl),
            "script string.bpe(ctrl)" to SprudelPattern.compile(""""$pat".bpe("$ctrl")"""),
            "bpe(ctrl)" to seq(pat).apply(bpe(ctrl)),
            "script bpe(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(bpe("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "bpe() with continuous pattern sets bpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpe(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.bpenv shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.bpenv shouldBe (0.0 plusOrMinus EPSILON)
    }
})
