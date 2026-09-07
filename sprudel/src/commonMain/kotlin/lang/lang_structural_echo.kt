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
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import kotlin.math.pow

// -- echo() / stut() --------------------------------------------------------------------------------------------------

private fun applyEcho(source: SprudelPattern, times: Int, delay: Double, decay: Double): SprudelPattern {
    if (times < 1) return silence
    if (times == 1) return source // Only original, no echoes

    // Create layers: original + echoes
    val layers = (0 until times).map { i ->
        if (i == 0) {
            source // Original (no delay, no gain change)
        } else {
            // Delayed and decayed echo
            val gainMultiplier = decay.pow(i)
            source.late(delay * i).gain(gainMultiplier)
        }
    }

    return StackPattern(layers)
}

/**
 * Superimposes delayed and decayed copies of the pattern, creating an echo effect.
 *
 * Each copy is delayed by `delay × copy_number` cycles and its gain reduced by `decay ^ copy_number`.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * s("bd sd").echo(3, 0.125, 0.7)  // original + 2 echoes, 0.125 cycles apart
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").echo(4, 0.25, 0.5)  // 4 layers, quarter-cycle spacing, halving gain
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@KlangScript.Function
fun SprudelPattern.echo(times: Int, delay: Double, decay: Double, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyEcho(this, times, delay, decay)

/**
 * Like [echo] applied to a mini-notation string.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * "bd sd".echo(3, 0.125, 0.7).s()  // original + 2 echoes, 0.125 cycles apart
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@KlangScript.Function
fun String.echo(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).echo(times, delay, decay, callInfo)

/**
 * Returns a [PatternMapperFn] that superimposes delayed and decayed copies of the source pattern.
 *
 * @param times Number of layers including the original (must be ≥ 1).
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier applied to each successive echo (0.0–1.0).
 * @return A [PatternMapperFn] that applies the echo effect to the source.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(echo(3, 0.125, 0.7))  // via mapper
 * ```
 *
 * @alias stut
 * @category structural
 * @tags echo, stut, delay, decay, reverb, effect
 */
@KlangScript.Function
fun echo(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.echo(times, delay, decay, callInfo) }

/** Chains an echo onto this [PatternMapperFn]; superimposes delayed and decayed copies of the result. */
@KlangScript.Function
fun PatternMapperFn.echo(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.echo(times, delay, decay, callInfo) }

/**
 * Alias for [echo] — superimposes delayed and decayed copies of the pattern.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * n("0").stut(4, 0.5, 0.5)  // 4 echoes, half-cycle spacing, halving gain
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").stut(3, 0.125, 0.8)  // hi-hat with 2 trailing echoes
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@KlangScript.Function
fun SprudelPattern.stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): SprudelPattern =
    this.echo(times, delay, decay, callInfo)

/**
 * Alias for [echo] applied to a mini-notation string.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A stacked pattern of the original plus decayed, delayed echoes.
 *
 * ```KlangScript(Playable)
 * "hh".stut(3, 0.125, 0.8).s()  // hi-hat with 2 trailing echoes
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@KlangScript.Function
fun String.stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stut(times, delay, decay, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [echo] — superimposes delayed and decayed copies.
 *
 * @param times Number of layers including the original.
 * @param delay Time offset per echo in cycles.
 * @param decay Gain multiplier per echo (0.0–1.0).
 * @return A [PatternMapperFn] that applies the echo effect to the source.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(stut(3, 0.125, 0.8))  // via mapper
 * ```
 *
 * @alias echo
 * @category structural
 * @tags stut, echo, delay, decay, reverb, effect
 */
@KlangScript.Function
fun stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.stut(times, delay, decay, callInfo) }

/** Chains a stut onto this [PatternMapperFn]; alias for [PatternMapperFn.echo]. */
@KlangScript.Function
fun PatternMapperFn.stut(times: Int, delay: Double, decay: Double, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.stut(times, delay, decay, callInfo) }

// -- echoWith() / stutWith() ------------------------------------------------------------------------------------------

private fun applyEchoWith(source: SprudelPattern, times: Int, delay: Double, transform: PatternMapperFn): SprudelPattern {
    if (times <= 0) return silence
    if (times == 1) return source // Only original, no additional layers

    // Build layers with cumulative transformation
    val layers = mutableListOf(source) // Layer 0: original
    var current = source

    repeat(times - 1) { i ->
        // Apply transform cumulatively
        current = transform(current)
        // Delay this layer
        layers.add(current.late(delay * (i + 1)))
    }

    return StackPattern(layers)
}

/**
 * Superimposes versions of the pattern with a transform applied cumulatively to each layer.
 *
 * Unlike [echo], which simply decays gain, each layer receives the transform applied once more than
 * the previous layer, creating a compounding effect.
 *
 * @param times     Number of layers including the original (must be ≥ 1).
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").echoWith(4, 0.125, x => x.add(2))  // layers at n=0,2,4,6, each 0.125 apart
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").echoWith(3, 0.25, x => x.fast(2))  // original + 2× and 4× faster copies
 * ```
 * @alias stutWith, stutwith, echowith
 * @category structural
 * @tags echoWith, stutWith, delay, transform, layers, effect
 */
@KlangScript.Function
fun SprudelPattern.echoWith(
    times: Int,
    delay: Double,
    transform: PatternMapperFn,
    @Suppress("unused") callInfo: CallInfo? = null
): SprudelPattern =
    applyEchoWith(this, times, delay, transform)

/** Like [echoWith] applied to a mini-notation string. */
@KlangScript.Function
fun String.echoWith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).echoWith(times, delay, transform, callInfo)

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").stutWith(4, 0.125, x => x.add(2))  // stut alias with additive transform
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh").stutWith(3, 0.25, x => x.gain(0.7))  // quieter copies
 * ```
 * @alias echoWith, stutwith, echowith
 * @category structural
 * @tags stutWith, echoWith, delay, transform, layers, effect
 */
@KlangScript.Function
fun SprudelPattern.stutWith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.echoWith(times, delay, transform, callInfo)

/** Alias for [echoWith]. */
@KlangScript.Function
fun String.stutWith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stutWith(times, delay, transform, callInfo)

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").stutwith(4, 0.125, x => x.add(2))  // lowercase alias
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd").stutwith(3, 0.25, x => x.speed(1.5))  // each copy 50% faster
 * ```
 * @alias echoWith, stutWith, echowith
 * @category structural
 * @tags stutwith, echoWith, stutWith, delay, transform, layers
 */
@KlangScript.Function
fun SprudelPattern.stutwith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.echoWith(times, delay, transform, callInfo)

/** Alias for [echoWith]. */
@KlangScript.Function
fun String.stutwith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).stutwith(times, delay, transform, callInfo)

/**
 * Alias for [echoWith] — superimposes cumulatively-transformed layers.
 *
 * @param times     Number of layers including the original.
 * @param delay     Time offset per layer in cycles.
 * @param transform Function applied cumulatively to each successive layer.
 * @return A stacked pattern where each layer has the transform applied one more time.
 *
 * ```KlangScript(Playable)
 * n("0").echowith(4, 0.125, x => x.add(2))  // lowercase alias
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").echowith(3, 0.5, x => x.rev())  // each copy reversed
 * ```
 * @alias echoWith, stutWith, stutwith
 * @category structural
 * @tags echowith, echoWith, stutWith, delay, transform, layers
 */
@KlangScript.Function
fun SprudelPattern.echowith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.echoWith(times, delay, transform, callInfo)

/** Alias for [echoWith]. */
@KlangScript.Function
fun String.echowith(times: Int, delay: Double, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).echowith(times, delay, transform, callInfo)

