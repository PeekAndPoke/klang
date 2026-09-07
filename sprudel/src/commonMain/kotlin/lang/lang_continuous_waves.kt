/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.BerlinNoise
import io.peekandpoke.klang.common.math.PerlinNoise
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.pattern.ContinuousPattern
import kotlin.math.PI
import kotlin.math.sin

// -- signal -----------------------------------------------------------------------------------------------------------

internal fun applySignal(f: (Double) -> Double): SprudelPattern =
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
 * signal(t => Math.sin(t * 6.28318)).range(200, 2000).freq().segment(128)
 * ```
 *
 * ```KlangScript(Playable)
 * signal(t => t % 1.0).range(0.0, 127.0).freq().segment(128)
 * ```
 * @category continuous
 * @tags signal, continuous, lfo, function, custom, oscillator
 */
@KlangScript.Function
fun signal(@Suppress("unused") callInfo: CallInfo? = null, f: (Double) -> Double): SprudelPattern = applySignal(f)

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
 * steady(60).note().segment(128)  // constant note c4 (MIDI 60) on every event
 * ```
 * @category continuous
 * @tags steady, constant, continuous, signal, dc
 */
@KlangScript.Function
fun steady(value: Number, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern {
    val v = value.toDouble()
    return applySignal { _ -> v }
}

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
 * note("a!8").adsr(0.2, 1.0, 1.0, 0.2).gain(sine.slow(4))  // gain modulation
 * ```
 *
 * @category continuous
 * @tags sine, oscillator, lfo, continuous, wave
 */
@KlangScript.Constant
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
@KlangScript.Constant
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
 * note("a!8").adsr(0.2, 1.0, 1.0, 0.2).pan(cosine.slow(4))  // stereo panning with cosine
 * ```
 * @category continuous
 * @tags cosine, oscillator, lfo, continuous, wave
 */
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
@KlangScript.Constant
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
 * The sawtooth construction makes it a characterful, surprisingly musical modulation source.
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
@KlangScript.Constant
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
@KlangScript.Constant
val berlin2: SprudelPattern = createBerlin2()
