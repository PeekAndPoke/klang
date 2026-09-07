/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._bind
import io.peekandpoke.klang.sprudel._bindRestart
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._splitQueries
import io.peekandpoke.klang.sprudel._withHapSpan
import io.peekandpoke.klang.sprudel._withQuerySpan
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.pattern.StackPattern
import io.peekandpoke.klang.sprudel.withSteps
import kotlin.math.floor

// -- zoom() -----------------------------------------------------------------------------------------------------------

private fun applyZoom(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return source
    }

    // We convert both arguments to patterns to support dynamic zoom (e.g. zoom("<0 0.5>", "<0.5 1>"))
    val startCtrl = args[0].toPattern()
    val endCtrl = args[1].toPattern()

    // Bind the start pattern...
    return startCtrl._bind { startEv ->
        val s = startEv.data.value?.asDouble ?: return@_bind null

        // ... then bind the end pattern
        endCtrl._bind { endEv ->
            val e = endEv.data.value?.asDouble ?: return@_bind null

            if (s >= e) return@_bind silence

            val d = e - s
            val steps = source.numSteps?.let { it * d }

            // Using relative start to ensure correct periodicity even if s > 1
            val sRelTime = CycleTime.ofCycles(s - floor(s))

            // Reanchor query/hap spans via withQuerySpan + withHapSpan + splitQueries.
            source
                // Apply transformation to cycle-local time: t => t * d + sRel
                ._withQuerySpan { span -> span.withCycle { t -> t.scaleBy(d) + sRelTime } }
                // Apply transformation to cycle-local time: t => (t - sRel) / d
                ._withHapSpan { span -> span.withCycle { t -> (t - sRelTime).divBy(d) } }
                ._splitQueries()
                .withSteps(steps)
        }
    }
}

/**
 * Plays a portion of this pattern within a time window, stretching it to fill a full cycle.
 *
 * The window `[start, end]` is zoomed in on — events within that portion are stretched to fill the cycle.
 * Both `start` and `end` can be pattern strings for dynamic zooming (e.g. `"<0 0.25>"`).
 *
 * @param start Start of the zoom window (0.0 to 1.0). Can be a pattern string.
 * @param end End of the zoom window (0.0 to 1.0). Can be a pattern string.
 * @return The zoomed portion of the pattern stretched to one full cycle.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd hh").zoom(0.0, 0.5)   // plays only first half, stretched to full cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").zoom(0.25, 0.75)  // plays middle two notes, stretched to full cycle
 * ```
 *
 * @category structural
 * @tags zoom, window, time, stretch, slice
 */
@KlangScript.Function
fun SprudelPattern.zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyZoom(this, listOf(start, end).asSprudelDslArgs(callInfo))

/** Plays a portion of this string pattern within a time window, stretched to fill a cycle. */
@KlangScript.Function
fun String.zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).zoom(start, end, callInfo)

/**
 * Returns a [PatternMapperFn] that plays a portion of the source, stretching it to fill a cycle.
 *
 * @param start Start of the zoom window (0.0 to 1.0).
 * @param end End of the zoom window (0.0 to 1.0).
 * @return A [PatternMapperFn] that zooms the source into the given window.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd hh").apply(zoom(0.0, 0.5))   // via mapper
 * ```
 *
 * @category structural
 * @tags zoom, window, time, stretch, slice
 */
@KlangScript.Function
fun zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.zoom(start, end, callInfo) }

/** Chains a zoom onto this [PatternMapperFn]; plays a window of the result stretched to one cycle. */
@KlangScript.Function
fun PatternMapperFn.zoom(start: PatternLike, end: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.zoom(start, end, callInfo) }

// -- within() ---------------------------------------------------------------------------------------------------------

private fun applyWithin(
    source: SprudelPattern,
    startVal: Double,
    endVal: Double,
    transform: PatternMapperFn,
): SprudelPattern {
    if (startVal >= endVal || startVal < 0.0 || endVal > 1.0) {
        return source // Return unchanged if invalid window
    }

    val startTime = CycleTime.ofCycles(startVal)
    val endTime = CycleTime.ofCycles(endVal)
    val isBeginInWindow: (SprudelPatternEvent) -> Boolean = { ev ->
        val cycle = ev.part.begin.floorToCycle()
        if (startVal < endVal) {
            val s = cycle + startTime
            val e = cycle + endTime
            ev.part.begin >= s && ev.part.begin < e
        } else {
            val s1 = cycle + startTime
            val e1 = cycle + CycleTime.ONE
            val e2 = cycle + endTime
            (ev.part.begin >= s1 && ev.part.begin < e1) || (ev.part.begin >= cycle && ev.part.begin < e2)
        }
    }

    val inside = source.filter(isBeginInWindow)
    val outsidePredicate: (SprudelPatternEvent) -> Boolean = { !isBeginInWindow(it) }
    val outside = source.filter(outsidePredicate)

    return StackPattern(listOf(transform(inside), outside))
}

/**
 * Applies a transformation to the portion of the pattern that falls within a time window.
 *
 * Events inside `[start, end)` are extracted, transformed, then stacked back with the untouched events
 * outside the window. The window bounds must satisfy `0.0 <= start < end <= 1.0`.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return The pattern with the windowed portion transformed, stacked with the unaffected portion.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").within(0.0, 0.5, x => x.fast(2))  // double-speed in first half
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").within(0.25, 0.75, x => x.transpose(12))  // octave up in the middle
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@KlangScript.Function
fun SprudelPattern.within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    @Suppress("unused") callInfo: CallInfo? = null,
): SprudelPattern = applyWithin(this, start, end, transform)

/**
 * Applies a transformation to the portion of this string-parsed pattern that falls within a time window.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return The pattern with the windowed portion transformed, stacked with the unaffected portion.
 *
 * ```KlangScript(Playable)
 * "bd sd hh cp".within(0.0, 0.5, x => x.fast(2)).s()  // double-speed in first half
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@KlangScript.Function
fun String.within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).within(start, end, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a transformation to the portion of the source pattern
 * that falls within a time window.
 *
 * @param start Start of the window (0.0 to 1.0, must be less than `end`).
 * @param end End of the window (0.0 to 1.0).
 * @param transform [PatternMapperFn] applied to the events inside the window.
 * @return A [PatternMapperFn] that applies `transform` to events in `[start, end)`.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(within(0.0, 0.5, x => x.fast(2)))  // via mapper
 * ```
 *
 * @category structural
 * @tags within, window, time, conditional, transform
 */
@KlangScript.Function
fun within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    callInfo: CallInfo? = null,
): PatternMapperFn = { p -> p.within(start, end, transform, callInfo) }

/** Chains a within onto this [PatternMapperFn]; applies `transform` to events in the time window of the result. */
@KlangScript.Function
fun PatternMapperFn.within(
    start: Double,
    end: Double,
    transform: PatternMapperFn,
    callInfo: CallInfo? = null,
): PatternMapperFn = this.chain { p -> p.within(start, end, transform, callInfo) }

// -- linger() ---------------------------------------------------------------------------------------------------------

private fun applyLinger(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val tArg = args.getOrNull(0) ?: return pattern

    return pattern._innerJoin(tArg) { src, tVal ->
        val t = tVal?.asDouble ?: return@_innerJoin src

        when {
            t == 0.0 -> silence
            t < 0.0 -> {
                // Negative: zoom from (t+1) to 1, then slow by t (which is negative)
                src.zoom(t + 1.0, 1.0).slow(-t).timeLoop(1.0)
            }

            else -> {
                // Positive: zoom from 0 to t, then slow by t
                src.zoom(0.0, t).slow(t).timeLoop(1.0)
            }
        }
    }
}

/**
 * Selects the given fraction of the pattern and repeats that part to fill the remainder of the cycle.
 *
 * - `linger(0.5)`: Takes first 50% of pattern, repeats it to fill the cycle
 * - `linger(-0.5)`: Takes last 50% of pattern, repeats it to fill the cycle
 * - `linger(0)`: Returns silence
 *
 * The selected portion is slowed down by the fraction amount to fill the full cycle time.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A pattern of the selected fraction, looped to fill the cycle.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").linger(0.5)  // repeats "bd sd" throughout the cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("lt ht mt cp").linger("<1 .5 .25 .125 .0625 .03125>")  // different fraction each cycle
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@KlangScript.Function
fun SprudelPattern.linger(t: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyLinger(this, listOf(t).asSprudelDslArgs(callInfo))

/**
 * Selects the given fraction of this string-parsed pattern and repeats that part to fill the cycle.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A pattern of the selected fraction, looped to fill the cycle.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".linger(0.5).s()  // repeats "bd sd" throughout the cycle
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@KlangScript.Function
fun String.linger(t: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).linger(t, callInfo)

/**
 * Returns a [PatternMapperFn] that selects the given fraction of the source pattern and repeats it to fill the cycle.
 *
 * @param t Fraction to select (positive = from start, negative = from end, 0 = silence). Can be a pattern string.
 * @return A [PatternMapperFn] that lingers on the selected fraction of the source.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").apply(linger(0.5))  // via mapper
 * ```
 *
 * @category structural
 * @tags linger, loop, fraction, repeat, slice
 */
@KlangScript.Function
fun linger(t: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.linger(t, callInfo) }

/** Chains a linger onto this [PatternMapperFn]; repeats the selected fraction of the result to fill the cycle. */
@KlangScript.Function
fun PatternMapperFn.linger(t: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.linger(t, callInfo) }

// -- bite() -----------------------------------------------------------------------------------------------------------

private fun applyBite(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return silence

    val nPattern = args.take(1).toPattern()
    val indicesPattern = args.drop(1).toPattern()
    val indicesSteps: Double = indicesPattern.numSteps ?: 1.0

    return source._innerJoin(nPattern, indicesPattern) { src, nValue, indexValue ->
        val steps: Double =
            src.numSteps ?: return@_innerJoin silence
        val n: Double =
            nValue?.asDouble?.takeIf { it > 0.0 } ?: return@_innerJoin silence
        val index: Double =
            indexValue?.asDouble ?: return@_innerJoin silence
        val indexMod: Double =
            ((index % steps) + steps) % steps

        val start = indexMod / n
        val end = (indexMod + 1.0) / n

        src.zoom(start, end).fast(indicesSteps)
    }
}

/**
 * Splits the pattern into `n` equal slices and plays them in the order given by `indices`.
 *
 * Each event in the `indices` pattern selects a slice by number and plays it scaled to fit that event's
 * duration. This allows reordering, repeating, or otherwise rearranging slices of any pattern.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A new pattern built by playing slices in the order specified by `indices`.
 *
 * ```KlangScript(Playable)
 * n("0 1 2 3").bite(4, "3 2 1 0").scale("c3:major")  // reverse the pattern
 * ```
 *
 * ```KlangScript(Playable)
 * n("0 1 2 3").bite(4, "0!2 1").scale("c3:major")  // play slice 0 twice then slice 1
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@KlangScript.Function
fun SprudelPattern.bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyBite(this, listOf(n, indices).asSprudelDslArgs(callInfo))

/**
 * Like [bite] applied to a mini-notation string.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A new pattern built by playing slices in the order specified by `indices`.
 *
 * ```KlangScript(Playable)
 * "0 1 2 3".bite(4, "3 2 1 0").n().scale("c3:major")  // reverse the pattern
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@KlangScript.Function
fun String.bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bite(n, indices, callInfo)

/**
 * Returns a [PatternMapperFn] that splits the source into `n` equal slices and plays them in `indices` order.
 *
 * @param n       Number of equal slices to cut each cycle into.
 * @param indices Pattern of slice indices (0-based). Can be a mini-notation string or a pattern.
 * @return A [PatternMapperFn] that rearranges slices of the source pattern.
 *
 * ```KlangScript(Playable)
 * n("0 1 2 3").apply(bite(4, "3 2 1 0")).scale("c3:major")  // via mapper
 * ```
 *
 * @category structural
 * @tags bite, slice, rearrange, index, stutter
 */
@KlangScript.Function
fun bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bite(n, indices, callInfo) }

/** Chains a bite onto this [PatternMapperFn]; rearranges slices of the result in `indices` order. */
@KlangScript.Function
fun PatternMapperFn.bite(n: PatternLike, indices: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bite(n, indices, callInfo) }

// -- ribbon() ---------------------------------------------------------------------------------------------------------

private fun applyRibbon(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val offsetArg = args.getOrNull(0) ?: SprudelDslArg.of(0.0)
    val cyclesArg = args.getOrNull(1) ?: SprudelDslArg.of(1.0)

    // pat.early(offset) -> use applyTimeShift with factor -1
    val shifted = applyTimeShift(pattern, listOf(offsetArg), -1.0)

    // pure(1).slow(cycles)
    // We use SequencePattern to ensure we get discrete events per cycle (or per 'cycles' duration),
    // which forces _bindRestart to re-trigger the pattern repeatedly (looping it).
    // If we just used AtomicPattern, it might produce a single long event, preventing the loop.
    val one = AtomicPattern(createSprudelVoiceData { value = 1.asVoiceValue() })
    val pureOne = SequencePattern(listOf(one))

    val loopStructure = applySlow(pureOne, listOf(cyclesArg))

    // struct.restart(shifted)
    return loopStructure._bindRestart { shifted }
}

/**
 * Loops a segment of the pattern starting at `offset` for `cycles` cycles.
 *
 * Imagine the entire timeline as a ribbon: `ribbon` cuts a piece starting at `offset` with length
 * `cycles`, then loops that piece indefinitely.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").ribbon(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").ribbon(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@KlangScript.Function
fun SprudelPattern.ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    applyRibbon(this, listOf(offset, cycles).asSprudelDslArgs(callInfo))

/**
 * Loops a segment of the mini-notation string pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * "bd sd hh cp".ribbon(0.5, 1).s()  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@KlangScript.Function
fun String.ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).ribbon(offset, cycles, callInfo)

/**
 * Returns a [PatternMapperFn] that loops a segment of the source pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A [PatternMapperFn] that loops the specified segment of the source.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").apply(ribbon(1, 2))  // via mapper
 * ```
 *
 * @alias rib
 * @category structural
 * @tags ribbon, rib, loop, slice, offset, cycle
 */
@KlangScript.Function
fun ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.ribbon(offset, cycles, callInfo) }

/** Chains a ribbon onto this [PatternMapperFn]; loops a segment of the result starting at `offset`. */
@KlangScript.Function
fun PatternMapperFn.ribbon(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.ribbon(offset, cycles, callInfo) }

/**
 * Alias for [ribbon]. Loops a segment of the pattern starting at `offset` for `cycles` cycles.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").rib(1, 2)  // loops the 2-cycle segment starting at cycle 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").rib(0.5, 1)  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@KlangScript.Function
fun SprudelPattern.rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.ribbon(offset, cycles, callInfo)

/**
 * Alias for [ribbon] applied to a mini-notation string.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A pattern that loops the specified segment.
 *
 * ```KlangScript(Playable)
 * "bd sd hh cp".rib(0.5, 1).s()  // starts half a cycle in, loops 1 cycle
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@KlangScript.Function
fun String.rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).rib(offset, cycles, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [ribbon] — loops a segment of the source pattern.
 *
 * @param offset Start point of the loop in cycles.
 * @param cycles Length of the looped segment in cycles (default 1.0).
 * @return A [PatternMapperFn] that loops the specified segment of the source.
 *
 * ```KlangScript(Playable)
 * note("<c d e f>").apply(rib(1, 2))  // via mapper
 * ```
 *
 * @alias ribbon
 * @category structural
 * @tags rib, ribbon, loop, slice, offset, cycle
 */
@KlangScript.Function
fun rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.rib(offset, cycles, callInfo) }

/** Chains a rib onto this [PatternMapperFn]; alias for [PatternMapperFn.ribbon]. */
@KlangScript.Function
fun PatternMapperFn.rib(offset: PatternLike, cycles: PatternLike = 1.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.rib(offset, cycles, callInfo) }
