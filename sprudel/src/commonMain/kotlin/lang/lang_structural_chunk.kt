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
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern

// -- chunk() ----------------------------------------------------------------------------------------------------------

internal fun applyChunk(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nArg = args.getOrNull(0) ?: SprudelDslArg.of(1)
    val n = nArg.value?.asIntOrNull() ?: 1
    val transform = args.getOrNull(1).toPatternMapper() ?: { it }

    // TODO: support control patterns
    val back = args.getOrNull(2)?.value as? Boolean ?: false
    val fast = args.getOrNull(3)?.value as? Boolean ?: false
    val earlyOffset = args.getOrNull(4)?.value?.asIntOrNull() ?: 0

    if (n <= 0) {
        return silence
    }

    val binary = MutableList(n - 1) { false }
    binary.add(0, true)  // [true, false, false, false] for n=4

    // Construct binary patterns manually to avoid dependency on 'pure' DSL property order
    val binaryPatterns = binary.map {
        AtomicPattern(createSprudelVoiceData { value = it.asVoiceValue() })
    }
    val binarySequence = applySeq(binaryPatterns)

    var binaryIter = if (back) {
        applyIter(binarySequence, listOf(nArg))  // forward (default)
    } else {
        applyIterBack(binarySequence, listOf(nArg))  // backward
    }

    if (earlyOffset != 0) {
        binaryIter = binaryIter.early(earlyOffset.toDouble())
    }

    val pattern = if (!fast) {
        source.repeatCycles(n)
    } else {
        source
    }

    // return pat.when(binary_pat, func);
    return pattern.`when`(binaryIter, transform)
}

/**
 * Divides the pattern into `n` chunks and cycles through them, applying [transform] to one chunk per cycle.
 *
 * Over `n` cycles the whole pattern plays `n` times, with each cycle highlighting one of the `n` equal
 * segments via [transform]. Use `back = true` to cycle backward, and `fast = true` to let the pattern
 * progress at its natural speed (without the `n`-times repetition).
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated — it runs at natural speed (default `false`).
 * @param transform Function applied to the currently active chunk each cycle.
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").chunk(4, x => x.add(7)).scale("c:minor").n()  // one chunk transformed per cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunk(4, x => x.gain(1.5))  // one hit louder, cycling forward
 * ```
 * @alias slowchunk, slowChunk
 * @category structural
 * @tags chunk, cycle, transform, rotate, slice
 */
@KlangScript.Function
fun SprudelPattern.chunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = applyChunk(this, listOf(n, transform, back, fast).asSprudelDslArgs(callInfo))

/** Like [chunk] applied to a mini-notation string. */
@KlangScript.Function
fun String.chunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).chunk(n, transform, back, fast, callInfo)

/**
 * Alias for [chunk] — divides the pattern into `n` chunks and cycles through them.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk each cycle.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated (default `false`).
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").slowchunk(4, x => x.gain(1.5))  // alias for chunk
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").slowchunk(4, x => x.transpose(5))
 * ```
 * @alias chunk, slowChunk
 * @category structural
 * @tags slowchunk, chunk, cycle, transform, rotate, slice
 */
@KlangScript.Function
fun SprudelPattern.slowchunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.chunk(n, transform, back, fast, callInfo)

/** Alias for [chunk]. */
@KlangScript.Function
fun String.slowchunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).slowchunk(n, transform, back, fast, callInfo)

/**
 * Alias for [chunk] — divides the pattern into `n` chunks and cycles through them.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk each cycle.
 * @param back      If `true`, cycles backward through chunks (default `false`).
 * @param fast      If `true`, the source pattern is not repeated (default `false`).
 * @return A new pattern with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").slowChunk(4, x => x.gain(1.5))  // alias for chunk
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").slowChunk(4, x => x.transpose(5))
 * ```
 * @alias chunk, slowchunk
 * @category structural
 * @tags slowChunk, chunk, cycle, transform, rotate, slice
 */
@KlangScript.Function
fun SprudelPattern.slowChunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.chunk(n, transform, back, fast, callInfo)

/** Alias for [chunk]. */
@KlangScript.Function
fun String.slowChunk(
    n: Int,
    transform: PatternMapperFn,
    back: Boolean = false,
    fast: Boolean = false,
    callInfo: CallInfo? = null,
): SprudelPattern = this.toVoiceValuePattern(callInfo?.receiverLocation).slowChunk(n, transform, back, fast, callInfo)

// -- chunkBack() / chunkback() ----------------------------------------------------------------------------------------

/**
 * Like [chunk], but cycles through the parts in reverse order (known as `chunk'` in TidalCycles).
 *
 * Divides the pattern into `n` chunks and cycles backward through them — starting from chunk 0,
 * then chunk n-1, n-2, …, 1 — applying [transform] to one chunk per cycle.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern cycling backward through transformed chunks.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").chunkBack(4, x => x.add(7)).scale("c:minor").n()  // backward: 0, 3, 2, 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkBack(4, x => x.gain(0.1))  // one hit less gain, cycling back
 * ```
 * @alias chunkback
 * @category structural
 * @tags chunk, cycle, transform, reverse, rotate
 */
@KlangScript.Function
fun SprudelPattern.chunkBack(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, true, false).asSprudelDslArgs(callInfo))

/**
 * Like [chunk] on a string-parsed pattern, but cycles backward through parts.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".chunkBack(4, x => x.gain(0.1)).s()  // one hit less gain, cycling back
 * ```
 * @alias chunkback
 */
@KlangScript.Function
fun String.chunkBack(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkBack(n, transform, callInfo)

/**
 * Alias for [chunkBack] — cycles backward through transformed chunks.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern cycling backward through transformed chunks.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").chunkback(4, x => x.add(7))  // backward: 0, 3, 2, 1
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkback(4, x => x.gain(0.1))  // one hit less gain, cycling back
 * ```
 * @alias chunkBack
 * @category structural
 * @tags chunk, cycle, transform, reverse, rotate
 */
@KlangScript.Function
fun SprudelPattern.chunkback(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.chunkBack(n, transform, callInfo)

/**
 * Alias for [chunkBack] on a string-parsed pattern.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".chunkback(4, x => x.gain(0.8)).s()  // one hit boosted, cycling back
 * ```
 * @alias chunkBack
 */
@KlangScript.Function
fun String.chunkback(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkback(n, transform, callInfo)

// -- fastChunk() / fastchunk() ----------------------------------------------------------------------------------------

/**
 * Like [chunk], but the source pattern plays at natural speed (not repeated `n` times).
 *
 * While [chunk] repeats the source `n` times before cycling through transformations, `fastChunk` lets
 * the pattern progress naturally while the transformed chunk cycles independently.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern at natural speed with chunks cycling through the transformation.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").fastChunk(4, x => x.add(10)).scale("c:minor").n()  // no repeat: chunks at normal speed
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").fastChunk(4, x => x.gain(0.8))  // one hit boosted per cycle, no repeat
 * ```
 * @alias fastchunk
 * @category structural
 * @tags chunk, cycle, transform, fast, rotate
 */
@KlangScript.Function
fun SprudelPattern.fastChunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, false, true).asSprudelDslArgs(callInfo))

/**
 * Like [chunk] on a string-parsed pattern but at natural speed.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".fastChunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastchunk
 */
@KlangScript.Function
fun String.fastChunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fastChunk(n, transform, callInfo)

/**
 * Alias for [fastChunk] — like [chunk] but pattern plays at natural speed.
 *
 * @param n Number of chunks.
 * @param transform Function applied to the active chunk each cycle.
 * @return Pattern at natural speed with chunks cycling through the transformation.
 *
 * ```KlangScript(Playable)
 * seq("0 1 2 3").fastchunk(4, x => x.add(10)).scale("c:minor").n()  // no repeat: chunks at normal speed
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").fastchunk(4, x => x.gain(0.8))  // one hit boosted per cycle, no repeat
 * ```
 * @alias fastChunk
 * @category structural
 * @tags chunk, cycle, transform, fast, rotate
 */
@KlangScript.Function
fun SprudelPattern.fastchunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.fastChunk(n, transform, callInfo)

/**
 * Alias for [fastChunk] on a string-parsed pattern.
 *
 * ```KlangScript(Playable)
 * "bd sd ht lt".fastchunk(4, x => x.gain(0.8)).s()  // one hit boosted, no repeat
 * ```
 * @alias fastChunk
 */
@KlangScript.Function
fun String.fastchunk(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fastchunk(n, transform, callInfo)

// -- chunkInto() ------------------------------------------------------------------------------------------------------

/**
 * Like [chunk], but applies [transform] to a fast-looped subcycle instead of repeating the pattern `n` times.
 *
 * Equivalent to `fastChunk` — the source pattern plays at its natural speed while the transformed
 * chunk cycles through the `n` parts independently each cycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the active chunk each cycle.
 * @return A pattern at natural speed with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkInto(4, x => x.hurry(2))  // transform cycles, no repeat
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g h").chunkInto(3, x => x.transpose(7))  // 3 chunks, each transposed
 * ```
 * @alias chunkinto
 * @category structural
 * @tags chunkInto, chunk, cycle, transform, fast, slice
 */
@KlangScript.Function
fun SprudelPattern.chunkInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, false, true).asSprudelDslArgs(callInfo))

/** Like [chunkInto] applied to a mini-notation string. */
@KlangScript.Function
fun String.chunkInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkInto(n, transform, callInfo)

/**
 * Alias for [chunkInto] — applies [transform] to a fast-looped subcycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the active chunk each cycle.
 * @return A pattern at natural speed with the transform cycling through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkinto(4, x => x.hurry(2))  // lowercase alias
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").chunkinto(4, x => x.rev())  // reversed active chunk
 * ```
 * @alias chunkInto
 * @category structural
 * @tags chunkinto, chunkInto, chunk, cycle, transform, fast, slice
 */
@KlangScript.Function
fun SprudelPattern.chunkinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.chunkInto(n, transform, callInfo)

/** Alias for [chunkInto]. */
@KlangScript.Function
fun String.chunkinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkinto(n, transform, callInfo)

// -- chunkBackInto() --------------------------------------------------------------------------------------------------

/**
 * Divides a pattern into `n` chunks and applies a transform to the active chunk, cycling backwards.
 *
 * Like [chunkInto], but advances through chunks in reverse order each cycle.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk.
 * @return A new pattern with the transform cycling backwards through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkBackInto(4, x => x.hurry(2))  // transform cycles backward
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g h").chunkBackInto(3, x => x.transpose(7))  // 3 chunks, reversed order
 * ```
 * @alias chunkbackinto
 * @category structural
 * @tags chunk, slice, backward, transform, cycle
 */
@KlangScript.Function
fun SprudelPattern.chunkBackInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    applyChunk(this, listOf(n, transform, true, true, 1).asSprudelDslArgs(callInfo))

/** Like [chunkBackInto] applied to a mini-notation string. */
@KlangScript.Function
fun String.chunkBackInto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkBackInto(n, transform, callInfo)

/**
 * Alias for [chunkBackInto] — divides the pattern into `n` chunks, applying a transform cycling backwards.
 *
 * @param n         Number of chunks to divide the pattern into.
 * @param transform Function applied to the currently active chunk.
 * @return A new pattern with the transform cycling backwards through chunks.
 *
 * ```KlangScript(Playable)
 * s("bd sd ht lt").chunkbackinto(4, x => x.hurry(2))  // backwards chunk transform
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").chunkbackinto(4, x => x.rev())  // reversed active chunk
 * ```
 * @alias chunkBackInto
 * @category structural
 * @tags chunk, slice, backward, transform, cycle
 */
@KlangScript.Function
fun SprudelPattern.chunkbackinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.chunkBackInto(n, transform, callInfo)

/** Alias for [chunkBackInto]. */
@KlangScript.Function
fun String.chunkbackinto(n: Int, transform: PatternMapperFn, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).chunkbackinto(n, transform, callInfo)

