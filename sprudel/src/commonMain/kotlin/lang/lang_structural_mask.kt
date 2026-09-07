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
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.map
import io.peekandpoke.klang.sprudel.pattern.StructurePattern

// -- struct() ---------------------------------------------------------------------------------------------------------

private fun applyStruct(source: SprudelPattern, structArg: SprudelDslArg<Any?>?): SprudelPattern {
    val structure = structArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = true
    )
}

/**
 * Restructures this pattern using the timing of a mask pattern, keeping only truthy mask events.
 *
 * The mask provides the rhythmic structure; the source pattern provides the values. Only source
 * events that overlap with truthy events in the mask are kept, clipped to the mask's timing.
 *
 * @param mask Pattern whose truthy events define the new rhythmic structure.
 * @return The source pattern reshaped to the mask's rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").struct("x ~ x x ~ x x ~")  // hats shaped by a boolean rhythm pattern
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e g").struct("x*4")         // chord hits restructured to 4 equal beats
 * ```
 *
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@KlangScript.Function
fun SprudelPattern.struct(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStruct(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Restructures this string pattern using the mask's timing; keeps only truthy mask events. */
@KlangScript.Function
fun String.struct(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).struct(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that restructures the source pattern using the mask's timing.
 *
 * @param mask Pattern whose truthy events define the new rhythmic structure.
 * @return A [PatternMapperFn] that reshapes the source pattern to the mask's rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(struct("x ~ x x"))    // via mapper
 * ```
 *
 * @category structural
 * @tags struct, mask, rhythm, structure, timing
 */
@KlangScript.Function
fun struct(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.struct(*args, callInfo = callInfo) }

/** Chains a struct onto this [PatternMapperFn]; restructures using the mask's timing. */
@KlangScript.Function
fun PatternMapperFn.struct(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.struct(*args, callInfo = callInfo) }

// -- structAll() ------------------------------------------------------------------------------------------------------

private fun applyStructAll(source: SprudelPattern, structArg: SprudelDslArg<Any?>?): SprudelPattern {
    val structure = structArg?.toPattern() ?: return silence

    // We use a different implementation for structAll that preserves all source events
    return StructurePattern(
        source = source,
        other = structure,
        mode = StructurePattern.Mode.Out,
        filterByTruthiness = false
    )
}

/**
 * Like [struct], but keeps all source events overlapping the mask — including falsy ones.
 *
 * While [struct] filters to only truthy mask events, `structAll` uses the mask purely for timing
 * without filtering by value. Useful when the mask defines structure but all events should pass.
 *
 * @param mask Pattern that defines the rhythmic structure.
 * @return The source pattern reshaped to the mask's rhythm, with all overlapping events kept.
 *
 * ```KlangScript(Playable)
 * note("c e g").structAll("x x")   // all chord notes kept within each x window
 * ```
 *
 * ```KlangScript(Playable)
 * note("c e").structAll("x x x x") // both c and e kept within each mask window
 * ```
 *
 * @category structural
 * @tags structAll, struct, mask, rhythm, structure, timing
 */
@KlangScript.Function
fun SprudelPattern.structAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyStructAll(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Like [structAll] on a string pattern; keeps all events overlapping the mask. */
@KlangScript.Function
fun String.structAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).structAll(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that reshapes the source using the mask, keeping all overlapping events.
 *
 * @param mask Pattern that defines the rhythmic structure.
 * @return A [PatternMapperFn] that reshapes the source keeping all overlapping events.
 *
 * ```KlangScript(Playable)
 * note("c e g").apply(structAll("x x"))   // via mapper
 * ```
 *
 * @category structural
 * @tags structAll, struct, mask, rhythm, structure, timing
 */
@KlangScript.Function
fun structAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.structAll(*args, callInfo = callInfo) }

/** Chains a structAll onto this [PatternMapperFn]; reshapes keeping all overlapping events. */
@KlangScript.Function
fun PatternMapperFn.structAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.structAll(*args, callInfo = callInfo) }

// -- mask() -----------------------------------------------------------------------------------------------------------

private fun applyMask(source: SprudelPattern, maskArg: SprudelDslArg<Any?>?): SprudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = true
    )
}

/**
 * Filters this pattern using a boolean mask, keeping events that overlap truthy mask events.
 *
 * Unlike [struct], which uses the mask's timing for structure, `mask` keeps the source pattern's
 * original timing and gates out events where the mask is falsy.
 *
 * @param mask Boolean pattern — truthy events let the source through, falsy events silence it.
 * @return The source pattern gated by the mask.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").mask("1 0 1 1")  // second beat silenced by the mask
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").mask("<1 0>")      // entire pattern alternates on/off each cycle
 * ```
 *
 * @category structural
 * @tags mask, gate, filter, rhythm, boolean
 */
@KlangScript.Function
fun SprudelPattern.mask(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMask(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Filters this string pattern using a boolean mask; truthy events pass, falsy events are silenced. */
@KlangScript.Function
fun String.mask(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).mask(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that filters the source using a boolean mask.
 *
 * @param mask Boolean pattern — truthy events let the source through, falsy events silence it.
 * @return A [PatternMapperFn] that gates the source pattern by the mask.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(mask("1 0 1 1"))  // via mapper
 * ```
 *
 * @category structural
 * @tags mask, gate, filter, rhythm, boolean
 */
@KlangScript.Function
fun mask(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.mask(*args, callInfo = callInfo) }

/** Chains a mask onto this [PatternMapperFn]; gates the result by the boolean mask. */
@KlangScript.Function
fun PatternMapperFn.mask(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.mask(*args, callInfo = callInfo) }

// -- maskAll() --------------------------------------------------------------------------------------------------------

private fun applyMaskAll(source: SprudelPattern, maskArg: SprudelDslArg<Any?>?): SprudelPattern {
    val maskPattern = maskArg?.toPattern() ?: return silence

    return StructurePattern(
        source = source,
        other = maskPattern,
        mode = StructurePattern.Mode.In,
        filterByTruthiness = false
    )
}

/**
 * Like [mask], but keeps all source events overlapping the mask structure regardless of truthiness.
 *
 * @param mask Pattern that defines the gating structure (all values allowed, not just truthy).
 * @return The source pattern gated by the mask's structure, with all overlapping events kept.
 *
 * ```KlangScript(Playable)
 * note("c d e f").maskAll("x ~ x ~")  // every other beat silenced by structure
 * ```
 *
 * @category structural
 * @tags maskAll, mask, gate, filter, rhythm
 */
@KlangScript.Function
fun SprudelPattern.maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyMaskAll(this, args.toList().asSprudelDslArgs(callInfo).firstOrNull())

/** Like [maskAll] on a string pattern; gates by mask structure, all values pass. */
@KlangScript.Function
fun String.maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).maskAll(*args, callInfo = callInfo)

/**
 * Returns a [PatternMapperFn] that gates the source using the mask's structure (all values pass).
 *
 * @param mask Pattern that defines the gating structure.
 * @return A [PatternMapperFn] that gates the source, keeping all overlapping events.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(maskAll("x ~ x ~"))  // via mapper
 * ```
 *
 * @category structural
 * @tags maskAll, mask, gate, filter, rhythm
 */
@KlangScript.Function
fun maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.maskAll(*args, callInfo = callInfo) }

/** Chains a maskAll onto this [PatternMapperFn]; gates the result keeping all overlapping events. */
@KlangScript.Function
fun PatternMapperFn.maskAll(vararg args: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.maskAll(*args, callInfo = callInfo) }

// -- filter() ---------------------------------------------------------------------------------------------------------

private fun applyFilter(source: SprudelPattern, predicate: (SprudelPatternEvent) -> Boolean): SprudelPattern {
    return source.map { events -> events.filter(predicate) }
}

/**
 * Filters events from this pattern using a predicate function.
 *
 * Only events for which [predicate] returns `true` are kept.
 *
 * @param predicate Function that receives a [SprudelPatternEvent] and returns `true` to keep it.
 * @return A pattern containing only the events that satisfy the predicate.
 *
 * No KlangScript example: an event's properties are not reachable from script today, so a
 * predicate written there has nothing to test. For time-based filtering use `filterWhen`,
 * which takes the begin time as a plain number. See docs/tasks/sprudel-function-testing.md.
 *
 * @category structural
 * @tags filter, gate, conditional, predicate
 */
@KlangScript.Function
fun SprudelPattern.filter(predicate: (SprudelPatternEvent) -> Boolean, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyFilter(this, predicate)

/** Filters events from this string pattern using a predicate function. */
@KlangScript.Function
fun String.filter(predicate: (SprudelPatternEvent) -> Boolean, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).filter(predicate, callInfo)

/**
 * Returns a [PatternMapperFn] that filters events from the source using a predicate.
 *
 * @param predicate Function that receives a [SprudelPatternEvent] and returns `true` to keep it.
 * @return A [PatternMapperFn] that keeps only events satisfying the predicate.
 *
 * No KlangScript example, for the same reason as [SprudelPattern.filter]: use `filterWhen`.
 *
 * @category structural
 * @tags filter, gate, conditional, predicate
 */
@KlangScript.Function
fun filter(predicate: (SprudelPatternEvent) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.filter(predicate, callInfo) }

/** Chains a filter onto this [PatternMapperFn]; keeps only events satisfying the predicate. */
@KlangScript.Function
fun PatternMapperFn.filter(predicate: (SprudelPatternEvent) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.filter(predicate, callInfo) }

// -- filterWhen() -----------------------------------------------------------------------------------------------------

/**
 * Filters events from this pattern based on their begin time.
 *
 * Only events whose `part.begin` (as `Double`) satisfies [predicate] are kept.
 *
 * @param predicate Function that receives the begin time as a `Double`; returns `true` to keep the event.
 * @return A pattern with only the events whose begin time satisfies the predicate.
 *
 * ```KlangScript(Playable)
 * note("c d e f").filterWhen(t => t < 0.5)       // keep only first-half events
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").filterWhen(t => t % 0.25 == 0) // keep only events on beat boundaries
 * ```
 *
 * @category structural
 * @tags filterWhen, filter, time, conditional, predicate
 */
@KlangScript.Function
fun SprudelPattern.filterWhen(predicate: (Double) -> Boolean, @Suppress("unused") callInfo: CallInfo? = null): SprudelPattern =
    applyFilter(this) { predicate(it.part.begin.toCycles()) }

/** Filters events from this string pattern based on their begin time. */
@KlangScript.Function
fun String.filterWhen(predicate: (Double) -> Boolean, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).filterWhen(predicate, callInfo)

/**
 * Returns a [PatternMapperFn] that filters events from the source based on their begin time.
 *
 * @param predicate Function that receives the begin time as a `Double`; returns `true` to keep the event.
 * @return A [PatternMapperFn] that filters events by their begin time.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(filterWhen(t => t < 0.5))   // via mapper
 * ```
 *
 * @category structural
 * @tags filterWhen, filter, time, conditional, predicate
 */
@KlangScript.Function
fun filterWhen(predicate: (Double) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.filterWhen(predicate, callInfo) }

/** Chains a filterWhen onto this [PatternMapperFn]; filters events by their begin time. */
@KlangScript.Function
fun PatternMapperFn.filterWhen(predicate: (Double) -> Boolean, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.filterWhen(predicate, callInfo) }

