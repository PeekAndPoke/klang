/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries

/**
 * `Pipeline` for KlangScript: builds [PipelineDsl] voice-pipeline configs.
 *
 * Start from a built-in (`Pipeline.modern(...)` / `Pipeline.pedal(...)`) and tune it, or build a
 * custom one by CALLING `Pipeline` with a configure lambda; the lambda receives a
 * [PipelineBuilder] whose stage knobs append stages in written order. Pass the result to a
 * pattern's `.pipeline(...)`:
 *
 * ```
 * let warm  = Pipeline.modern(p => p.tuneVca(v => v.expK(2.5).declick(0.0008)))
 * let dirty = Pipeline(p => p.vca(v => v.expK(2.0)).distort().filter(f => f.drift(8.0)))
 * note("c e g").pipeline(dirty)
 * ```
 *
 * `Pipeline()` with no lambda is the engine default, [modern]; `Pipeline(p => ...)` is the same
 * as [build]. The method forms exist so the callable form can be tested against them.
 */
@KlangScript.Library(KlangScriptLibraries.STDLIB)
@KlangScript.Object("Pipeline")
object KlangScriptPipeline {
    override fun toString(): String = "[Pipeline object]"

    /**
     * The default subtractive engine: `osc, waveshaper, VCF, VCA` (ADSR last).
     * @param configure receives a [PipelineBuilder] holding the preset's stages; tune them with `tuneVca` / `tuneFilter`, or append more.
     */
    @KlangScript.Method
    fun modern(configure: ((PipelineBuilder) -> PipelineBuilder)? = null): PipelineDsl =
        PipelineBuilder(PipelineDsl.modern).configuredBy("Pipeline.modern", configure).node

    /**
     * Guitar-pedal engine: VCA first, so the waveshapers respond to dynamics.
     * @param configure receives a [PipelineBuilder] holding the preset's stages; tune them with `tuneVca` / `tuneFilter`, or append more.
     */
    @KlangScript.Method
    fun pedal(configure: ((PipelineBuilder) -> PipelineBuilder)? = null): PipelineDsl =
        PipelineBuilder(PipelineDsl.pedal).configuredBy("Pipeline.pedal", configure).node

    /**
     * Builds a custom engine from scratch: the lambda receives an EMPTY [PipelineBuilder] and
     * appends stages in order. Stages may be omitted freely; a slot only renders if the note's
     * matching amount (distort/crush/cutoff...) is active. No lambda is the engine default ([modern]).
     *
     * ```
     * Pipeline.build(p => p.filterMod().vca().distort().filter().vca())   // double-VCA sandwich
     * ```
     *
     * @param configure receives the [PipelineBuilder] and returns it.
     */
    @KlangScript.Method
    fun build(configure: ((PipelineBuilder) -> PipelineBuilder)? = null): PipelineDsl {
        if (configure == null) {
            return PipelineDsl.modern
        }
        return PipelineBuilder(PipelineDsl(emptyList())).configuredBy("Pipeline", configure).node
    }

    /**
     * `Pipeline(p => ...)`: the callable form of [build]. `Pipeline()` is [modern].
     *
     * ```
     * note("c e g").pipeline(Pipeline(p => p.filterMod().vca().distort().filter().vca()))
     * ```
     */
    @KlangScript.Method(name = "invoke")
    fun invoke(configure: ((PipelineBuilder) -> PipelineBuilder)? = null): PipelineDsl = build(configure)
}
