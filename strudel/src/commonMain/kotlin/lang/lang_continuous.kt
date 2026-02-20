@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel._mapRangeContext
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.math.BerlinNoise
import io.peekandpoke.klang.strudel.math.PerlinNoise
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern.Companion.withContext
import io.peekandpoke.klang.strudel.pattern.ContinuousPattern
import io.peekandpoke.klang.strudel.pattern.EmptyPattern
import kotlin.math.PI
import kotlin.math.sin

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangContinuousInit = false

// -- toBipolar --------------------------------------------------------------------------------------------------------

internal val StrudelPattern._toBipolar by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    val contextAware = p._mapRangeContext(
        transformMin = { (it + 1.0) / 2.0 },
        transformMax = { (it + 1.0) / 2.0 }
    )
    applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) (d * 2.0 - 1.0).asVoiceValue() else v
    }
}

internal val String._toBipolar by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p._toBipolar(emptyList()) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * sine.toBipolar().range2(-12, 12).note()   // bipolar pitch vibrato in semitones
 * ```
 *
 * ```KlangScript
 * saw.toBipolar().range2(-1, 1).pan()        // panning sweep using a bipolar saw
 * ```
 * @category continuous
 * @tags toBipolar, bipolar, unipolar, range, lfo, oscillator
 */
@StrudelDsl
fun StrudelPattern.toBipolar(): StrudelPattern = this._toBipolar(emptyList())

/** Parses this string as a pattern, then maps its values from `0..1` to `-1..1`. */
@StrudelDsl
fun String.toBipolar(): StrudelPattern = this._toBipolar(emptyList())

// -- fromBipolar ------------------------------------------------------------------------------------------------------

internal val StrudelPattern._fromBipolar by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    val contextAware = p._mapRangeContext(
        transformMin = { it * 2.0 - 1.0 },
        transformMax = { it * 2.0 - 1.0 }
    )
    applyUnaryOp(contextAware) { v ->
        val d = v.asDouble
        if (d != null) ((d + 1.0) / 2.0).asVoiceValue() else v
    }
}

internal val String._fromBipolar by dslStringExtension { p, /* args */ _, /* callInfo */ _ -> p._fromBipolar(emptyList()) }

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * sine2.fromBipolar().range(200, 2000).freq().segment(128)   // bipolar sine to frequency range
 * ```
 *
 * ```KlangScript
 * tri2.fromBipolar().range(0.2, 0.8).gain().segment(128)     // bipolar triangle to gain range
 * ```
 * @category continuous
 * @tags fromBipolar, bipolar, unipolar, range, lfo, oscillator
 */
@StrudelDsl
fun StrudelPattern.fromBipolar(): StrudelPattern = this._fromBipolar(emptyList())

/** Parses this string as a pattern, then maps its values from `-1..1` to `0..1`. */
@StrudelDsl
fun String.fromBipolar(): StrudelPattern = this._fromBipolar(emptyList())

// -- range ------------------------------------------------------------------------------------------------------------

private fun applyRange(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): ContextModifierPattern {
    val min = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0
    val granularity = args.getOrNull(2)?.value?.asDoubleOrNull()?.toRational() ?: Rational.ONE

    return pattern.withContext {
        set(ContinuousPattern.minKey, min)
        set(ContinuousPattern.maxKey, max)
        set(ContinuousPattern.granularityKey, granularity)
    }
}

internal val StrudelPattern._range by dslPatternExtension { p, args, /* callInfo */ _ -> applyRange(p, args) }
internal val String._range by dslStringExtension { p, args, /* callInfo */ _ -> applyRange(p, args) }

// ===== USER-FACING OVERLOADS =====

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
 * @param granularity Quantisation step size; `1.0` (default) means fully continuous (no rounding).
 * @return A new pattern with values linearly scaled to `[min, max]`.
 *
 * ```KlangScript
 * sine.range(200, 2000).freq().segment(128)            // sine frequency sweep 200–2000 Hz
 * ```
 *
 * ```KlangScript
 * perlin.range(0.2, 0.9).gain().segment(128)           // noise-modulated gain
 * ```
 * @category continuous
 * @tags range, scale, min, max, oscillator, lfo, continuous
 */
@StrudelDsl
fun StrudelPattern.range(min: Number = 0.0, max: Number = 1.0, granularity: Number = 1.0): StrudelPattern =
    this._range(listOf(min.toDouble(), max.toDouble(), granularity.toDouble()).asStrudelDslArgs())

/** Parses this string as a pattern, then linearly scales its values to `[min, max]`. */
@StrudelDsl
fun String.range(min: Number = 0.0, max: Number = 1.0, granularity: Number = 1.0): StrudelPattern =
    this._range(listOf(min.toDouble(), max.toDouble(), granularity.toDouble()).asStrudelDslArgs())

// -- rangex -----------------------------------------------------------------------------------------------------------

private fun applyRangex(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val min = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    val max = args.getOrNull(1)?.value?.asDoubleOrNull() ?: 1.0
    val granularity = args.getOrNull(2)?.value?.asDoubleOrNull()?.toRational() ?: Rational.ONE

    // Apply logarithmic transformation to min/max for exponential scaling
    val logMin = kotlin.math.ln(kotlin.math.max(min, 0.0001)) // Avoid log(0)
    val logMax = kotlin.math.ln(kotlin.math.max(max, 0.0001))

    val ranged = pattern.withContext {
        set(ContinuousPattern.minKey, logMin)
        set(ContinuousPattern.maxKey, logMax)
        set(ContinuousPattern.granularityKey, granularity)
    }

    // Apply exponential function to the result
    return applyUnaryOp(ranged) { v ->
        v.asRational?.exp()?.asVoiceValue() ?: v
    }
}

internal val StrudelPattern._rangex by dslPatternExtension { p, args, /* callInfo */ _ -> applyRangex(p, args) }
internal val String._rangex by dslStringExtension { p, args, /* callInfo */ _ -> applyRangex(p, args) }

// ===== USER-FACING OVERLOADS =====

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
 * @param granularity Quantisation step size; `1.0` (default) means fully continuous.
 * @return A new pattern with values exponentially scaled to `[min, max]`.
 *
 * ```KlangScript
 * sine.rangex(100, 1000).freq().segment(128)           // frequency sweep with musical spacing
 * ```
 *
 * ```KlangScript
 * perlin.rangex(50, 1000).freq().segment(128)        // exponential filter cutoff sweep
 * ```
 * @category continuous
 * @tags rangex, range, exponential, logarithmic, scale, frequency, oscillator, lfo, continuous
 */
@StrudelDsl
fun StrudelPattern.rangex(min: Number = 0.0, max: Number = 1.0, granularity: Number = 1.0): StrudelPattern =
    this._rangex(listOf(min.toDouble(), max.toDouble(), granularity.toDouble()).asStrudelDslArgs())

/** Parses this string as a pattern, then exponentially scales its values to `[min, max]`. */
@StrudelDsl
fun String.rangex(min: Number = 0.0, max: Number = 1.0, granularity: Number = 1.0): StrudelPattern =
    this._rangex(listOf(min.toDouble(), max.toDouble(), granularity.toDouble()).asStrudelDslArgs())

// -- range2 -----------------------------------------------------------------------------------------------------------

private fun applyRange2(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Convert bipolar (-1 to 1) to unipolar (0 to 1), then apply range
    return applyRange(pattern.fromBipolar(), args)
}

internal val StrudelPattern._range2 by dslPatternExtension { p, args, /* callInfo */ _ -> applyRange2(p, args) }
internal val String._range2 by dslStringExtension { p, args, /* callInfo */ _ -> applyRange2(p, args) }

// ===== USER-FACING OVERLOADS =====

/**
 * Scales bipolar values (`-1..1`) to the range `[min, max]` in a single step.
 *
 * Equivalent to calling [fromBipolar] followed by [range]: converts a centred LFO output
 * (`sine2`, `tri2`, `saw2`, …) directly to the desired target range without an intermediate
 * step.
 *
 * @param min The target minimum value (default `0.0`).
 * @param max The target maximum value (default `1.0`).
 * @param granularity Quantisation step size; `1.0` (default) means fully continuous.
 * @return A new pattern with bipolar values scaled to `[min, max]`.
 *
 * ```KlangScript
 * sine2.range2(200, 2000).freq().segment(128)          // bipolar sine mapped to frequency range
 * ```
 *
 * ```KlangScript
 * tri2.range2(-24, 24).note().segment(128)             // pitch vibrato in semitones
 * ```
 * @category continuous
 * @tags range2, bipolar, range, scale, lfo, oscillator, continuous
 */
@StrudelDsl
fun StrudelPattern.range2(min: Number = 0.0, max: Number = 1.0, granularity: Number = 1.0): StrudelPattern =
    this._range2(listOf(min.toDouble(), max.toDouble(), granularity.toDouble()).asStrudelDslArgs())

/** Parses this string as a pattern, then converts its bipolar values to `[min, max]`. */
@StrudelDsl
fun String.range2(min: Number = 0.0, max: Number = 1.0, granularity: Number = 1.0): StrudelPattern =
    this._range2(listOf(min.toDouble(), max.toDouble(), granularity.toDouble()).asStrudelDslArgs())

// -- silence / rest / nothing -----------------------------------------------------------------------------------------

/**
 * An empty pattern that produces no events.
 *
 * Use `silence` wherever a [StrudelPattern] argument is required but nothing should play.
 * It is the identity element for [stack] and acts as a rest in sequencing functions like [cat].
 *
 *
 * ```KlangScript
 * seq("c3", silence, "e3", "g3").note()        // rest on the second step
 * ```
 *
 * ```KlangScript
 * cat(s("bd sd"), silence)                      // phrase followed by a silent cycle
 * ```
 * @alias rest, nothing
 * @category continuous
 * @tags silence, rest, empty, quiet
 */
@StrudelDsl
val silence by dslObject { EmptyPattern }

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript
 * seq("c3", rest, "e3", "g3").note()           // rest on the second step
 * ```
 *
 * ```KlangScript
 * cat(s("bd sd"), rest)                         // phrase followed by a silent cycle
 * ```
 * @alias silence, nothing
 * @category continuous
 * @tags rest, silence, empty, quiet
 */
@StrudelDsl
val rest by dslObject { EmptyPattern }

/**
 * An empty pattern that produces no events. Alias for [silence].
 *
 *
 * ```KlangScript
 * seq("c3", nothing, "e3", "g3").note()        // rest on the second step
 * ```
 *
 * ```KlangScript
 * cat(s("bd sd"), nothing)                      // phrase followed by a silent cycle
 * ```
 * @alias silence, rest
 * @category continuous
 * @tags nothing, silence, rest, empty, quiet
 */
@StrudelDsl
val nothing by dslObject { EmptyPattern }

// -- signal -----------------------------------------------------------------------------------------------------------

internal val _signal by dslFunction { args, /* callInfo */ _ ->
    @Suppress("UNCHECKED_CAST")
    val value = args.getOrNull(0)?.value as? Function1<Double, Any?> ?: { 0.0 }

    ContinuousPattern { t -> value(t)?.asDoubleOrNull() ?: 0.0 }
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * signal { t -> kotlin.math.sin(t * 2 * kotlin.math.PI) }.range(200, 2000).freq().segment(128)
 * ```
 *
 * ```KlangScript
 * signal { t -> t % 1.0 }.range(0.0, 127.0).freq().segment(128)
 * ```
 * @category continuous
 * @tags signal, continuous, lfo, function, custom, oscillator
 */
@StrudelDsl
fun signal(f: (Double) -> Double): StrudelPattern = _signal { t -> f(t) }

// -- steady -----------------------------------------------------------------------------------------------------------

internal val _steady by dslFunction { args, /* callInfo */ _ ->
    val value = args.getOrNull(0)?.value?.asDoubleOrNull() ?: 0.0
    signal { _ -> value }
}

// ===== USER-FACING OVERLOADS =====

/**
 * Creates a continuous pattern that always returns the same constant [value].
 *
 * `steady` is a convenience wrapper around [signal] that ignores the time argument.
 * It is useful as a placeholder or when a continuous-pattern slot needs a fixed scalar value.
 *
 * @param value The constant value the pattern should produce at every point in time.
 * @return A continuous pattern that always evaluates to [value].
 *
 * ```KlangScript
 * steady(440.0).freq().segment(128)         // constant 440 Hz carrier frequency
 * ```
 *
 * ```KlangScript
 * steady("c").note().segment(128)           // constant note "c" on every event
 * ```
 * @category continuous
 * @tags steady, constant, continuous, signal, dc
 */
@StrudelDsl
fun steady(value: Number): StrudelPattern = _steady(value.toDouble())

// -- time -------------------------------------------------------------------------------------------------------------

private val timeBase: StrudelPattern by lazy { signal { t -> t } }

/**
 * Continuous ramp — current cycle time, increases linearly by `1.0` per cycle.
 *
 * At cycle `n`, the value equals `n + fraction_of_cycle`. Useful as a time-dependent modulation
 * source or for creating patterns that evolve over many cycles. Use [range] or [rangex] to map
 * it to a target parameter range.
 *
 *
 * ```KlangScript
 * time.range(100.0, 255.0).freq().segment(128)     // linearly rising frequency per cycle
 * ```
 *
 * ```KlangScript
 * time.rangex(100.0, 2000.0).freq().segment(128)   // exponentially rising frequency over time
 * ```
 * @category continuous
 * @tags time, continuous, linear, ramp
 */
@StrudelDsl
val time by dslObject { timeBase }

// -- sine / sine2 -----------------------------------------------------------------------------------------------------

private val sineBase: StrudelPattern by lazy { signal { t -> (sin(t * 2.0 * PI) + 1.0) / 2.0 } }
private val sine2Base: StrudelPattern by lazy { sineBase.toBipolar() }

/**
 * Sine oscillator — unscaled continuous values in `0..1`.
 *
 * Starts at `0.5` at phase 0, rises to `1.0` at the quarter cycle, falls back through `0.5`
 * at the half cycle, reaches `0.0` at three-quarters, and returns to `0.5` at cycle end.
 * For a centred (`-1..1`) LFO use [sine2]. Use [range] to map to a target parameter range.
 *
 * ```KlangScript
 * sine.range(200, 2000).freq().segment(128)                      // sinusoidal frequency sweep
 * ```
 *
 * ```KlangScript
 * note("a!8").adsr("0.2:1.0:1.0:0.2").gain(sine.slow(4))         // gain modulation
 * ```
 *
 * @category continuous
 * @tags sine, oscillator, lfo, continuous, wave
 */
@StrudelDsl
val sine by dslObject { sineBase }

/**
 * Bipolar sine oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `sine.toBipolar()`. Use [range2] to scale to any target range.
 *
 * ```KlangScript
 * sine2.range2(200, 2000).freq().segment(128)                     // bipolar sine frequency sweep
 * ```
 *
 * ```KlangScript
 * sine2.range2(-12, 12).note().segment(128)                       // pitch vibrato in semitones
 * ```
 *
 * @category continuous
 * @tags sine2, sine, oscillator, lfo, bipolar, continuous, wave
 */
@StrudelDsl
val sine2 by dslObject { sine2Base }

// -- cosine / cosine2 -------------------------------------------------------------------------------------------------

private val cosineBase: StrudelPattern by lazy { signal { t -> (sin(t * 2.0 * PI + PI / 2.0) + 1.0) / 2.0 } }
private val cosine2Base: StrudelPattern by lazy { cosineBase.toBipolar() }

/**
 * Cosine oscillator — unscaled continuous values in `0..1`.
 *
 * Like [sine] but shifted by a quarter cycle: starts at `1.0` at phase 0, falls to `0.5` at the
 * quarter cycle, reaches `0.0` at the half cycle, and rises back to `1.0` at cycle end.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript
 * cosine.range(200, 2000).freq().segment(128)                         // cosine frequency sweep
 * ```
 *
 * ```KlangScript
 * note("a!8").adsr("0.2:1.0:1.0:0.2").pan(cosine.slow(4))             // stereo panning with cosine
 * ```
 * @category continuous
 * @tags cosine, oscillator, lfo, continuous, wave
 */
@StrudelDsl
val cosine by dslObject { cosineBase }

/**
 * Bipolar cosine oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `cosine.toBipolar()`. Use [range2] to scale to any target range.
 *
 *
 * ```KlangScript
 * cosine2.range2(200, 2000).freq().segment(128)                   // bipolar cosine frequency sweep
 * ```
 *
 * ```KlangScript
 * cosine2.range2(-1, 1).pan().segment(128)                        // stereo panning with bipolar cosine
 * ```
 * @category continuous
 * @tags cosine2, cosine, oscillator, lfo, bipolar, continuous, wave
 */
@StrudelDsl
val cosine2 by dslObject { cosine2Base }

// -- saw / saw2 -------------------------------------------------------------------------------------------------------

private val sawBase: StrudelPattern by lazy { signal { t -> t % 1.0 } }
private val saw2Base: StrudelPattern by lazy { sawBase.toBipolar() }

/**
 * Sawtooth oscillator — unscaled continuous values in `0..1`.
 *
 * Resets to `0.0` at the start of each cycle and rises linearly to `1.0` by the end.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript
 * saw.range(200, 2000).freq()                       // linearly rising frequency sweep per cycle
 * ```
 *
 * ```KlangScript
 * saw.range(0.0, 0.8).gain()                        // linearly increasing gain per cycle
 * ```
 * @category continuous
 * @tags saw, sawtooth, oscillator, lfo, continuous, wave
 */
@StrudelDsl
val saw by dslObject { sawBase }

/**
 * Bipolar sawtooth oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `saw.toBipolar()`. Rising from `-1` to `1` each cycle. Use [range2] to scale.
 *
 *
 * ```KlangScript
 * saw2.range2(200, 2000).freq().segment(128)                      // bipolar saw frequency sweep
 * ```
 *
 * ```KlangScript
 * saw2.range2(-12, 12).note().segment(128)                        // rising pitch slide per cycle
 * ```
 * @category continuous
 * @tags saw2, saw, sawtooth, oscillator, lfo, bipolar, continuous, wave
 */
@StrudelDsl
val saw2 by dslObject { saw2Base }

// -- isaw / isaw2 -----------------------------------------------------------------------------------------------------

private val isawBase: StrudelPattern by lazy { signal { t -> 1.0 - (t % 1.0) } }
private val isaw2Base: StrudelPattern by lazy { isawBase.toBipolar() }

/**
 * Inverse sawtooth oscillator — unscaled continuous values in `0..1`.
 *
 * Mirror image of [saw]: starts at `1.0` and falls linearly to `0.0` by the end of the cycle.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript
 * isaw.range(200, 2000).freq()                      // linearly falling frequency sweep per cycle
 * ```
 *
 * ```KlangScript
 * isaw.range(0.0, 0.8).gain()                       // linearly decreasing gain per cycle
 * ```
 * @category continuous
 * @tags isaw, sawtooth, oscillator, lfo, continuous, wave
 */
@StrudelDsl
val isaw by dslObject { isawBase }

/**
 * Bipolar inverse sawtooth oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `isaw.toBipolar()`. Falling from `1` to `-1` each cycle. Use [range2] to scale.
 *
 *
 * ```KlangScript
 * isaw2.range2(200, 2000).freq().segment(128)                     // descending frequency sweep per cycle
 * ```
 *
 * ```KlangScript
 * isaw2.range2(-12, 12).note().segment(128)                       // descending pitch slide per cycle
 * ```
 * @category continuous
 * @tags isaw2, isaw, sawtooth, oscillator, lfo, bipolar, continuous, wave
 */
@StrudelDsl
val isaw2 by dslObject { isaw2Base }

// -- tri / tri2 -------------------------------------------------------------------------------------------------------

private val triBase: StrudelPattern by lazy {
    signal { t ->
        val phase = t % 1.0
        if (phase < 0.5) phase * 2.0 else 2.0 - (phase * 2.0)
    }
}
private val tri2Base: StrudelPattern by lazy { triBase.toBipolar() }

/**
 * Triangle oscillator — unscaled continuous values in `0..1`.
 *
 * Rises linearly `0→1` in the first half of the cycle then falls `1→0` in the second half.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript
 * tri.range(200, 2000).freq().segment(128)                       // triangular frequency oscillation
 * ```
 *
 * ```KlangScript
 * tri.range(0.2, 0.9).gain().segment(128)                        // triangular amplitude tremolo
 * ```
 * @category continuous
 * @tags tri, triangle, oscillator, lfo, continuous, wave
 */
@StrudelDsl
val tri by dslObject { triBase }

/**
 * Bipolar triangle oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `tri.toBipolar()`. Rises `-1→1` then falls `1→-1`. Use [range2] to scale.
 *
 *
 * ```KlangScript
 * tri2.range2(200, 2000).freq()                      // symmetric frequency oscillation
 * ```
 *
 * ```KlangScript
 * tri2.range2(-12, 12).note()                        // symmetric pitch vibrato in semitones
 * ```
 * @category continuous
 * @tags tri2, tri, triangle, oscillator, lfo, bipolar, continuous, wave
 */
@StrudelDsl
val tri2 by dslObject { tri2Base }

// -- itri / itri2 -----------------------------------------------------------------------------------------------------

private val itriBase: StrudelPattern by lazy {
    signal { t ->
        val phase = t % 1.0
        if (phase < 0.5) 1.0 - phase * 2.0 else phase * 2.0 - 1.0
    }
}
private val itri2Base: StrudelPattern by lazy { itriBase.toBipolar() }

/**
 * Inverse triangle oscillator — unscaled continuous values in `0..1`.
 *
 * Mirror image of [tri]: falls `1→0` in the first half of the cycle, then rises `0→1`.
 * Use [range] to map to a target parameter range.
 *
 *
 * ```KlangScript
 * itri.range(200, 2000).freq().segment(128)                      // inverted triangular frequency oscillation
 * ```
 *
 * ```KlangScript
 * itri.range(0.2, 0.9).gain().segment(128)                       // inverted triangle gain tremolo
 * ```
 * @category continuous
 * @tags itri, triangle, oscillator, lfo, continuous, wave
 */
@StrudelDsl
val itri by dslObject { itriBase }

/**
 * Bipolar inverse triangle oscillator — unscaled continuous values in `-1..1`.
 *
 * Identical to `itri.toBipolar()`. Falls `1→-1` then rises `-1→1`. Use [range2] to scale.
 *
 *
 * ```KlangScript
 * itri2.range2(200, 2000).freq().segment(128)                     // inverted symmetric frequency oscillation
 * ```
 *
 * ```KlangScript
 * itri2.range2(-12, 12).note().segment(128)                       // inverted pitch vibrato in semitones
 * ```
 * @category continuous
 * @tags itri2, itri, triangle, oscillator, lfo, bipolar, continuous, wave
 */
@StrudelDsl
val itri2 by dslObject { itri2Base }

// -- square / square2 -------------------------------------------------------------------------------------------------

private val squareBase: StrudelPattern by lazy { signal { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 } }
private val square2Base: StrudelPattern by lazy { squareBase.toBipolar() }

/**
 * Square oscillator — unscaled continuous values alternating `0` / `1`.
 *
 * Produces `0.0` for the first half of each cycle and `1.0` for the second half (50 % duty cycle).
 * Use [range] to map those two discrete levels to any pair of target values.
 *
 *
 * ```KlangScript
 * square.range(200, 800).freq().segment(128)                     // frequency alternates between two values
 * ```
 *
 * ```KlangScript
 * square.range(0.0, 1.0).gain().segment(128)                     // alternates between silence and full volume
 * ```
 * @category continuous
 * @tags square, oscillator, lfo, continuous, wave, gate
 */
@StrudelDsl
val square by dslObject { squareBase }

/**
 * Bipolar square oscillator — unscaled continuous values alternating `-1` / `1`.
 *
 * Identical to `square.toBipolar()`. Alternates `-1.0` / `1.0`. Use [range2] to scale.
 *
 *
 * ```KlangScript
 * square2.range2(200, 800).freq().segment(128)                    // frequency jumps via bipolar square
 * ```
 *
 * ```KlangScript
 * square2.range2(-12, 12).note().segment(128)                     // pitch alternates between two values
 * ```
 * @category continuous
 * @tags square2, square, oscillator, lfo, bipolar, continuous, wave, gate
 */
@StrudelDsl
val square2 by dslObject { square2Base }

// -- perlin / perlin2 -------------------------------------------------------------------------------------------------

private fun createPerlin(): StrudelPattern {
    val cache = mutableMapOf<Long?, PerlinNoise>()

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
 * ```KlangScript
 * perlin.range(200, 2000).freq().segment(128)                    // smooth random frequency drift
 * ```
 *
 * ```KlangScript
 * perlin.range(0.2, 0.9).gain().segment(128)                     // smooth random gain modulation
 * ```
 * @category continuous
 * @tags perlin, noise, random, smooth, continuous, lfo
 */
@StrudelDsl
val perlin by dslObject { createPerlin() }

/**
 * Bipolar Perlin noise — smoothly varying unscaled values in `-1..1`.
 *
 * Identical to `perlin.toBipolar()`. Smoothly varying centred noise. Use [range2] to scale.
 *
 *
 * ```KlangScript
 * perlin2.range2(200, 2000).freq().segment(128)                        // bipolar smooth random frequency drift
 * ```
 *
 * ```KlangScript
 * perlin2.range2(40, 50).slow(4).note().segment(64).s("sine").hpf(90)  // random pitch drift in semitones
 * ```
 * @category continuous
 * @tags perlin2, perlin, noise, random, bipolar, smooth, continuous, lfo
 */
@StrudelDsl
val perlin2 by dslObject { createPerlin().toBipolar() }

// -- berlin / berlin2 -------------------------------------------------------------------------------------------------

private fun createBerlin(): StrudelPattern {
    val cache = mutableMapOf<Long?, BerlinNoise>()

    return ContinuousPattern { from, _, ctx ->
        val seedKey = ctx.getOrNull(QueryContext.randomSeedKey)
        val engine = cache.getOrPut(seedKey) { BerlinNoise(ctx.getSeededRandom("Berlin")) }
        engine.noise(from)
    }
}

private fun createBerlin2(): StrudelPattern {
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
 * ```KlangScript
 * berlin.range(200, 2000).freq().segment(128)                    // sawtooth-textured random frequency
 * ```
 *
 * ```KlangScript
 * berlin.range(0.2, 0.9).gain().segment(128)                     // sawtooth-textured gain variation
 * ```
 * @category continuous
 * @tags berlin, noise, random, sawtooth, continuous, lfo
 */
@StrudelDsl
val berlin by dslObject { createBerlin() }

/**
 * Bipolar Berlin noise — sawtooth-textured unscaled values in `-1..1`.
 *
 * Identical to `berlin.toBipolar()`. Sawtooth-flavoured centred noise. Use [range2] to scale.
 *
 *
 * ```KlangScript
 * berlin2.range2(200, 2000).slow(4).freq().segment(128)                 // bipolar berlin noise frequency drift
 * ```
 *
 * ```KlangScript
 * berlin2.range2(40, 80).slow(4).note().segment(64).s("sine").hpf(90)   // random pitch drift in semitones
 * ```
 * @category continuous
 * @tags berlin2, berlin, noise, random, bipolar, sawtooth, continuous, lfo
 */
@StrudelDsl
val berlin2 by dslObject { createBerlin2() }
