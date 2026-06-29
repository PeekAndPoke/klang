/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangPipelineSpec : StringSpec({

    "engine dsl interface" {
        val pat = "c3"
        val pipelineVal = "pedal"

        dslInterfaceTests(
            "pattern.pipeline(v)" to note(pat).pipeline(pipelineVal),
            "script pattern.pipeline(v)" to SprudelPattern.compile("""note("$pat").pipeline("$pipelineVal")"""),
            "string.pipeline(v)" to pat.pipeline(pipelineVal),
            "script string.pipeline(v)" to SprudelPattern.compile(""""$pat".pipeline("$pipelineVal")"""),
            "pipeline(v)" to note(pat).apply(pipeline(pipelineVal)),
            "script pipeline(v)" to SprudelPattern.compile("""note("$pat").apply(pipeline("$pipelineVal"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pipeline shouldBe "pedal"
        }
    }

    "pipeline() sets the pipeline property" {
        val p = note("c3").pipeline("pedal")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pipeline shouldBe "pedal"
    }

    "pipeline() lowercases the value" {
        val p = note("c3").pipeline("PEDAL")
        val events = p.queryArc(0.0, 1.0)

        events[0].data.pipeline shouldBe "pedal"
    }

    "pipeline() works with string pattern sequences" {
        val p = note("c3 e3").pipeline("modern pedal")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.pipeline shouldBe "modern"
        events[1].data.pipeline shouldBe "pedal"
    }

    "pipeline() as string extension" {
        val p = "c3 e3".pipeline("pedal")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.pipeline shouldBe "pedal"
        events[1].data.pipeline shouldBe "pedal"
    }

    "pipeline() as pattern mapper function" {
        val p = note("c3").apply(pipeline("pedal"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pipeline shouldBe "pedal"
    }

    "pipeline() as chained pattern mapper" {
        val p = note("c3").apply(gain(0.5).pipeline("pedal"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pipeline shouldBe "pedal"
        events[0].data.gain shouldBe 0.5
    }

    "pipeline() flows through to VoiceData" {
        val p = note("c3").pipeline("pedal")
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        voiceData.pipeline shouldBe "pedal"
    }

    "pipeline() merge precedence: later value wins" {
        val base = note("c3").pipeline("modern")
        val override = base.pipeline("pedal")
        val events = override.queryArc(0.0, 1.0)

        events[0].data.pipeline shouldBe "pedal"
    }

    "engine not set: defaults to null" {
        val p = note("c3")
        val events = p.queryArc(0.0, 1.0)

        events[0].data.pipeline shouldBe null
    }
})
