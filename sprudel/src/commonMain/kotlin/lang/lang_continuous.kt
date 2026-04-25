@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.BerlinNoise
import io.peekandpoke.klang.common.math.PerlinNoise
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._mapRangeContext
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern.Companion.withContext
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import io.peekandpoke.klang.sprudel.pattern.EmptyPattern
import kotlin.math.PI
import kotlin.math.sin

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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.toBipolar(callInfo: CallInfo? = null): SprudelPattern = applyToBipolar(this)

/** Parses this string as a pattern, then maps its values from `0..1` to `-1..1`. */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fromBipolar(callInfo: CallInfo? = null): SprudelPattern = applyFromBipolar(this)

/** Parses this string as a pattern, then maps its values from `-1..1` to `0..1`. */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
        v.asRational?.exp()?.asVoiceValue() ?: v
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.range2(min: Number = 0.0, max: Number = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.range2(min, max, callInfo) }

// -- silence / rest / nothing -----------------------------------------------------------------------------------------

/**
 * An empty pattern that produces no events.
 *
 * Use `silence` wherever a [SprudelPattern] argument is required but nothing should play.
 * It is the identity element for [stack] and acts as a rest in sequencing functions like [cat].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", silence, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), silence)  // phrase followed by a silent cycle
 * ```
 * @alias rest, nothing
 * @category continuous
 * @tags silence, rest, empty, quiet
 */
@SprudelDsl
@KlangScript.Property
val silence: SprudelPattern = EmptyPattern

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", rest, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), rest)  // phrase followed by a silent cycle
 * ```
 * @alias silence, nothing
 * @category continuous
 * @tags rest, silence, empty, quiet
 */
@SprudelDsl
@KlangScript.Property
val rest: SprudelPattern = EmptyPattern

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript(Playable)
 * seq("c3", nothing, "e3", "g3").note()  // rest on the second step
 * ```
 *
 * ```KlangScript(Playable)
 * cat(s("bd sd"), nothing)  // phrase followed by a silent cycle
 * ```
 * @alias silence, rest
 * @category continuous
 * @tags nothing, silence, rest, empty, quiet
 */
@SprudelDsl
@KlangScript.Property
val nothing: SprudelPattern = EmptyPattern

// -- signal -----------------------------------------------------------------------------------------------------------

private fun applySignal(f: (Double) -> Double): SprudelPattern =
    ContinuousPattern { t -> f(t) }

/**
 * Creates a continuous pattern driven by a user-supplied function of time.
 *
 * The function [f] receives the current cycle position as a [Double] (increases by `1` per
 * cycle) and must return a [Double]. The resulting pattern is continuous — it has no discrete
 * events and can be queried for any time position. Use [range] or [rangex] afterwards to map
 * the output to a useful parameter range.
 *
 * @param f A function `(t: Double) -> Double` where `t` is the current cycle time.
 * @return A continuous pattern driven by [f].
 *
 * ```KlangScript(Playable)
 * signal { t -> kotlin.math.sin(t * 2 * kotlin.math.PI) }.range(200, 2000).freq().segment(128)
 * ```
 *
 * ```KlangScript(Playable)
 * signal { t -> t % 1.0 }.range(0.0, 127.0).freq().segment(128)
 * ```
 * @category continuous
 * @tags signal, continuous, lfo, function, custom, oscillator
 */
@SprudelDsl
@KlangScript.Function
fun signal(callInfo: CallInfo? = null, f: (Double) -> Double): SprudelPattern = applySignal(f)

// -- steady -----------------------------------------------------------------------------------------------------------

/**
 * Creates a continuous pattern that always returns the same constant [value].
 *
 * `steady` is a convenience wrapper around [signal] that ignores the time argument.
 * It is useful as a placeholder or when a continuous-pattern slot needs a fixed scalar value.
 *
 * @param value The constant value the pattern should produce at every point in time.
 * @return A continuous pattern that always evaluates to [value].
 *
 * ```KlangScript(Playable)
 * steady(440.0).freq().segment(128)  // constant 440 Hz carrier frequency
 * ```
 *
 * ```KlangScript(Playable)
 * steady("c").note().segment(128)  // constant note "c" on every event
 * ```
 * @category continuous
 * @tags steady, constant, continuous, signal, dc
 */
@SprudelDsl
@KlangScript.Function
fun steady(value: Number, callInfo: CallInfo? = null): SprudelPattern {
    val v = value.toDouble()
    return applySignal { _ -> v }
}

// -- time -------------------------------------------------------------------------------------------------------------

private val timeBase: SprudelPattern by lazy { applySignal { t -> t } }

/**
 * Continuous ramp — current cycle time, increases linearly by `1.0` per cycle.
 *
 * At cycle `n`, the value equals `n + fraction_of_cycle`. Useful as a time-dependent modulation
 * source or for creating patterns that evolve over many cycles. Use [range] or [rangex] to map
 * it to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * time.range(100.0, 255.0).freq().segment(128)  // linearly rising frequency per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * time.rangex(100.0, 2000.0).freq().segment(128)  // exponentially rising frequency over time
 * ```
 * @category continuous
 * @tags time, continuous, linear, ramp
 */
@SprudelDsl
@KlangScript.Property
val time: SprudelPattern = timeBase

// -- sine / sine2 -----------------------------------------------------------------------------------------------------

private val sineBase: SprudelPattern by lazy { applySignal { t -> (sin(t * 2.0 * PI) + 1.0) / 2.0 } }
private val sine2Base: SprudelPattern by lazy { sineBase.toBipolar() }

/**
 * Sine oscillator — unscaled continuous values in `0..1`.
 *
 * Starts at `0.5` at phase 0, rises to `1.0` at the quarter cycle, falls back through `0.5`
 * at the half cycle, reaches `0.0` at three-quarters, and returns to `0.5` at cycle end.
 * For a centred (`-1..1`) LFO use [sine2]. Use [range] to map to a target parameter range.
 *
 * ```KlangScript(Playable)
 * sine.range(200, 2000).freq().segment(128)  // sinusoidal frequency sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("a!8").adsr("0.2:1.0:1.0:0.2").gain(sine.slow(4))  // gain modulation
 * ```
 *
 * @category continuous
 * @tags sine, oscillator, lfo, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val sine: SprudelPattern = sineBase

/**
 * Bipolar sine oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `sine.toBipolar()`. Use [range2] to scale to any target range.
 *
 * ```KlangScript(Playable)
 * sine2.range2(200, 2000).freq().segment(128)  // bipolar sine frequency sweep
 * ```
 *
 * ```KlangScript(Playable)
 * sine2.range2(40, 60).note().segment(128)  // pitch vibrato in semitones
 * ```
 *
 * @category continuous
 * @tags sine2, sine, oscillator, lfo, bipolar, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val sine2: SprudelPattern = sine2Base

// -- cosine / cosine2 -------------------------------------------------------------------------------------------------

private val cosineBase: SprudelPattern by lazy { applySignal { t -> (sin(t * 2.0 * PI + PI / 2.0) + 1.0) / 2.0 } }
private val cosine2Base: SprudelPattern by lazy { cosineBase.toBipolar() }

/**
 * Cosine oscillator — unscaled continuous values in `0..1`.
 *
 * Like [sine] but shifted by a quarter cycle: starts at `1.0` at phase 0, falls to `0.5` at the
 * quarter cycle, reaches `0.0` at the half cycle, and rises back to `1.0` at cycle end.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * cosine.range(200, 2000).freq().segment(128)  // cosine frequency sweep
 * ```
 *
 * ```KlangScript(Playable)
 * note("a!8").adsr("0.2:1.0:1.0:0.2").pan(cosine.slow(4))  // stereo panning with cosine
 * ```
 * @category continuous
 * @tags cosine, oscillator, lfo, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val cosine: SprudelPattern = cosineBase

/**
 * Bipolar cosine oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `cosine.toBipolar()`. Use [range2] to scale to any target range.
 *
 *
 * ```KlangScript(Playable)
 * cosine2.range2(200, 2000).freq().segment(128)  // bipolar cosine frequency sweep
 * ```
 *
 * ```KlangScript(Playable)
 * cosine2.range2(-1, 1).pan().segment(128)  // stereo panning with bipolar cosine
 * ```
 * @category continuous
 * @tags cosine2, cosine, oscillator, lfo, bipolar, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val cosine2: SprudelPattern = cosine2Base

// -- saw / saw2 -------------------------------------------------------------------------------------------------------

private val sawBase: SprudelPattern by lazy { applySignal { t -> t % 1.0 } }
private val saw2Base: SprudelPattern by lazy { sawBase.toBipolar() }

/**
 * Sawtooth oscillator — unscaled continuous values in `0..1`.
 *
 * Resets to `0.0` at the start of each cycle and rises linearly to `1.0` by the end.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * saw.range(200, 2000).freq()  // linearly rising frequency sweep per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * saw.range(0.0, 0.8).gain()  // linearly increasing gain per cycle
 * ```
 * @category continuous
 * @tags saw, sawtooth, oscillator, lfo, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val saw: SprudelPattern = sawBase

/**
 * Bipolar sawtooth oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `saw.toBipolar()`. Rising from `-1` to `1` each cycle. Use [range2] to scale.
 *
 *
 * ```KlangScript(Playable)
 * saw2.range2(200, 2000).freq().segment(128)  // bipolar saw frequency sweep
 * ```
 *
 * ```KlangScript(Playable)
 * saw2.range2(40, 60).note().segment(128)  // rising pitch slide per cycle
 * ```
 * @category continuous
 * @tags saw2, saw, sawtooth, oscillator, lfo, bipolar, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val saw2: SprudelPattern = saw2Base

// -- isaw / isaw2 -----------------------------------------------------------------------------------------------------

private val isawBase: SprudelPattern by lazy { applySignal { t -> 1.0 - (t % 1.0) } }
private val isaw2Base: SprudelPattern by lazy { isawBase.toBipolar() }

/**
 * Inverse sawtooth oscillator — unscaled continuous values in `0..1`.
 *
 * Mirror image of [saw]: starts at `1.0` and falls linearly to `0.0` by the end of the cycle.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * isaw.range(200, 2000).freq().slow(8).segment(64)  // linearly falling frequency sweep per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * isaw.range(0.0, 0.8).gain()  // linearly decreasing gain per cycle
 * ```
 * @category continuous
 * @tags isaw, sawtooth, oscillator, lfo, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val isaw: SprudelPattern = isawBase

/**
 * Bipolar inverse sawtooth oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `isaw.toBipolar()`. Falling from `1` to `-1` each cycle. Use [range2] to scale.
 *
 *
 * ```KlangScript(Playable)
 * isaw2.range2(200, 2000).freq().segment(128)  // descending frequency sweep per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * isaw2.range2(40, 60).note().segment(128)  // descending pitch slide per cycle
 * ```
 * @category continuous
 * @tags isaw2, isaw, sawtooth, oscillator, lfo, bipolar, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val isaw2: SprudelPattern = isaw2Base

// -- tri / tri2 -------------------------------------------------------------------------------------------------------

private val triBase: SprudelPattern by lazy {
    applySignal { t ->
        val phase = t % 1.0
        if (phase < 0.5) phase * 2.0 else 2.0 - (phase * 2.0)
    }
}
private val tri2Base: SprudelPattern by lazy { triBase.toBipolar() }

/**
 * Triangle oscillator — unscaled continuous values in `0..1`.
 *
 * Rises linearly `0→1` in the first half of the cycle then falls `1→0` in the second half.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * tri.range(200, 2000).freq().segment(128)  // triangular frequency oscillation
 * ```
 *
 * ```KlangScript(Playable)
 * tri.range(0.2, 0.9).gain().segment(128)  // triangular amplitude tremolo
 * ```
 * @category continuous
 * @tags tri, triangle, oscillator, lfo, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val tri: SprudelPattern = triBase

/**
 * Bipolar triangle oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `tri.toBipolar()`. Rises `-1→1` then falls `1→-1`. Use [range2] to scale.
 *
 *
 * ```KlangScript(Playable)
 * tri2.range2(200, 2000).freq()  // symmetric frequency oscillation
 * ```
 *
 * ```KlangScript(Playable)
 * tri2.range2(40, 60).note()  // symmetric pitch vibrato in semitones
 * ```
 * @category continuous
 * @tags tri2, tri, triangle, oscillator, lfo, bipolar, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val tri2: SprudelPattern = tri2Base

// -- itri / itri2 -----------------------------------------------------------------------------------------------------

private val itriBase: SprudelPattern by lazy {
    applySignal { t ->
        val phase = t % 1.0
        if (phase < 0.5) 1.0 - phase * 2.0 else phase * 2.0 - 1.0
    }
}
private val itri2Base: SprudelPattern by lazy { itriBase.toBipolar() }

/**
 * Inverse triangle oscillator — unscaled continuous values in `0..1`.
 *
 * Mirror image of [tri]: falls `1→0` in the first half of the cycle, then rises `0→1`.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * itri.range(200, 2000).freq().segment(128)  // inverted triangular frequency oscillation
 * ```
 *
 * ```KlangScript(Playable)
 * itri.range(0.2, 0.9).gain().segment(128)  // inverted triangle gain tremolo
 * ```
 * @category continuous
 * @tags itri, triangle, oscillator, lfo, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val itri: SprudelPattern = itriBase

/**
 * Bipolar inverse triangle oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `itri.toBipolar()`. Falls `1→-1` then rises `-1→1`. Use [range2] to scale.
 *
 *
 * ```KlangScript(Playable)
 * itri2.range2(200, 2000).freq().segment(128)  // inverted symmetric frequency oscillation
 * ```
 *
 * ```KlangScript(Playable)
 * itri2.range2(40, 60).note().segment(128)  // inverted pitch vibrato in semitones
 * ```
 * @category continuous
 * @tags itri2, itri, triangle, oscillator, lfo, bipolar, continuous, wave
 */
@SprudelDsl
@KlangScript.Property
val itri2: SprudelPattern = itri2Base

// -- square / square2 -------------------------------------------------------------------------------------------------

private val squareBase: SprudelPattern by lazy { applySignal { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 } }
private val square2Base: SprudelPattern by lazy { squareBase.toBipolar() }

/**
 * Square oscillator — unscaled continuous values alternating `0` / `1`.
 *
 * Produces `0.0` for the first half of each cycle and `1.0` for the second half (50 % duty cycle).
 * Use [range] to map those two discrete levels to any pair of target values.
 *
 *
 * ```KlangScript(Playable)
 * square.range(200, 800).freq().segment(128)  // frequency alternates between two values
 * ```
 *
 * ```KlangScript(Playable)
 * square.range(0.0, 1.0).gain().segment(128)  // alternates between silence and full volume
 * ```
 * @category continuous
 * @tags square, oscillator, lfo, continuous, wave, gate
 */
@SprudelDsl
@KlangScript.Property
val square: SprudelPattern = squareBase

/**
 * Bipolar square oscillator — unscaled continuous values alternating `-1` / `1`.
 *
 * Identical to `square.toBipolar()`. Alternates `-1.0` / `1.0`. Use [range2] to scale.
 *
 *
 * ```KlangScript(Playable)
 * square2.range2(200, 800).freq().segment(128)  // frequency jumps via bipolar square
 * ```
 *
 * ```KlangScript(Playable)
 * square2.range2(40, 60).note().segment(128)  // pitch alternates between two values
 * ```
 * @category continuous
 * @tags square2, square, oscillator, lfo, bipolar, continuous, wave, gate
 */
@SprudelDsl
@KlangScript.Property
val square2: SprudelPattern = square2Base

// -- perlin / perlin2 -------------------------------------------------------------------------------------------------

private fun createPerlin(): SprudelPattern {
    val cache = mutableMapOf<Int?, PerlinNoise>()

    return ContinuousPattern { from, _, ctx ->
        val seedKey = ctx.getOrNull(QueryContext.randomSeedKey)
        val engine = cache.getOrPut(seedKey) { PerlinNoise(ctx.getSeededRandom("perlin")) }
        (engine.noise(from) + 1.0) / 2.0
    }
}

/**
 * Continuous Perlin noise — smoothly varying unscaled values in `0..1`.
 *
 * Each instantiation is seeded independently, producing different-but-deterministic smooth curves.
 * Perlin noise transitions gradually between values, making it ideal for organic modulation.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * perlin.range(200, 2000).freq().segment(128)  // smooth random frequency drift
 * ```
 *
 * ```KlangScript(Playable)
 * perlin.range(0.2, 0.9).gain().segment(128)  // smooth random gain modulation
 * ```
 * @category continuous
 * @tags perlin, noise, random, smooth, continuous, lfo
 */
@SprudelDsl
@KlangScript.Property
val perlin: SprudelPattern = createPerlin()

/**
 * Bipolar Perlin noise — smoothly varying unscaled values in `-1..1`.
 *
 * Identical to `perlin.toBipolar()`. Smoothly varying centred noise. Use [range2] to scale.
 *
 *
 * ```KlangScript(Playable)
 * perlin2.range2(200, 2000).freq().segment(128)  // bipolar smooth random frequency drift
 * ```
 *
 * ```KlangScript(Playable)
 * perlin2.range2(40, 50).slow(4).note().segment(64).s("sine").hpf(90)  // random pitch drift in semitones
 * ```
 * @category continuous
 * @tags perlin2, perlin, noise, random, bipolar, smooth, continuous, lfo
 */
@SprudelDsl
@KlangScript.Property
val perlin2: SprudelPattern = createPerlin().toBipolar()

// -- berlin / berlin2 -------------------------------------------------------------------------------------------------

private fun createBerlin(): SprudelPattern {
    val cache = mutableMapOf<Int?, BerlinNoise>()

    return ContinuousPattern { from, _, ctx ->
        val seedKey = ctx.getOrNull(QueryContext.randomSeedKey)
        val engine = cache.getOrPut(seedKey) { BerlinNoise(ctx.getSeededRandom("Berlin")) }
        engine.noise(from)
    }
}

private fun createBerlin2(): SprudelPattern {
    return createBerlin().toBipolar()
}

/**
 * Continuous Berlin noise — sawtooth-textured unscaled values in `0..1`.
 *
 * Like [perlin] but built from sawtooth waves, giving a harsher, more angular quality.
 * Conceived by James Coyne and Jade Rowland as a joke but turned out to be surprisingly useful.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript(Playable)
 * berlin.range(200, 2000).freq().segment(128)  // sawtooth-textured random frequency
 * ```
 *
 * ```KlangScript(Playable)
 * berlin.range(0.2, 0.9).gain().segment(128)  // sawtooth-textured gain variation
 * ```
 * @category continuous
 * @tags berlin, noise, random, sawtooth, continuous, lfo
 */
@SprudelDsl
@KlangScript.Property
val berlin: SprudelPattern = createBerlin()

/**
 * Bipolar Berlin noise — sawtooth-textured unscaled values in `-1..1`.
 *
 * Identical to `berlin.toBipolar()`. Sawtooth-flavoured centred noise. Use [range2] to scale.
 *
 *
 * ```KlangScript(Playable)
 * berlin2.range2(200, 2000).slow(4).freq().segment(128)  // bipolar berlin noise frequency drift
 * ```
 *
 * ```KlangScript(Playable)
 * berlin2.range2(40, 80).slow(4).note().segment(64).s("sine").hpf(90)  // random pitch drift in semitones
 * ```
 * @category continuous
 * @tags berlin2, berlin, noise, random, bipolar, sawtooth, continuous, lfo
 */
@SprudelDsl
@KlangScript.Property
val berlin2: SprudelPattern = createBerlin2()
