/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

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
 * @tags sine, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSine(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd
 */
@KlangScript.Function
fun String.sndSine(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSine(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd
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
 * @tags saw, sawtooth, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSaw(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd
 */
@KlangScript.Function
fun String.sndSaw(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSaw(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd
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
 * @tags square, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndSquare(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd
 */
@KlangScript.Function
fun String.sndSquare(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSquare(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to square wave.
 * @category tonal
 * @tags square, oscillator, snd
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
 * @tags triangle, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndTriangle(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd
 */
@KlangScript.Function
fun String.sndTriangle(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndTriangle(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to triangle wave.
 * @category tonal
 * @tags triangle, oscillator, snd
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
 * @tags ramp, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndRamp(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd
 */
@KlangScript.Function
fun String.sndRamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndRamp(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd
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
 * @tags zamp, ramp, raw, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndZamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndZamp(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to raw ramp ("zamp").
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd
 */
@KlangScript.Function
fun String.sndZamp(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndZamp(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to raw ramp ("zamp").
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd
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
 * @tags noise, whitenoise, snd
 */
@KlangScript.Function
fun SprudelPattern.sndNoise(color: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndNoise(this)
    if (color != null) p = p.oscparam("color", color, callInfo?.forParam(0, 1))
    return p
}

/** Parses this string as a pattern and sets sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd
 */
@KlangScript.Function
fun String.sndNoise(color: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndNoise(color, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to white noise.
 * @category tonal
 * @tags noise, whitenoise, snd
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
 * @tags noise, brownnoise, brown, snd
 */
@KlangScript.Function
fun SprudelPattern.sndBrown(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndBrown(this)
    if (depth != null) p = p.oscparam("depth", depth, callInfo?.forParam(0, 1))
    return p
}

/** Parses this string as a pattern and sets sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd
 */
@KlangScript.Function
fun String.sndBrown(depth: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndBrown(depth, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd
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
 * @tags noise, pinknoise, pink, snd
 */
@KlangScript.Function
fun SprudelPattern.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySndPink(this, listOfNotNull(params).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and sets sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd
 */
@KlangScript.Function
fun String.sndPink(params: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndPink(params, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd
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
 * @tags pulze, pulse, oscillator, snd
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
 * @tags pulze, pulse, oscillator, snd
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
 * @tags pulze, pulse, oscillator, snd
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
 * @tags dust, impulse, noise, snd
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
 * @tags dust, impulse, noise, snd
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
 * @tags dust, impulse, noise, snd
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
 * @tags crackle, noise, snd
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
 * @tags crackle, noise, snd
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
 * @tags crackle, noise, snd
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
