/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

// -- pipeline() -------------------------------------------------------------------------------------------------------

// A name is stored as PipelineValue.Named (lowercased for consistency — PipelinePreset.fromName also
// lowercases defensively so non-sprudel callers, e.g. raw VoiceData, are still case-insensitive).
private val pipelineMutation = voiceSetter { name ->
    pipeline = name?.toString()?.lowercase()?.let { PipelineValue.Named(it) }
}

private fun applyPipeline(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // An inline pipeline DSL is stamped directly onto every source event as PipelineValue.Dsl — it
    // denormalizes to a synthetic name at the wire boundary. Mirrors the inline-ignitor `sound(dsl)` path.
    val singleArg = args.singleOrNull()?.value
    if (singleArg is PipelineDsl) {
        return source.reinterpretVoice { vd -> vd.copy(pipeline = PipelineValue.Dsl(singleArg)) }
    }

    return source._applyControlFromParams(args, pipelineMutation) { src, ctrl ->
        src.pipeline = ctrl.pipeline
        src
    }
}

/**
 * Selects the voice pipeline for this pattern.
 *
 * Each pipeline is a different topology for the voice's Filter stage. Currently:
 *
 * - `"modern"` (default) — classic subtractive `osc → VCF → VCA`. ADSR runs last so the filter
 *   and phaser see steady-amplitude signal — no attack smearing. Waveshapers still precede
 *   the filter so LP/HP can clean up their harmonics.
 * - `"pedal"` — guitar-pedal feel. ADSR runs first so the waveshapers respond to dynamics
 *   (quiet attack stays clean, hot sustain saturates, release tail fades through the drive).
 *
 * Unknown or null names fall back to `modern`.
 *
 * @param name The pipeline name (case-insensitive).
 * @return A new pattern using the selected pipeline.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").distort(0.8).pipeline("pedal")   // dynamics-responsive distortion
 * ```
 *
 * @category effects
 * @tags pipeline, topology, modern, pedal, motor
 */
@KlangScript.Function
fun SprudelPattern.pipeline(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPipeline(this, listOf(name).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and selects the voice pipeline.
 *
 * @param name The pipeline name (case-insensitive). See [SprudelPattern.pipeline] for known pipelines.
 * @return A new pattern using the selected pipeline.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".pipeline("pedal").s("supersaw").distort(0.8)
 * ```
 */
@KlangScript.Function
fun String.pipeline(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pipeline(name, callInfo)

/**
 * Returns a [PatternMapperFn] that selects the voice pipeline.
 *
 * @param name The pipeline name (case-insensitive). See [SprudelPattern.pipeline] for known pipelines.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(pipeline("pedal"))   // selects pedal pipeline via mapper
 * ```
 *
 * @category effects
 * @tags pipeline, topology, modern, pedal, motor
 */
@KlangScript.Function
fun pipeline(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pipeline(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that selects the voice pipeline after the previous mapper.
 *
 * @param name The pipeline name (case-insensitive). See [SprudelPattern.pipeline] for known pipelines.
 */
@KlangScript.Function
fun PatternMapperFn.pipeline(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pipeline(name, callInfo) }
