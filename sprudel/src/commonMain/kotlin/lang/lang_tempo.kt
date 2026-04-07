@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel.TimeSpan
import io.peekandpoke.klang.sprudel._bind
import io.peekandpoke.klang.sprudel._bindSqueeze
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._liftNumericField
import io.peekandpoke.klang.sprudel._withHapSpan
import io.peekandpoke.klang.sprudel._withHapTime
import io.peekandpoke.klang.sprudel._withQuerySpan
import io.peekandpoke.klang.sprudel._withQueryTime
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.mapEvents
import io.peekandpoke.klang.sprudel.pattern.AtomicInfinitePattern
import io.peekandpoke.klang.sprudel.pattern.ControlValueProvider
import io.peekandpoke.klang.sprudel.pattern.FastGapPattern
import io.peekandpoke.klang.sprudel.pattern.ReversePattern
import io.peekandpoke.klang.sprudel.pattern.SequencePattern
import io.peekandpoke.klang.sprudel.pattern.SwingPattern
import io.peekandpoke.klang.sprudel.pattern.TimeShiftPattern
import io.peekandpoke.klang.sprudel.withSteps

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangTempoInit = false

// Helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun applyTimeShift(
    pattern: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    factor: Rational = Rational.ONE,
): SprudelPattern {
    if (args.isEmpty()) return pattern

    val control = args[0].asControlValueProvider(Rational.ZERO.asVoiceValue())

    return TimeShiftPattern(
        source = pattern,
        offsetProvider = control,
        factor = factor,
    )
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Tempo / Timing / Order modifiers
// ///

// -- slow() -----------------------------------------------------------------------------------------------------------

fun applySlow(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val factorArg = args.firstOrNull() ?: return pattern

    val result = pattern._innerJoin(factorArg) { src, factorVal ->
        val factor = factorVal?.asRational ?: return@_innerJoin src
        if (factor == Rational.ZERO) return@_innerJoin silence
        val inverseFactor = Rational.ONE / factor

        src._withQueryTime { t -> t * inverseFactor }
            ._withHapTime { t -> t / inverseFactor }
            .withSteps(src.numSteps)
    }

    val staticFactor = factorArg.value?.asRationalOrNull()

    if (staticFactor != null && staticFactor > Rational.ZERO) {
        return object : SprudelPattern by result {
            override fun estimateCycleDuration(): Rational =
                pattern.estimateCycleDuration() * staticFactor
        }
    }

    return result
}

internal val SprudelPattern._slow by dslPatternExtension { p, args, /* callInfo */ _ -> applySlow(p, args) }
internal val String._slow by dslStringExtension { p, args, callInfo -> p._slow(args, callInfo) }
internal val _slow by dslPatternMapper { args, callInfo -> { p -> p._slow(args, callInfo) } }
internal val PatternMapperFn._slow by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_slow(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Slows down a pattern by the given factor.
 *
 * `slow(2)` stretches the pattern so it takes 2 cycles to complete. Accepts control patterns for the factor.
 *
 * @return A pattern slowed by `factor`.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").slow(2)              // half tempo — pattern spans 2 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").slow("<1 2 4>")            // varying slow factor each cycle
 * ```
 *
 * @category tempo
 * @tags slow, tempo, stretch, speed
 */
@SprudelDsl
fun SprudelPattern.slow(factor: PatternLike): SprudelPattern = this._slow(listOf(factor).asSprudelDslArgs())

/** Slows down this string pattern by the given factor. */
@SprudelDsl
fun String.slow(factor: PatternLike): SprudelPattern = this._slow(listOf(factor).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that slows down a pattern by the given factor.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").apply(slow(2))       // mapper form
 * ```
 *
 * @category tempo
 * @tags slow, tempo, stretch, speed
 */
@SprudelDsl
fun slow(factor: PatternLike): PatternMapperFn = _slow(listOf(factor).asSprudelDslArgs())

/** Chains a slow operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.slow(factor: PatternLike): PatternMapperFn = this._slow(listOf(factor).asSprudelDslArgs())

// -- fast() -----------------------------------------------------------------------------------------------------------

fun applyFast(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val factorArg = args.firstOrNull() ?: return pattern

    val result = pattern._innerJoin(factorArg) { src, factorVal ->
        val factor = factorVal?.asRational ?: return@_innerJoin src
        if (factor == Rational.ZERO) return@_innerJoin silence

        src._withQueryTime { t -> t * factor }
            ._withHapTime { t -> t / factor }
            .withSteps(src.numSteps)
    }

    val staticFactor = factorArg.value?.asRationalOrNull()

    if (staticFactor != null && staticFactor > Rational.ZERO) {
        return object : SprudelPattern by result {
            override fun estimateCycleDuration(): Rational =
                pattern.estimateCycleDuration() / staticFactor
        }
    }

    return result
}

internal val SprudelPattern._fast by dslPatternExtension { p, args, /* callInfo */ _ -> applyFast(p, args) }
internal val String._fast by dslStringExtension { p, args, callInfo -> p._fast(args, callInfo) }
internal val _fast by dslPatternMapper { args, callInfo -> { p -> p._fast(args, callInfo) } }
internal val PatternMapperFn._fast by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_fast(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Speeds up the pattern by the given factor.
 *
 * `fast(2)` plays the pattern twice per cycle. Accepts mini-notation strings and control patterns.
 *
 * @param factor Speed-up factor. Values > 1 play faster; values < 1 play slower.
 * @return A pattern sped up by `factor`.
 *
 * ```KlangScript(Playable)
 * note("c d e f").fast(2)           // 8 events per cycle instead of 4
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").fast("<1 2 4>")     // varying speed each cycle
 * ```
 *
 * @category tempo
 * @tags fast, speed, tempo, accelerate
 */
@SprudelDsl
fun SprudelPattern.fast(factor: PatternLike): SprudelPattern = this._fast(listOf(factor).asSprudelDslArgs())

/** Speeds up the mini-notation string pattern by `factor`. */
@SprudelDsl
fun String.fast(factor: PatternLike): SprudelPattern = this._fast(listOf(factor).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that speeds up a pattern by the given factor.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").apply(fast(2))      // mapper form
 * ```
 *
 * @category tempo
 * @tags fast, speed, tempo, accelerate
 */
@SprudelDsl
fun fast(factor: PatternLike): PatternMapperFn = _fast(listOf(factor).asSprudelDslArgs())

/** Chains a fast operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.fast(factor: PatternLike): PatternMapperFn = this._fast(listOf(factor).asSprudelDslArgs())

// -- rev() ------------------------------------------------------------------------------------------------------------

fun applyRev(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val nProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(Rational.ONE.asVoiceValue())

    return ReversePattern(inner = pattern, nProvider = nProvider)
}

internal val SprudelPattern._rev by dslPatternExtension { p, args, /* callInfo */ _ -> applyRev(p, args) }
internal val String._rev by dslStringExtension { p, args, callInfo -> p._rev(args, callInfo) }
internal val _rev by dslPatternMapper { args, callInfo -> { p -> p._rev(args, callInfo) } }
internal val PatternMapperFn._rev by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_rev(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Reverses the order of events within each cycle (or across `n` cycles when given an argument).
 *
 * With no argument, each individual cycle plays its events in reverse order. With an integer `n`,
 * the reversal is applied across every `n`-cycle span — useful for longer retrograde effects.
 * Accepts control patterns for the cycle count.
 *
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
@SprudelDsl
fun SprudelPattern.rev(n: PatternLike = 1): SprudelPattern = this._rev(listOf(n).asSprudelDslArgs())

/** Reverses the order of events, applying the reversal across every `n`-cycle span. */
@SprudelDsl
fun String.rev(n: PatternLike = 1): SprudelPattern = this._rev(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that reverses the order of events across every `n`-cycle span.
 *
 * ```KlangScript(Playable)
 * s("bd hh sd cp").apply(rev())             // mapper form
 * ```
 *
 * @category tempo
 * @tags rev, reverse, order, retrograde
 */
@SprudelDsl
fun rev(n: PatternLike = 1): PatternMapperFn = _rev(listOf(n).asSprudelDslArgs())

/** Chains a rev operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.rev(n: PatternLike = 1): PatternMapperFn = this._rev(listOf(n).asSprudelDslArgs())

// -- revv() -----------------------------------------------------------------------------------------------------------

// TODO: deep checks ... does not seem to work to well for complex patterns
fun applyRevv(pattern: SprudelPattern): SprudelPattern {
    // Negates a time span: [begin, end] → [-end, -begin]
    val negateSpan: (TimeSpan) -> TimeSpan = { span ->
        TimeSpan(
            begin = Rational.ZERO - span.end,
            end = Rational.ZERO - span.begin
        )
    }

    // Transform both query spans and event spans
    return pattern._withQuerySpan(negateSpan)._withHapSpan(negateSpan)
}

internal val SprudelPattern._revv by dslPatternExtension { p, /* args */ _, /* callInfo */ _ -> applyRevv(p) }
internal val String._revv by dslStringExtension { p, args, callInfo -> p._revv(args, callInfo) }
internal val _revv by dslPatternMapper { args, callInfo -> { p -> p._revv(args, callInfo) } }
internal val PatternMapperFn._revv by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_revv(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
@SprudelDsl
fun SprudelPattern.revv(): SprudelPattern = this._revv(emptyList())

/** Reverses the pattern in absolute time across all cycles. */
@SprudelDsl
fun String.revv(): SprudelPattern = this._revv(emptyList())

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
@SprudelDsl
fun revv(): PatternMapperFn = _revv(emptyList())

/** Chains a revv operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.revv(): PatternMapperFn = this._revv(emptyList())

// -- palindrome() -----------------------------------------------------------------------------------------------------

fun applyPalindrome(pattern: SprudelPattern): SprudelPattern {
    // Palindrome needs to play the pattern forward, then backward.
    // Critically, it must use ABSOLUTE time (Prime behavior) so that the 'rev' version
    // reverses the content of the *second* cycle, not the first cycle played again.
    // Matches JS: pat.lastOf(2, rev) -> equivalent to slowcatPrime(pat, rev(pat))
    return applySlowcatPrime(listOf(pattern, applyRev(pattern, listOf(SprudelDslArg(1, null)))))
}

internal val SprudelPattern._palindrome by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyPalindrome(p)
}
internal val String._palindrome by dslStringExtension { p, args, callInfo -> p._palindrome(args, callInfo) }
internal val _palindrome by dslPatternMapper { args, callInfo -> { p -> p._palindrome(args, callInfo) } }
internal val PatternMapperFn._palindrome by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_palindrome(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
@SprudelDsl
fun SprudelPattern.palindrome(): SprudelPattern = this._palindrome(emptyList())

/** Plays the pattern forward then backward, creating a two-cycle palindrome. */
@SprudelDsl
fun String.palindrome(): SprudelPattern = this._palindrome(emptyList())

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
@SprudelDsl
fun palindrome(): PatternMapperFn = _palindrome(emptyList())

/** Chains a palindrome operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.palindrome(): PatternMapperFn = this._palindrome(emptyList())

// -- early() ----------------------------------------------------------------------------------------------------------

internal val SprudelPattern._early by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTimeShift(pattern = p, args = args, factor = Rational.MINUS_ONE)
}
internal val String._early by dslStringExtension { p, args, callInfo -> p._early(args, callInfo) }
internal val _early by dslPatternMapper { args, callInfo -> { p -> p._early(args, callInfo) } }
internal val PatternMapperFn._early by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_early(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Nudges the pattern to start earlier by the given number of cycles.
 *
 * Shifts all events backward in time by the specified amount. For example, `early(0.5)` moves
 * every event half a cycle earlier so that what was at position 0.5 now appears at position 0.
 * Useful for creating syncopation or aligning patterns that are slightly off-beat.
 *
 * @return A pattern shifted earlier by the given number of cycles.
 *
 * ```KlangScript(Playable)
 * note("c d e f").early(0.25)         // shifts the pattern a quarter cycle earlier
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").stack(s("hh*4").early(0.125))   // hi-hat slightly ahead of the beat
 * ```
 *
 * @category tempo
 * @tags early, shift, time, offset, nudge, ahead
 */
@SprudelDsl
fun SprudelPattern.early(amount: PatternLike): SprudelPattern = this._early(listOf(amount).asSprudelDslArgs())

/** Nudges the pattern to start earlier by the given number of cycles. */
@SprudelDsl
fun String.early(amount: PatternLike): SprudelPattern = this._early(listOf(amount).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that nudges a pattern earlier by the given number of cycles.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(early(0.125))       // mapper form
 * ```
 *
 * @category tempo
 * @tags early, shift, time, offset, nudge, ahead
 */
@SprudelDsl
fun early(amount: PatternLike): PatternMapperFn = _early(listOf(amount).asSprudelDslArgs())

/** Chains an early operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.early(amount: PatternLike): PatternMapperFn = this._early(listOf(amount).asSprudelDslArgs())

// -- late() -----------------------------------------------------------------------------------------------------------

internal val SprudelPattern._late by dslPatternExtension { p, args, _ ->
    applyTimeShift(pattern = p, args = args, factor = Rational.ONE)
}
internal val String._late by dslStringExtension { p, args, callInfo -> p._late(args, callInfo) }
internal val _late by dslPatternMapper { args, callInfo -> { p -> p._late(args, callInfo) } }
internal val PatternMapperFn._late by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_late(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Nudges the pattern to start later by the given number of cycles.
 *
 * Shifts all events forward in time by the specified amount. For example, `late(0.5)` moves
 * every event half a cycle later so that what was at position 0 now appears at position 0.5.
 * Useful for creating delay effects or adjusting phase relationships between patterns.
 *
 * @return A pattern shifted later by the given number of cycles.
 *
 * ```KlangScript(Playable)
 * note("c d e f").late(0.25)          // shifts the pattern a quarter cycle later
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").stack(s("hh*4").late(0.125))    // hi-hat slightly behind the beat
 * ```
 *
 * @category tempo
 * @tags late, shift, time, offset, nudge, delay, behind
 */
@SprudelDsl
fun SprudelPattern.late(amount: PatternLike): SprudelPattern = this._late(listOf(amount).asSprudelDslArgs())

/** Nudges the pattern to start later by the given number of cycles. */
@SprudelDsl
fun String.late(amount: PatternLike): SprudelPattern = this._late(listOf(amount).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that nudges a pattern later by the given number of cycles.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(late(0.125))        // mapper form
 * ```
 *
 * @category tempo
 * @tags late, shift, time, offset, nudge, delay, behind
 */
@SprudelDsl
fun late(amount: PatternLike): PatternMapperFn = _late(listOf(amount).asSprudelDslArgs())

/** Chains a late operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.late(amount: PatternLike): PatternMapperFn = this._late(listOf(amount).asSprudelDslArgs())

// -- compress() -------------------------------------------------------------------------------------------------------

fun applyCompress(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    // We convert both arguments to patterns to support dynamic compress
    val startCtrl = listOf(args[0]).toPattern(voiceValueModifier)
    val endCtrl = listOf(args[1]).toPattern(voiceValueModifier)

    // Bind start...
    return startCtrl._bind { startEv ->
        val b = startEv.data.value?.asRational ?: return@_bind null

        // ...bind end
        endCtrl._bind { endEv ->
            val e = endEv.data.value?.asRational ?: return@_bind null

            // JS check: if (b.gt(e) || b.gt(1) || e.gt(1) || b.lt(0) || e.lt(0)) return silence
            if (b > e || b > Rational.ONE || e > Rational.ONE || b < Rational.ZERO || e < Rational.ZERO) {
                return@_bind null // effectively silence for this event
            }

            val duration = e - b
            if (duration == Rational.ZERO) return@_bind null

            val factor = Rational.ONE / duration
            val fastGapped = pattern._fastGap(factor)

            fastGapped._withQueryTime { t -> t - b }.mapEvents { ev ->
                val shiftedPart = ev.part.shift(b)
                val shiftedWhole = ev.whole.shift(b)
                ev.copy(part = shiftedPart, whole = shiftedWhole)
            }
        }
    }
}

internal val SprudelPattern._compress by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCompress(p, args)
}
internal val String._compress by dslStringExtension { p, args, callInfo -> p._compress(args, callInfo) }
internal val _compress by dslPatternMapper { args, callInfo -> { p -> p._compress(args, callInfo) } }
internal val PatternMapperFn._compress by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_compress(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Compresses the pattern into a sub-range of each cycle, leaving silence outside that range.
 *
 * `compress(start, end)` squeezes the full pattern into the window `[start, end]` within
 * each cycle and leaves a gap everywhere else. Both values are in the range 0–1.
 *
 * @return A pattern compressed into `[start, end]` with silence elsewhere.
 *
 * ```KlangScript(Playable)
 * note("c d e f").compress(0, 0.5)        // all 4 events fit into first half of each cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").compress(0.25, 0.75)         // pattern compressed into middle 50% of each cycle
 * ```
 *
 * @category tempo
 * @tags compress, squeeze, timespan, gap, range
 */
@SprudelDsl
fun SprudelPattern.compress(start: PatternLike, end: PatternLike): SprudelPattern =
    this._compress(listOf(start, end).asSprudelDslArgs())

/** Compresses the pattern into `[start, end]` within each cycle, leaving silence outside. */
@SprudelDsl
fun String.compress(start: PatternLike, end: PatternLike): SprudelPattern =
    this._compress(listOf(start, end).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that compresses a pattern into `[start, end]` per cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(compress(0, 0.5))  // mapper form
 * ```
 *
 * @category tempo
 * @tags compress, squeeze, timespan, gap, range
 */
@SprudelDsl
fun compress(start: PatternLike, end: PatternLike): PatternMapperFn =
    _compress(listOf(start, end).asSprudelDslArgs())

/** Chains a compress operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.compress(start: PatternLike, end: PatternLike): PatternMapperFn =
    this._compress(listOf(start, end).asSprudelDslArgs())

// -- focus() ----------------------------------------------------------------------------------------------------------

fun applyFocus(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return source

    val startCtrl = listOf(args[0]).toPattern(voiceValueModifier)
    val endCtrl = listOf(args[1]).toPattern(voiceValueModifier)

    return startCtrl._bind { startEv ->
        val s = startEv.data.value?.asRational ?: return@_bind null

        endCtrl._bind { endEv ->
            val e = endEv.data.value?.asRational ?: return@_bind null

            if (s >= e) return@_bind null

            val d = e - s
            val sFloored = s.floor()

            source._withQueryTime { t -> (t - s) / d + sFloored }.mapEvents { ev ->
                val scaledPart = ev.part.shift(-sFloored).scale(d).shift(s)
                val scaledWhole = ev.whole.shift(-sFloored).scale(d).shift(s)
                ev.copy(part = scaledPart, whole = scaledWhole)
            }
        }
    }
}

internal val SprudelPattern._focus by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyFocus(p, args)
}
internal val String._focus by dslStringExtension { p, args, callInfo -> p._focus(args, callInfo) }
internal val _focus by dslPatternMapper { args, callInfo -> { p -> p._focus(args, callInfo) } }
internal val PatternMapperFn._focus by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_focus(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Zooms in on a sub-range of a cycle, stretching that portion to fill the whole cycle.
 *
 * `focus(start, end)` is like the inverse of `compress`: it takes the slice `[start, end]`
 * of the original pattern and stretches it to fill a full cycle.
 *
 * @return A pattern that zooms into `[start, end]`, stretching it to fill each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").focus(0, 0.5)       // only the first half is shown, stretched to a full cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").focus(0.25, 0.75)  // middle 50% of the pattern, stretched to fill the cycle
 * ```
 *
 * @category tempo
 * @tags focus, zoom, timespan, range, stretch
 */
@SprudelDsl
fun SprudelPattern.focus(start: PatternLike, end: PatternLike): SprudelPattern =
    this._focus(listOf(start, end).asSprudelDslArgs())

/** Zooms in on `[start, end]` of a cycle and stretches that portion to fill each cycle. */
@SprudelDsl
fun String.focus(start: PatternLike, end: PatternLike): SprudelPattern =
    this._focus(listOf(start, end).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that zooms in on `[start, end]` and stretches it to fill each cycle.
 *
 * ```KlangScript(Playable)
 * note("c d e f").apply(focus(0, 0.5))  // mapper form
 * ```
 *
 * @category tempo
 * @tags focus, zoom, timespan, range, stretch
 */
@SprudelDsl
fun focus(start: PatternLike, end: PatternLike): PatternMapperFn =
    _focus(listOf(start, end).asSprudelDslArgs())

/** Chains a focus operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.focus(start: PatternLike, end: PatternLike): PatternMapperFn =
    this._focus(listOf(start, end).asSprudelDslArgs())

// -- ply() ------------------------------------------------------------------------------------------------------------

fun applyPly(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val factorArg = args[0]

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asRationalOrNull()
    val newSteps = if (staticFactor != null) pattern.numSteps?.times(staticFactor) else null

    // Convert factor to pattern (supports static values and control patterns)
    val factorPattern = factorArg.toPattern()

    val result = pattern._bindSqueeze { event ->
        // pure(x) -> infinite pattern of the event's data
        val infiniteAtom = AtomicInfinitePattern(event.data)

        // To support "Patterned Ply" (Tidal style) where the factor pattern is aligned with the cycle,
        // we must project the global factor pattern into the event's local timeframe.
        // factorPattern.zoom(begin, end) takes the slice of factor corresponding to the event
        // and stretches it to 0..1, which matches the squeezed context.
        val localFactor = if (staticFactor == null) {
            factorPattern.zoom(event.whole.begin.toDouble(), event.whole.end.toDouble())
        } else {
            factorPattern
        }

        // ._fast(factor)
        infiniteAtom.fast(localFactor)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

internal val SprudelPattern._ply by dslPatternExtension { p, args, /* callInfo */ _ -> applyPly(p, args) }
internal val String._ply by dslStringExtension { p, args, callInfo -> p._ply(args, callInfo) }
internal val _ply by dslPatternMapper { args, callInfo -> { p -> p._ply(args, callInfo) } }
internal val PatternMapperFn._ply by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_ply(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

/**
 * Repeats each event `n` times within its original timespan.
 *
 * Each event in the pattern is subdivided into `n` equal copies squeezed into the same
 * duration. For example, `ply(3)` on a 2-event pattern produces 6 events: 3 copies of the
 * first event followed by 3 copies of the second. Accepts control patterns for `n`.
 *
 * @return A pattern with each event repeated `n` times within its timespan.
 *
 * ```KlangScript(Playable)
 * note("c d").ply(3)                  // c c c d d d — 6 events squeezed into 1 cycle
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").ply("<1 2 4>")      // varying subdivision each cycle
 * ```
 *
 * @category tempo
 * @tags ply, repeat, subdivide, multiply, density
 */
@SprudelDsl
fun SprudelPattern.ply(n: PatternLike): SprudelPattern = this._ply(listOf(n).asSprudelDslArgs())

/** Repeats each event `n` times within its original timespan. */
@SprudelDsl
fun String.ply(n: PatternLike): SprudelPattern = this._ply(listOf(n).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that repeats each event `n` times within its timespan.
 *
 * ```KlangScript(Playable)
 * note("c d").apply(ply(3))           // mapper form
 * ```
 *
 * @category tempo
 * @tags ply, repeat, subdivide, multiply, density
 */
@SprudelDsl
fun ply(n: PatternLike): PatternMapperFn = _ply(listOf(n).asSprudelDslArgs())

/** Chains a ply operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.ply(n: PatternLike): PatternMapperFn = this._ply(listOf(n).asSprudelDslArgs())

// -- plyWith() --------------------------------------------------------------------------------------------------------

/**
 * Helper function to apply a function n times to a value.
 * Equivalent to JS applyN(n, func, p).
 */
fun applyFunctionNTimes(n: Int, func: PatternMapperFn, pattern: SprudelPattern): SprudelPattern {
    var result = pattern
    repeat(n) {
        result = func(result)
    }
    return result
}

fun applyPlyWith(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val factorArg = args[0]
    val funcArg = args[1]
    val func = funcArg.toPatternMapper() ?: return pattern

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asRationalOrNull()?.toInt()

    val newSteps = if (staticFactor != null) {
        pattern.numSteps?.times(staticFactor.toRational())
    } else {
        null
    }

    val result = pattern._bindSqueeze { event ->
        val factor = staticFactor ?: event.data.value?.asRational?.toInt() ?: 1

        if (factor <= 0) {
            return@_bindSqueeze null
        }

        // Create factor number of patterns, applying func 0, 1, 2, ... (factor-1) times
        // Equivalent to: cat(...listRange(0, factor - 1).map((i) => applyN(i, func, x)))
        val patterns = (0 until factor).map { i ->
            val atomPattern = AtomicInfinitePattern(event.data)
            applyFunctionNTimes(i, func, atomPattern)
        }

        // Concatenate all patterns - SequencePattern squashes them into one cycle
        // _bindSqueeze will squeeze this into the event's timespan
        if (patterns.size == 1) patterns.first() else SequencePattern(patterns)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

internal val SprudelPattern._plyWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPlyWith(p, args)
}
internal val String._plyWith by dslStringExtension { p, args, callInfo -> p._plyWith(args, callInfo) }
internal val _plyWith by dslPatternMapper { args, callInfo -> { p -> p._plyWith(args, callInfo) } }
internal val PatternMapperFn._plyWith by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_plyWith(args, callInfo))
}

internal val SprudelPattern._plywith by dslPatternExtension { p, args, callInfo -> p._plyWith(args, callInfo) }
internal val String._plywith by dslStringExtension { p, args, callInfo -> p._plywith(args, callInfo) }
internal val _plywith by dslPatternMapper { args, callInfo -> { p -> p._plywith(args, callInfo) } }
internal val PatternMapperFn._plywith by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_plywith(args, callInfo))
}

/**
 * Repeats each event `n` times within its timespan, applying `transform` cumulatively.
 *
 * Like `ply(n)` but instead of plain copies, each repetition applies `transform` one more
 * time: copy 0 is unmodified, copy 1 has `transform` applied once, copy 2 twice, and so on.
 * This creates escalating variations within each event's slot.
 *
 * @return A pattern with `n` progressively transformed copies of each event per slot.
 *
 * ```KlangScript(Playable)
 * note("c").plyWith(4) { it.add(7) }   // c, g, d5, a5 — each copy adds 7 semitones more
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").plyWith(3) { it.fast(2) }    // original, then 2x speed, then 4x speed in same slot
 * ```
 *
 * @alias plywith
 * @category tempo
 * @tags plyWith, repeat, transform, cumulative, subdivide
 */
@SprudelDsl
fun SprudelPattern.plyWith(factor: Int, transform: PatternMapperFn): SprudelPattern =
    this._plyWith(listOf(factor, transform).asSprudelDslArgs())

/** Repeats each event `n` times, applying `transform` cumulatively (0, 1, 2 … times). */
@SprudelDsl
fun String.plyWith(factor: Int, transform: PatternMapperFn): SprudelPattern =
    this._plyWith(listOf(factor, transform).asSprudelDslArgs())

/**
 * Returns a [PatternMapperFn] that repeats each event `n` times, applying `transform` cumulatively.
 *
 * ```KlangScript(Playable)
 * note("c").apply(plyWith(4) { it.add(7) })   // mapper form
 * ```
 *
 * @alias plywith
 * @category tempo
 * @tags plyWith, repeat, transform, cumulative, subdivide
 */
@SprudelDsl
fun plyWith(factor: Int, transform: PatternMapperFn): PatternMapperFn =
    _plyWith(listOf(factor, transform).asSprudelDslArgs())

/** Chains a plyWith operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.plyWith(factor: Int, transform: PatternMapperFn): PatternMapperFn =
    this._plyWith(listOf(factor, transform).asSprudelDslArgs())

/**
 * Alias for `plyWith`.
 *
 * @alias plyWith
 * @category tempo
 * @tags plywith, plyWith, repeat, transform, cumulative
 */
@SprudelDsl
fun SprudelPattern.plywith(factor: Int, transform: PatternMapperFn): SprudelPattern =
    this._plywith(listOf(factor, transform).asSprudelDslArgs())

/** Alias for [plyWith] on a string pattern. */
@SprudelDsl
fun String.plywith(factor: Int, transform: PatternMapperFn): SprudelPattern =
    this._plywith(listOf(factor, transform).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that is an alias for [plyWith]. */
@SprudelDsl
fun plywith(factor: Int, transform: PatternMapperFn): PatternMapperFn =
    _plywith(listOf(factor, transform).asSprudelDslArgs())

/** Alias for [PatternMapperFn.plyWith]. */
@SprudelDsl
fun PatternMapperFn.plywith(factor: Int, transform: PatternMapperFn): PatternMapperFn =
    this._plywith(listOf(factor, transform).asSprudelDslArgs())

// -- plyForEach() -----------------------------------------------------------------------------------------------------

fun applyPlyForEach(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val factorArg = args[0]
    val funcArg = args[1]

    // Extract the function from the argument
    val func = funcArg.value as? ((SprudelPattern, Int) -> SprudelPattern) ?: return pattern

    // Calculate new steps if factor is purely static
    val staticFactor = factorArg.value?.asRationalOrNull()?.toInt()

    val newSteps = if (staticFactor != null) {
        pattern.numSteps?.times(staticFactor)
    } else {
        null
    }

    val result = pattern._bindSqueeze { event ->
        val factor = staticFactor ?: event.data.value?.asRationalOrNull()?.toInt() ?: 1

        if (factor <= 0) {
            return@_bindSqueeze null
        }

        // Start with the original value, then add transformed versions for i = 1 to factor-1
        // Equivalent to: cat(pure(x), ...listRange(1, factor - 1).map((i) => func(pure(x), i)))
        val atomPattern = AtomicInfinitePattern(event.data)

        val patterns = buildList {
            // First pattern is the original
            add(atomPattern)
            // Then add transformed patterns for i = 1 to factor-1
            for (i in 1 until factor) {
                add(func(atomPattern, i))
            }
        }

        // Concatenate all patterns - SequencePattern squashes them into one cycle
        // _bindSqueeze will squeeze this into the event's timespan
        if (patterns.size == 1) patterns.first() else SequencePattern(patterns)
    }

    return if (newSteps != null) result.withSteps(newSteps) else result
}

internal val SprudelPattern._plyForEach by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPlyForEach(p, args)
}
internal val String._plyForEach by dslStringExtension { p, args, callInfo -> p._plyForEach(args, callInfo) }
internal val _plyForEach by dslPatternMapper { args, callInfo -> { p -> p._plyForEach(args, callInfo) } }
internal val PatternMapperFn._plyForEach by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_plyForEach(args, callInfo))
}

internal val SprudelPattern._plyforeach by dslPatternExtension { p, args, callInfo -> p._plyForEach(args, callInfo) }
internal val String._plyforeach by dslStringExtension { p, args, callInfo -> p._plyforeach(args, callInfo) }
internal val _plyforeach by dslPatternMapper { args, callInfo -> { p -> p._plyforeach(args, callInfo) } }
internal val PatternMapperFn._plyforeach by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_plyforeach(args, callInfo))
}

/**
 * Repeats each event `n` times within its timespan, passing the iteration index to `transform`.
 *
 * Similar to `plyWith` but the transform function receives both the pattern and the index
 * (0-based). Copy 0 is always unmodified; copies 1 through `n-1` receive their index so the
 * transform can produce index-specific variations. As a top-level function the third argument
 * is the source pattern.
 *
 * @return A pattern with `n` index-specific copies of each event per slot.
 *
 * ```KlangScript(Playable)
 * note("c").plyForEach(4) { pat, i -> pat.add(i * 2) }   // c, d, e, f# — index * 2 semitones
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").plyForEach(3) { pat, i -> pat.gain(1.0 - i * 0.3) }  // fading copies
 * ```
 *
 * @alias plyforeach
 * @category tempo
 * @tags plyForEach, repeat, transform, index, subdivide
 */
/** Repeats each event `n` times, passing the iteration index to `transform`. */
@SprudelDsl
fun SprudelPattern.plyForEach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): SprudelPattern =
    this._plyForEach(listOf(factor, transform).asSprudelDslArgs())

/** Repeats each event `n` times, passing the iteration index to `transform`. */
@SprudelDsl
fun String.plyForEach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): SprudelPattern =
    this._plyForEach(listOf(factor, transform).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that repeats each event `n` times, passing the index to `transform`. */
@SprudelDsl
fun plyForEach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): PatternMapperFn =
    _plyForEach(listOf(factor, transform).asSprudelDslArgs())

/** Chains a plyForEach operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.plyForEach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): PatternMapperFn =
    this._plyForEach(listOf(factor, transform).asSprudelDslArgs())

/**
 * Alias for `plyForEach`.
 *
 * @alias plyForEach
 * @category tempo
 * @tags plyforeach, plyForEach, repeat, transform, index
 */
/** Alias for [plyForEach] on this pattern. */
@SprudelDsl
fun SprudelPattern.plyforeach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): SprudelPattern =
    this._plyforeach(listOf(factor, transform).asSprudelDslArgs())

/** Alias for [plyForEach] on a string pattern. */
@SprudelDsl
fun String.plyforeach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): SprudelPattern =
    this._plyforeach(listOf(factor, transform).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that is an alias for [plyForEach]. */
@SprudelDsl
fun plyforeach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): PatternMapperFn =
    _plyforeach(listOf(factor, transform).asSprudelDslArgs())

/** Alias for [PatternMapperFn.plyForEach]. */
@SprudelDsl
fun PatternMapperFn.plyforeach(factor: Int, transform: (SprudelPattern, Int) -> SprudelPattern): PatternMapperFn =
    this._plyforeach(listOf(factor, transform).asSprudelDslArgs())

// -- hurry() ----------------------------------------------------------------------------------------------------------

fun applyHurry(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val factorArg = args.firstOrNull() ?: return pattern
    // 1. fast(factor)
    val spedUp = applyFast(pattern, listOf(factorArg))
    // 2. speed = speed * factor
    return spedUp._liftNumericField(listOf(factorArg)) { factor ->
        val f = factor ?: 1.0
        val currentSpeed = speed ?: 1.0
        copy(speed = currentSpeed * f)
    }
}

internal val SprudelPattern._hurry by dslPatternExtension { p, args, /* callInfo */ _ -> applyHurry(p, args) }
internal val String._hurry by dslStringExtension { p, args, callInfo -> p._hurry(args, callInfo) }
internal val _hurry by dslPatternMapper { args, callInfo -> { p -> p._hurry(args, callInfo) } }
internal val PatternMapperFn._hurry by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_hurry(args, callInfo))
}

/**
 * Speeds up the pattern like `fast()` and multiplies the `speed` audio parameter by the same factor.
 *
 * Unlike `fast()` which only changes the temporal density of events, `hurry()` also scales the
 * `speed` field (sample playback rate) so samples sound proportionally higher-pitched. This
 * mimics tape-speed acceleration. As a top-level function the second argument is the source pattern.
 *
 * @return A pattern sped up in both timing and sample playback rate.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh").hurry(2)              // twice as fast and samples pitch up by an octave
 * ```
 *
 * ```KlangScript(Playable)
 * s("bass:1").speed(0.5).hurry(2)     // existing speed 0.5 × hurry 2 = speed 1.0
 * ```
 *
 * @category tempo
 * @tags hurry, fast, speed, pitch, accelerate
 */
/** Speeds up pattern and multiplies the `speed` audio parameter by the same factor. */
@SprudelDsl
fun SprudelPattern.hurry(factor: PatternLike): SprudelPattern = this._hurry(listOf(factor).asSprudelDslArgs())

/** Speeds up pattern and multiplies the `speed` audio parameter by the same factor. */
@SprudelDsl
fun String.hurry(factor: PatternLike): SprudelPattern = this._hurry(listOf(factor).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that speeds up a pattern and multiplies the `speed` parameter. */
@SprudelDsl
fun hurry(factor: PatternLike): PatternMapperFn = _hurry(listOf(factor).asSprudelDslArgs())

/** Chains a hurry operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.hurry(factor: PatternLike): PatternMapperFn = this._hurry(listOf(factor).asSprudelDslArgs())

// -- fastGap() --------------------------------------------------------------------------------------------------------

fun applyFastGap(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val factorProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(Rational.ONE.asVoiceValue())

    return FastGapPattern(source = pattern, factorProvider = factorProvider)
}

internal val SprudelPattern._fastGap by dslPatternExtension { p, args, /* callInfo */ _ -> applyFastGap(p, args) }
internal val String._fastGap by dslStringExtension { p, args, callInfo -> p._fastGap(args, callInfo) }
internal val _fastGap by dslPatternMapper { args, callInfo -> { p -> p._fastGap(args, callInfo) } }
internal val PatternMapperFn._fastGap by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_fastGap(args, callInfo))
}

internal val SprudelPattern._densityGap by dslPatternExtension { p, args, callInfo -> p._fastGap(args, callInfo) }
internal val String._densityGap by dslStringExtension { p, args, callInfo -> p._densityGap(args, callInfo) }
internal val _densityGap by dslPatternMapper { args, callInfo -> { p -> p._densityGap(args, callInfo) } }
internal val PatternMapperFn._densityGap by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_densityGap(args, callInfo))
}

/**
 * Speeds up the pattern by `factor` but plays it only once per cycle, leaving a gap.
 *
 * Unlike `fast(n)` which tiles the pattern `n` times to fill the cycle, `fastGap(n)` compresses
 * the pattern into the first `1/n` of the cycle and leaves silence in the remaining space. As a
 * top-level function the second argument is the source pattern.
 *
 * @return A pattern compressed into the first `1/factor` of each cycle with silence after.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").fastGap(2)         // 4 events in first half, silence in second half
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g").fastGap(4)        // all events squeezed into first quarter
 * ```
 *
 * @alias densityGap
 * @category tempo
 * @tags fastGap, fast, gap, silence, compress, density
 */
/** Speeds up the pattern but plays it only once per cycle, leaving a gap. */
@SprudelDsl
fun SprudelPattern.fastGap(factor: PatternLike): SprudelPattern = this._fastGap(listOf(factor).asSprudelDslArgs())

/** Speeds up the pattern but plays it only once per cycle, leaving a gap. */
@SprudelDsl
fun String.fastGap(factor: PatternLike): SprudelPattern = this._fastGap(listOf(factor).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that speeds up a pattern but plays it only once per cycle. */
@SprudelDsl
fun fastGap(factor: PatternLike): PatternMapperFn = _fastGap(listOf(factor).asSprudelDslArgs())

/** Chains a fastGap operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.fastGap(factor: PatternLike): PatternMapperFn = this._fastGap(listOf(factor).asSprudelDslArgs())

/** Alias for [fastGap]. */
@SprudelDsl
fun SprudelPattern.densityGap(factor: PatternLike): SprudelPattern =
    this._densityGap(listOf(factor).asSprudelDslArgs())

/** Alias for [fastGap]. */
@SprudelDsl
fun String.densityGap(factor: PatternLike): SprudelPattern = this._densityGap(listOf(factor).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that is an alias for [fastGap]. */
@SprudelDsl
fun densityGap(factor: PatternLike): PatternMapperFn = _densityGap(listOf(factor).asSprudelDslArgs())

/** Alias for [PatternMapperFn.fastGap]. */
@SprudelDsl
fun PatternMapperFn.densityGap(factor: PatternLike): PatternMapperFn =
    this._densityGap(listOf(factor).asSprudelDslArgs())

// -- inside() ---------------------------------------------------------------------------------------------------------

fun applyInside(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) return pattern

    val factorArg = args[0]
    val func = args[1].toPatternMapper() ?: return pattern

    // Use apply functions directly to avoid double-wrapping SprudelDslArg
    val slowed = applySlow(pattern, listOf(factorArg))
    val transformed = func(slowed)

    return applyFast(transformed, listOf(factorArg))
}

internal val SprudelPattern._inside by dslPatternExtension { p, args, /* callInfo */ _ -> applyInside(p, args) }
internal val String._inside by dslStringExtension { p, args, callInfo -> p._inside(args, callInfo) }
internal val _inside by dslPatternMapper { args, callInfo -> { p -> p._inside(args, callInfo) } }
internal val PatternMapperFn._inside by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_inside(args, callInfo))
}

/**
 * Applies a transformation inside a zoomed-in view of the cycle.
 *
 * Slows the pattern by `factor`, applies `transform`, then speeds it back up to the original
 * tempo. The net effect is that `transform` sees a pattern spread over `factor` cycles,
 * allowing operations like `rev()` to work across a larger musical phrase while the result
 * still fits in one cycle.
 *
 * ```KlangScript(Playable)
 * note("0 1 2 3").inside(4) { it.rev() }       // reverse across 4-cycle span, then compress back
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").inside(2) { it.slow(2) }    // double-slow inside = no net change in tempo
 * ```
 *
 * @category tempo
 * @tags inside, transform, zoom, slow, fast
 */
@SprudelDsl
fun SprudelPattern.inside(factor: PatternLike, transform: PatternMapperFn): SprudelPattern =
    this._inside(listOf(factor, transform).asSprudelDslArgs())

/** Applies a transformation inside a zoomed-in view of the cycle. */
@SprudelDsl
fun String.inside(factor: PatternLike, transform: PatternMapperFn): SprudelPattern =
    this._inside(listOf(factor, transform).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies a transformation inside a zoomed-in view of the cycle. */
@SprudelDsl
fun inside(factor: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    _inside(listOf(factor, transform).asSprudelDslArgs())

/** Chains an inside operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.inside(factor: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    this._inside(listOf(factor, transform).asSprudelDslArgs())

// -- outside() --------------------------------------------------------------------------------------------------------

fun applyOutside(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.size < 2) {
        return pattern
    }

    // TODO: support control pattern for "factor"
    val factor = args[0].value?.asRationalOrNull() ?: Rational.ONE

    val func = args[1].toPatternMapper() ?: return pattern

    val sped = applyFast(pattern, listOf(SprudelDslArg.of(factor)))
    val transformed = func(sped)

    return applySlow(transformed, listOf(SprudelDslArg.of(factor)))
}

internal val SprudelPattern._outside by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyOutside(p, args)
}
internal val String._outside by dslStringExtension { p, args, callInfo -> p._outside(args, callInfo) }
internal val _outside by dslPatternMapper { args, callInfo -> { p -> p._outside(args, callInfo) } }
internal val PatternMapperFn._outside by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_outside(args, callInfo))
}

/**
 * Applies a transformation outside the current cycle, across a wider temporal context.
 *
 * Speeds the pattern by `factor`, applies `transform`, then slows it back down. The net effect
 * is that `transform` sees only `1/factor` of the original pattern per cycle, allowing
 * operations like `rev()` to work on a globally coarser time scale.
 *
 * ```KlangScript(Playable)
 * note("0 1 2 3").outside(4) { it.rev() }      // reverse on 1/4 speed, then speed back up
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh cp").outside(2) { it.fast(2) }   // double-fast outside = no net change in tempo
 * ```
 *
 * @category tempo
 * @tags outside, transform, zoom, fast, slow
 */
@SprudelDsl
fun SprudelPattern.outside(factor: PatternLike, transform: PatternMapperFn): SprudelPattern =
    this._outside(listOf(factor, transform).asSprudelDslArgs())

/** Applies a transformation outside the current cycle on this string pattern. */
@SprudelDsl
fun String.outside(factor: PatternLike, transform: PatternMapperFn): SprudelPattern =
    this._outside(listOf(factor, transform).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies a transformation outside the current cycle. */
@SprudelDsl
fun outside(factor: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    _outside(listOf(factor, transform).asSprudelDslArgs())

/** Chains an outside operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.outside(factor: PatternLike, transform: PatternMapperFn): PatternMapperFn =
    this._outside(listOf(factor, transform).asSprudelDslArgs())

// -- swingBy() --------------------------------------------------------------------------------------------------------

fun applySwingBy(pattern: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return pattern._innerJoin(args) { source, swing, n ->
        val swingValue = swing?.asRational ?: return@_innerJoin silence
        val nVal = n?.asRational ?: Rational.ONE

        SwingPattern(source = source, swing = swingValue, n = nVal)
    }
}

internal val SprudelPattern._swingBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySwingBy(p, args)
}
internal val String._swingBy by dslStringExtension { p, args, callInfo -> p._swingBy(args, callInfo) }
internal val _swingBy by dslPatternMapper { args, callInfo -> { p -> p._swingBy(args, callInfo) } }
internal val PatternMapperFn._swingBy by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_swingBy(args, callInfo))
}

/**
 * Creates a swing or shuffle rhythm by adjusting event timing and duration within subdivisions.
 *
 * Divides each cycle into `n` subdivisions. Within each subdivision, events are split into
 * two groups — first half gets `(1 + swing) / 2` of the slot duration, second half gets
 * `(1 - swing) / 2`. This creates a natural rhythmic feel without overlapping events.
 *
 * - Positive swing (e.g. 1/3): "long-short" pattern — classic jazz swing
 * - Negative swing (e.g. -1/3): "short-long" pattern — reverse swing
 * - Zero swing: equal durations (no effect)
 *
 * @param swing Swing amount in the range -1 to 1.
 * @param n Number of subdivisions per cycle.
 *
 * ```KlangScript(Playable)
 * s("hh*8").swingBy(1/3, 4)               // classic swing on hi-hats, 4 subdivisions
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f").swingBy(-0.25, 2)       // reverse swing on notes, 2 subdivisions
 * ```
 *
 * @category tempo
 * @tags swingBy, swing, shuffle, rhythm, timing, groove
 */
@SprudelDsl
fun SprudelPattern.swingBy(swing: PatternLike, n: PatternLike): SprudelPattern =
    this._swingBy(listOf(swing, n).asSprudelDslArgs())

/** Creates a swing rhythm with custom amount; see `swingBy` for details. */
@SprudelDsl
fun String.swingBy(swing: PatternLike, n: PatternLike): SprudelPattern =
    this._swingBy(listOf(swing, n).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that creates a swing or shuffle rhythm. */
@SprudelDsl
fun swingBy(swing: PatternLike, n: PatternLike): PatternMapperFn =
    _swingBy(listOf(swing, n).asSprudelDslArgs())

/** Chains a swingBy operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.swingBy(swing: PatternLike, n: PatternLike): PatternMapperFn =
    this._swingBy(listOf(swing, n).asSprudelDslArgs())

// -- swing() ----------------------------------------------------------------------------------------------------------

internal val SprudelPattern._swing by dslPatternExtension { p, args, /* callInfo */ _ ->
    if (args.isEmpty()) {
        return@dslPatternExtension p
    }

    val nArg = args.getOrNull(0)

    // swing(n) = swingBy(1/3, n)
    applySwingBy(p, listOf(SprudelDslArg.of(1.0 / 3.0), nArg ?: SprudelDslArg.of(1.0)))
}
internal val String._swing by dslStringExtension { p, args, callInfo -> p._swing(args, callInfo) }
internal val _swing by dslPatternMapper { args, callInfo -> { p -> p._swing(args, callInfo) } }
internal val PatternMapperFn._swing by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_swing(args, callInfo))
}

/**
 * Shorthand for `swingBy(1/3, n)` — classic jazz swing feel.
 *
 * Applies a 1/3 swing amount across `n` subdivisions per cycle. Equivalent to
 * `swingBy(1/3, n)`, creating the characteristic "long-short" groove.
 *
 * @param n Number of subdivisions per cycle.
 *
 * ```KlangScript(Playable)
 * s("hh*8").swing(4)                  // classic swing on hi-hats, 4 subdivisions
 * ```
 *
 * ```KlangScript(Playable)
 * note("c d e f g a b c").swing(2)    // swing feel on a melody, 2 subdivisions
 * ```
 *
 * @category tempo
 * @tags swing, swingBy, shuffle, rhythm, timing, groove
 */
@SprudelDsl
fun SprudelPattern.swing(n: PatternLike): SprudelPattern = this._swing(listOf(n).asSprudelDslArgs())

/** Shorthand for `swingBy(1/3, n)` — classic jazz swing feel. */
@SprudelDsl
fun String.swing(n: PatternLike): SprudelPattern = this._swing(listOf(n).asSprudelDslArgs())

/** Returns a [PatternMapperFn] that applies classic jazz swing feel. */
@SprudelDsl
fun swing(n: PatternLike): PatternMapperFn = _swing(listOf(n).asSprudelDslArgs())

/** Chains a swing operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.swing(n: PatternLike): PatternMapperFn = this._swing(listOf(n).asSprudelDslArgs())

// -- brak() -----------------------------------------------------------------------------------------------------------

/**
 * Makes every other cycle syncopated (breakbeat-style).
 *
 * JavaScript: `pat.when(slowcat(false, true), (x) => fastcat(x, silence)._late(0.25))`
 *
 * Effect:
 * - Cycle 0: plays normally
 * - Cycle 1: plays first half then silence, delayed by 0.25 cycles (syncopation)
 * - Cycle 2: plays normally
 * - Cycle 3: syncopated
 * - etc.
 */
fun applyBrak(pattern: SprudelPattern): SprudelPattern {
    // when(slowcat(false, true), x => fastcat(x, silence).late(0.25))
    val condition = applyCat(
        listOf(
            pure(false),
            pure(true)
        )
    )

    return pattern.`when`(condition) { x ->
        fastcat(x, silence).late(0.25.toRational())
    }
}

internal val SprudelPattern._brak by dslPatternExtension { p, /* args */ _, /* callInfo */ _ -> applyBrak(p) }
internal val String._brak by dslStringExtension { p, args, callInfo -> p._brak(args, callInfo) }
internal val _brak by dslPatternMapper { args, callInfo -> { p -> p._brak(args, callInfo) } }
internal val PatternMapperFn._brak by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_brak(args, callInfo))
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
@SprudelDsl
fun SprudelPattern.brak(): SprudelPattern = this._brak(emptyList())

/** Makes every other cycle syncopated — a classic breakbeat effect. */
@SprudelDsl
fun String.brak(): SprudelPattern = this._brak(emptyList())

/** Returns a [PatternMapperFn] that makes every other cycle syncopated. */
@SprudelDsl
fun brak(): PatternMapperFn = _brak(emptyList())

/** Chains a brak operation onto this [PatternMapperFn]. */
@SprudelDsl
fun PatternMapperFn.brak(): PatternMapperFn = this._brak(emptyList())
