/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

/**
 * The name rows use `"custom"`: they pin how a NAME travels (stored lowercased, merged, carried to
 * `VoiceData`), not what it resolves to. The backend resolves it (`PipelineRegistry`).
 */
class LangPipelineSpec : StringSpec({

    "pipeline dsl interface" {
        val pat = "c3"
        val pipelineVal = "custom"

        dslInterfaceTests(
            "pattern.pipeline(v)" to note(pat).pipeline(pipelineVal),
            "script pattern.pipeline(v)" to SprudelPattern.compile("""note("$pat").pipeline("$pipelineVal")"""),
            "string.pipeline(v)" to pat.pipeline(pipelineVal),
            "script string.pipeline(v)" to SprudelPattern.compile(""""$pat".pipeline("$pipelineVal")"""),
            "pipeline(v)" to note(pat).apply(pipeline(pipelineVal)),
            "script pipeline(v)" to SprudelPattern.compile("""note("$pat").apply(pipeline("$pipelineVal"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.pipeline shouldBe PipelineValue.Named("custom")
        }
    }

    "pipeline() sets the pipeline property" {
        val p = note("c3").pipeline("custom")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pipeline shouldBe PipelineValue.Named("custom")
    }

    "pipeline() lowercases the value" {
        val p = note("c3").pipeline("CUSTOM")
        val events = p.queryArc(0.0, 1.0)

        events[0].data.pipeline shouldBe PipelineValue.Named("custom")
    }

    "pipeline() works with string pattern sequences" {
        val p = note("c3 e3").pipeline("modern custom")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.pipeline shouldBe PipelineValue.Named("modern")
        events[1].data.pipeline shouldBe PipelineValue.Named("custom")
    }

    "pipeline() as string extension" {
        val p = "c3 e3".pipeline("custom")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.pipeline shouldBe PipelineValue.Named("custom")
        events[1].data.pipeline shouldBe PipelineValue.Named("custom")
    }

    "pipeline() as pattern mapper function" {
        val p = note("c3").apply(pipeline("custom"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pipeline shouldBe PipelineValue.Named("custom")
    }

    "pipeline() as chained pattern mapper" {
        val p = note("c3").apply(gain(0.5).pipeline("custom"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pipeline shouldBe PipelineValue.Named("custom")
        events[0].data.gain shouldBe 0.5
    }

    "pipeline() flows through to VoiceData" {
        val p = note("c3").pipeline("custom")
        val events = p.queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        voiceData.pipeline shouldBe "custom"
    }

    "pipeline() merge precedence: later value wins" {
        val base = note("c3").pipeline("modern")
        val override = base.pipeline("custom")
        val events = override.queryArc(0.0, 1.0)

        events[0].data.pipeline shouldBe PipelineValue.Named("custom")
    }

    "pipeline() with an inline PipelineDsl stamps a PipelineValue.Dsl" {
        val dsl = PipelineDsl(listOf(StageDsl.Vca(), StageDsl.Distort, StageDsl.Filter()))
        val p = note("c3").pipeline(dsl)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.pipeline shouldBe PipelineValue.Dsl(dsl)
    }

    "inline pipeline DSL resolves to its uniqueId name in VoiceData" {
        val dsl = PipelineDsl(listOf())   // a distinct custom pipeline
        val p = note("c3").pipeline(dsl)
        val voiceData = p.queryArc(0.0, 1.0)[0].data.toVoiceData()

        voiceData.pipeline shouldBe dsl.uniqueId()
    }

    "pipeline not set: defaults to null" {
        val p = note("c3")
        val events = p.queryArc(0.0, 1.0)

        events[0].data.pipeline shouldBe null
    }
})
