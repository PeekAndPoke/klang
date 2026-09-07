/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangBpqSpec : StringSpec({

    // ---- bandq ----

    "bpf(q = ...) sets BPF Q specifically" {
        // Apply BPF first, then update bandq to 1.5
        val p = note("c").bpf(freq = "1000", q = "1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandf shouldBe 1000.0
        events[0].data.bandq shouldBe 1.5

        // Verify conversion to VoiceData
        val voiceData = events[0].data.toVoiceData()
        (voiceData.filters[0] as FilterDef.BandPass).q shouldBe 1.5
    }

    "bpf(q = ...) works as pattern extension" {
        val p = note("c").bpf(q = "1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandq shouldBe 1.5
    }

    "bpf(q = ...) works as string extension" {
        val p = "c".bpf(q = "1.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bandq shouldBe 1.5
    }

    "bpf(q = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").bpf(q = "1.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bandq shouldBe 1.5
    }

    "bpq dsl interface" {
        val pat = "a b"
        val ctrl = "1.2 1.8"
        dslInterfaceTests(
            "pattern.bpf(q = ctrl)" to seq(pat).bpf(q = ctrl),
            "script pattern.bpf(q = ctrl)" to SprudelPattern.compile("""seq("$pat").bpf(q = "$ctrl")"""),
            "string.bpf(q = ctrl)" to pat.bpf(q = ctrl),
            "script string.bpf(q = ctrl)" to SprudelPattern.compile(""""$pat".bpf(q = "$ctrl")"""),
            "bpf(q = ctrl)" to seq(pat).apply(bpf(q = ctrl)),
            "script bpf(q = ctrl)" to SprudelPattern.compile("""seq("$pat").apply(bpf(q = "$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bandq shouldBe 1.2
            events[1].data.bandq shouldBe 1.8
        }
    }

    "bpf(q = ...) with continuous pattern sets bandq correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpf(q = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.bandq shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.bandq shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.bandq shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.bandq shouldBe (0.0 plusOrMinus EPSILON)
    }
})
