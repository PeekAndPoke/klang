/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

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

    "bpf(env = ...) works as pattern extension" {
        val p = note("c").bpf(env = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpf(env = ...) works as string extension" {
        val p = "c".bpf(env = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpf(env = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").bpf(env = "0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpf(env = ...) creates FilterEnvDef in FilterDef" {
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
            "pattern.bpf(env = ctrl)" to seq(pat).bpf(env = ctrl),
            "script pattern.bpf(env = ctrl)" to SprudelPattern.compile("""seq("$pat").bpf(env = "$ctrl")"""),
            "string.bpf(env = ctrl)" to pat.bpf(env = ctrl),
            "script string.bpf(env = ctrl)" to SprudelPattern.compile(""""$pat".bpf(env = "$ctrl")"""),
            "bpf(env = ctrl)" to seq(pat).apply(bpf(env = ctrl)),
            "script bpf(env = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(bpf(env = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpenv shouldBe 0.5
            events[1].data.bpenv shouldBe 1.0
        }
    }

    "bpf(env = ...) with continuous pattern sets bpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpf(env = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.bpenv shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.bpenv shouldBe (0.0 plusOrMinus EPSILON)
    }
})
