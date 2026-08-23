/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang.addons

import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.PatternLike
import io.peekandpoke.klang.sprudel.lang.PatternMapperFn
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.chain
import io.peekandpoke.klang.sprudel.lang.toVoiceValuePattern
import io.peekandpoke.klang.sprudel.lang.voiceSetter
import io.peekandpoke.klang.sprudel.putOscParams
import io.peekandpoke.klang.sprudel.putOscParamsFrom

// -- sndPluck() -------------------------------------------------------------------------------------------------------

private fun applySndPluck(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("pluck")) }

/**
 * Sets the sound to a Karplus-Strong plucked string and optionally configures its
 * parameters. Each parameter is independent and patternable.
 *
 * Each field is optional — trailing fields can be omitted.
 * - **decay**: feedback amount, 0.9–0.999 (higher = longer ring)
 * - **brightness**: lowpass cutoff, 0.0 (dark) to 1.0 (bright)
 * - **pickPosition**: pluck position, 0.0 (bridge) to 1.0 (neck)
 * - **stiffness**: harmonic stiffness, 0.0 (nylon) to 1.0 (piano wire)
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").sndPluck()                     // default plucked string
 * note("c3 e3 g3").sndPluck(0.999, 0.8)          // bright, long sustain
 * note("c3 e3 g3").sndPluck(0.93, 0.2)           // dark pizzicato
 * note("c3 e3 g3").sndPluck(0.996, 0.5, 0.2, 0.5)  // steel string, bridge pick
 * ```
 *
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 * @param-tool decay SprudelPluckEditor, SprudelPluckSequenceEditor
 * @param decay Feedback amount (0.9–0.999, higher = longer ring)
 * @return A new pattern with sound set to "pluck" and parameters applied.
 * @category tonal
 * @tags pluck, string, karplus-strong, physical-model, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndPluck(decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndPluck(this)
    if (decay != null) p = p.oscparam("decay", decay, callInfo?.forParam(0, 1))
    if (brightness != null) p = p.oscparam("brightness", brightness, callInfo?.forParam(1, 1))
    if (pickPosition != null) p = p.oscparam("pickPosition", pickPosition, callInfo?.forParam(2, 1))
    if (stiffness != null) p = p.oscparam("stiffness", stiffness, callInfo?.forParam(3, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to plucked string.
 *
 * @param decay Feedback amount (0.9–0.999, higher = longer ring).
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 * @return A new pattern with sound set to "pluck" and parameters applied.
 * @category tonal
 * @tags pluck, string, karplus-strong, physical-model, snd, addon
 */
@KlangScript.Function
fun String.sndPluck(decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndPluck(decay, brightness, pickPosition, stiffness, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to plucked string.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(sndPluck(0.999, 0.8))
 * ```
 *
 * @param decay Feedback amount (0.9–0.999, higher = longer ring).
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 * @return A [PatternMapperFn] that sets sound to "pluck".
 * @category tonal
 * @tags pluck, string, karplus-strong, physical-model, snd, addon
 */
@KlangScript.Function
fun sndPluck(decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPluck(decay, brightness, pickPosition, stiffness, callInfo) }

/**
 * Chains a plucked string sound onto this [PatternMapperFn].
 *
 * @param decay Feedback amount (0.9–0.999, higher = longer ring).
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 */
@KlangScript.Function
fun PatternMapperFn.sndPluck(decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndPluck(decay, brightness, pickPosition, stiffness, callInfo) }

// -- sndSuperPluck() --------------------------------------------------------------------------------------------------

private fun applySndSuperPluck(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("superpluck")) }

/**
 * Sets the sound to a super plucked string (multiple detuned Karplus-Strong strings)
 * and optionally configures parameters via `"voices:detune:decay:brightness:pickPosition:stiffness"`.
 *
 * Like a 12-string guitar or chorus of harps — each string has independent noise excitation
 * and drift, creating rich evolving shimmer.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").sndSuperPluck()                         // default 5-string
 * note("c3 e3 g3").sndSuperPluck(7, 0.3, 0.998, 0.8)       // 7-string, wide, bright, long
 * note("c3 e3 g3").sndSuperPluck(3, 0.1, 0.93, 0.2)        // 3-string, tight, dark pizzicato
 * ```
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 * @param-tool voices SprudelSuperPluckEditor, SprudelSuperPluckSequenceEditor
 * @param decay Feedback amount (0.9–0.999, higher = longer ring)
 * @return A new pattern with sound set to "superpluck" and parameters applied.
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSuperPluck(voices: PatternLike? = null, spread: PatternLike? = null, decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndSuperPluck(this)
    if (voices != null) p = p.oscparam("voices", voices, callInfo?.forParam(0, 1))
    if (spread != null) p = p.oscparam("spread", spread, callInfo?.forParam(1, 1))
    if (decay != null) p = p.oscparam("decay", decay, callInfo?.forParam(2, 1))
    if (brightness != null) p = p.oscparam("brightness", brightness, callInfo?.forParam(3, 1))
    if (pickPosition != null) p = p.oscparam("pickPosition", pickPosition, callInfo?.forParam(4, 1))
    if (stiffness != null) p = p.oscparam("stiffness", stiffness, callInfo?.forParam(5, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to super plucked string.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @param decay Feedback amount (0.9–0.999, higher = longer ring).
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 * @return A new pattern with sound set to "superpluck".
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@KlangScript.Function
fun String.sndSuperPluck(voices: PatternLike? = null, spread: PatternLike? = null, decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSuperPluck(voices, spread, decay, brightness, pickPosition, stiffness, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super plucked string.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @param decay Feedback amount (0.9–0.999, higher = longer ring).
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 * @return A [PatternMapperFn] that sets sound to "superpluck".
 * @category tonal
 * @tags superpluck, pluck, string, karplus-strong, unison, physical-model, snd, addon
 */
@KlangScript.Function
fun sndSuperPluck(voices: PatternLike? = null, spread: PatternLike? = null, decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperPluck(voices, spread, decay, brightness, pickPosition, stiffness, callInfo) }

/**
 * Chains a super plucked string sound onto this [PatternMapperFn].
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @param decay Feedback amount (0.9–0.999, higher = longer ring).
 * @param brightness Lowpass cutoff (0 = dark, 1 = bright).
 * @param pickPosition Pluck position (0 = bridge, 1 = neck).
 * @param stiffness String stiffness (0 = nylon, 1 = piano wire).
 */
@KlangScript.Function
fun PatternMapperFn.sndSuperPluck(voices: PatternLike? = null, spread: PatternLike? = null, decay: PatternLike? = null, brightness: PatternLike? = null, pickPosition: PatternLike? = null, stiffness: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperPluck(voices, spread, decay, brightness, pickPosition, stiffness, callInfo) }

// -- sndSine() --------------------------------------------------------------------------------------------------------

private fun applySndSine(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = SoundValue.Named("sine")) }
}

/**
 * Sets the sound to a sine wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "sine".
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSine(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSine(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSine(params, callInfo) }

/** Chains a sine wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSine(params, callInfo) }

// -- sndSaw() ---------------------------------------------------------------------------------------------------------

private fun applySndSaw(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = SoundValue.Named("sawtooth")) }
}

/**
 * Sets the sound to a sawtooth wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "sawtooth".
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSaw(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSaw(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSaw(params, callInfo) }

/** Chains a sawtooth wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSaw(params, callInfo) }

// -- sndSquare() ------------------------------------------------------------------------------------------------------

private fun applySndSquare(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = SoundValue.Named("square")) }
}

/**
 * Sets the sound to a square wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "square".
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSquare(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSquare(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSquare(params, callInfo) }

/** Chains a square wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSquare(params, callInfo) }

// -- sndTriangle() ----------------------------------------------------------------------------------------------------

private fun applySndTriangle(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = SoundValue.Named("triangle")) }
}

/**
 * Sets the sound to a triangle wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "triangle".
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndTriangle(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndTriangle(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd, addon
 */
@KlangScript.Function
fun sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndTriangle(params, callInfo) }

/** Chains a triangle wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndTriangle(params, callInfo) }

// -- sndRamp() --------------------------------------------------------------------------------------------------------

private fun applySndRamp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = SoundValue.Named("ramp")) }
}

/**
 * Sets the sound to a ramp wave oscillator.
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "ramp".
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndRamp(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndRamp(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd, addon
 */
@KlangScript.Function
fun sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndRamp(params, callInfo) }

/** Chains a ramp wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndRamp(params, callInfo) }

// -- sndZamp() --------------------------------------------------------------------------------------------------------

private fun applySndZamp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = SoundValue.Named("zamp")) }
}

/**
 * Sets the sound to a raw ramp ("zamp") oscillator — the naive/aliased counterpart of [sndRamp].
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "zamp".
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndZamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndZamp(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to raw ramp ("zamp").
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndZamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndZamp(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to raw ramp ("zamp").
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd, addon
 */
@KlangScript.Function
fun sndZamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndZamp(params, callInfo) }

/** Chains a raw ramp ("zamp") sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndZamp(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndZamp(params, callInfo) }

// -- sndNoise() -------------------------------------------------------------------------------------------------------

private fun applySndNoise(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("whitenoise")) }

/**
 * Sets the sound to white noise.
 *
 * @param color Noise color.
 * @return A new pattern with sound set to "whitenoise".
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndNoise(color: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndNoise(this)
    if (color != null) p = p.oscparam("color", color, callInfo?.forParam(0, 1))
    return p
}

/** Parses this string as a pattern and sets sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@KlangScript.Function
fun String.sndNoise(color: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndNoise(color, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd, addon
 */
@KlangScript.Function
fun sndNoise(color: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndNoise(color, callInfo) }

/** Chains a white noise sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndNoise(color: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndNoise(color, callInfo) }

// -- sndBrown() -------------------------------------------------------------------------------------------------------

private fun applySndBrown(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("brownnoise")) }

/**
 * Sets the sound to brown noise (Brownian/red noise).
 *
 * @param depth Depth (0–1).
 * @return A new pattern with sound set to "brownnoise".
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndBrown(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndBrown(this)
    if (depth != null) p = p.oscparam("depth", depth, callInfo?.forParam(0, 1))
    return p
}

/** Parses this string as a pattern and sets sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@KlangScript.Function
fun String.sndBrown(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndBrown(depth, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd, addon
 */
@KlangScript.Function
fun sndBrown(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndBrown(depth, callInfo) }

/** Chains a brown noise sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndBrown(depth: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndBrown(depth, callInfo) }

// -- sndPink() --------------------------------------------------------------------------------------------------------

private fun applySndPink(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { copy(sound = SoundValue.Named("pinknoise")) }
}

/**
 * Sets the sound to pink noise (1/f noise).
 *
 * @param params Optional pattern-like parameter.
 * @return A new pattern with sound set to "pinknoise".
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndPink(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@KlangScript.Function
fun String.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndPink(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd, addon
 */
@KlangScript.Function
fun sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPink(params, callInfo) }

/** Chains a pink noise sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndPink(params, callInfo) }

// -- sndPulze() -------------------------------------------------------------------------------------------------------

private fun applySndPulze(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("pulze")) }

/**
 * Sets the sound to a pulse wave oscillator with configurable duty cycle.
 *
 * @param-tool duty SprudelPulzeEditor, SprudelPulzeSequenceEditor
 * @param duty Pulse width / duty cycle (0.0–1.0)
 * @return A new pattern with sound set to "pulze" and parameters applied.
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndPulze(duty: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndPulze(this)
    if (duty != null) p = p.oscparam("duty", duty, callInfo?.forParam(0, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to pulse wave.
 *
 * @param duty Duty cycle (0–1).
 * @return A new pattern with sound set to "pulze".
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndPulze(duty: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndPulze(duty, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to pulse wave.
 *
 * @param duty Duty cycle (0–1).
 * @return A [PatternMapperFn] that sets sound to "pulze".
 * @category tonal
 * @tags pulze, pulse, oscillator, snd, addon
 */
@KlangScript.Function
fun sndPulze(duty: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPulze(duty, callInfo) }

/** Chains a pulse wave sound onto this [PatternMapperFn].
 * @param duty Duty cycle (0–1).
 */
@KlangScript.Function
fun PatternMapperFn.sndPulze(duty: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndPulze(duty, callInfo) }

// -- sndDust() --------------------------------------------------------------------------------------------------------

private fun applySndDust(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("dust")) }

/**
 * Sets the sound to dust (random impulse) generator with configurable density.
 *
 * @param tail Impulse tail length.
 * @param-tool density SprudelDustEditor, SprudelDustSequenceEditor
 * @param density Impulse density (impulses per second)
 * @return A new pattern with sound set to "dust" and parameters applied.
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndDust(density: PatternLike? = null, tail: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndDust(this)
    if (density != null) p = p.oscparam("density", density, callInfo?.forParam(0, 1))
    if (tail != null) p = p.oscparam("tail", tail, callInfo?.forParam(1, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to dust generator.
 *
 * @param density Impulses per second.
 * @param tail Impulse tail length.
 * @return A new pattern with sound set to "dust".
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@KlangScript.Function
fun String.sndDust(density: PatternLike? = null, tail: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndDust(density, tail, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to dust generator.
 *
 * @param density Impulses per second.
 * @param tail Impulse tail length.
 * @return A [PatternMapperFn] that sets sound to "dust".
 * @category tonal
 * @tags dust, impulse, noise, snd, addon
 */
@KlangScript.Function
fun sndDust(density: PatternLike? = null, tail: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndDust(density, tail, callInfo) }

/** Chains a dust generator sound onto this [PatternMapperFn].
 * @param density Impulses per second.
 * @param tail Impulse tail length.
 */
@KlangScript.Function
fun PatternMapperFn.sndDust(density: PatternLike? = null, tail: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndDust(density, tail, callInfo) }

// -- sndCrackle() -----------------------------------------------------------------------------------------------------

private fun applySndCrackle(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("crackle")) }

/**
 * Sets the sound to a crackle generator (chaotic recurrence → bipolar pops) with configurable chaos.
 *
 * @param chaos Chaos amount (~1 sparse … ~2 dense).
 * @return A new pattern with sound set to "crackle" and parameters applied.
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndCrackle(chaos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndCrackle(this)
    if (chaos != null) p = p.oscparam("chaos", chaos, callInfo?.forParam(0, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to crackle generator.
 *
 * @param chaos Chaos amount (~1 sparse … ~2 dense).
 * @return A new pattern with sound set to "crackle".
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@KlangScript.Function
fun String.sndCrackle(chaos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndCrackle(chaos, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to crackle generator.
 *
 * @param chaos Chaos amount (~1 sparse … ~2 dense).
 * @return A [PatternMapperFn] that sets sound to "crackle".
 * @category tonal
 * @tags crackle, noise, snd, addon
 */
@KlangScript.Function
fun sndCrackle(chaos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndCrackle(chaos, callInfo) }

/** Chains a crackle generator sound onto this [PatternMapperFn].
 * @param chaos Chaos amount (~1 sparse … ~2 dense).
 */
@KlangScript.Function
fun PatternMapperFn.sndCrackle(chaos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndCrackle(chaos, callInfo) }

// -- sndSuperSaw() ----------------------------------------------------------------------------------------------------

private fun applySndSuperSaw(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("supersaw")) }

/**
 * Sets the sound to a super sawtooth (multiple detuned sawtooth oscillators).
 *
 * @param spread Detune spread between voices.
 * @param-tool voices SprudelSuperSawEditor, SprudelSuperSawSequenceEditor
 * @param voices Number of oscillators (1–16)
 * @return A new pattern with sound set to "supersaw" and parameters applied.
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSuperSaw(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndSuperSaw(this)
    if (voices != null) p = p.oscparam("voices", voices, callInfo?.forParam(0, 1))
    if (spread != null) p = p.oscparam("spread", spread, callInfo?.forParam(1, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to super sawtooth.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A new pattern with sound set to "supersaw".
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSuperSaw(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSuperSaw(voices, spread, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super sawtooth.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A [PatternMapperFn] that sets sound to "supersaw".
 * @category tonal
 * @tags supersaw, saw, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSuperSaw(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperSaw(voices, spread, callInfo) }

/**
 * Chains a super sawtooth sound onto this [PatternMapperFn].
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 */
@KlangScript.Function
fun PatternMapperFn.sndSuperSaw(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperSaw(voices, spread, callInfo) }

// -- sndSuperSine() ---------------------------------------------------------------------------------------------------

private fun applySndSuperSine(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("supersine")) }

/**
 * Sets the sound to a super sine (multiple detuned sine oscillators).
 *
 * @param spread Detune spread between voices.
 * @param-tool voices SprudelSuperSawEditor, SprudelSuperSawSequenceEditor
 * @param voices Number of oscillators (1–16)
 * @return A new pattern with sound set to "supersine" and parameters applied.
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSuperSine(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndSuperSine(this)
    if (voices != null) p = p.oscparam("voices", voices, callInfo?.forParam(0, 1))
    if (spread != null) p = p.oscparam("spread", spread, callInfo?.forParam(1, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to super sine.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A new pattern with sound set to "supersine".
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSuperSine(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSuperSine(voices, spread, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super sine.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A [PatternMapperFn] that sets sound to "supersine".
 * @category tonal
 * @tags supersine, sine, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSuperSine(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperSine(voices, spread, callInfo) }

/**
 * Chains a super sine sound onto this [PatternMapperFn].
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 */
@KlangScript.Function
fun PatternMapperFn.sndSuperSine(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperSine(voices, spread, callInfo) }

// -- sndSuperSquare() -------------------------------------------------------------------------------------------------

private fun applySndSuperSquare(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("supersquare")) }

/**
 * Sets the sound to a super square (multiple detuned square oscillators).
 *
 * @param spread Detune spread between voices.
 * @param-tool voices SprudelSuperSawEditor, SprudelSuperSawSequenceEditor
 * @param voices Number of oscillators (1–16)
 * @return A new pattern with sound set to "supersquare" and parameters applied.
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSuperSquare(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndSuperSquare(this)
    if (voices != null) p = p.oscparam("voices", voices, callInfo?.forParam(0, 1))
    if (spread != null) p = p.oscparam("spread", spread, callInfo?.forParam(1, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to super square.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A new pattern with sound set to "supersquare".
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSuperSquare(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSuperSquare(voices, spread, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super square.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A [PatternMapperFn] that sets sound to "supersquare".
 * @category tonal
 * @tags supersquare, square, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSuperSquare(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperSquare(voices, spread, callInfo) }

/**
 * Chains a super square sound onto this [PatternMapperFn].
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 */
@KlangScript.Function
fun PatternMapperFn.sndSuperSquare(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperSquare(voices, spread, callInfo) }

// -- sndSuperTri() ----------------------------------------------------------------------------------------------------

private fun applySndSuperTri(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("supertri")) }

/**
 * Sets the sound to a super triangle (multiple detuned triangle oscillators).
 *
 * @param spread Detune spread between voices.
 * @param-tool voices SprudelSuperSawEditor, SprudelSuperSawSequenceEditor
 * @param voices Number of oscillators (1–16)
 * @return A new pattern with sound set to "supertri" and parameters applied.
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSuperTri(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndSuperTri(this)
    if (voices != null) p = p.oscparam("voices", voices, callInfo?.forParam(0, 1))
    if (spread != null) p = p.oscparam("spread", spread, callInfo?.forParam(1, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to super triangle.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A new pattern with sound set to "supertri".
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSuperTri(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSuperTri(voices, spread, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super triangle.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A [PatternMapperFn] that sets sound to "supertri".
 * @category tonal
 * @tags supertri, triangle, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSuperTri(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperTri(voices, spread, callInfo) }

/**
 * Chains a super triangle sound onto this [PatternMapperFn].
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 */
@KlangScript.Function
fun PatternMapperFn.sndSuperTri(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperTri(voices, spread, callInfo) }

// -- sndSuperRamp() ---------------------------------------------------------------------------------------------------

private fun applySndSuperRamp(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("superramp")) }

/**
 * Sets the sound to a super ramp (multiple detuned ramp oscillators).
 *
 * @param spread Detune spread between voices.
 * @param-tool voices SprudelSuperSawEditor, SprudelSuperSawSequenceEditor
 * @param voices Number of oscillators (1–16)
 * @return A new pattern with sound set to "superramp" and parameters applied.
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun SprudelPattern.sndSuperRamp(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndSuperRamp(this)
    if (voices != null) p = p.oscparam("voices", voices, callInfo?.forParam(0, 1))
    if (spread != null) p = p.oscparam("spread", spread, callInfo?.forParam(1, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to super ramp.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A new pattern with sound set to "superramp".
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun String.sndSuperRamp(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSuperRamp(voices, spread, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to super ramp.
 *
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 * @return A [PatternMapperFn] that sets sound to "superramp".
 * @category tonal
 * @tags superramp, ramp, unison, oscillator, snd, addon
 */
@KlangScript.Function
fun sndSuperRamp(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSuperRamp(voices, spread, callInfo) }

/**
 * Chains a super ramp sound onto this [PatternMapperFn].
 * @param voices Number of unison voices.
 * @param spread Detune spread between voices.
 */
@KlangScript.Function
fun PatternMapperFn.sndSuperRamp(voices: PatternLike? = null, spread: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSuperRamp(voices, spread, callInfo) }
