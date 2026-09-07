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
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.SoloPattern
import io.peekandpoke.klang.sprudel.pattern.StructurePattern

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Structural patterns
// ///

// -- hush() / bypass() / mute() --------------------------------------------------------------------------------------

/**
 * Applies hush/mute with control pattern support.
 * Returns silence when the condition is true.
 * When called without arguments, unconditionally returns silence.
 */
private fun applyHush(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // No arguments: unconditionally mute
    if (args.isEmpty()) return silence

    // With control pattern: mute when condition is true
    val condition = args.toPattern()
    val conditionNot = condition.not()

    return StructurePattern(
        source = source,
        other = conditionNot,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Silences this pattern. Without arguments, unconditionally returns silence.
 * With a condition, silences when the condition is truthy — supports control patterns.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript(Playable)
 * s("bd sd").hush()          // Unconditional silence
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").hush("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias bypass, mute
 * @category structural
 * @tags silence, mute, control
 */
@KlangScript.Function
fun SprudelPattern.hush(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyHush(this, args.toList().asSprudelDslArgs(callInfo))

/** Silences this string pattern. Without arguments, unconditionally returns silence. */
@KlangScript.Function
fun String.hush(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hush(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that silences the source pattern.
 *
 * Without arguments, unconditionally silences. With a condition, silences when truthy.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(hush())          // Unconditional silence via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(hush("<1 0>"))   // Silent on odd cycles via mapper
 * ```
 *
 * @alias bypass, mute
 * @category structural
 * @tags silence, mute, control
 */
@KlangScript.Function
fun hush(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.hush(*args, callInfo = callInfo) }

/** Chains a hush onto this [PatternMapperFn]; silences or conditionally gates the result. */
@KlangScript.Function
fun PatternMapperFn.hush(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hush(*args, callInfo = callInfo) }

/**
 * Alias for [hush]. Silences this pattern. Without arguments, unconditionally returns silence.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript(Playable)
 * s("bd sd").bypass()          // Unconditional silence
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").bypass("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias hush, mute
 * @category structural
 * @tags silence, mute, bypass, control
 */
@KlangScript.Function
fun SprudelPattern.bypass(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.hush(*args, callInfo = callInfo)

/** Alias for [hush] on a string pattern. Without arguments, unconditionally returns silence. */
@KlangScript.Function
fun String.bypass(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bypass(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] — alias for [hush] — that silences the source pattern.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(bypass())   // Unconditional silence via mapper
 * ```
 *
 * @alias hush, mute
 * @category structural
 * @tags silence, mute, bypass, control
 */
@KlangScript.Function
fun bypass(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bypass(*args, callInfo = callInfo) }

/** Chains a bypass (alias for [hush]) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bypass(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bypass(*args, callInfo = callInfo) }

/**
 * Alias for [hush]. Silences this pattern. Without arguments, unconditionally returns silence.
 *
 * @param args Optional condition pattern. When truthy the pattern is silenced.
 * @return Silence, or the original pattern gated by the condition.
 *
 * ```KlangScript(Playable)
 * s("bd sd").mute()          // Unconditional silence
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").mute("<1 0>")   // Silent on odd cycles, audible on even
 * ```
 *
 * @alias hush, bypass
 * @category structural
 * @tags silence, mute, control
 */
@KlangScript.Function
fun SprudelPattern.mute(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.hush(*args, callInfo = callInfo)

/** Alias for [hush] on a string pattern. Without arguments, unconditionally returns silence. */
@KlangScript.Function
fun String.mute(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).mute(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] — alias for [hush] — that silences the source pattern.
 *
 * @param args Optional condition pattern. When truthy the source pattern is silenced.
 * @return A [PatternMapperFn] that silences or conditionally gates the source pattern.
 *
 * ```KlangScript(Playable)
 * s("bd sd").apply(mute())   // Unconditional silence via mapper
 * ```
 *
 * @alias hush, bypass
 * @category structural
 * @tags silence, mute, control
 */
@KlangScript.Function
fun mute(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.mute(*args, callInfo = callInfo) }

/** Chains a mute (alias for [hush]) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.mute(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.mute(*args, callInfo = callInfo) }

// -- solo() -----------------------------------------------------------------------------------------------------------

private fun applySolo(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(SprudelDslArg.of(0.97)) }
    val soloControl = effectiveArgs.first().toPattern()
    return SoloPattern(source = source, soloControl = soloControl)
}

/**
 * Solos this pattern, muting all non-soloed patterns during playback.
 *
 * Pass a value between `0.0` (no solo) and `1.0` (full solo). Omit or pass `null` to use
 * the default amount of `0.97`. Accepts control patterns for per-cycle dynamic toggling.
 *
 * ```KlangScript(Playable)
 * stack(
 *   s("bd*4").solo(),             // only the kick is heard (amount = 0.97)
 *   note("c3 e3")                 // muted because another pattern is soloed
 * )
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").solo(1)              // full solo
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").solo(0.5)            // half solo amount
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").solo("<1 0>")         // toggle solo on/off every other cycle
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 *
 * @category structural
 * @tags solo, mute, isolate, playback
 */
@KlangScript.Function
fun SprudelPattern.solo(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySolo(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and solos it, muting all non-soloed patterns.
 *
 * ```KlangScript(Playable)
 * "bd sd".solo().s()              // solo this string pattern; everything else is muted
 * ```
 *
 * ```KlangScript(Playable)
 * "bd sd".solo("<1 0>").s()       // toggle solo on/off every other cycle
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 */
@KlangScript.Function
fun String.solo(amount: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).solo(amount, callInfo)

/**
 * Creates a [PatternMapperFn] that solos the input pattern, muting all non-soloed patterns.
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(solo())         // solo the kick via a mapper (amount = 0.97)
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").apply(solo("<1 0>"))  // toggle solo on/off every other cycle via a mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(timeLoop(2).solo())   // loop then solo
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 *
 * @category structural
 * @tags solo, mute, isolate, playback
 */
@KlangScript.Function
fun solo(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.solo(amount, callInfo) }

/**
 * Chains a solo operation onto this [PatternMapperFn].
 *
 * ```KlangScript(Playable)
 * s("bd*4").apply(timeLoop(2).solo())              // loop first 2 cycles, then solo
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*8").apply(timeLoop(1).solo("<1 0>"))       // loop then conditionally solo
 * ```
 *
 * @param amount `0.0`..`1.0` solo strength; `null` defaults to `0.97`. Accepts control patterns.
 */
@KlangScript.Function
fun PatternMapperFn.solo(amount: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.solo(amount, callInfo) }

