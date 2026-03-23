@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.*
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.withOscParam

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangSndAddonsInit = false

// -- sndPluck() -------------------------------------------------------------------------------------------------------

private val sndPluckMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "pluck")
        .withOscParam("decay", parts.getOrNull(0))
        .withOscParam("brightness", parts.getOrNull(1))
        .withOscParam("pickPosition", parts.getOrNull(2))
        .withOscParam("stiffness", parts.getOrNull(3))
}

private fun applySndPluck(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        // No params: just set sound to "pluck" with defaults
        source._liftOrReinterpretStringField(args) { copy(sound = "pluck") }
    } else {
        source._applyControlFromParams(args, sndPluckMutation) { src, ctrl ->
            var result = src.copy(sound = ctrl.sound ?: src.sound)
            ctrl.oscParams?.get("decay")?.let { result = result.withOscParam("decay", it) }
            ctrl.oscParams?.get("brightness")?.let { result = result.withOscParam("brightness", it) }
            ctrl.oscParams?.get("pickPosition")?.let { result = result.withOscParam("pickPosition", it) }
            ctrl.oscParams?.get("stiffness")?.let { result = result.withOscParam("stiffness", it) }
            result
        }
    }
}

internal val _sndPluck by dslPatternMapper { args, callInfo -> { p -> p._sndPluck(args, callInfo) } }
internal val SprudelPattern._sndPluck by dslPatternExtension { p, args, /* callInfo */ _ -> applySndPluck(p, args) }
internal val String._sndPluck by dslStringExtension { p, args, callInfo -> p._sndPluck(args, callInfo) }
internal val PatternMapperFn._sndPluck by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndPluck(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a Karplus-Strong plucked string and optionally configures its parameters
 * via a colon-separated string `"decay:brightness:pickPosition:stiffness"`.
 *
 * Each field is optional — trailing fields can be omitted.
 * - **decay**: feedback amount, 0.9–0.999 (higher = longer ring)
 * - **brightness**: lowpass cutoff, 0.0 (dark) to 1.0 (bright)
 * - **pickPosition**: pluck position, 0.0 (bridge) to 1.0 (neck)
 * - **stiffness**: harmonic stiffness, 0.0 (nylon) to 1.0 (piano wire)
 *
 * ```KlangScript
 * note("c3 e3 g3").sndPluck()                     // default plucked string
 * note("c3 e3 g3").sndPluck("0.999:0.8")          // bright, long sustain
 * note("c3 e3 g3").sndPluck("0.93:0.2")           // dark pizzicato
 * note("c3 e3 g3").sndPluck("0.996:0.5:0.2:0.5")  // steel string, bridge pick
 * ```
 *
 * @param params Pluck parameters as `"decay:brightness:pickPosition:stiffness"`.
 * @param-tool params SprudelPluckSequenceEditor
 * @param-sub params decay Feedback amount (0.9–0.999, higher = longer ring)
 * @param-sub params brightness Lowpass cutoff (0 = dark, 1 = bright)
 * @param-sub params pickPosition Pluck position (0 = bridge, 1 = neck)
 * @param-sub params stiffness String stiffness (0 = nylon, 1 = piano wire)
 * @return A new pattern with sound set to "pluck" and parameters applied.
 * @category tonal
 * @tags pluck, string, karplus-strong, physical-model, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndPluck(params: PatternLike? = null): SprudelPattern =
    this._sndPluck(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to plucked string.
 *
 * @param params Pluck parameters as `"decay:brightness:pickPosition:stiffness"`.
 * @return A new pattern with sound set to "pluck" and parameters applied.
 * @category tonal
 * @tags pluck, string, karplus-strong, physical-model, snd, addon
 */
@SprudelDsl
fun String.sndPluck(params: PatternLike? = null): SprudelPattern =
    this._sndPluck(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to plucked string.
 *
 * ```KlangScript
 * note("c3 e3 g3").apply(sndPluck("0.999:0.8"))
 * ```
 *
 * @param params Pluck parameters as `"decay:brightness:pickPosition:stiffness"`.
 * @return A [PatternMapperFn] that sets sound to "pluck".
 * @category tonal
 * @tags pluck, string, karplus-strong, physical-model, snd, addon
 */
@SprudelDsl
fun sndPluck(params: PatternLike? = null): PatternMapperFn =
    _sndPluck(listOfNotNull(params).asSprudelDslArgs())

/**
 * Chains a plucked string sound onto this [PatternMapperFn].
 *
 * @param params Pluck parameters as `"decay:brightness:pickPosition:stiffness"`.
 */
@SprudelDsl
fun PatternMapperFn.sndPluck(params: PatternLike? = null): PatternMapperFn =
    _sndPluck(listOfNotNull(params).asSprudelDslArgs())

// -- sndSuperPluck() --------------------------------------------------------------------------------------------------

private val sndSuperPluckMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "superpluck")
        .withOscParam("voices", parts.getOrNull(0))
        .withOscParam("freqSpread", parts.getOrNull(1))
        .withOscParam("decay", parts.getOrNull(2))
        .withOscParam("brightness", parts.getOrNull(3))
        .withOscParam("pickPosition", parts.getOrNull(4))
        .withOscParam("stiffness", parts.getOrNull(5))
}

private fun applySndSuperPluck(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "superpluck") }
    } else {
        source._applyControlFromParams(args, sndSuperPluckMutation) { src, ctrl ->
            var result = src.copy(sound = ctrl.sound ?: src.sound)
            ctrl.oscParams?.get("voices")?.let { result = result.withOscParam("voices", it) }
            ctrl.oscParams?.get("freqSpread")?.let { result = result.withOscParam("freqSpread", it) }
            ctrl.oscParams?.get("decay")?.let { result = result.withOscParam("decay", it) }
            ctrl.oscParams?.get("brightness")?.let { result = result.withOscParam("brightness", it) }
            ctrl.oscParams?.get("pickPosition")?.let { result = result.withOscParam("pickPosition", it) }
            ctrl.oscParams?.get("stiffness")?.let { result = result.withOscParam("stiffness", it) }
            result
        }
    }
}

internal val _sndSuperPluck by dslPatternMapper { args, callInfo -> { p -> p._sndSuperPluck(args, callInfo) } }
internal val SprudelPattern._sndSuperPluck by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSuperPluck(p, args) }
internal val String._sndSuperPluck by dslStringExtension { p, args, callInfo -> p._sndSuperPluck(args, callInfo) }
internal val PatternMapperFn._sndSuperPluck by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSuperPluck(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a super plucked string (multiple detuned Karplus-Strong strings)
 * and optionally configures parameters via `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 *
 * Like a 12-string guitar or chorus of harps — each string has independent noise excitation
 * and drift, creating rich evolving shimmer.
 *
 * ```KlangScript
 * note("c3 e3 g3").sndSuperPluck()                         // default 5-string
 * note("c3 e3 g3").sndSuperPluck("7:0.3:0.998:0.8")       // 7-string, wide, bright, long
 * note("c3 e3 g3").sndSuperPluck("3:0.1:0.93:0.2")        // 3-string, tight, dark pizzicato
 * ```
 *
 * @param params Parameters as `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 * @param-tool params SprudelSuperPluckSequenceEditor
 * @param-sub params voices Number of strings (1–16)
 * @param-sub params freqSpread Detune spread in semitones
 * @param-sub params decay Feedback amount (0.9–0.999, higher = longer ring)
 * @param-sub params brightness Lowpass cutoff (0 = dark, 1 = bright)
 * @param-sub params pickPosition Pluck position (0 = bridge, 1 = neck)
 * @param-sub params stiffness String stiffness (0 = nylon, 1 = piano wire)
 * @return A new pattern with sound set to "superpluck" and parameters applied.
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSuperPluck(params: PatternLike? = null): SprudelPattern =
    this._sndSuperPluck(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to super plucked string.
 *
 * @param params Parameters as `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 * @return A new pattern with sound set to "superpluck".
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@SprudelDsl
fun String.sndSuperPluck(params: PatternLike? = null): SprudelPattern =
    this._sndSuperPluck(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to super plucked string.
 *
 * @param params Parameters as `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 * @return A [PatternMapperFn] that sets sound to "superpluck".
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@SprudelDsl
fun sndSuperPluck(params: PatternLike? = null): PatternMapperFn =
    _sndSuperPluck(listOfNotNull(params).asSprudelDslArgs())

/**
 * Chains a super plucked string sound onto this [PatternMapperFn].
 *
 * @param params Parameters as `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 */
@SprudelDsl
fun PatternMapperFn.sndSuperPluck(params: PatternLike? = null): PatternMapperFn =
    _sndSuperPluck(listOfNotNull(params).asSprudelDslArgs())
