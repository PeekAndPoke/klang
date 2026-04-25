@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
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

private fun applyEngine(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, engineMutation) { src, ctrl ->
        src.copy(engine = ctrl.engine)
    }
}

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
@KlangScript.Function
fun SprudelPattern.engine(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyEngine(this, listOf(name).asSprudelDslArgs(callInfo))

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
@KlangScript.Function
fun String.engine(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).engine(name, callInfo)

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
@KlangScript.Function
fun engine(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.engine(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that selects the voice pipeline engine after the previous mapper.
 *
 * @param name The engine name (case-insensitive). See [SprudelPattern.engine] for known engines.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.engine(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.engine(name, callInfo) }
