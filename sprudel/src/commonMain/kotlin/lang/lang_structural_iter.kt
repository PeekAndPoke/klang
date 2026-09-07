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
import io.peekandpoke.klang.sprudel.SprudelVoiceValue
import io.peekandpoke.klang.sprudel._fmap
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._squeezeJoin
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.mapEvents
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern

// -- iter() -----------------------------------------------------------------------------------------------------------

internal fun applyIter(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.firstOrNull()
    // iter only supports static integer n because it defines the number of cycles in the sequence
    val n = nArg?.value?.asIntOrNull() ?: 1

    if (n <= 0) return silence

    val nDbl = n.toDouble()

    // Build `times` copies of the pattern, each shiftedearlier by i/times.
    val patterns = (0 until n).map { i ->
        val shift = i.toDouble() / nDbl
        // We use early() to shift the view forward (events appear earlier, effectively rotating the pattern left)
        source.early(shift)
    }

    // The standard approach uses slowcat here, but since iter slices are time-shifted manually above,
    // we can use slowcatPrime logic to sequence them without double-shifting.
    return applySlowcatPrime(patterns)
}

/**
 * Divides this pattern into `n` slices and shifts the view forward by one slice each cycle.
 *
 * Each cycle `c` starts at offset `(c % n) / n`, creating a rotating effect.
 * Only static integer values are supported for `n`.
 *
 * @param n Number of slices to divide the pattern into.
 * @return A pattern that rotates forward by one slice each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").iter(4)  // cycle 0: c d e f, cycle 1: d e f c, …
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").iter(4)  // rotating drum pattern every cycle
 * ```
 *
 * @category structural
 * @tags iter, iterate, rotate, cycle, shift, forward
 */
@KlangScript.Function
fun SprudelPattern.iter(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyIter(this, listOf(n).asSprudelDslArgs(callInfo))

/** Rotates this string pattern forward by one slice each cycle, dividing into `n` slices. */
@KlangScript.Function
fun String.iter(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iter(n, callInfo)

/**
 * Returns a [PatternMapperFn] that rotates the source forward by one slice per cycle.
 *
 * @param n Number of slices to divide the source into.
 * @return A [PatternMapperFn] that rotates the source forward each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(iter(4))  // via mapper
 * ```
 *
 * @category structural
 * @tags iter, iterate, rotate, cycle, shift, forward
 */
@KlangScript.Function
fun iter(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.iter(n, callInfo) }

/** Chains an iter onto this [PatternMapperFn]; rotates forward by one slice per cycle. */
@KlangScript.Function
fun PatternMapperFn.iter(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.iter(n, callInfo) }

// -- iterBack() -------------------------------------------------------------------------------------------------------

internal fun applyIterBack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.firstOrNull()
    val n = nArg?.value?.asIntOrNull() ?: 1

    if (n <= 0) return silence

    val nDbl = n.toDouble()

    // Build `times` copies of the pattern, each shiftedlater by i/times.
    val patterns = (0 until n).map { i ->
        val shift = i.toDouble() / nDbl
        // We use late() to shift the view backward (events appear later, rotating pattern right)
        source.late(shift)
    }

    return applySlowcatPrime(patterns)
}

/**
 * Divides this pattern into `n` slices and shifts the view backward by one slice each cycle.
 *
 * Like [iter] but in the opposite direction: each cycle starts later within the pattern.
 * Only static integer values are supported for `n`.
 *
 * @param n Number of slices to divide the pattern into.
 * @return A pattern that rotates backward by one slice each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").iterBack(4)  // cycle 0: c d e f, cycle 1: f c d e, …
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").iterBack(4)  // backward-rotating drum pattern
 * ```
 *
 * @category structural
 * @tags iterBack, iterate, rotate, cycle, shift, backward
 */
@KlangScript.Function
fun SprudelPattern.iterBack(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyIterBack(this, listOf(n).asSprudelDslArgs(callInfo))

/** Rotates this string pattern backward by one slice each cycle, dividing into `n` slices. */
@KlangScript.Function
fun String.iterBack(n: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iterBack(n, callInfo)

/**
 * Returns a [PatternMapperFn] that rotates the source backward by one slice per cycle.
 *
 * @param n Number of slices to divide the source into.
 * @return A [PatternMapperFn] that rotates the source backward each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(iterBack(4))  // via mapper
 * ```
 *
 * @category structural
 * @tags iterBack, iterate, rotate, cycle, shift, backward
 */
@KlangScript.Function
fun iterBack(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.iterBack(n, callInfo) }

/** Chains an iterBack onto this [PatternMapperFn]; rotates backward by one slice per cycle. */
@KlangScript.Function
fun PatternMapperFn.iterBack(n: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.iterBack(n, callInfo) }

// -- invert() / inv() ------------------------------------------------------------------------------------------------

/**
 * Inverts boolean values in a pattern: true <-> false, 1 <-> 0.
 * Useful for inverting structural patterns and masks.
 */
private fun applyInvert(pattern: SprudelPattern): SprudelPattern {
    return pattern.mapEvents { event ->
        val currentBool = event.data.value?.asBoolean ?: false
        val invertedBool = !currentBool
        event.copy(data = event.data.copy(value = SprudelVoiceValue.Bool(invertedBool)))
    }
}

/**
 * Inverts boolean values in this pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * Useful for flipping structural masks so that silent beats become active and vice-versa.
 *
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript(Playable)
 * "1 0 1 1".invert()                         // produces 0 1 0 0
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").struct("1 0 1 0").invert() // swaps active and silent beats
 * ```
 *
 * @alias inv
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@KlangScript.Function
fun SprudelPattern.invert(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern = applyInvert(this)

/** Inverts boolean values in this string pattern. */
@KlangScript.Function
fun String.invert(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).invert(callInfo)

/**
 * Returns a [PatternMapperFn] that inverts boolean values in the source pattern.
 *
 * @return A [PatternMapperFn] that toggles all boolean values.
 *
 * ```KlangScript(Playable)
 * seq("1 0 1 1").apply(invert())  // via mapper
 * ```
 *
 * @alias inv
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@KlangScript.Function
fun invert(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.invert(callInfo) }

/** Chains an invert onto this [PatternMapperFn]; toggles all boolean values. */
@KlangScript.Function
fun PatternMapperFn.invert(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.invert(callInfo) }

/**
 * Alias for [invert]. Inverts boolean values in this pattern: `true` ↔ `false`, `1` ↔ `0`.
 *
 * @return A pattern with all boolean values toggled.
 *
 * ```KlangScript(Playable)
 * "1 0 1 1".inv()                         // produces 0 1 0 0
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").struct("1 0 1 0").inv() // swaps active and silent beats
 * ```
 *
 * @alias invert
 * @category structural
 * @tags invert, inv, boolean, mask, flip, negate
 */
@KlangScript.Function
fun SprudelPattern.inv(callInfo: CallInfo? = null): SprudelPattern = this.invert(callInfo)

/** Alias for [invert] on a string pattern. */
@KlangScript.Function
fun String.inv(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).inv(callInfo)

/** Returns a [PatternMapperFn] — alias for [invert] — that toggles all boolean values. */
@KlangScript.Function
fun inv(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.inv(callInfo) }

/** Chains an inv (alias for [invert]) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.inv(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.inv(callInfo) }

// -- applyN() --------------------------------------------------------------------------------------------------------

/**
 * Applies a function to a pattern n times sequentially.
 * Supports control patterns for n.
 *
 * Example: `pattern.applyN(3, x => x.fast(2))` applies fast(2) three times
 */
private fun applyApplyN(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val transform = args.getOrNull(1).toPatternMapper() ?: return pattern

    // Use _innerJoin to support control patterns for n
    return pattern._innerJoin(args.take(1)) { src, nValue ->
        val n = nValue?.asInt ?: 0

        var result = src
        repeat(n) { result = transform(result) }
        result
    }
}

/**
 * Applies a mapper function to this pattern `n` times.
 *
 * Supports control patterns for `n`, so the repetition count can vary each cycle.
 *
 * @param n Number of times to apply `transform`.
 * @param transform Function applied repeatedly to the pattern.
 * @return A pattern with `transform` applied `n` times.
 *
 * ```KlangScript(Playable)
 * note("c d e f").applyN(2, x => x.fast(2))      // fast(2) applied twice
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").applyN(3, x => x.echo(2, 0.25, 0.5)) // echo applied 3 times
 * ```
 *
 * @category structural
 * @tags applyN, apply, repeat, transform, function, iterate
 */
@KlangScript.Function
fun SprudelPattern.applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyApplyN(this, listOf(n, transform).asSprudelDslArgs(callInfo))

/** Applies `transform` to this string pattern `n` times. */
@KlangScript.Function
fun String.applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).applyN(n, transform, callInfo)

/**
 * Returns a [PatternMapperFn] that applies `transform` to the source `n` times.
 *
 * @param n Number of times to apply `transform`.
 * @param transform Function applied repeatedly.
 * @return A [PatternMapperFn] that applies `transform` `n` times.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(applyN(2, x => x.fast(2)))  // via mapper
 * ```
 *
 * @category structural
 * @tags applyN, apply, repeat, transform, function, iterate
 */
@KlangScript.Function
fun applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.applyN(n, transform, callInfo) }

/** Chains an applyN onto this [PatternMapperFn]; applies `transform` `n` times. */
@KlangScript.Function
fun PatternMapperFn.applyN(n: PatternLike, transform: PatternMapperFn, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.applyN(n, transform, callInfo) }

// -- pressBy() --------------------------------------------------------------------------------------------------------

/**
 * Syncopates rhythm by compressing events to start at position {r} within their timespan.
 *
 * - r = 0: No compression (normal timing)
 * - r = 0.5: Events start halfway through (syncopated)
 * - r = 1: Events compressed to end
 *
 * Each event is squeezed into a compressed sub-span (control patterns for `r` are supported).
 *
 * Example: s("bd mt sd ht").pressBy("<0 0.5 0.25>")
 */
private fun applyPressBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val rArg = args.getOrNull(0) ?: return pattern

    return pattern._innerJoin(rArg) { src, rVal ->
        val r = rVal?.asDouble ?: return@_innerJoin src

        src._fmap { value ->
            // Create atomic pattern with the value
            val atomicPattern = AtomicPattern.value(value)
            // Compress to [r, 1] - applyCompress handles control patterns internally
            applyCompress(atomicPattern, listOf(SprudelDslArg.of(r), SprudelDslArg.of(1.0)))
        }._squeezeJoin()
    }
}

/**
 * Syncopates this pattern by compressing each event to start at position `r` within its timespan.
 *
 * - `r = 0`: No compression (normal timing).
 * - `r = 0.5`: Events start halfway through their slot (classic syncopation).
 * - `r = 1`: Events compressed to the very end of their slot.
 *
 * @param r Compression ratio in the range [0, 1]; supports control patterns.
 * @return A syncopated pattern.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").pressBy(0.5)          // classic syncopation
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").pressBy("<0 0.5 0.25>") // varying syncopation each cycle
 * ```
 *
 * @category structural
 * @tags pressBy, press, syncopate, compress, rhythm, timing
 */
@KlangScript.Function
fun SprudelPattern.pressBy(r: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyPressBy(this, listOf(r).asSprudelDslArgs(callInfo))

/** Syncopates this string pattern by compressing events to start at position `r`. */
@KlangScript.Function
fun String.pressBy(r: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).pressBy(r, callInfo)

/**
 * Returns a [PatternMapperFn] that syncopates the source by compressing events to position `r`.
 *
 * @param r Compression ratio in the range [0, 1].
 * @return A [PatternMapperFn] that syncopates the source.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").apply(pressBy(0.5))   // classic syncopation via mapper
 * ```
 *
 * @category structural
 * @tags pressBy, press, syncopate, compress, rhythm, timing
 */
@KlangScript.Function
fun pressBy(r: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.pressBy(r, callInfo) }

/** Chains a pressBy onto this [PatternMapperFn]; compresses events to position `r`. */
@KlangScript.Function
fun PatternMapperFn.pressBy(r: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.pressBy(r, callInfo) }

// -- press() ----------------------------------------------------------------------------------------------------------

/**
 * Syncopates rhythm by shifting each event halfway into its timespan.
 * Equivalent to `pressBy(0.5)`.
 *
 * Example: s("bd mt sd ht").every(4, { it.press() })
 */
private fun applyPress(pattern: SprudelPattern): SprudelPattern {
    return applyPressBy(pattern, listOf(SprudelDslArg.of(0.5)))
}

/**
 * Syncopates this pattern by shifting each event halfway into its timespan.
 *
 * Equivalent to `pressBy(0.5)`. Creates a classic off-beat feel.
 *
 * @return A syncopated pattern where events start halfway through their slots.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").press()                   // classic off-beat feel
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").every(4, x => x.press())   // press every 4th cycle
 * ```
 *
 * @category structural
 * @tags press, pressBy, syncopate, compress, rhythm, timing, off-beat
 */
@KlangScript.Function
fun SprudelPattern.press(@Suppress("unused") callInfo: CallInfo? = null): SprudelPattern = applyPress(this)

/** Syncopates this string pattern by shifting events halfway into their timespan. */
@KlangScript.Function
fun String.press(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).press(callInfo)

/**
 * Returns a [PatternMapperFn] that syncopates the source by shifting events halfway.
 *
 * Equivalent to `pressBy(0.5)` as a mapper. Apply using `.apply()`.
 *
 * @return A [PatternMapperFn] that shifts each event halfway into its slot.
 *
 * ```KlangScript(Playable)
 * s("bd mt sd ht").apply(press())   // classic off-beat feel via mapper
 * ```
 *
 * @category structural
 * @tags press, pressBy, syncopate, compress, rhythm, timing, off-beat
 */
@KlangScript.Function
fun press(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.press(callInfo) }

/** Chains a press onto this [PatternMapperFn]; shifts each event halfway into its timespan. */
@KlangScript.Function
fun PatternMapperFn.press(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.press(callInfo) }

