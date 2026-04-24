@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDsl
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.voiceModifier
import io.peekandpoke.klang.sprudel.mergeOscParamsFrom
import io.peekandpoke.klang.sprudel.withOscParams

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
@KlangScript.Function
fun SprudelPattern.sndPluck(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndPluck(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to plucked string.
 *
 * @param params Pluck parameters as `"decay:brightness:pickPosition:stiffness"`.
 * @return A new pattern with sound set to "pluck" and parameters applied.
 * @category tonal
 * @tags pluck, string, karplus-strong, physical-model, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndPluck(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndPluck(params, callInfo)

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
@KlangScript.Function
fun sndPluck(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPluck(params, callInfo) }

/**
 * Chains a plucked string sound onto this [PatternMapperFn].
 *
 * @param params Pluck parameters as `"decay:brightness:pickPosition:stiffness"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndPluck(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndPluck(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndSuperPluck(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSuperPluck(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to super plucked string.
 *
 * @param params Parameters as `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 * @return A new pattern with sound set to "superpluck".
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSuperPluck(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSuperPluck(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super plucked string.
 *
 * @param params Parameters as `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 * @return A [PatternMapperFn] that sets sound to "superpluck".
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSuperPluck(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperPluck(params, callInfo) }

/**
 * Chains a super plucked string sound onto this [PatternMapperFn].
 *
 * @param params Parameters as `"voices:freqSpread:decay:brightness:pickPosition:stiffness"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSuperPluck(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperPluck(params, callInfo) }

// -- sndSine() --------------------------------------------------------------------------------------------------------

private fun applySndSine(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "sine") }
}

/**
 * Sets the sound to a sine wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "sine".
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSine(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSine(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSine(params, callInfo) }

/** Chains a sine wave sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSine(params, callInfo) }

// -- sndSaw() ---------------------------------------------------------------------------------------------------------

private fun applySndSaw(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "sawtooth") }
}

/**
 * Sets the sound to a sawtooth wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "sawtooth".
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSaw(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSaw(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSaw(params, callInfo) }

/** Chains a sawtooth wave sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSaw(params, callInfo) }

// -- sndSquare() ------------------------------------------------------------------------------------------------------

private fun applySndSquare(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "square") }
}

/**
 * Sets the sound to a square wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "square".
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSquare(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSquare(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSquare(params, callInfo) }

/** Chains a square wave sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSquare(params, callInfo) }

// -- sndTriangle() ----------------------------------------------------------------------------------------------------

private fun applySndTriangle(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "triangle") }
}

/**
 * Sets the sound to a triangle wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "triangle".
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndTriangle(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndTriangle(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndTriangle(params, callInfo) }

/** Chains a triangle wave sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndTriangle(params, callInfo) }

// -- sndRamp() --------------------------------------------------------------------------------------------------------

private fun applySndRamp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "ramp") }
}

/**
 * Sets the sound to a ramp wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "ramp".
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndRamp(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndRamp(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndRamp(params, callInfo) }

/** Chains a ramp wave sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndRamp(params, callInfo) }

// -- sndNoise() -------------------------------------------------------------------------------------------------------

private fun applySndNoise(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "whitenoise") }
}

/**
 * Sets the sound to white noise.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "whitenoise".
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndNoise(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndNoise(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndNoise(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndNoise(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndNoise(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndNoise(params, callInfo) }

/** Chains a white noise sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndNoise(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndNoise(params, callInfo) }

// -- sndBrown() -------------------------------------------------------------------------------------------------------

private fun applySndBrown(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "brownnoise") }
}

/**
 * Sets the sound to brown noise (Brownian/red noise).
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "brownnoise".
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndBrown(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndBrown(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndBrown(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndBrown(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndBrown(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndBrown(params, callInfo) }

/** Chains a brown noise sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndBrown(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndBrown(params, callInfo) }

// -- sndPink() --------------------------------------------------------------------------------------------------------

private fun applySndPink(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = "pinknoise") }
}

/**
 * Sets the sound to pink noise (1/f noise).
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "pinknoise".
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndPink(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndPink(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPink(params, callInfo) }

/** Chains a pink noise sound onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndPink(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndPulze(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndPulze(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to pulse wave.
 *
 * @param params Pulse parameter as `"duty"`.
 * @return A new pattern with sound set to "pulze".
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndPulze(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndPulze(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to pulse wave.
 *
 * @param params Pulse parameter as `"duty"`.
 * @return A [PatternMapperFn] that sets sound to "pulze".
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndPulze(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPulze(params, callInfo) }

/** Chains a pulse wave sound onto this [PatternMapperFn].
 * @param params Pulse parameter as `"duty"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndPulze(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndPulze(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndDust(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndDust(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to dust generator.
 *
 * @param params Dust parameter as `"density"`.
 * @return A new pattern with sound set to "dust".
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndDust(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndDust(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to dust generator.
 *
 * @param params Dust parameter as `"density"`.
 * @return A [PatternMapperFn] that sets sound to "dust".
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndDust(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndDust(params, callInfo) }

/** Chains a dust generator sound onto this [PatternMapperFn].
 * @param params Dust parameter as `"density"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndDust(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndDust(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndCrackle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndCrackle(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to crackle generator.
 *
 * @param params Crackle parameter as `"density"`.
 * @return A new pattern with sound set to "crackle".
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndCrackle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndCrackle(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to crackle generator.
 *
 * @param params Crackle parameter as `"density"`.
 * @return A [PatternMapperFn] that sets sound to "crackle".
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndCrackle(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndCrackle(params, callInfo) }

/** Chains a crackle generator sound onto this [PatternMapperFn].
 * @param params Crackle parameter as `"density"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndCrackle(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndCrackle(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndSuperSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSuperSaw(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to super sawtooth.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supersaw".
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSuperSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSuperSaw(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super sawtooth.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supersaw".
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSuperSaw(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperSaw(params, callInfo) }

/**
 * Chains a super sawtooth sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSuperSaw(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperSaw(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndSuperSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSuperSine(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to super sine.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supersine".
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSuperSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSuperSine(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super sine.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supersine".
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSuperSine(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperSine(params, callInfo) }

/**
 * Chains a super sine sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSuperSine(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperSine(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndSuperSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSuperSquare(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to super square.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supersquare".
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSuperSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSuperSquare(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super square.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supersquare".
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSuperSquare(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperSquare(params, callInfo) }

/**
 * Chains a super square sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSuperSquare(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperSquare(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndSuperTri(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSuperTri(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to super triangle.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "supertri".
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSuperTri(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSuperTri(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super triangle.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "supertri".
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSuperTri(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperTri(params, callInfo) }

/**
 * Chains a super triangle sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSuperTri(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperTri(params, callInfo) }

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
@KlangScript.Function
fun SprudelPattern.sndSuperRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSuperRamp(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets sound to super ramp.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A new pattern with sound set to "superramp".
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun String.sndSuperRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().sndSuperRamp(params, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super ramp.
 *
 * @param params Parameters as `"voices:freqSpread"`.
 * @return A [PatternMapperFn] that sets sound to "superramp".
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@SprudelDsl
@KlangScript.Function
fun sndSuperRamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperRamp(params, callInfo) }

/**
 * Chains a super ramp sound onto this [PatternMapperFn].
 * @param params Parameters as `"voices:freqSpread"`.
 */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.sndSuperRamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperRamp(params, callInfo) }
