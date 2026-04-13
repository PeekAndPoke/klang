@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangEngineInit = false

// -- engine() ---------------------------------------------------------------------------------------------------------

// Lowercased on storage for consistency. AudioEngine.fromName also lowercases defensively
// so non-sprudel callers (e.g. raw VoiceData) are still case-insensitive.
private val engineMutation = voiceModifier { name -> copy(engine = name?.toString()?.lowercase()) }

fun applyEngine(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, engineMutation) { src, ctrl ->
        src.copy(engine = ctrl.engine)
    }
}

internal val _engine by dslPatternMapper { args, callInfo -> { p -> p._engine(args, callInfo) } }
internal val SprudelPattern._engine by dslPatternExtension { p, args, /* callInfo */ _ -> applyEngine(p, args) }
internal val String._engine by dslStringExtension { p, args, callInfo -> p._engine(args, callInfo) }
internal val PatternMapperFn._engine by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_engine(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Selects the voice pipeline engine for this pattern.
 *
 * Each engine is a different topology for the voice's Filter stage. Currently:
 *
 * - `"modern"` (default) — classic subtractive `osc → VCF → VCA`. ADSR runs last so the filter
 *   and phaser see steady-amplitude signal — no attack smearing. Waveshapers still precede
 *   the filter so LP/HP can clean up their harmonics.
 * - `"pedal"` — guitar-pedal feel. ADSR runs first so the waveshapers respond to dynamics
 *   (quiet attack stays clean, hot sustain saturates, release tail fades through the drive).
 *
 * Unknown or null names fall back to `modern`.
 *
 * @param name The engine name (case-insensitive).
 * @return A new pattern using the selected engine.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").distort(0.8).engine("pedal")   // dynamics-responsive distortion
 * ```
 *
 * @category effects
 * @tags engine, pipeline, topology, modern, pedal, motor
 */
@SprudelDsl
fun SprudelPattern.engine(name: PatternLike): SprudelPattern =
    this._engine(listOf(name).asSprudelDslArgs())

/**
 * Parses this string as a pattern and selects the voice pipeline engine.
 *
 * @param name The engine name (case-insensitive). See [SprudelPattern.engine] for known engines.
 * @return A new pattern using the selected engine.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".engine("pedal").s("supersaw").distort(0.8)
 * ```
 */
@SprudelDsl
fun String.engine(name: PatternLike): SprudelPattern =
    this._engine(listOf(name).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that selects the voice pipeline engine.
 *
 * @param name The engine name (case-insensitive). See [SprudelPattern.engine] for known engines.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(engine("pedal"))   // selects pedal engine via mapper
 * ```
 *
 * @category effects
 * @tags engine, pipeline, topology, modern, pedal, motor
 */
@SprudelDsl
fun engine(name: PatternLike): PatternMapperFn =
    _engine(listOf(name).asSprudelDslArgs())

/**
 * Creates a chained [PatternMapperFn] that selects the voice pipeline engine after the previous mapper.
 *
 * @param name The engine name (case-insensitive). See [SprudelPattern.engine] for known engines.
 */
@SprudelDsl
fun PatternMapperFn.engine(name: PatternLike): PatternMapperFn =
    _engine(listOf(name).asSprudelDslArgs())
