/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "ClassName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.BerlinNoise
import io.peekandpoke.klang.common.math.PerlinNoise
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError
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

// -- the signals ------------------------------------------------------------------------------------------------------

/**
 * A sprudel signal: a continuous pattern that swings between `0` and `1`, and the object behind its script name.
 *
 * Bare, the object IS the pattern (`perlin.slow(8)`, `.pan(perlin)`), every pattern method and door takes it as it
 * is. Called with two values it is [range]: `perlin(200, 400)` is exactly `perlin.range(200, 400)`. Both values are
 * required, because `Ignitor.sine(200)` means 200 Hz on the Ignitor side and a one-value `sine(200)` here would be a
 * trap; a call with fewer is a [KlangScriptArgumentError] naming the fix, on both doors.
 *
 * Each signal object declares its own `@KlangScript.Invoke` member (KSP reads the annotation on the object) and
 * hands it to [ranged]. Decided 2026-10-05, `docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md`.
 */
sealed class SprudelSignal(private val name: String, source: SprudelPattern) : SprudelPattern by source {

    /** [range] with both values, or the script error that names the fix. */
    protected fun ranged(from: Number?, to: Number?, callInfo: CallInfo?): SprudelPattern {
        if (from == null || to == null) {
            throw KlangScriptArgumentError(
                functionName = name,
                message = "a signal's range takes two values: $name(from, to)",
                location = callInfo?.callLocation,
            )
        }

        return range(from, to, callInfo)
    }
}

// -- sine -------------------------------------------------------------------------------------------------------------

private val sineBase: SprudelPattern by lazy { applySignal { t -> (sin(t * 2.0 * PI) + 1.0) / 2.0 } }

/**
 * Sine oscillator: unscaled continuous values in `0..1`.
 *
 * Starts at `0.5` at phase 0, rises to `1.0` at the quarter cycle, falls back through `0.5`
 * at the half cycle, reaches `0.0` at three-quarters, and returns to `0.5` at cycle end.
 * Use [range] to map it to a target range, or call it: `sine(200, 2000)` is `sine.range(200, 2000)`.
 * For a swing centred on 0 use `sine(-1, 1)`.
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
@KlangScript.Library("sprudel")
@KlangScript.Object("sine")
object sine : SprudelSignal("sine", sineBase) {

    /**
     * The shorthand for [range]: `sine(from, to)` is exactly `sine.range(from, to)`.
     *
     * Both values are required; `sine` alone is the signal itself, swinging between `0` and `1`.
     *
     * ```KlangScript(Playable)
     * note("c3*8").s("saw").lpf(sine(400, 2000).slow(4))  // the filter opens and closes over four cycles
     * ```
     *
     * @param from Required. The value where the signal is at its low end.
     * @param to Required. The value where the signal is at its high end.
     * @return The signal swinging between [from] and [to].
     */
    @KlangScript.Invoke
    operator fun invoke(from: Number? = null, to: Number? = null, callInfo: CallInfo? = null): SprudelPattern =
        ranged(from, to, callInfo)
}

// -- cosine -----------------------------------------------------------------------------------------------------------

/**
 * Cosine oscillator: unscaled continuous values in `0..1`. It is `sine.early(0.25)`, exactly.
 *
 * [sine] a quarter cycle earlier: starts at `1.0` at phase 0, falls to `0.5` at the
 * quarter cycle, reaches `0.0` at the half cycle, and rises back to `1.0` at cycle end.
 * It is built that way, so `sine` is the one source of the wave and the two never drift apart.
 * Use [range] to map it to a target range, or call it: `cosine(200, 2000)` is `cosine.range(200, 2000)`.
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
@KlangScript.Library("sprudel")
@KlangScript.Object("cosine")
object cosine : SprudelSignal("cosine", sine.early(0.25)) {

    /**
     * The shorthand for [range]: `cosine(from, to)` is exactly `cosine.range(from, to)`.
     *
     * Both values are required; `cosine` alone is the signal itself, swinging between `0` and `1`.
     *
     * ```KlangScript(Playable)
     * note("c3*8").s("saw").pan(cosine(0.2, 0.8).slow(2))  // pans between left and right
     * ```
     *
     * @param from Required. The value where the signal is at its low end.
     * @param to Required. The value where the signal is at its high end.
     * @return The signal swinging between [from] and [to].
     */
    @KlangScript.Invoke
    operator fun invoke(from: Number? = null, to: Number? = null, callInfo: CallInfo? = null): SprudelPattern =
        ranged(from, to, callInfo)
}

// -- saw --------------------------------------------------------------------------------------------------------------

private val sawBase: SprudelPattern by lazy { applySignal { t -> t % 1.0 } }

/**
 * Sawtooth oscillator: unscaled continuous values in `0..1`.
 *
 * Resets to `0.0` at the start of each cycle and rises linearly to `1.0` by the end.
 * Use [range] to map it to a target range, or call it: `saw(200, 2000)` is `saw.range(200, 2000)`.
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
@KlangScript.Library("sprudel")
@KlangScript.Object("saw")
object saw : SprudelSignal("saw", sawBase) {

    /**
     * The shorthand for [range]: `saw(from, to)` is exactly `saw.range(from, to)`.
     *
     * Both values are required; `saw` alone is the signal itself, swinging between `0` and `1`.
     *
     * ```KlangScript(Playable)
     * note("c3*8").s("saw").lpf(saw(300, 3000))  // the filter opens across every cycle
     * ```
     *
     * @param from Required. The value where the signal is at its low end.
     * @param to Required. The value where the signal is at its high end.
     * @return The signal swinging between [from] and [to].
     */
    @KlangScript.Invoke
    operator fun invoke(from: Number? = null, to: Number? = null, callInfo: CallInfo? = null): SprudelPattern =
        ranged(from, to, callInfo)
}

// -- tri --------------------------------------------------------------------------------------------------------------

private val triBase: SprudelPattern by lazy {
    applySignal { t ->
        val phase = t % 1.0
        if (phase < 0.5) phase * 2.0 else 2.0 - (phase * 2.0)
    }
}

/**
 * Triangle oscillator: unscaled continuous values in `0..1`.
 *
 * Rises linearly `0→1` in the first half of the cycle then falls `1→0` in the second half.
 * Use [range] to map it to a target range, or call it: `tri(200, 2000)` is `tri.range(200, 2000)`.
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
@KlangScript.Library("sprudel")
@KlangScript.Object("tri")
object tri : SprudelSignal("tri", triBase) {

    /**
     * The shorthand for [range]: `tri(from, to)` is exactly `tri.range(from, to)`.
     *
     * Both values are required; `tri` alone is the signal itself, swinging between `0` and `1`.
     *
     * ```KlangScript(Playable)
     * note("c3*8").s("saw").lpf(tri(400, 2400).slow(2))  // the filter rises and falls evenly
     * ```
     *
     * @param from Required. The value where the signal is at its low end.
     * @param to Required. The value where the signal is at its high end.
     * @return The signal swinging between [from] and [to].
     */
    @KlangScript.Invoke
    operator fun invoke(from: Number? = null, to: Number? = null, callInfo: CallInfo? = null): SprudelPattern =
        ranged(from, to, callInfo)
}

// -- square -----------------------------------------------------------------------------------------------------------

private val squareBase: SprudelPattern by lazy { applySignal { t -> if (t % 1.0 < 0.5) 0.0 else 1.0 } }

/**
 * Square oscillator: unscaled continuous values alternating `0` / `1`.
 *
 * Produces `0.0` for the first half of each cycle and `1.0` for the second half (50 % duty cycle).
 * Use [range] to map those two levels to any pair of values, or call it: `square(200, 800)` is
 * `square.range(200, 800)`.
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
@KlangScript.Library("sprudel")
@KlangScript.Object("square")
object square : SprudelSignal("square", squareBase) {

    /**
     * The shorthand for [range]: `square(from, to)` is exactly `square.range(from, to)`.
     *
     * Both values are required; `square` alone is the signal itself, swinging between `0` and `1`.
     *
     * ```KlangScript(Playable)
     * note("c3*8").s("saw").lpf(square(500, 3000))  // dark for half a cycle, bright for the other half
     * ```
     *
     * @param from Required. The value where the signal is at its low end.
     * @param to Required. The value where the signal is at its high end.
     * @return The signal swinging between [from] and [to].
     */
    @KlangScript.Invoke
    operator fun invoke(from: Number? = null, to: Number? = null, callInfo: CallInfo? = null): SprudelPattern =
        ranged(from, to, callInfo)
}

// -- perlin -----------------------------------------------------------------------------------------------------------

private fun createPerlin(): SprudelPattern {
    val cache = mutableMapOf<Int?, PerlinNoise>()

    return ContinuousPattern { from, _, ctx ->
        val seedKey = ctx.getOrNull(QueryContext.randomSeedKey)
        val engine = cache.getOrPut(seedKey) { PerlinNoise(ctx.getSeededRandom("perlin")) }
        (engine.noise(from) + 1.0) / 2.0
    }
}

/**
 * Continuous Perlin noise: smoothly varying unscaled values in `0..1`.
 *
 * Each instantiation is seeded independently, producing different-but-deterministic smooth curves.
 * Perlin noise transitions gradually between values, making it ideal for organic modulation.
 * Use [range] to map it to a target range, or call it: `perlin(200, 2000)` is `perlin.range(200, 2000)`.
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
@KlangScript.Library("sprudel")
@KlangScript.Object("perlin")
object perlin : SprudelSignal("perlin", createPerlin()) {

    /**
     * The shorthand for [range]: `perlin(from, to)` is exactly `perlin.range(from, to)`.
     *
     * Both values are required; `perlin` alone is the signal itself, swinging between `0` and `1`.
     *
     * ```KlangScript(Playable)
     * note("c3*8").s("saw").lpf(perlin(400, 2000).slow(8))  // the filter wanders, smoothly
     * ```
     *
     * @param from Required. The value where the signal is at its low end.
     * @param to Required. The value where the signal is at its high end.
     * @return The signal swinging between [from] and [to].
     */
    @KlangScript.Invoke
    operator fun invoke(from: Number? = null, to: Number? = null, callInfo: CallInfo? = null): SprudelPattern =
        ranged(from, to, callInfo)
}

// -- berlin -----------------------------------------------------------------------------------------------------------

private fun createBerlin(): SprudelPattern {
    val cache = mutableMapOf<Int?, BerlinNoise>()

    return ContinuousPattern { from, _, ctx ->
        val seedKey = ctx.getOrNull(QueryContext.randomSeedKey)
        val engine = cache.getOrPut(seedKey) { BerlinNoise(ctx.getSeededRandom("Berlin")) }
        engine.noise(from)
    }
}

/**
 * Continuous Berlin noise: sawtooth-textured unscaled values in `0..1`.
 *
 * Like [perlin] but built from sawtooth waves, giving a harsher, more angular quality.
 * The sawtooth construction makes it a characterful, surprisingly musical modulation source.
 * Use [range] to map it to a target range, or call it: `berlin(200, 2000)` is `berlin.range(200, 2000)`.
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
@KlangScript.Library("sprudel")
@KlangScript.Object("berlin")
object berlin : SprudelSignal("berlin", createBerlin()) {

    /**
     * The shorthand for [range]: `berlin(from, to)` is exactly `berlin.range(from, to)`.
     *
     * Both values are required; `berlin` alone is the signal itself, swinging between `0` and `1`.
     *
     * ```KlangScript(Playable)
     * note("c3*8").s("saw").lpf(berlin(400, 2000).slow(4))  // the filter wanders in rough steps
     * ```
     *
     * @param from Required. The value where the signal is at its low end.
     * @param to Required. The value where the signal is at its high end.
     * @return The signal swinging between [from] and [to].
     */
    @KlangScript.Invoke
    operator fun invoke(from: Number? = null, to: Number? = null, callInfo: CallInfo? = null): SprudelPattern =
        ranged(from, to, callInfo)
}
