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
import io.peekandpoke.klang.sprudel.IgnitorSlotKeys
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField

// -- sndSine() --------------------------------------------------------------------------------------------------------

private fun applySndSine(source: SprudelPattern): SprudelPattern {
    return source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("sine")) }
}

/**
 * Sets the sound to a sine wave oscillator.
 *
 * @return A new pattern with sound set to "sine".
 * @category tonal
 * @tags sine, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndSine(callInfo: CallInfo? = null): SprudelPattern =
    applySndSine(this)

/** Parses this string as a pattern and sets sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd
 */
@KlangScript.Function
fun String.sndSine(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSine(callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sine wave.
 * @category tonal
 * @tags sine, oscillator, snd
 */
@KlangScript.Function
fun sndSine(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSine(callInfo) }

/** Chains a sine wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndSine(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSine(callInfo) }

// -- sndSaw() ---------------------------------------------------------------------------------------------------------

private fun applySndSaw(source: SprudelPattern): SprudelPattern {
    return source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("sawtooth")) }
}

/**
 * Sets the sound to a sawtooth wave oscillator.
 *
 * @return A new pattern with sound set to "sawtooth".
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndSaw(callInfo: CallInfo? = null): SprudelPattern =
    applySndSaw(this)

/** Parses this string as a pattern and sets sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd
 */
@KlangScript.Function
fun String.sndSaw(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSaw(callInfo)

/** Returns a [PatternMapperFn] that sets the sound to sawtooth wave.
 * @category tonal
 * @tags saw, sawtooth, oscillator, snd
 */
@KlangScript.Function
fun sndSaw(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSaw(callInfo) }

/** Chains a sawtooth wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndSaw(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSaw(callInfo) }

// -- sndSquare() ------------------------------------------------------------------------------------------------------

private fun applySndSquare(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("square")) }

/**
 * Sets the sound to the square wave oscillator: the clean (band-limited) pulse, whose width is the `duty` slot.
 * [sndPulze] is the raw, aliased pulse with the same knob.
 *
 * @tool duty: SprudelPulzeEditor, SprudelPulzeSequenceEditor
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
 * @return A new pattern with sound set to "square".
 * @category tonal
 * @tags square, pulse, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndSquare(duty: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndSquare(this)
    if (duty != null) p = p.ignitorParam(IgnitorSlotKeys.duty, duty, callInfo?.forParam(0, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to square wave.
 *
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
 * @category tonal
 * @tags square, pulse, oscillator, snd
 */
@KlangScript.Function
fun String.sndSquare(duty: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndSquare(duty, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sound to square wave.
 *
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
 * @category tonal
 * @tags square, pulse, oscillator, snd
 */
@KlangScript.Function
fun sndSquare(duty: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndSquare(duty, callInfo) }

/** Chains a square wave sound onto this [PatternMapperFn].
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
 */
@KlangScript.Function
fun PatternMapperFn.sndSquare(duty: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndSquare(duty, callInfo) }

// -- sndTri() ---------------------------------------------------------------------------------------------------------

private fun applySndTri(source: SprudelPattern): SprudelPattern {
    return source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("triangle")) }
}

/**
 * Sets the sound to a triangle wave oscillator.
 *
 * @return A new pattern with sound set to "triangle".
 * @category tonal
 * @tags tri, triangle, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndTri(callInfo: CallInfo? = null): SprudelPattern =
    applySndTri(this)

/** Parses this string as a pattern and sets sound to triangle wave.
 * @category tonal
 * @tags tri, triangle, oscillator, snd
 */
@KlangScript.Function
fun String.sndTri(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndTri(callInfo)

/** Returns a [PatternMapperFn] that sets the sound to triangle wave.
 * @category tonal
 * @tags tri, triangle, oscillator, snd
 */
@KlangScript.Function
fun sndTri(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndTri(callInfo) }

/** Chains a triangle wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndTri(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndTri(callInfo) }

// -- sndRamp() --------------------------------------------------------------------------------------------------------

private fun applySndRamp(source: SprudelPattern): SprudelPattern {
    return source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("ramp")) }
}

/**
 * Sets the sound to a ramp wave oscillator.
 *
 * @return A new pattern with sound set to "ramp".
 * @category tonal
 * @tags ramp, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndRamp(callInfo: CallInfo? = null): SprudelPattern =
    applySndRamp(this)

/** Parses this string as a pattern and sets sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd
 */
@KlangScript.Function
fun String.sndRamp(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndRamp(callInfo)

/** Returns a [PatternMapperFn] that sets the sound to ramp wave.
 * @category tonal
 * @tags ramp, oscillator, snd
 */
@KlangScript.Function
fun sndRamp(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndRamp(callInfo) }

/** Chains a ramp wave sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndRamp(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndRamp(callInfo) }

// -- sndZamp() --------------------------------------------------------------------------------------------------------

private fun applySndZamp(source: SprudelPattern): SprudelPattern {
    return source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("zamp")) }
}

/**
 * Sets the sound to a raw ramp ("zamp") oscillator — the naive/aliased counterpart of [sndRamp].
 *
 * @return A new pattern with sound set to "zamp".
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndZamp(callInfo: CallInfo? = null): SprudelPattern =
    applySndZamp(this)

/** Parses this string as a pattern and sets sound to raw ramp ("zamp").
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd
 */
@KlangScript.Function
fun String.sndZamp(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndZamp(callInfo)

/** Returns a [PatternMapperFn] that sets the sound to raw ramp ("zamp").
 * @category tonal
 * @tags zamp, ramp, raw, oscillator, snd
 */
@KlangScript.Function
fun sndZamp(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndZamp(callInfo) }

/** Chains a raw ramp ("zamp") sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndZamp(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndZamp(callInfo) }

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
    if (color != null) p = p.ignitorParam(IgnitorSlotKeys.color, color, callInfo?.forParam(0, 1))
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
 * @param leak Per-sample white leak (default 0.02): lower is deeper and slower, higher is brighter.
 * @return A new pattern with sound set to "brownnoise".
 * @category tonal
 * @tags noise, brownnoise, brown, snd
 */
@KlangScript.Function
fun SprudelPattern.sndBrown(leak: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndBrown(this)
    if (leak != null) p = p.ignitorParam(IgnitorSlotKeys.leak, leak, callInfo?.forParam(0, 1))
    return p
}

/** Parses this string as a pattern and sets sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd
 */
@KlangScript.Function
fun String.sndBrown(leak: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndBrown(leak, callInfo)

/** Returns a [PatternMapperFn] that sets the sound to brown noise.
 * @category tonal
 * @tags noise, brownnoise, brown, snd
 */
@KlangScript.Function
fun sndBrown(leak: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndBrown(leak, callInfo) }

/** Chains a brown noise sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndBrown(leak: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndBrown(leak, callInfo) }

// -- sndPink() --------------------------------------------------------------------------------------------------------

private fun applySndPink(source: SprudelPattern): SprudelPattern {
    return source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("pinknoise")) }
}

/**
 * Sets the sound to pink noise (1/f noise).
 *
 * @return A new pattern with sound set to "pinknoise".
 * @category tonal
 * @tags noise, pinknoise, pink, snd
 */
@KlangScript.Function
fun SprudelPattern.sndPink(callInfo: CallInfo? = null): SprudelPattern =
    applySndPink(this)

/** Parses this string as a pattern and sets sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd
 */
@KlangScript.Function
fun String.sndPink(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).sndPink(callInfo)

/** Returns a [PatternMapperFn] that sets the sound to pink noise.
 * @category tonal
 * @tags noise, pinknoise, pink, snd
 */
@KlangScript.Function
fun sndPink(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPink(callInfo) }

/** Chains a pink noise sound onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.sndPink(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.sndPink(callInfo) }

// -- sndPulze() -------------------------------------------------------------------------------------------------------

private fun applySndPulze(source: SprudelPattern): SprudelPattern =
    source._liftOrReinterpretStringField(emptyList()) { copy(sound = SoundValue.Named("pulze")) }

/**
 * Sets the sound to the raw pulse wave oscillator with configurable duty cycle: not band-limited, so it aliases
 * (its edges are hard on purpose). [sndSquare] is the clean pulse with the same knob.
 *
 * @tool duty: SprudelPulzeEditor, SprudelPulzeSequenceEditor
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
 * @return A new pattern with sound set to "pulze" and parameters applied.
 * @category tonal
 * @tags pulze, pulse, oscillator, snd
 */
@KlangScript.Function
fun SprudelPattern.sndPulze(duty: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndPulze(this)
    if (duty != null) p = p.ignitorParam(IgnitorSlotKeys.duty, duty, callInfo?.forParam(0, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to pulse wave.
 *
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
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
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
 * @return A [PatternMapperFn] that sets sound to "pulze".
 * @category tonal
 * @tags pulze, pulse, oscillator, snd
 */
@KlangScript.Function
fun sndPulze(duty: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndPulze(duty, callInfo) }

/** Chains a pulse wave sound onto this [PatternMapperFn].
 * @param duty The share of each cycle the wave is high, 0 to 1 (default 0.5, a square).
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
 * @param tail Amplitude exponent, not a length: 1 = uniform (default), above 1 mostly tiny impulses, rare loud pops.
 * @tool density: SprudelDustEditor, SprudelDustSequenceEditor
 * @param density Impulse rate, 0 to 1, scaling a 200 per second ceiling.
 * @return A new pattern with sound set to "dust" and parameters applied.
 * @category tonal
 * @tags dust, impulse, noise, snd
 */
@KlangScript.Function
fun SprudelPattern.sndDust(density: PatternLike? = null, tail: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    var p = applySndDust(this)
    if (density != null) p = p.ignitorParam(IgnitorSlotKeys.density, density, callInfo?.forParam(0, 1))
    if (tail != null) p = p.ignitorParam(IgnitorSlotKeys.tail, tail, callInfo?.forParam(1, 1))
    return p
}

/**
 * Parses this string as a pattern and sets sound to dust generator.
 *
 * @param density Impulse rate, 0 to 1, scaling a 200 per second ceiling.
 * @param tail Amplitude exponent, not a length: 1 = uniform (default), above 1 mostly tiny impulses, rare loud pops.
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
 * @param density Impulse rate, 0 to 1, scaling a 200 per second ceiling.
 * @param tail Amplitude exponent, not a length: 1 = uniform (default), above 1 mostly tiny impulses, rare loud pops.
 * @return A [PatternMapperFn] that sets sound to "dust".
 * @category tonal
 * @tags dust, impulse, noise, snd
 */
@KlangScript.Function
fun sndDust(density: PatternLike? = null, tail: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.sndDust(density, tail, callInfo) }

/** Chains a dust generator sound onto this [PatternMapperFn].
 * @param density Impulse rate, 0 to 1, scaling a 200 per second ceiling.
 * @param tail Amplitude exponent, not a length: 1 = uniform (default), above 1 mostly tiny impulses, rare loud pops.
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
    if (chaos != null) p = p.ignitorParam(IgnitorSlotKeys.chaos, chaos, callInfo?.forParam(0, 1))
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
