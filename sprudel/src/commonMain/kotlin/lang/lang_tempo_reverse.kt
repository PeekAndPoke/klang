/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._withHapSpan
import io.peekandpoke.klang.sprudel._withQuerySpan
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ControlValueProvider
import io.peekandpoke.klang.sprudel.pattern.ReversePattern

// -- rev() ------------------------------------------------------------------------------------------------------------

private fun applyRev(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(1.0.asVoiceValue())

    return ReversePattern(inner = pattern, nProvider = nProvider)
}

/**
 * Reverses the order of events within each cycle (or across `n` cycles when given an argument).
 *
 * With no argument, each individual cycle plays its events in reverse order. With an integer `n`,
 * the reversal is applied across every `n`-cycle span — useful for longer retrograde effects.
 * Accepts control patterns for the cycle count.
 *
 * @param n Number of cycles to reverse across. Default: 1 (reverse within each cycle). Typical range: 1–8.
 * @return A pattern with events reversed per cycle (or per `n`-cycle group).
 *
 * ```KlangScript(Playable)
 * s("bd hh sd hh hh cp").rev()              // reversed each cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("<[c d] [e f]>").rev(2)              // reverses across every 2-cycle span
 * ```
 *
 * @category tempo
 * @tags rev, reverse, order, retrograde
 */
@KlangScript.Function
fun SprudelPattern.rev(n: PatternLike = 1, callInfo: CallInfo? = null): SprudelPattern =
    applyRev(this, listOf(n).asSprudelDslArgs(callInfo))

/** Reverses the order of events, applying the reversal across every `n`-cycle span. */
@KlangScript.Function
fun String.rev(n: PatternLike = 1, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rev(n, callInfo)

/**
 * Returns a [PatternMapperFn] that reverses the order of events across every `n`-cycle span.
 *
 * @param n Number of cycles to reverse across. Default: 1. Typical range: 1–8.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd cp").apply(rev())             // mapper form
 * ```
 *
 * @category tempo
 * @tags rev, reverse, order, retrograde
 */
@KlangScript.Function
fun rev(n: PatternLike = 1, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rev(n, callInfo) }

/** Chains a rev operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.rev(n: PatternLike = 1, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.rev(n, callInfo) }

// -- revv() -----------------------------------------------------------------------------------------------------------

// TODO: deep checks ... does not seem to work to well for complex patterns
private fun applyRevv(pattern: SprudelPattern): SprudelPattern {
    // Negates a time span: [begin, end] → [-end, -begin]
    val negateSpan: (CycleTimeSpan) -> CycleTimeSpan = { span ->
        CycleTimeSpan(
            begin = -span.end,
            end = -span.begin
        )
    }

    // Transform both query spans and event spans
    return pattern._withQuerySpan(negateSpan)._withHapSpan(negateSpan)
}

/**
 * Reverses the pattern in absolute time across all cycles.
 *
 * Unlike `rev()` which reverses each cycle independently, `revv()` negates the time axis
 * globally: cycle N becomes cycle -N, so a long phrase is played completely backwards in
 * absolute time. Useful for true retrograde playback of multi-cycle phrases.
 *
 * @return A globally time-reversed version of the pattern.
 *
 * ```KlangScript(Playable)
 * note("c d e f").revv()              // plays f e d c in absolute negative time direction
 * ```
 *
 * @category tempo
 * @tags revv, reverse, retrograde, time, global
 */
@KlangScript.Function
@Suppress("UNUSED_PARAMETER")
fun SprudelPattern.revv(callInfo: CallInfo? = null): SprudelPattern = applyRevv(this)

/** Reverses the pattern in absolute time across all cycles. */
@KlangScript.Function
fun String.revv(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).revv(callInfo)

/**
 * Returns a [PatternMapperFn] that reverses the pattern in absolute time.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(revv())       // mapper form
 * ```
 *
 * @category tempo
 * @tags revv, reverse, retrograde, time, global
 */
@KlangScript.Function
fun revv(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.revv(callInfo) }

/** Chains a revv operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.revv(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.revv(callInfo) }

// -- palindrome() -----------------------------------------------------------------------------------------------------

private fun applyPalindrome(pattern: SprudelPattern): SprudelPattern {
    // Palindrome needs to play the pattern forward, then backward.
    // Critically, it must use ABSOLUTE time (Prime behavior) so that the 'rev' version
    // reverses the content of the *second* cycle, not the first cycle played again.
    // e.g. lastOf(2, rev): alternate the plain and transformed pattern per cycle (via slowcatPrime).
    return applySlowcatPrime(listOf(pattern, applyRev(pattern, listOf(SprudelDslArg(1, null)))))
}

/**
 * Plays the pattern forward then backward, creating a two-cycle palindrome.
 *
 * Cycle 0 plays the original pattern; cycle 1 plays the reversed pattern. The reversal
 * uses absolute time so the second half mirrors the first half correctly over the full
 * phrase, not just a single cycle.
 *
 * @return A two-cycle palindrome pattern.
 *
 * ```KlangScript(Playable)
 * note("c d e f").palindrome()        // c d e f ... f e d c ... c d e f ...
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").palindrome()       // forward drum loop then reversed drum loop
 * ```
 *
 * @category tempo
 * @tags palindrome, reverse, mirror, order, retrograde
 */
@KlangScript.Function
@Suppress("UNUSED_PARAMETER")
fun SprudelPattern.palindrome(callInfo: CallInfo? = null): SprudelPattern = applyPalindrome(this)

/** Plays the pattern forward then backward, creating a two-cycle palindrome. */
@KlangScript.Function
fun String.palindrome(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).palindrome(callInfo)

/**
 * Returns a [PatternMapperFn] that plays the pattern forward then backward.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(palindrome())  // mapper form
 * ```
 *
 * @category tempo
 * @tags palindrome, reverse, mirror, order, retrograde
 */
@KlangScript.Function
fun palindrome(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.palindrome(callInfo) }

/** Chains a palindrome operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.palindrome(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.palindrome(callInfo) }

// -- brak() -----------------------------------------------------------------------------------------------------------

/**
 * Makes every other cycle syncopated (breakbeat-style).
 *
 * Effect:
 * - Cycle 0: plays normally
 * - Cycle 1: plays first half then silence, delayed by 0.25 cycles (syncopation)
 * - Cycle 2: plays normally
 * - Cycle 3: syncopated
 * - etc.
 */
private fun applyBrak(pattern: SprudelPattern): SprudelPattern {
    // Every other cycle: squeeze the events into the first half and delay them by a quarter cycle.
    val condition = applyCat(
        listOf(
            pure(false),
            pure(true)
        )
    )

    return pattern.`when`(condition, { x ->
        fastcat(x, silence).late(0.25)
    })
}

/**
 * Makes every other cycle syncopated — a classic breakbeat effect.
 *
 * Cycle 0, 2, 4, … play normally. Cycle 1, 3, 5, … compress the pattern into the first half
 * and delay it by a quarter cycle, creating an off-beat syncopation reminiscent of amen
 * break-style patterns.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").brak()             // alternating straight and syncopated cycles
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").brak()              // melodic pattern with every-other-cycle offset
 * ```
 *
 * @category tempo
 * @tags brak, breakbeat, syncopation, rhythm, offset, amen
 */
@KlangScript.Function
@Suppress("UNUSED_PARAMETER")
fun SprudelPattern.brak(callInfo: CallInfo? = null): SprudelPattern = applyBrak(this)

/** Makes every other cycle syncopated — a classic breakbeat effect. */
@KlangScript.Function
fun String.brak(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).brak(callInfo)

/** Returns a [PatternMapperFn] that makes every other cycle syncopated. */
@KlangScript.Function
fun brak(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.brak(callInfo) }

/** Chains a brak operation onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.brak(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.brak(callInfo) }
