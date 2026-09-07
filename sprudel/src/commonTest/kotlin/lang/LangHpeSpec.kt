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

class LangHpeSpec : StringSpec({

    // ---- hpenv ----

    "hpf(env = ...) works as pattern extension" {
        val p = note("c").hpf(env = "0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpf(env = ...) works as string extension" {
        val p = "c".hpf(env = "0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpf(env = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").hpf(env = "0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpenv shouldBe 0.6
    }

    "hpf(env = ...) creates FilterEnvDef in FilterDef" {
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
            "pattern.hpf(env = ctrl)" to seq(pat).hpf(env = ctrl),
            "script pattern.hpf(env = ctrl)" to SprudelPattern.compile("""seq("$pat").hpf(env = "$ctrl")"""),
            "string.hpf(env = ctrl)" to pat.hpf(env = ctrl),
            "script string.hpf(env = ctrl)" to SprudelPattern.compile(""""$pat".hpf(env = "$ctrl")"""),
            "hpf(env = ctrl)" to seq(pat).apply(hpf(env = ctrl)),
            "script hpf(env = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpf(env = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpenv shouldBe 0.5
            events[1].data.hpenv shouldBe 1.0
        }
    }

    "hpf(env = ...) with continuous pattern sets hpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpf(env = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hpenv shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hpenv shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hpenv shouldBe (0.0 plusOrMinus EPSILON)
    }
})
