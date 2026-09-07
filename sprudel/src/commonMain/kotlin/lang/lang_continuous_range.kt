/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._mapRangeContext
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern.Companion.withContext
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import kotlin.math.exp

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all top-level vals (e.g. oscillator constants) are eagerly evaluated.
 */
// -- toBipolar --------------------------------------------------------------------------------------------------------

private fun applyToBipolar(pattern: SprudelPattern): SprudelPattern {
    val contextAware = pattern._mapRangeContext(
        transformMin = { (it + 1.0) / 2.0 },
        transformMax = { (it + 1.0) / 2.0 }
    )
    return applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) (d * 2.0 - 1.0).asVoiceValue() else v
    }
}

/**
 * Maps values in this pattern from the unipolar range `0..1` to the bipolar range `-1..1`.
 *
 * The linear transform is `out = in * 2 − 1`, so `0 → -1`, `0.5 → 0`, `1 → 1`.
 * Range metadata stored in the pattern context is also remapped so that chained
 * [range] or [rangex] calls continue to produce correct results.
 *
 * Typical use: convert a unipolar oscillator (`sine`, `saw`, `tri`) into a centred LFO
 * before passing it to a parameter that expects bipolar modulation.
 *
 * @return A new pattern whose values are remapped to `-1..1`.
 *
 * ```KlangScript(Playable)
 * sine.toBipolar().range2(40, 60).note()  // bipolar pitch vibrato in semitones
 * ```
 *
 * ```KlangScript(Playable)
 * saw.toBipolar().range2(-1, 1).pan()  // panning sweep using a bipolar saw
 * ```
 * @category continuous
 * @tags toBipolar, bipolar, unipolar, range, lfo, oscillator
 */
@KlangScript.Function
fun SprudelPattern.toBipolar(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern = applyToBipolar(this)

/** Parses this string as a pattern, then maps its values from `0..1` to `-1..1`. */
@KlangScript.Function
fun String.toBipolar(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).toBipolar(callInfo)

/**
 * Returns a [PatternMapperFn] that maps values from the unipolar range `0..1` to the bipolar range `-1..1`.
 *
 * ```KlangScript(Playable)
 * sine.apply(toBipolar()).range2(-10, 10).note().segment(128)  // bipolar sine pitch vibrato
 * ```
 *
 * ```KlangScript(Playable)
 * saw.apply(toBipolar().range2(-1, 1)).pan().segment(128)  // chain toBipolar then range2
 * ```
 *
 * @return A [PatternMapperFn] that maps values from `0..1` to `-1..1`.
 * @category continuous
 * @tags toBipolar, bipolar, unipolar, range, lfo, mapper
 */
@KlangScript.Function
fun toBipolar(callInfo: CallInfo? = null): PatternMapperFn = { p -> p.toBipolar(callInfo) }

/**
 * Chains a unipolar-to-bipolar mapping onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * sine.apply(range(0, 1).toBipolar())  // chain range then bipolar conversion
 * ```
 *
 * @return A [PatternMapperFn] that maps values from `0..1` to `-1..1`.
 */
@KlangScript.Function
fun PatternMapperFn.toBipolar(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.toBipolar(callInfo) }

// -- fromBipolar ------------------------------------------------------------------------------------------------------

private fun applyFromBipolar(pattern: SprudelPattern): SprudelPattern {
    val contextAware = pattern._mapRangeContext(
        transformMin = { it * 2.0 - 1.0 },
        transformMax = { it * 2.0 - 1.0 }
    )
    return applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) ((d + 1.0) / 2.0).asVoiceValue() else v
    }
}

/**
 * Maps values in this pattern from the bipolar range `-1..1` to the unipolar range `0..1`.
 *
 * The linear transform is `out = (in + 1) / 2`, so `-1 → 0`, `0 → 0.5`, `1 → 1`.
 * Range metadata is updated to keep downstream [range] calls consistent.
 *
 * Useful for converting a bipolar LFO (`sine2`, `tri2`) into a probability or a unipolar
 * selector value before passing it to parameters that live in `0..1`.
 *
 * @return A new pattern whose values are remapped to `0..1`.
 *
 * ```KlangScript(Playable)
 * sine2.fromBipolar().range(200, 2000).freq().segment(128)  // bipolar sine to frequency range
 * ```
 *
 * ```KlangScript(Playable)
 * tri2.fromBipolar().range(0.2, 0.8).gain().segment(128)  // bipolar triangle to gain range
 * ```
 * @category continuous
 * @tags fromBipolar, bipolar, unipolar, range, lfo, oscillator
 */
@KlangScript.Function
fun SprudelPattern.fromBipolar(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern = applyFromBipolar(this)

/** Parses this string as a pattern, then maps its values from `-1..1` to `0..1`. */
@KlangScript.Function
fun String.fromBipolar(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fromBipolar(callInfo)

/**
 * Returns a [PatternMapperFn] that maps values from the bipolar range `-1..1` to the unipolar range `0..1`.
 *
 * ```KlangScript(Playable)
 * sine2.apply(fromBipolar()).range(0, 100).freq().segment(128)  // bipolar to unipolar then frequency range
 * ```
 *
 * ```KlangScript(Playable)
 * sine2.apply(fromBipolar().range(0.2, 0.8)).gain().segment(128)  // chain fromBipolar then range
 * ```
 *
 * @return A [PatternMapperFn] that maps values from `-1..1` to `0..1`.
 * @category continuous
 * @tags fromBipolar, bipolar, unipolar, range, lfo, mapper
 */
@KlangScript.Function
fun fromBipolar(callInfo: CallInfo? = null): PatternMapperFn = { p -> p.fromBipolar(callInfo) }

/**
 * Chains a bipolar-to-unipolar mapping onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * sine2.apply(range2(0, 1).fromBipolar())  // chain range2 then fromBipolar
 * ```
 *
 * @return A [PatternMapperFn] that maps values from `-1..1` to `0..1`.
 */
@KlangScript.Function
fun PatternMapperFn.fromBipolar(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fromBipolar(callInfo) }

// -- range ------------------------------------------------------------------------------------------------------------

private fun applyRange(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): ContextModifierPattern {
    val min = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0

    return pattern.withContext {
        set(ContinuousPattern.minKey, min)
        set(ContinuousPattern.maxKey, max)
    }
}

/**
 * Scales the values of a continuous pattern from the unit range `0..1` to `[min, max]`.
 *
 * Continuous patterns such as oscillators and noise generators produce values in `0..1`
 * by default. `range` maps that interval linearly to `[min, max]` and stores the new bounds
 * in the pattern context so that chained `range` or `rangex` calls work correctly.
 *
 * For an exponential (perceptual) scaling — useful with frequencies — use [rangex] instead.
 * To scale bipolar (`-1..1`) patterns in one step, use [range2].
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 * @return A new pattern with values linearly scaled to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine.range(200, 2000).freq().segment(128)  // sine frequency sweep 200–2000 Hz
 * ```
 *
 * ```KlangScript(Playable)
 * perlin.range(0.2, 0.9).gain().segment(128)  // noise-modulated gain
 * ```
 * @category continuous
 * @tags range, scale, min, max, oscillator, lfo, continuous
 */
@KlangScript.Function
fun SprudelPattern.range(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    applyRange(this, listOf(min.toDouble(), max.toDouble()).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then linearly scales its values to `[min, max]`.
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 * @return A new pattern with values linearly scaled to `[min, max]`.
 */
@KlangScript.Function
fun String.range(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).range(min, max, callInfo)

/**
 * Returns a [PatternMapperFn] that linearly scales pattern values to `[min, max]`.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 * @return A [PatternMapperFn] that linearly scales values to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine.apply(range(200, 2000)).freq().segment(128)  // sine frequency sweep 200–2000 Hz
 * ```
 *
 * ```KlangScript(Playable)
 * sine.firstOf(4, range(0.2, 0.9)).gain().segment(128)  // alternate gain range every 4 cycles
 * ```
 * @category continuous
 * @tags range, scale, min, max, oscillator, lfo, continuous
 */
@KlangScript.Function
fun range(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.range(min, max, callInfo) }

/**
 * Chains a linear range-scaling onto this [PatternMapperFn], mapping values to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine.apply(toBipolar().range(-1, 1))  // chain bipolar conversion then scale
 * ```
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 */
@KlangScript.Function
fun PatternMapperFn.range(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.range(min, max, callInfo) }

// -- rangex -----------------------------------------------------------------------------------------------------------

private fun applyRangex(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val min = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0

    // Apply logarithmic transformation to min/max for exponential scaling
    val logMin = kotlin.math.ln(kotlin.math.max(min, 0.0001)) // Avoid log(0)
    val logMax = kotlin.math.ln(kotlin.math.max(max, 0.0001))

    val ranged = pattern.withContext {
        set(ContinuousPattern.minKey, logMin)
        set(ContinuousPattern.maxKey, logMax)
    }

    // Apply exponential function to the result
    return applyUnaryOp(ranged) { v ->
        v.asDouble?.let { exp(it) }?.asVoiceValue() ?: v
    }
}

/**
 * Scales the values of a continuous pattern to `[min, max]` using an **exponential** curve.
 *
 * Unlike [range] (linear), `rangex` applies a logarithmic input mapping so that equal
 * perceived steps correspond to equal value steps. This is particularly useful for audio
 * frequencies and filter cutoffs, where musical intervals (octaves, fifths) are ratios
 * rather than fixed differences.
 *
 * `min` must be greater than `0` for meaningful results; values ≤ `0` are clamped to
 * `0.0001` internally to avoid `ln(0)`.
 *
 * @param min The target minimum value (default `0.0`; use a small positive number for frequencies).
 * @param max The target maximum value (default `1.0`).
 * @return A new pattern with values exponentially scaled to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine.rangex(100, 1000).freq().segment(128)  // frequency sweep with musical spacing
 * ```
 *
 * ```KlangScript(Playable)
 * perlin.rangex(50, 1000).freq().segment(128)  // exponential filter cutoff sweep
 * ```
 * @category continuous
 * @tags rangex, range, exponential, logarithmic, scale, frequency, oscillator, lfo, continuous
 */
@KlangScript.Function
fun SprudelPattern.rangex(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    applyRangex(this, listOf(min.toDouble(), max.toDouble()).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then exponentially scales its values to `[min, max]`.
 *
 * @param min The target minimum value (default `0.0`; use a small positive number for frequencies).
 * @param max The target maximum value (default `1.0`).
 * @return A new pattern with values exponentially scaled to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * "0 0.5 1".rangex(100, 1000).freq()  // manual values scaled exponentially to frequency range
 * ```
 */
@KlangScript.Function
fun String.rangex(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rangex(min, max, callInfo)

/**
 * Returns a [PatternMapperFn] that exponentially scales pattern values to `[min, max]`.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param min The target minimum value (default `0.0`; use a small positive number for frequencies).
 * @param max The target maximum value (default `1.0`).
 * @return A [PatternMapperFn] that exponentially scales values to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine.apply(rangex(100, 1000)).freq().segment(128)  // exponential frequency sweep
 * ```
 *
 * ```KlangScript(Playable)
 * sine.firstOf(4, rangex(50, 500)).freq().segment(128)  // alternate exponential range every 4 cycles
 * ```
 * @category continuous
 * @tags rangex, range, exponential, logarithmic, scale, frequency, oscillator, lfo, continuous
 */
@KlangScript.Function
fun rangex(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rangex(min, max, callInfo) }

/**
 * Chains an exponential range-scaling onto this [PatternMapperFn], mapping values to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine.apply(fromBipolar().rangex(100, 2000))  // chain fromBipolar then exponential frequency range
 * ```
 *
 * @param min The target minimum value (default `0.0`; use a small positive number for frequencies).
 * @param max The target maximum value (default `1.0`).
 */
@KlangScript.Function
fun PatternMapperFn.rangex(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.rangex(min, max, callInfo) }

// -- range2 -----------------------------------------------------------------------------------------------------------

private fun applyRange2(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Convert bipolar (-1 to 1) to unipolar (0 to 1), then apply range
    return applyRange(pattern.fromBipolar(), args)
}

/**
 * Scales bipolar values (`-1..1`) to the range `[min, max]` in a single step.
 *
 * Equivalent to calling [fromBipolar] followed by [range]: converts a centred LFO output
 * (`sine2`, `tri2`, `saw2`, …) directly to the desired target range without an intermediate
 * step.
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 * @return A new pattern with bipolar values scaled to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine2.range2(200, 2000).freq().segment(128)  // bipolar sine mapped to frequency range
 * ```
 *
 * ```KlangScript(Playable)
 * tri2.range2(-24, 24).note().segment(128)  // pitch vibrato in semitones
 * ```
 * @category continuous
 * @tags range2, bipolar, range, scale, lfo, oscillator, continuous
 */
@KlangScript.Function
fun SprudelPattern.range2(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    applyRange2(this, listOf(min.toDouble(), max.toDouble()).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern, then converts its bipolar values to `[min, max]`.
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 * @return A new pattern with bipolar values scaled to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * "0 0.5 -0.5".range2(0, 100)  // manual bipolar values scaled to range
 * ```
 */
@KlangScript.Function
fun String.range2(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).range2(min, max, callInfo)

/**
 * Returns a [PatternMapperFn] that scales bipolar values (`-1..1`) to `[min, max]`.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 * @return A [PatternMapperFn] that scales bipolar values to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine2.apply(range2(0, 100)).freq().segment(128)  // bipolar sine to frequency range
 * ```
 *
 * ```KlangScript(Playable)
 * sine2.firstOf(4, range2(-24, 24)).note().segment(128)  // alternate pitch range every 4 cycles
 * ```
 * @category continuous
 * @tags range2, bipolar, range, scale, lfo, oscillator, continuous
 */
@KlangScript.Function
fun range2(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.range2(min, max, callInfo) }

/**
 * Chains a bipolar range-scaling onto this [PatternMapperFn], converting bipolar values (`-1..1`) to `[min, max]`.
 *
 * ```KlangScript(Playable)
 * sine2.apply(toBipolar().range2(-10, 10))  // deliberately redundant, illustrates chaining
 * ```
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 */
@KlangScript.Function
fun PatternMapperFn.range2(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.range2(min, max, callInfo) }
