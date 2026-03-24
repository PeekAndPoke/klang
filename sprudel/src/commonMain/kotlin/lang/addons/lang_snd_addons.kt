@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.sprudel.*
import io.peekandpoke.klang.sprudel.lang.*
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangSndAddonsInit = false

// -- sndPluck() -------------------------------------------------------------------------------------------------------

private val sndPluckMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "pluck").withOscParams(
        "decay" to parts.getOrNull(0),
        "brightness" to parts.getOrNull(1),
        "pickPosition" to parts.getOrNull(2),
        "stiffness" to parts.getOrNull(3),
    )
}

private fun applySndPluck(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "pluck") }
    } else {
        source._applyControlFromParams(args, sndPluckMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
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
 * ```KlangScript(Playable)
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
 * ```KlangScript(Playable)
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

    copy(sound = "superpluck").withOscParams(
        "voices" to parts.getOrNull(0),
        "freqSpread" to parts.getOrNull(1),
        "decay" to parts.getOrNull(2),
        "brightness" to parts.getOrNull(3),
        "pickPosition" to parts.getOrNull(4),
        "stiffness" to parts.getOrNull(5),
    )
}

private fun applySndSuperPluck(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "superpluck") }
    } else {
        source._applyControlFromParams(args, sndSuperPluckMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
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
 * ```KlangScript(Playable)
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

// -- sndSine() --------------------------------------------------------------------------------------------------------

private fun applySndSine(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "sine") }
}

internal val _sndSine by dslPatternMapper { args, callInfo -> { p -> p._sndSine(args, callInfo) } }
internal val SprudelPattern._sndSine by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSine(p, args) }
internal val String._sndSine by dslStringExtension { p, args, callInfo -> p._sndSine(args, callInfo) }
internal val PatternMapperFn._sndSine by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSine(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a sine wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "sine".
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSine(params: PatternLike? = null): SprudelPattern =
    this._sndSine(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSine(params: PatternLike? = null): SprudelPattern =
    this._sndSine(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@SprudelDsl
fun sndSine(params: PatternLike? = null): PatternMapperFn =
    _sndSine(listOfNotNull(params).asSprudelDslArgs())

/** Chains a sine wave sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndSine(params: PatternLike? = null): PatternMapperFn =
    _sndSine(listOfNotNull(params).asSprudelDslArgs())

// -- sndSaw() ---------------------------------------------------------------------------------------------------------

private fun applySndSaw(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "sawtooth") }
}

internal val _sndSaw by dslPatternMapper { args, callInfo -> { p -> p._sndSaw(args, callInfo) } }
internal val SprudelPattern._sndSaw by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSaw(p, args) }
internal val String._sndSaw by dslStringExtension { p, args, callInfo -> p._sndSaw(args, callInfo) }
internal val PatternMapperFn._sndSaw by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSaw(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a sawtooth wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "sawtooth".
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSaw(params: PatternLike? = null): SprudelPattern =
    this._sndSaw(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSaw(params: PatternLike? = null): SprudelPattern =
    this._sndSaw(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@SprudelDsl
fun sndSaw(params: PatternLike? = null): PatternMapperFn =
    _sndSaw(listOfNotNull(params).asSprudelDslArgs())

/** Chains a sawtooth wave sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndSaw(params: PatternLike? = null): PatternMapperFn =
    _sndSaw(listOfNotNull(params).asSprudelDslArgs())

// -- sndSquare() ------------------------------------------------------------------------------------------------------

private fun applySndSquare(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "square") }
}

internal val _sndSquare by dslPatternMapper { args, callInfo -> { p -> p._sndSquare(args, callInfo) } }
internal val SprudelPattern._sndSquare by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSquare(p, args) }
internal val String._sndSquare by dslStringExtension { p, args, callInfo -> p._sndSquare(args, callInfo) }
internal val PatternMapperFn._sndSquare by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSquare(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a square wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "square".
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSquare(params: PatternLike? = null): SprudelPattern =
    this._sndSquare(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSquare(params: PatternLike? = null): SprudelPattern =
    this._sndSquare(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@SprudelDsl
fun sndSquare(params: PatternLike? = null): PatternMapperFn =
    _sndSquare(listOfNotNull(params).asSprudelDslArgs())

/** Chains a square wave sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndSquare(params: PatternLike? = null): PatternMapperFn =
    _sndSquare(listOfNotNull(params).asSprudelDslArgs())

// -- sndTriangle() ----------------------------------------------------------------------------------------------------

private fun applySndTriangle(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "triangle") }
}

internal val _sndTriangle by dslPatternMapper { args, callInfo -> { p -> p._sndTriangle(args, callInfo) } }
internal val SprudelPattern._sndTriangle by dslPatternExtension { p, args, /* callInfo */ _ -> applySndTriangle(p, args) }
internal val String._sndTriangle by dslStringExtension { p, args, callInfo -> p._sndTriangle(args, callInfo) }
internal val PatternMapperFn._sndTriangle by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndTriangle(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a triangle wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "triangle".
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndTriangle(params: PatternLike? = null): SprudelPattern =
    this._sndTriangle(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndTriangle(params: PatternLike? = null): SprudelPattern =
    this._sndTriangle(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@SprudelDsl
fun sndTriangle(params: PatternLike? = null): PatternMapperFn =
    _sndTriangle(listOfNotNull(params).asSprudelDslArgs())

/** Chains a triangle wave sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndTriangle(params: PatternLike? = null): PatternMapperFn =
    _sndTriangle(listOfNotNull(params).asSprudelDslArgs())

// -- sndRamp() --------------------------------------------------------------------------------------------------------

private fun applySndRamp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "ramp") }
}

internal val _sndRamp by dslPatternMapper { args, callInfo -> { p -> p._sndRamp(args, callInfo) } }
internal val SprudelPattern._sndRamp by dslPatternExtension { p, args, /* callInfo */ _ -> applySndRamp(p, args) }
internal val String._sndRamp by dslStringExtension { p, args, callInfo -> p._sndRamp(args, callInfo) }
internal val PatternMapperFn._sndRamp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndRamp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a ramp wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "ramp".
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndRamp(params: PatternLike? = null): SprudelPattern =
    this._sndRamp(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndRamp(params: PatternLike? = null): SprudelPattern =
    this._sndRamp(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@SprudelDsl
fun sndRamp(params: PatternLike? = null): PatternMapperFn =
    _sndRamp(listOfNotNull(params).asSprudelDslArgs())

/** Chains a ramp wave sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndRamp(params: PatternLike? = null): PatternMapperFn =
    _sndRamp(listOfNotNull(params).asSprudelDslArgs())

// -- sndNoise() -------------------------------------------------------------------------------------------------------

private fun applySndNoise(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "whitenoise") }
}

internal val _sndNoise by dslPatternMapper { args, callInfo -> { p -> p._sndNoise(args, callInfo) } }
internal val SprudelPattern._sndNoise by dslPatternExtension { p, args, /* callInfo */ _ -> applySndNoise(p, args) }
internal val String._sndNoise by dslStringExtension { p, args, callInfo -> p._sndNoise(args, callInfo) }
internal val PatternMapperFn._sndNoise by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndNoise(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to white noise.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "whitenoise".
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndNoise(params: PatternLike? = null): SprudelPattern =
    this._sndNoise(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@SprudelDsl
fun String.sndNoise(params: PatternLike? = null): SprudelPattern =
    this._sndNoise(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@SprudelDsl
fun sndNoise(params: PatternLike? = null): PatternMapperFn =
    _sndNoise(listOfNotNull(params).asSprudelDslArgs())

/** Chains a white noise sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndNoise(params: PatternLike? = null): PatternMapperFn =
    _sndNoise(listOfNotNull(params).asSprudelDslArgs())

// -- sndBrown() -------------------------------------------------------------------------------------------------------

private fun applySndBrown(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "brownnoise") }
}

internal val _sndBrown by dslPatternMapper { args, callInfo -> { p -> p._sndBrown(args, callInfo) } }
internal val SprudelPattern._sndBrown by dslPatternExtension { p, args, /* callInfo */ _ -> applySndBrown(p, args) }
internal val String._sndBrown by dslStringExtension { p, args, callInfo -> p._sndBrown(args, callInfo) }
internal val PatternMapperFn._sndBrown by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndBrown(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to brown noise (Brownian/red noise).
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "brownnoise".
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndBrown(params: PatternLike? = null): SprudelPattern =
    this._sndBrown(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@SprudelDsl
fun String.sndBrown(params: PatternLike? = null): SprudelPattern =
    this._sndBrown(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@SprudelDsl
fun sndBrown(params: PatternLike? = null): PatternMapperFn =
    _sndBrown(listOfNotNull(params).asSprudelDslArgs())

/** Chains a brown noise sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndBrown(params: PatternLike? = null): PatternMapperFn =
    _sndBrown(listOfNotNull(params).asSprudelDslArgs())

// -- sndPink() --------------------------------------------------------------------------------------------------------

private fun applySndPink(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "pinknoise") }
}

internal val _sndPink by dslPatternMapper { args, callInfo -> { p -> p._sndPink(args, callInfo) } }
internal val SprudelPattern._sndPink by dslPatternExtension { p, args, /* callInfo */ _ -> applySndPink(p, args) }
internal val String._sndPink by dslStringExtension { p, args, callInfo -> p._sndPink(args, callInfo) }
internal val PatternMapperFn._sndPink by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndPink(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to pink noise (1/f noise).
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "pinknoise".
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndPink(params: PatternLike? = null): SprudelPattern =
    this._sndPink(listOfNotNull(params).asSprudelDslArgs())

/** Parses this string as a pattern and sets sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@SprudelDsl
fun String.sndPink(params: PatternLike? = null): SprudelPattern =
    this._sndPink(listOfNotNull(params).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that sets the sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@SprudelDsl
fun sndPink(params: PatternLike? = null): PatternMapperFn =
    _sndPink(listOfNotNull(params).asSprudelDslArgs())

/** Chains a pink noise sound onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.sndPink(params: PatternLike? = null): PatternMapperFn =
    _sndPink(listOfNotNull(params).asSprudelDslArgs())

// -- sndPulze() -------------------------------------------------------------------------------------------------------

private val sndPulzeMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "pulze").withOscParams(
        "duty" to parts.getOrNull(0),
    )
}

private fun applySndPulze(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "pulze") }
    } else {
        source._applyControlFromParams(args, sndPulzeMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndPulze by dslPatternMapper { args, callInfo -> { p -> p._sndPulze(args, callInfo) } }
internal val SprudelPattern._sndPulze by dslPatternExtension { p, args, /* callInfo */ _ -> applySndPulze(p, args) }
internal val String._sndPulze by dslStringExtension { p, args, callInfo -> p._sndPulze(args, callInfo) }
internal val PatternMapperFn._sndPulze by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndPulze(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a pulse wave oscillator with configurable duty cycle.
 *
 * @param params Pulse parameter as `"duty"` (0.0–1.0, default 0.5).
 * @param-tool params SprudelPulzeSequenceEditor
 * @param-sub params duty Pulse width / duty cycle (0.0–1.0)
 * @return A new pattern with sound set to "pulze" and parameters applied.
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndPulze(params: PatternLike? = null): SprudelPattern =
    this._sndPulze(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to pulse wave.
 *
 * @param params Pulse parameter as `"duty"`.
 * @return A new pattern with sound set to "pulze".
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndPulze(params: PatternLike? = null): SprudelPattern =
    this._sndPulze(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to pulse wave.
 *
 * @param params Pulse parameter as `"duty"`.
 * @return A [PatternMapperFn] that sets sound to "pulze".
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@SprudelDsl
fun sndPulze(params: PatternLike? = null): PatternMapperFn =
    _sndPulze(listOfNotNull(params).asSprudelDslArgs())

/** Chains a pulse wave sound onto this [PatternMapperFn].
 * @param params Pulse parameter as `"duty"`.
 */
@SprudelDsl
fun PatternMapperFn.sndPulze(params: PatternLike? = null): PatternMapperFn =
    _sndPulze(listOfNotNull(params).asSprudelDslArgs())

// -- sndDust() --------------------------------------------------------------------------------------------------------

private val sndDustMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "dust").withOscParams(
        "density" to parts.getOrNull(0),
    )
}

private fun applySndDust(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "dust") }
    } else {
        source._applyControlFromParams(args, sndDustMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndDust by dslPatternMapper { args, callInfo -> { p -> p._sndDust(args, callInfo) } }
internal val SprudelPattern._sndDust by dslPatternExtension { p, args, /* callInfo */ _ -> applySndDust(p, args) }
internal val String._sndDust by dslStringExtension { p, args, callInfo -> p._sndDust(args, callInfo) }
internal val PatternMapperFn._sndDust by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndDust(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to dust (random impulse) generator with configurable density.
 *
 * @param params Dust parameter as `"density"` (impulses per second).
 * @param-tool params SprudelDustSequenceEditor
 * @param-sub params density Impulse density (impulses per second)
 * @return A new pattern with sound set to "dust" and parameters applied.
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndDust(params: PatternLike? = null): SprudelPattern =
    this._sndDust(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to dust generator.
 *
 * @param params Dust parameter as `"density"`.
 * @return A new pattern with sound set to "dust".
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@SprudelDsl
fun String.sndDust(params: PatternLike? = null): SprudelPattern =
    this._sndDust(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to dust generator.
 *
 * @param params Dust parameter as `"density"`.
 * @return A [PatternMapperFn] that sets sound to "dust".
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@SprudelDsl
fun sndDust(params: PatternLike? = null): PatternMapperFn =
    _sndDust(listOfNotNull(params).asSprudelDslArgs())

/** Chains a dust generator sound onto this [PatternMapperFn].
 * @param params Dust parameter as `"density"`.
 */
@SprudelDsl
fun PatternMapperFn.sndDust(params: PatternLike? = null): PatternMapperFn =
    _sndDust(listOfNotNull(params).asSprudelDslArgs())

// -- sndCrackle() -----------------------------------------------------------------------------------------------------

private val sndCrackleMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "crackle").withOscParams(
        "density" to parts.getOrNull(0),
    )
}

private fun applySndCrackle(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "crackle") }
    } else {
        source._applyControlFromParams(args, sndCrackleMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndCrackle by dslPatternMapper { args, callInfo -> { p -> p._sndCrackle(args, callInfo) } }
internal val SprudelPattern._sndCrackle by dslPatternExtension { p, args, /* callInfo */ _ -> applySndCrackle(p, args) }
internal val String._sndCrackle by dslStringExtension { p, args, callInfo -> p._sndCrackle(args, callInfo) }
internal val PatternMapperFn._sndCrackle by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndCrackle(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a crackle generator with configurable density.
 *
 * @param params Crackle parameter as `"density"`.
 * @param-tool params SprudelDustSequenceEditor
 * @param-sub params density Crackle density
 * @return A new pattern with sound set to "crackle" and parameters applied.
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndCrackle(params: PatternLike? = null): SprudelPattern =
    this._sndCrackle(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to crackle generator.
 *
 * @param params Crackle parameter as `"density"`.
 * @return A new pattern with sound set to "crackle".
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@SprudelDsl
fun String.sndCrackle(params: PatternLike? = null): SprudelPattern =
    this._sndCrackle(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to crackle generator.
 *
 * @param params Crackle parameter as `"density"`.
 * @return A [PatternMapperFn] that sets sound to "crackle".
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@SprudelDsl
fun sndCrackle(params: PatternLike? = null): PatternMapperFn =
    _sndCrackle(listOfNotNull(params).asSprudelDslArgs())

/** Chains a crackle generator sound onto this [PatternMapperFn].
 * @param params Crackle parameter as `"density"`.
 */
@SprudelDsl
fun PatternMapperFn.sndCrackle(params: PatternLike? = null): PatternMapperFn =
    _sndCrackle(listOfNotNull(params).asSprudelDslArgs())

// -- sndSuperSaw() ----------------------------------------------------------------------------------------------------

private val sndSuperSawMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "supersaw").withOscParams(
        "voices" to parts.getOrNull(0),
        "freqSpread" to parts.getOrNull(1),
    )
}

private fun applySndSuperSaw(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "supersaw") }
    } else {
        source._applyControlFromParams(args, sndSuperSawMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndSuperSaw by dslPatternMapper { args, callInfo -> { p -> p._sndSuperSaw(args, callInfo) } }
internal val SprudelPattern._sndSuperSaw by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSuperSaw(p, args) }
internal val String._sndSuperSaw by dslStringExtension { p, args, callInfo -> p._sndSuperSaw(args, callInfo) }
internal val PatternMapperFn._sndSuperSaw by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSuperSaw(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a super sawtooth (multiple detuned sawtooth oscillators).
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @param-tool params SprudelSuperSawSequenceEditor
 * @param-sub params voices Number of oscillators (1–16)
 * @param-sub params freqSpread Detune spread in semitones
 * @return A new pattern with sound set to "supersaw" and parameters applied.
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSuperSaw(params: PatternLike? = null): SprudelPattern =
    this._sndSuperSaw(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to super sawtooth.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supersaw".
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSuperSaw(params: PatternLike? = null): SprudelPattern =
    this._sndSuperSaw(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to super sawtooth.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supersaw".
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@SprudelDsl
fun sndSuperSaw(params: PatternLike? = null): PatternMapperFn =
    _sndSuperSaw(listOfNotNull(params).asSprudelDslArgs())

/**
 * Chains a super sawtooth sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
fun PatternMapperFn.sndSuperSaw(params: PatternLike? = null): PatternMapperFn =
    _sndSuperSaw(listOfNotNull(params).asSprudelDslArgs())

// -- sndSuperSine() ---------------------------------------------------------------------------------------------------

private val sndSuperSineMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "supersine").withOscParams(
        "voices" to parts.getOrNull(0),
        "freqSpread" to parts.getOrNull(1),
    )
}

private fun applySndSuperSine(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "supersine") }
    } else {
        source._applyControlFromParams(args, sndSuperSineMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndSuperSine by dslPatternMapper { args, callInfo -> { p -> p._sndSuperSine(args, callInfo) } }
internal val SprudelPattern._sndSuperSine by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSuperSine(p, args) }
internal val String._sndSuperSine by dslStringExtension { p, args, callInfo -> p._sndSuperSine(args, callInfo) }
internal val PatternMapperFn._sndSuperSine by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSuperSine(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a super sine (multiple detuned sine oscillators).
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @param-tool params SprudelSuperSawSequenceEditor
 * @param-sub params voices Number of oscillators (1–16)
 * @param-sub params freqSpread Detune spread in semitones
 * @return A new pattern with sound set to "supersine" and parameters applied.
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSuperSine(params: PatternLike? = null): SprudelPattern =
    this._sndSuperSine(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to super sine.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supersine".
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSuperSine(params: PatternLike? = null): SprudelPattern =
    this._sndSuperSine(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to super sine.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supersine".
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@SprudelDsl
fun sndSuperSine(params: PatternLike? = null): PatternMapperFn =
    _sndSuperSine(listOfNotNull(params).asSprudelDslArgs())

/**
 * Chains a super sine sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
fun PatternMapperFn.sndSuperSine(params: PatternLike? = null): PatternMapperFn =
    _sndSuperSine(listOfNotNull(params).asSprudelDslArgs())

// -- sndSuperSquare() -------------------------------------------------------------------------------------------------

private val sndSuperSquareMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "supersquare").withOscParams(
        "voices" to parts.getOrNull(0),
        "freqSpread" to parts.getOrNull(1),
    )
}

private fun applySndSuperSquare(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "supersquare") }
    } else {
        source._applyControlFromParams(args, sndSuperSquareMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndSuperSquare by dslPatternMapper { args, callInfo -> { p -> p._sndSuperSquare(args, callInfo) } }
internal val SprudelPattern._sndSuperSquare by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSuperSquare(p, args) }
internal val String._sndSuperSquare by dslStringExtension { p, args, callInfo -> p._sndSuperSquare(args, callInfo) }
internal val PatternMapperFn._sndSuperSquare by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSuperSquare(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a super square (multiple detuned square oscillators).
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @param-tool params SprudelSuperSawSequenceEditor
 * @param-sub params voices Number of oscillators (1–16)
 * @param-sub params freqSpread Detune spread in semitones
 * @return A new pattern with sound set to "supersquare" and parameters applied.
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSuperSquare(params: PatternLike? = null): SprudelPattern =
    this._sndSuperSquare(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to super square.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supersquare".
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSuperSquare(params: PatternLike? = null): SprudelPattern =
    this._sndSuperSquare(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to super square.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supersquare".
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@SprudelDsl
fun sndSuperSquare(params: PatternLike? = null): PatternMapperFn =
    _sndSuperSquare(listOfNotNull(params).asSprudelDslArgs())

/**
 * Chains a super square sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
fun PatternMapperFn.sndSuperSquare(params: PatternLike? = null): PatternMapperFn =
    _sndSuperSquare(listOfNotNull(params).asSprudelDslArgs())

// -- sndSuperTri() ----------------------------------------------------------------------------------------------------

private val sndSuperTriMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "supertri").withOscParams(
        "voices" to parts.getOrNull(0),
        "freqSpread" to parts.getOrNull(1),
    )
}

private fun applySndSuperTri(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "supertri") }
    } else {
        source._applyControlFromParams(args, sndSuperTriMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndSuperTri by dslPatternMapper { args, callInfo -> { p -> p._sndSuperTri(args, callInfo) } }
internal val SprudelPattern._sndSuperTri by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSuperTri(p, args) }
internal val String._sndSuperTri by dslStringExtension { p, args, callInfo -> p._sndSuperTri(args, callInfo) }
internal val PatternMapperFn._sndSuperTri by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSuperTri(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a super triangle (multiple detuned triangle oscillators).
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @param-tool params SprudelSuperSawSequenceEditor
 * @param-sub params voices Number of oscillators (1–16)
 * @param-sub params freqSpread Detune spread in semitones
 * @return A new pattern with sound set to "supertri" and parameters applied.
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSuperTri(params: PatternLike? = null): SprudelPattern =
    this._sndSuperTri(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to super triangle.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supertri".
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSuperTri(params: PatternLike? = null): SprudelPattern =
    this._sndSuperTri(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to super triangle.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supertri".
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@SprudelDsl
fun sndSuperTri(params: PatternLike? = null): PatternMapperFn =
    _sndSuperTri(listOfNotNull(params).asSprudelDslArgs())

/**
 * Chains a super triangle sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
fun PatternMapperFn.sndSuperTri(params: PatternLike? = null): PatternMapperFn =
    _sndSuperTri(listOfNotNull(params).asSprudelDslArgs())

// -- sndSuperRamp() ---------------------------------------------------------------------------------------------------

private val sndSuperRampMutation = voiceModifier {
    val parts = it?.toString()?.split(":")
        ?.map { d -> d.trim().toDoubleOrNull() } ?: emptyList()

    copy(sound = "superramp").withOscParams(
        "voices" to parts.getOrNull(0),
        "freqSpread" to parts.getOrNull(1),
    )
}

private fun applySndSuperRamp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return if (args.isEmpty()) {
        source._liftOrReinterpretStringField(args) { copy(sound = "superramp") }
    } else {
        source._applyControlFromParams(args, sndSuperRampMutation) { src, ctrl ->
            src.copy(sound = ctrl.sound ?: src.sound).mergeOscParamsFrom(ctrl)
        }
    }
}

internal val _sndSuperRamp by dslPatternMapper { args, callInfo -> { p -> p._sndSuperRamp(args, callInfo) } }
internal val SprudelPattern._sndSuperRamp by dslPatternExtension { p, args, /* callInfo */ _ -> applySndSuperRamp(p, args) }
internal val String._sndSuperRamp by dslStringExtension { p, args, callInfo -> p._sndSuperRamp(args, callInfo) }
internal val PatternMapperFn._sndSuperRamp by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_sndSuperRamp(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the sound to a super ramp (multiple detuned ramp oscillators).
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @param-tool params SprudelSuperSawSequenceEditor
 * @param-sub params voices Number of oscillators (1–16)
 * @param-sub params freqSpread Detune spread in semitones
 * @return A new pattern with sound set to "superramp" and parameters applied.
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@SprudelDsl
fun SprudelPattern.sndSuperRamp(params: PatternLike? = null): SprudelPattern =
    this._sndSuperRamp(listOfNotNull(params).asSprudelDslArgs())

/**
 * Parses this string as a pattern and sets sound to super ramp.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "superramp".
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@SprudelDsl
fun String.sndSuperRamp(params: PatternLike? = null): SprudelPattern =
    this._sndSuperRamp(listOfNotNull(params).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the sound to super ramp.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "superramp".
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@SprudelDsl
fun sndSuperRamp(params: PatternLike? = null): PatternMapperFn =
    _sndSuperRamp(listOfNotNull(params).asSprudelDslArgs())

/**
 * Chains a super ramp sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
fun PatternMapperFn.sndSuperRamp(params: PatternLike? = null): PatternMapperFn =
    _sndSuperRamp(listOfNotNull(params).asSprudelDslArgs())
