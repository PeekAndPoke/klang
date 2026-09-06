/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptReferenceError
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * The pipeline doors: `Pipeline(configure)` (the `invoke` operator), its alias `Pipeline.build`,
 * the presets `Pipeline.modern(configure)` / `Pipeline.pedal(configure)`, and the two operations
 * on a [PipelineBuilder]: stage knobs APPEND, `tuneVca` / `tuneFilter` configure existing stages.
 * Script vs the Kotlin data classes, node for node.
 */
class KlangScriptPipelineBuilderSpec : StringSpec({

    fun ks(code: String): PipelineDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<PipelineDsl>()
    }

    "Pipeline() is the engine default, same as Pipeline.modern()" {
        ks("Pipeline()") shouldBe PipelineDsl.modern
        ks("Pipeline.modern()") shouldBe PipelineDsl.modern
        ks("Pipeline.pedal()") shouldBe PipelineDsl.pedal
    }

    "Pipeline(p => ...) == Pipeline.build(p => ...), node for node" {
        val code = "p => p.filterMod().vca(v => v.expK(2)).distort().filter(f => f.drive(1)).vca()"
        ks("Pipeline($code)") shouldBe ks("Pipeline.build($code)")
    }

    "stages append in written order: the whitepaper's double-VCA sandwich" {
        ks("Pipeline(p => p.filterMod().vca().distort().filter().vca())") shouldBe PipelineDsl(
            listOf(StageDsl.FilterMod, StageDsl.Vca(), StageDsl.Distort, StageDsl.Filter(), StageDsl.Vca())
        )
    }

    "every marker stage" {
        ks("Pipeline(p => p.filterMod().crush().coarse().distort().tremolo().phaser())") shouldBe PipelineDsl(
            listOf(StageDsl.FilterMod, StageDsl.Crush, StageDsl.Coarse, StageDsl.Distort, StageDsl.Tremolo, StageDsl.Phaser)
        )
    }

    "vca knobs: expK, declick, on (numeric flag coerced like sprudel's adsrOn(0))" {
        ks("Pipeline(p => p.vca(v => v.expK(2.5).declick(0.0008).on(0)))") shouldBe PipelineDsl(
            listOf(StageDsl.Vca(expK = 2.5, declickSeconds = 0.0008, on = false))
        )
        ks("Pipeline(p => p.vca(v => v.on(true)))") shouldBe PipelineDsl(listOf(StageDsl.Vca(on = true)))
    }

    "filter knobs: cutoffOffset, drive, drift" {
        ks("Pipeline(p => p.filter(f => f.cutoffOffset(0.001).drive(1.0).drift(8.0)))") shouldBe PipelineDsl(
            listOf(StageDsl.Filter(cutoffOffsetPerAnalog = 0.001, drivePerAnalog = 1.0, driftRelToOsc = 8.0))
        )
    }

    "a preset's VCA is tuned in place with tuneVca, the rest of the preset untouched" {
        ks("Pipeline.modern(p => p.tuneVca(v => v.expK(2.5).declick(0.0008)))") shouldBe PipelineDsl(
            PipelineDsl.modern.stages.map { if (it is StageDsl.Vca) it.copy(expK = 2.5, declickSeconds = 0.0008) else it }
        )
    }

    "a preset's filter is tuned in place with tuneFilter" {
        ks("Pipeline.pedal(p => p.tuneFilter(f => f.drive(1.0)))") shouldBe PipelineDsl(
            PipelineDsl.pedal.stages.map { if (it is StageDsl.Filter) it.copy(drivePerAnalog = 1.0) else it }
        )
    }

    "tuning a stage that is not there is an error that says what to do" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Pipeline(p => p.tuneVca(v => v.expK(2)))") }
        err.message shouldContain "no VCA stage to tune"
        shouldThrow<KlangScriptTypeError> { ks("Pipeline(p => p.vca().tuneFilter(f => f.drive(1)))") }
    }

    "appending after a preset works too" {
        ks("Pipeline.modern(p => p.phaser())") shouldBe PipelineDsl(PipelineDsl.modern.stages + StageDsl.Phaser)
    }

    "the Kotlin door takes the same lambda" {
        ks("Pipeline(p => p.vca(v => v.expK(2)).distort())") shouldBe
                KlangScriptPipeline.build { it.vca { v -> v.expK(2.0) }.distort() }
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Pipeline(p => { p.distort() })") }
        err.message shouldContain "the configure lambda of Pipeline returned nothing"
    }

    "Pipeline.of and Stage are gone" {
        shouldThrow<KlangScriptTypeError> { ks("Pipeline.of()") }
        shouldThrow<KlangScriptReferenceError> { ks("Stage.vca()") }
    }
})
