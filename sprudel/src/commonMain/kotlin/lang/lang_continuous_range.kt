/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.constants.RANGEX_FLOOR
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern.Companion.withContext
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import kotlin.math.exp

// -- range ------------------------------------------------------------------------------------------------------------

private fun applyRange(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): ContextModifierPattern {
    val from = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val to = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0

    return pattern.withContext {
        set(ContinuousPattern.rangeFromKey, from)
        set(ContinuousPattern.rangeToKey, to)
    }
}

/**
 * Lets a continuous signal swing between [from] and [to].
 *
 * The signals (`sine`, `saw`, `perlin`, `rand`, ...) swing between `0` and `1` on their own. `range` maps that
 * swing linearly onto `[from, to]`: where the signal is `0` you get [from], where it is `1` you get [to]. Every
 * signal has a shorthand for it: `perlin(200, 400)` is exactly `perlin.range(200, 400)`.
 *
 * `range` shapes continuous signals only. Discrete values (a mini-notation string, `seq(...)`) scale with `mul` and
 * `add`: `"0 0.5 1".mul(900).add(100)` gives 100, 550 and 1000.
 *
 * Where the swing sits is up to the two values:
 *
 * | Call              | The signal moves                                      |
 * |-------------------|-------------------------------------------------------|
 * | `range(0, 1)`     | only upward, between 0 and 1 (the signal as it is)    |
 * | `range(-1, 0)`    | only downward, between -1 and 0                       |
 * | `range(-1, 1)`    | both ways, centred on 0                               |
 * | `range(-0.5, 1)`  | mostly upward, dipping a little below 0               |
 * | `range(1, 0)`     | the same swing turned upside down                     |
 *
 * The Ignitor oscillators swing between `-1` and `1` instead, and their `range(from, to)` gives the same result:
 * `x.range(200, 400)` swings between 200 and 400 in both DSLs, so no one needs to know where a source started.
 *
 * The innermost range wins: `perlin(200, 400).range(0, 1)` still swings between 200 and 400, so a signal is
 * ranged once. An outer [rangex] does not reshape a ranged signal either (`perlin(200, 400).rangex(100, 1000)` gives
 * wrong values): use [rangex] on the bare signal, `perlin.rangex(200, 400)`. A centred swing is `sine.range(-1, 1)`.
 *
 * For an exponential (perceptually even) scaling, useful with frequencies, use [rangex] instead.
 *
 * @param from The value where the signal is at its low end (default `0.0`).
 * @param to The value where the signal is at its high end (default `1.0`).
 * @return A new pattern with values linearly scaled to `[from, to]`.
 *
 * ```KlangScript(Playable)
 * sine.range(200, 2000).freq().segment(128)  // sine frequency sweep 200 to 2000 Hz
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").pan(0.5).pan(add(perlin.seg(8).range(-0.2, 0.2)))  // drifts both ways around the centre
 * ```
 *
 * ```KlangScript(Playable)
 * note("c4*8").s("saw").lpf(perlin(400, 2000).slow(8))  // the shorthand: perlin.range(400, 2000)
 * ```
 * @category continuous
 * @tags range, scale, from, to, oscillator, lfo, continuous, unipolar, bipolar
 */
@KlangScript.Function
fun SprudelPattern.range(from: Number = 0.0, to: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    applyRange(this, listOf(from.toDouble(), to.toDouble()).asSprudelDslArgs(callInfo))

/**
 * Returns a [PatternMapperFn] that linearly scales pattern values to `[from, to]`.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param from The value where the signal is at its low end (default `0.0`).
 * @param to The value where the signal is at its high end (default `1.0`).
 * @return A [PatternMapperFn] that linearly scales values to `[from, to]`.
 *
 * ```KlangScript(Playable)
 * sine.apply(range(200, 2000)).freq().segment(128)  // sine frequency sweep 200–2000 Hz
 * ```
 *
 * ```KlangScript(Playable)
 * sine.firstOf(4, range(0.2, 0.9)).gain().segment(128)  // alternate gain range every 4 cycles
 * ```
 * @category continuous
 * @tags range, scale, from, to, oscillator, lfo, continuous
 */
@KlangScript.Function
fun range(from: Number = 0.0, to: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.range(from, to, callInfo) }

/**
 * Chains a linear range-scaling onto this [PatternMapperFn], mapping values to `[from, to]`.
 *
 * ```KlangScript(Playable)
 * sine.apply(slow(4).range(200, 2000)).freq().segment(64)  // chain slow then scale
 * ```
 *
 * @param from The value where the signal is at its low end (default `0.0`).
 * @param to The value where the signal is at its high end (default `1.0`).
 */
@KlangScript.Function
fun PatternMapperFn.range(from: Number = 0.0, to: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.range(from, to, callInfo) }

// -- rangex -----------------------------------------------------------------------------------------------------------

private fun applyRangex(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val from = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val to = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0

    // Apply logarithmic transformation to from/to for exponential scaling
    // Coerced to the shared floor with the Ignitor's comparison, so a value at or below it and a NaN read as the floor
    val logFrom = kotlin.math.ln(if (from > RANGEX_FLOOR) from else RANGEX_FLOOR)
    val logTo = kotlin.math.ln(if (to > RANGEX_FLOOR) to else RANGEX_FLOOR)

    val ranged = pattern.withContext {
        set(ContinuousPattern.rangeFromKey, logFrom)
        set(ContinuousPattern.rangeToKey, logTo)
    }

    // Apply exponential function to the result
    return applyUnaryOp(ranged) { v ->
        v.asDouble?.let { exp(it) }?.asVoiceValue() ?: v
    }
}

/**
 * Scales the values of a continuous pattern to `[from, to]` using an **exponential** curve.
 *
 * `rangex` shapes continuous signals only, like [range]. Discrete values scale with `mul` and `add`.
 *
 * Unlike [range] (linear), `rangex` applies a logarithmic input mapping so that equal
 * perceived steps correspond to equal value steps. This is particularly useful for audio
 * frequencies and filter cutoffs, where musical intervals (octaves, fifths) are ratios
 * rather than fixed differences.
 *
 * Both values must be greater than `0` for meaningful results; values ≤ `0` are coerced to
 * `0.0001` internally to avoid `ln(0)`. The Ignitor's `rangex(from, to)` is the same mapping on its `-1..1` swing.
 *
 * @param from The value where the signal is at its low end (default `0.0`; use a small positive number for frequencies).
 * @param to The value where the signal is at its high end (default `1.0`).
 * @return A new pattern with values exponentially scaled to `[from, to]`.
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
fun SprudelPattern.rangex(from: Number = 0.0, to: Number = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    applyRangex(this, listOf(from.toDouble(), to.toDouble()).asSprudelDslArgs(callInfo))

/**
 * Returns a [PatternMapperFn] that exponentially scales pattern values to `[from, to]`.
 *
 * Use the returned mapper as a transform argument or apply it to a pattern via `.apply(...)`.
 *
 * @param from The value where the signal is at its low end (default `0.0`; use a small positive number for frequencies).
 * @param to The value where the signal is at its high end (default `1.0`).
 * @return A [PatternMapperFn] that exponentially scales values to `[from, to]`.
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
fun rangex(from: Number = 0.0, to: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rangex(from, to, callInfo) }

/**
 * Chains an exponential range-scaling onto this [PatternMapperFn], mapping values to `[from, to]`.
 *
 * ```KlangScript(Playable)
 * sine.apply(slow(4).rangex(100, 2000)).freq().segment(64)  // chain slow then exponential frequency range
 * ```
 *
 * @param from The value where the signal is at its low end (default `0.0`; use a small positive number for frequencies).
 * @param to The value where the signal is at its high end (default `1.0`).
 */
@KlangScript.Function
fun PatternMapperFn.rangex(from: Number = 0.0, to: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.rangex(from, to, callInfo) }
