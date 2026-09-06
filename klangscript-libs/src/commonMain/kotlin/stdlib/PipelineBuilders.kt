/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:KlangScript.Library(KlangScriptLibraries.STDLIB)

package io.peekandpoke.klang.script.stdlib

import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.StageDsl
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.annotations.KlangScriptLibraries
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError

/*
 * Builders for the per-voice pipeline. `Pipeline(p => ...)` hands a [PipelineBuilder] to the lambda;
 * every stage knob APPENDS one stage, in written order (the order is the topology, and it is
 * audible), and returns a new builder. The two stages with knobs of their own take a configure
 * lambda. Presets (`Pipeline.modern(p => ...)`, `Pipeline.pedal(p => ...)`) hand over a builder
 * that already holds the preset's stages; `tuneVca` / `tuneFilter` configure the stages that are
 * ALREADY there, which is how a preset's character is nudged without rebuilding it.
 *
 *     Pipeline(p => p.filterMod().vca(v => v.expK(2)).distort().filter(f => f.drive(1)).vca())
 *     Pipeline.modern(p => p.tuneVca(v => v.expK(2.5).declick(0.0008)))
 */

/**
 * Builder for a [PipelineDsl], handed to the `configure` lambda of `Pipeline(...)`. Stage knobs
 * (`filterMod`, `crush`, `coarse`, `distort`, `tremolo`, `phaser`, `filter`, `vca`) append one stage
 * each; `tuneVca` / `tuneFilter` configure existing stages. Immutable: every knob returns a new builder.
 */
data class PipelineBuilder(val node: PipelineDsl) {
    internal fun plus(stage: StageDsl): PipelineBuilder = copy(node = PipelineDsl(node.stages + stage))
}

/** Appends the control-rate filter-cutoff modulation stage (belongs first in the pipeline). */
@KlangScript.Function
fun PipelineBuilder.filterMod(): PipelineBuilder = plus(StageDsl.FilterMod)

/** Appends the bit-crusher waveshaper stage. */
@KlangScript.Function
fun PipelineBuilder.crush(): PipelineBuilder = plus(StageDsl.Crush)

/** Appends the sample-rate reducer ("coarse") waveshaper stage. */
@KlangScript.Function
fun PipelineBuilder.coarse(): PipelineBuilder = plus(StageDsl.Coarse)

/** Appends the distortion waveshaper stage. */
@KlangScript.Function
fun PipelineBuilder.distort(): PipelineBuilder = plus(StageDsl.Distort)

/** Appends the tremolo stage (post-filter amplitude LFO). */
@KlangScript.Function
fun PipelineBuilder.tremolo(): PipelineBuilder = plus(StageDsl.Tremolo)

/** Appends the phaser stage (post-filter all-pass sweep). */
@KlangScript.Function
fun PipelineBuilder.phaser(): PipelineBuilder = plus(StageDsl.Phaser)

/**
 * Appends the main filter stage with its per-voice "feel".
 * @param configure receives the [PipelineFilterBuilder] (knobs: `cutoffOffset`, `drive`, `drift`) and returns it.
 */
@KlangScript.Function
fun PipelineBuilder.filter(configure: ((PipelineFilterBuilder) -> PipelineFilterBuilder)? = null): PipelineBuilder =
    plus(PipelineFilterBuilder(StageDsl.Filter()).configuredBy("Pipeline filter", configure).node)

/**
 * Appends the amplitude VCA (ADSR) stage with its envelope character.
 * @param configure receives the [PipelineVcaBuilder] (knobs: `expK`, `declick`, `on`) and returns it.
 */
@KlangScript.Function
fun PipelineBuilder.vca(configure: ((PipelineVcaBuilder) -> PipelineVcaBuilder)? = null): PipelineBuilder =
    plus(PipelineVcaBuilder(StageDsl.Vca()).configuredBy("Pipeline vca", configure).node)

/**
 * Configures every VCA stage ALREADY in the pipeline (a preset's, typically) instead of appending
 * one: `Pipeline.modern(p => p.tuneVca(v => v.expK(2.5).declick(0.0008)))`. An error when there is
 * no VCA stage to tune; append one with `vca(...)` instead.
 * @param configure receives the [PipelineVcaBuilder] of each existing VCA stage and returns it.
 */
@KlangScript.Function
fun PipelineBuilder.tuneVca(configure: (PipelineVcaBuilder) -> PipelineVcaBuilder): PipelineBuilder {
    if (node.stages.none { it is StageDsl.Vca }) {
        throw KlangScriptTypeError("tuneVca: this pipeline has no VCA stage to tune; append one with vca(...)", operation = "Pipeline")
    }
    return copy(node = PipelineDsl(node.stages.map { stage ->
        if (stage is StageDsl.Vca) PipelineVcaBuilder(stage).configuredBy("Pipeline tuneVca", configure).node else stage
    }))
}

/**
 * Configures every filter stage ALREADY in the pipeline instead of appending one:
 * `Pipeline.pedal(p => p.tuneFilter(f => f.drive(1.0)))`. An error when there is no filter stage to
 * tune; append one with `filter(...)` instead.
 * @param configure receives the [PipelineFilterBuilder] of each existing filter stage and returns it.
 */
@KlangScript.Function
fun PipelineBuilder.tuneFilter(configure: (PipelineFilterBuilder) -> PipelineFilterBuilder): PipelineBuilder {
    if (node.stages.none { it is StageDsl.Filter }) {
        throw KlangScriptTypeError("tuneFilter: this pipeline has no filter stage to tune; append one with filter(...)", operation = "Pipeline")
    }
    return copy(node = PipelineDsl(node.stages.map { stage ->
        if (stage is StageDsl.Filter) PipelineFilterBuilder(stage).configuredBy("Pipeline tuneFilter", configure).node else stage
    }))
}

// ── VCA stage ────────────────────────────────────────────────────────────────

/** Builder for a [StageDsl.Vca] stage. Knobs: `expK`, `declick`, `on`. */
data class PipelineVcaBuilder(val node: StageDsl.Vca)

/** Exponential-curve steepness (default 3.0). Larger = steeper exp attack/decay/release. */
@KlangScript.Function
fun PipelineVcaBuilder.expK(k: Double): PipelineVcaBuilder = copy(node = node.copy(expK = k))

/** Gain de-click time constant in seconds (default 0.001). Rounds ADSR segment-join clicks. */
@KlangScript.Function
fun PipelineVcaBuilder.declick(seconds: Double): PipelineVcaBuilder = copy(node = node.copy(declickSeconds = seconds))

/**
 * Whether voices in this pipeline get an amp envelope by default (default `true`). Set `false`
 * on an engine built around instruments that carry their own envelope, so the two do not
 * compound. A voice overrides it per note with `.adsrOn()` / `.adsrOff()`.
 *
 * Accepts `true`/`false` or a truthy number, so `on(0)` works the way the sibling sprudel door
 * `adsrOn(0)` does: the runtime hands numbers through as `Double`, and a `Boolean` parameter
 * would throw on `on(0)`.
 */
@KlangScript.Function
fun PipelineVcaBuilder.on(flag: Any): PipelineVcaBuilder = copy(node = node.copy(on = coerceFlag(flag)))

// ── Filter stage ─────────────────────────────────────────────────────────────

/**
 * Builder for a [StageDsl.Filter] stage. Knobs: `cutoffOffset`, `drive`, `drift`; all values are
 * scaled by the note's `analog` param. Defaults quoted are the `FILTER_*` constants in
 * `audio_bridge/constants/FilterHumanizationDefaults.kt`, the single declaration the stage reads.
 */
data class PipelineFilterBuilder(val node: StageDsl.Filter)

/** Per-voice cutoff-offset scale per unit analog (default 0.0002, about plus or minus 0.35 cents at analog = 1). */
@KlangScript.Function
fun PipelineFilterBuilder.cutoffOffset(perAnalog: Double): PipelineFilterBuilder = copy(node = node.copy(cutoffOffsetPerAnalog = perAnalog))

/** SVF drive / saturation scale per unit analog (default 0.25; more = more OB-X "bite"). */
@KlangScript.Function
fun PipelineFilterBuilder.drive(perAnalog: Double): PipelineFilterBuilder = copy(node = node.copy(drivePerAnalog = perAnalog))

/**
 * Filter cutoff drift magnitude relative to oscillator pitch drift (default 0.25: the filter
 * wanders 4x LESS than pitch). Oscillator drift is 1.0 cent per unit analog, so this is directly
 * the filter-to-pitch drift ratio: 1.0 would make them equal.
 */
@KlangScript.Function
fun PipelineFilterBuilder.drift(relToOsc: Double): PipelineFilterBuilder = copy(node = node.copy(driftRelToOsc = relToOsc))

/**
 * The runtime hands numeric literals through as `Double`, so a `Boolean` parameter would throw a
 * raw ClassCastException on `on(1)`, and numeric flags are the established idiom on the sprudel
 * side (`adsrOn(0)`). Coerce rather than throw, per the project's user-facing-param rule.
 */
private fun coerceFlag(flag: Any): Boolean = when (flag) {
    is Boolean -> flag
    is Number -> flag.toDouble() != 0.0
    else -> true
}
