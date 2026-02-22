@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.*
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.addons.lateInCycle
import io.peekandpoke.klang.strudel.lang.addons.stretchBy
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.pattern.*

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangTempoInit = false

// Helpers /////////////////////////////////////////////////////////////////////////////////////////////////////////////

fun applyTimeShift(
    pattern: StrudelPattern,
    args: List<StrudelDslArg<Any?>>,
    factor: Rational = Rational.ONE,
): StrudelPattern {
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

fun applySlow(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
        return object : StrudelPattern by result {
            override fun estimateCycleDuration(): Rational =
                pattern.estimateCycleDuration() * staticFactor
        }
    }

    return result
}

internal val _slow by dslPatternFunction { args, /* callInfo */ _ ->
    val factorArg: StrudelDslArg<Any?>
    val sourceParts: List<StrudelDslArg<Any?>>

    // Heuristic: If >1 args, the first one is the factor, the rest is the source.
    // If only 1 arg, it is treated as the source (with factor 1.0).
    if (args.size > 1) {
        factorArg = args[0]
        sourceParts = args.drop(1)
    } else {
        factorArg = StrudelDslArg(1.0, null)
        sourceParts = args
    }

    val source = sourceParts.toPattern(voiceValueModifier)
    applySlow(source, listOf(factorArg))
}

internal val StrudelPattern._slow by dslPatternExtension { p, args, /* callInfo */ _ -> applySlow(p, args) }
internal val String._slow by dslStringExtension { p, args, callInfo -> p._slow(args, callInfo) }

/**
 * Slows down a pattern by the given factor.
 *
 * `slow(2)` stretches the pattern so it takes 2 cycles to complete. As a top-level function,
 * the first argument is the factor and the second is the pattern. As a method, applies to the
 * receiver pattern. Accepts control patterns for the factor.
 *
 * @return A pattern slowed by `factor`.
 *
 * ```KlangScript
 * s("bd sd hh cp").slow(2)              // half tempo — pattern spans 2 cycles
 * ```
 *
 * ```KlangScript
 * slow(2, s("bd sd hh cp"))             // top-level form with explicit source pattern
 * ```
 *
 * ```KlangScript
 * s("bd sd").slow("<1 2 4>")            // varying slow factor each cycle
 * ```
 *
 * @category tempo
 * @tags slow, tempo, stretch, speed
 */
@StrudelDsl
fun slow(factor: PatternLike, pattern: PatternLike): StrudelPattern =
    _slow(listOf(factor, pattern).asStrudelDslArgs())

/** Slows down this pattern by the given factor. */
@StrudelDsl
fun StrudelPattern.slow(factor: PatternLike): StrudelPattern = this._slow(listOf(factor).asStrudelDslArgs())

/** Slows down this string pattern by the given factor. */
@StrudelDsl
fun String.slow(factor: PatternLike): StrudelPattern = this._slow(listOf(factor).asStrudelDslArgs())

// -- fast() -----------------------------------------------------------------------------------------------------------

fun applyFast(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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
        return object : StrudelPattern by result {
            override fun estimateCycleDuration(): Rational =
                pattern.estimateCycleDuration() / staticFactor
        }
    }

    return result
}

internal val _fast by dslPatternFunction { args, /* callInfo */ _ ->
    val factorArg: StrudelDslArg<Any?>
    val sourceParts: List<StrudelDslArg<Any?>>

    if (args.size > 1) {
        factorArg = args[0]
        sourceParts = args.drop(1)
    } else {
        factorArg = StrudelDslArg(1.0, null)
        sourceParts = args
    }

    val source = sourceParts.toPattern(voiceValueModifier)
    applyFast(source, listOf(factorArg))
}

internal val StrudelPattern._fast by dslPatternExtension { p, args, /* callInfo */ _ -> applyFast(p, args) }

internal val String._fast by dslStringExtension { p, args, callInfo -> p._fast(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Speeds up the pattern by the given factor.
 *
 * `fast(2)` plays the pattern twice per cycle. Accepts mini-notation strings and control patterns.
 *
 * @param factor Speed-up factor. Values > 1 play faster; values < 1 play slower.
 * @param pattern The pattern to speed up (top-level call only).
 * @return A pattern sped up by `factor`.
 *
 * ```KlangScript
 * note("c d e f").fast(2)           // 8 events per cycle instead of 4
 * ```
 *
 * ```KlangScript
 * s("bd sd hh").fast("<1 2 4>")     // varying speed each cycle
 * ```
 * @category tempo
 * @tags fast, speed, tempo, accelerate
 */
@StrudelDsl
fun fast(factor: PatternLike, pattern: PatternLike): StrudelPattern =
    _fast(listOf(factor, pattern).asStrudelDslArgs())

/** Speeds up the pattern by `factor`. */
@StrudelDsl
fun StrudelPattern.fast(factor: PatternLike): StrudelPattern =
    this._fast(listOf(factor).asStrudelDslArgs())

/** Speeds up the mini-notation string pattern by `factor`. */
@StrudelDsl
fun String.fast(factor: PatternLike): StrudelPattern =
    this._fast(listOf(factor).asStrudelDslArgs())

// -- rev() ------------------------------------------------------------------------------------------------------------

fun applyRev(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(Rational.ONE.asVoiceValue())

    return ReversePattern(inner = pattern, nProvider = nProvider)
}

internal val _rev by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.map { it.value }.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslPatternFunction silence

    applyRev(pattern, args.take(1))
}

internal val StrudelPattern._rev by dslPatternExtension { p, args, /* callInfo */ _ -> applyRev(p, args) }
internal val String._rev by dslStringExtension { p, args, callInfo -> p._rev(args, callInfo) }

/**
 * Reverses the order of events within each cycle (or across `n` cycles when given an argument).
 *
 * With no argument, each individual cycle plays its events in reverse order. With an integer `n`,
 * the reversal is applied across every `n`-cycle span — useful for longer retrograde effects.
 * Accepts control patterns for the cycle count.
 *
 * @return A pattern with events reversed per cycle (or per `n`-cycle group).
 *
 * ```KlangScript
 * stack(
 *   s("bd hh sd hh hh cp"),          // bd sd hh cp
 *   s("bd hh sd hh hh cp").rev()     // cp hh sd bd — reversed each cycle
 * )
 * ```
 *
 * ```KlangScript
 * note("c d e f").rev()                     // f e d c per cycle
 * ```
 *
 * ```KlangScript
 * note("<[c d] [e f]>").rev(1)              // reverses across every 1-cycle span
 * ```
 *
 * ```KlangScript
 * note("<[c d] [e f]>").rev(2)              // reverses across every 2-cycle span
 * ```
 *
 * @category tempo
 * @tags rev, reverse, order, retrograde
 */
@StrudelDsl
fun StrudelPattern.rev(n: PatternLike = 1): StrudelPattern = this._rev(listOf(n).asStrudelDslArgs())

/**
 * Reverses the order of events, applying the reversal across every `n`-cycle span.
 * @param n The number of cycles to reverse across
 */
@StrudelDsl
fun String.rev(n: PatternLike = 1): StrudelPattern = this._rev(listOf(n).asStrudelDslArgs())

/**
 * Reverses the order of events, applying the reversal across every `n`-cycle span.
 * @param pattern The pattern to reverse
 * @param n The number of cycles to reverse across
 */
@StrudelDsl
fun rev(pattern: PatternLike, n: PatternLike = 1) = _rev(listOf(pattern, n).asStrudelDslArgs())

// -- revv() -----------------------------------------------------------------------------------------------------------

// TODO: deep checks ... does not seem to work to well for complex patterns
fun applyRevv(pattern: StrudelPattern): StrudelPattern {
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

internal val _revv by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.map { it.value }.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslPatternFunction silence

    applyRevv(pattern)
}

internal val StrudelPattern._revv by dslPatternExtension { p, /* args */ _, /* callInfo */ _ -> applyRevv(p) }
internal val String._revv by dslStringExtension { p, args, callInfo -> p._revv(args, callInfo) }

/**
 * Reverses the pattern in absolute time across all cycles.
 *
 * Unlike `rev()` which reverses each cycle independently, `revv()` negates the time axis
 * globally: cycle N becomes cycle -N, so a long phrase is played completely backwards in
 * absolute time. Useful for true retrograde playback of multi-cycle phrases.
 *
 * @return A globally time-reversed version of the pattern.
 *
 * ```KlangScript
 * note("c d e f").revv()              // plays f e d c in absolute negative time direction
 * ```
 *
 * @category tempo
 * @tags revv, reverse, retrograde, time, global
 */
@StrudelDsl
fun StrudelPattern.revv(): StrudelPattern = this._revv(emptyList())

/** Reverses the pattern in absolute time across all cycles. */
@StrudelDsl
fun String.revv(): StrudelPattern = this._revv(emptyList())

/**
 * Reverses the pattern in absolute time across all cycles.
 * @param pattern The pattern to reverse
 */
@StrudelDsl
fun revv(pattern: PatternLike) = _revv(listOf(pattern).asStrudelDslArgs())

// -- palindrome() -----------------------------------------------------------------------------------------------------

fun applyPalindrome(pattern: StrudelPattern): StrudelPattern {
    // Palindrome needs to play the pattern forward, then backward.
    // Critically, it must use ABSOLUTE time (Prime behavior) so that the 'rev' version
    // reverses the content of the *second* cycle, not the first cycle played again.
    // Matches JS: pat.lastOf(2, rev) -> equivalent to slowcatPrime(pat, rev(pat))
    return applySlowcatPrime(listOf(pattern, applyRev(pattern, listOf(StrudelDslArg(1, null)))))
}

internal val _palindrome by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.map { it.value }.filterIsInstance<StrudelPattern>().firstOrNull()
        ?: return@dslPatternFunction silence

    applyPalindrome(pattern)
}

internal val StrudelPattern._palindrome by dslPatternExtension { p, /* args */ _, /* callInfo */ _ ->
    applyPalindrome(p)
}

internal val String._palindrome by dslStringExtension { p, args, callInfo -> p._palindrome(args, callInfo) }

/**
 * Plays the pattern forward then backward, creating a two-cycle palindrome.
 *
 * Cycle 0 plays the original pattern; cycle 1 plays the reversed pattern. The reversal
 * uses absolute time so the second half mirrors the first half correctly over the full
 * phrase, not just a single cycle.
 *
 * @return A two-cycle palindrome pattern.
 *
 * ```KlangScript
 * note("c d e f").palindrome()        // c d e f ... f e d c ... c d e f ...
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").palindrome()       // forward drum loop then reversed drum loop
 * ```
 *
 * @category tempo
 * @tags palindrome, reverse, mirror, order, retrograde
 */
@StrudelDsl
fun StrudelPattern.palindrome(): StrudelPattern = this._palindrome(emptyList())

/** Plays the pattern forward then backward, creating a two-cycle palindrome. */
@StrudelDsl
fun String.palindrome(): StrudelPattern = this._palindrome(emptyList())

/** Plays the pattern forward then backward, creating a two-cycle palindrome. */
@StrudelDsl
fun palindrome(pattern: PatternLike): StrudelPattern = _palindrome(listOf(pattern).asStrudelDslArgs())

// -- early() ----------------------------------------------------------------------------------------------------------

internal val _early by dslPatternFunction { /* args */ _, /* callInfo */ _ -> silence }

internal val StrudelPattern._early by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyTimeShift(pattern = p, args = args, factor = Rational.MINUS_ONE)
}

internal val String._early by dslStringExtension { p, args, callInfo -> p._early(args, callInfo) }

/**
 * Nudges the pattern to start earlier by the given number of cycles.
 *
 * Shifts all events backward in time by the specified amount. For example, `early(0.5)` moves
 * every event half a cycle earlier so that what was at position 0.5 now appears at position 0.
 * Useful for creating syncopation or aligning patterns that are slightly off-beat.
 *
 * @return A pattern shifted earlier by the given number of cycles.
 *
 * ```KlangScript
 * note("c d e f").early(0.25)         // shifts the pattern a quarter cycle earlier
 * ```
 *
 * ```KlangScript
 * s("bd sd").stack(s("hh*4").early(0.125))   // hi-hat slightly ahead of the beat
 * ```
 *
 * @category tempo
 * @tags early, shift, time, offset, nudge, ahead
 */
@StrudelDsl
fun StrudelPattern.early(amount: PatternLike): StrudelPattern = this._early(listOf(amount).asStrudelDslArgs())

/** Nudges the pattern to start earlier by the given number of cycles. */
@StrudelDsl
fun String.early(amount: PatternLike): StrudelPattern = this._early(listOf(amount).asStrudelDslArgs())

// -- late() -----------------------------------------------------------------------------------------------------------

internal val StrudelPattern._late by dslPatternExtension { p, args, _ ->
    applyTimeShift(pattern = p, args = args, factor = Rational.ONE)
}

internal val String._late by dslStringExtension { p, args, callInfo -> p._late(args, callInfo) }

internal val _late by dslPatternFunction { /* args */ _, /* callInfo */ _ -> silence }

/**
 * Nudges the pattern to start later by the given number of cycles.
 *
 * Shifts all events forward in time by the specified amount. For example, `late(0.5)` moves
 * every event half a cycle later so that what was at position 0 now appears at position 0.5.
 * Useful for creating delay effects or adjusting phase relationships between patterns.
 *
 * @return A pattern shifted later by the given number of cycles.
 *
 * ```KlangScript
 * note("c d e f").late(0.25)          // shifts the pattern a quarter cycle later
 * ```
 *
 * ```KlangScript
 * s("bd sd").stack(s("hh*4").late(0.125))    // hi-hat slightly behind the beat
 * ```
 *
 * @category tempo
 * @tags late, shift, time, offset, nudge, delay, behind
 */
@StrudelDsl
fun StrudelPattern.late(amount: PatternLike): StrudelPattern = this._late(listOf(amount).asStrudelDslArgs())

/** Nudges the pattern to start later by the given number of cycles. */
@StrudelDsl
fun String.late(amount: PatternLike): StrudelPattern = this._late(listOf(amount).asStrudelDslArgs())

// -- compress() -------------------------------------------------------------------------------------------------------

fun applyCompress(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _compress by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslPatternFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyCompress(pattern, args.take(2))
}

internal val StrudelPattern._compress by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCompress(p, args)
}

internal val String._compress by dslStringExtension { p, args, callInfo -> p._compress(args, callInfo) }

/**
 * Compresses the pattern into a sub-range of each cycle, leaving silence outside that range.
 *
 * `compress(start, end)` squeezes the full pattern into the window `[start, end]` within
 * each cycle and leaves a gap everywhere else. Both values are in the range 0–1. As a
 * top-level function the third argument is the pattern to compress.
 *
 * @return A pattern compressed into `[start, end]` with silence elsewhere.
 *
 * ```KlangScript
 * note("c d e f").compress(0, 0.5)        // all 4 events fit into first half of each cycle
 * ```
 *
 * ```KlangScript
 * s("bd sd").compress(0.25, 0.75)         // pattern compressed into middle 50% of each cycle
 * ```
 *
 * ```KlangScript
 * compress(0, 0.5, note("c d e f"))       // top-level form
 * ```
 *
 * @category tempo
 * @tags compress, squeeze, timespan, gap, range
 */
@StrudelDsl
fun compress(start: PatternLike, end: PatternLike, pattern: PatternLike): StrudelPattern =
    _compress(listOf(start, end, pattern).asStrudelDslArgs())

/** Compresses the pattern into `[start, end]` within each cycle, leaving silence outside. */
@StrudelDsl
fun StrudelPattern.compress(start: PatternLike, end: PatternLike): StrudelPattern =
    this._compress(listOf(start, end).asStrudelDslArgs())

/** Compresses the pattern into `[start, end]` within each cycle, leaving silence outside. */
@StrudelDsl
fun String.compress(start: PatternLike, end: PatternLike): StrudelPattern =
    this._compress(listOf(start, end).asStrudelDslArgs())

// -- focus() ----------------------------------------------------------------------------------------------------------

fun applyFocus(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _focus by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslPatternFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyFocus(pattern, args.take(2))
}

internal val StrudelPattern._focus by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyFocus(p, args)
}

internal val String._focus by dslStringExtension { p, args, callInfo -> p._focus(args, callInfo) }

/**
 * Zooms in on a sub-range of a cycle, stretching that portion to fill the whole cycle.
 *
 * `focus(start, end)` is like the inverse of `compress`: it takes the slice `[start, end]`
 * of the original pattern and stretches it to fill a full cycle. As a top-level function the
 * third argument is the pattern to focus.
 *
 * @return A pattern that zooms into `[start, end]`, stretching it to fill each cycle.
 *
 * ```KlangScript
 * note("c d e f").focus(0, 0.5)       // only the first half is shown, stretched to a full cycle
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").focus(0.25, 0.75)  // middle 50% of the pattern, stretched to fill the cycle
 * ```
 *
 * ```KlangScript
 * focus(0, 0.5, note("c d e f"))      // top-level form
 * ```
 *
 * @category tempo
 * @tags focus, zoom, timespan, range, stretch
 */
@StrudelDsl
fun focus(start: PatternLike, end: PatternLike, pattern: PatternLike): StrudelPattern =
    _focus(listOf(start, end, pattern).asStrudelDslArgs())

/** Zooms in on `[start, end]` of a cycle and stretches that portion to fill each cycle. */
@StrudelDsl
fun StrudelPattern.focus(start: PatternLike, end: PatternLike): StrudelPattern =
    this._focus(listOf(start, end).asStrudelDslArgs())

/** Zooms in on `[start, end]` of a cycle and stretches that portion to fill each cycle. */
@StrudelDsl
fun String.focus(start: PatternLike, end: PatternLike): StrudelPattern =
    this._focus(listOf(start, end).asStrudelDslArgs())

// -- ply() ------------------------------------------------------------------------------------------------------------

fun applyPly(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _ply by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslPatternFunction silence
    }

    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyPly(pattern, args.take(1))
}

internal val StrudelPattern._ply by dslPatternExtension { p, args, /* callInfo */ _ -> applyPly(p, args) }
internal val String._ply by dslStringExtension { p, args, callInfo -> p._ply(args, callInfo) }

/**
 * Repeats each event `n` times within its original timespan.
 *
 * Each event in the pattern is subdivided into `n` equal copies squeezed into the same
 * duration. For example, `ply(3)` on a 2-event pattern produces 6 events: 3 copies of the
 * first event followed by 3 copies of the second. Accepts control patterns for `n`.
 *
 * @return A pattern with each event repeated `n` times within its timespan.
 *
 * ```KlangScript
 * note("c d").ply(3)                  // c c c d d d — 6 events squeezed into 1 cycle
 * ```
 *
 * ```KlangScript
 * s("bd sd hh").ply(2)                // each hit played twice in its slot
 * ```
 *
 * ```KlangScript
 * note("c d e f").ply("<1 2 4>")      // varying subdivision each cycle
 * ```
 *
 * @category tempo
 * @tags ply, repeat, subdivide, multiply, density
 */
@StrudelDsl
fun ply(n: PatternLike, pattern: PatternLike): StrudelPattern = _ply(listOf(n, pattern).asStrudelDslArgs())

/** Repeats each event `n` times within its original timespan. */
@StrudelDsl
fun StrudelPattern.ply(n: PatternLike): StrudelPattern = this._ply(listOf(n).asStrudelDslArgs())

/** Repeats each event `n` times within its original timespan. */
@StrudelDsl
fun String.ply(n: PatternLike): StrudelPattern = this._ply(listOf(n).asStrudelDslArgs())

// -- plyWith() --------------------------------------------------------------------------------------------------------

/**
 * Helper function to apply a function n times to a value.
 * Equivalent to JS applyN(n, func, p).
 */
fun applyFunctionNTimes(n: Int, func: PatternMapper, pattern: StrudelPattern): StrudelPattern {
    var result = pattern
    repeat(n) {
        result = func(result)
    }
    return result
}

fun applyPlyWith(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _plyWith by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslPatternFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyPlyWith(pattern, args.take(2))
}

internal val StrudelPattern._plyWith by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPlyWith(p, args)
}

internal val String._plyWith by dslStringExtension { p, args, callInfo -> p._plyWith(args, callInfo) }

internal val _plywith by dslPatternFunction { args, callInfo -> _plyWith(args, callInfo) }
internal val StrudelPattern._plywith by dslPatternExtension { p, args, callInfo -> p._plyWith(args, callInfo) }
internal val String._plywith by dslStringExtension { p, args, callInfo -> p._plywith(args, callInfo) }

/**
 * Repeats each event `n` times within its timespan, applying `transform` cumulatively.
 *
 * Like `ply(n)` but instead of plain copies, each repetition applies `transform` one more
 * time: copy 0 is unmodified, copy 1 has `transform` applied once, copy 2 twice, and so on.
 * This creates escalating variations within each event's slot. As a top-level function the
 * third argument is the source pattern.
 *
 * @return A pattern with `n` progressively transformed copies of each event per slot.
 *
 * ```KlangScript
 * note("c").plyWith(4) { it.add(7) }   // c, g, d5, a5 — each copy adds 7 semitones more
 * ```
 *
 * ```KlangScript
 * s("bd").plyWith(3) { it.fast(2) }    // original, then 2x speed, then 4x speed in same slot
 * ```
 *
 * @alias plywith
 * @category tempo
 * @tags plyWith, repeat, transform, cumulative, subdivide
 */
@StrudelDsl
fun plyWith(factor: Int, transform: PatternMapper, pattern: PatternLike): StrudelPattern =
    _plyWith(listOf(factor, transform, pattern).asStrudelDslArgs())

/** Repeats each event `n` times, applying `transform` cumulatively (0, 1, 2 … times). */
@StrudelDsl
fun StrudelPattern.plyWith(factor: Int, transform: PatternMapper): StrudelPattern =
    this._plyWith(listOf(factor, transform).asStrudelDslArgs())

/** Repeats each event `n` times, applying `transform` cumulatively (0, 1, 2 … times). */
@StrudelDsl
fun String.plyWith(factor: Int, transform: PatternMapper): StrudelPattern =
    this._plyWith(listOf(factor, transform).asStrudelDslArgs())

/**
 * Alias for `plyWith`.
 *
 * @alias plyWith
 * @category tempo
 * @tags plywith, plyWith, repeat, transform, cumulative
 */
@StrudelDsl
fun plywith(factor: Int, transform: PatternMapper, pattern: PatternLike): StrudelPattern =
    _plywith(listOf(factor, transform, pattern).asStrudelDslArgs())

/** Alias for [plyWith] on this pattern. */
@StrudelDsl
fun StrudelPattern.plywith(factor: Int, transform: PatternMapper): StrudelPattern =
    this._plywith(listOf(factor, transform).asStrudelDslArgs())

/** Alias for [plyWith] on a string pattern. */
@StrudelDsl
fun String.plywith(factor: Int, transform: PatternMapper): StrudelPattern =
    this._plywith(listOf(factor, transform).asStrudelDslArgs())

// -- plyForEach() -----------------------------------------------------------------------------------------------------

fun applyPlyForEach(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    val factorArg = args[0]
    val funcArg = args[1]

    // Extract the function from the argument
    val func = funcArg.value as? ((StrudelPattern, Int) -> StrudelPattern) ?: return pattern

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

internal val _plyForEach by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 3) {
        return@dslPatternFunction silence
    }

    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyPlyForEach(pattern, args.take(2))
}

internal val StrudelPattern._plyForEach by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyPlyForEach(p, args)
}

internal val String._plyForEach by dslStringExtension { p, args, callInfo -> p._plyForEach(args, callInfo) }

internal val _plyforeach by dslPatternFunction { args, callInfo -> _plyForEach(args, callInfo) }
internal val StrudelPattern._plyforeach by dslPatternExtension { p, args, callInfo -> p._plyForEach(args, callInfo) }
internal val String._plyforeach by dslStringExtension { p, args, callInfo -> p._plyforeach(args, callInfo) }

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
 * ```KlangScript
 * note("c").plyForEach(4) { pat, i -> pat.add(i * 2) }   // c, d, e, f# — index * 2 semitones
 * ```
 *
 * ```KlangScript
 * s("bd").plyForEach(3) { pat, i -> pat.gain(1.0 - i * 0.3) }  // fading copies
 * ```
 *
 * @alias plyforeach
 * @category tempo
 * @tags plyForEach, repeat, transform, index, subdivide
 */
@StrudelDsl
fun plyForEach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern, pattern: PatternLike): StrudelPattern =
    _plyForEach(listOf(factor, transform, pattern).asStrudelDslArgs())

/** Repeats each event `n` times, passing the iteration index to `transform`. */
@StrudelDsl
fun StrudelPattern.plyForEach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern =
    this._plyForEach(listOf(factor, transform).asStrudelDslArgs())

/** Repeats each event `n` times, passing the iteration index to `transform`. */
@StrudelDsl
fun String.plyForEach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern =
    this._plyForEach(listOf(factor, transform).asStrudelDslArgs())

/**
 * Alias for `plyForEach`.
 *
 * @alias plyForEach
 * @category tempo
 * @tags plyforeach, plyForEach, repeat, transform, index
 */
@StrudelDsl
fun plyforeach(
    factor: Int,
    transform: (StrudelPattern, Int) -> StrudelPattern,
    pattern: PatternLike,
): StrudelPattern = _plyforeach(listOf(factor, transform, pattern).asStrudelDslArgs())

/** Alias for [plyForEach] on this pattern. */
@StrudelDsl
fun StrudelPattern.plyforeach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern =
    this._plyforeach(listOf(factor, transform).asStrudelDslArgs())

/** Alias for [plyForEach] on a string pattern. */
@StrudelDsl
fun String.plyforeach(factor: Int, transform: (StrudelPattern, Int) -> StrudelPattern): StrudelPattern =
    this._plyforeach(listOf(factor, transform).asStrudelDslArgs())

// -- hurry() ----------------------------------------------------------------------------------------------------------

fun applyHurry(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
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

internal val _hurry by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslPatternFunction silence
    }

    val pattern = args.drop(1).toPattern(voiceValueModifier)
    applyHurry(pattern, args.take(1))
}

internal val StrudelPattern._hurry by dslPatternExtension { p, args, /* callInfo */ _ -> applyHurry(p, args) }
internal val String._hurry by dslStringExtension { p, args, callInfo -> p._hurry(args, callInfo) }

/**
 * Speeds up the pattern like `fast()` and multiplies the `speed` audio parameter by the same factor.
 *
 * Unlike `fast()` which only changes the temporal density of events, `hurry()` also scales the
 * `speed` field (sample playback rate) so samples sound proportionally higher-pitched. This
 * mimics tape-speed acceleration. As a top-level function the second argument is the source pattern.
 *
 * @return A pattern sped up in both timing and sample playback rate.
 *
 * ```KlangScript
 * s("bd sd hh").hurry(2)              // twice as fast and samples pitch up by an octave
 * ```
 *
 * ```KlangScript
 * s("bass:1").speed(0.5).hurry(2)     // existing speed 0.5 × hurry 2 = speed 1.0
 * ```
 *
 * @category tempo
 * @tags hurry, fast, speed, pitch, accelerate
 */
@StrudelDsl
fun hurry(factor: PatternLike, pattern: PatternLike): StrudelPattern =
    _hurry(listOf(factor, pattern).asStrudelDslArgs())

/** Speeds up pattern and multiplies the `speed` audio parameter by the same factor. */
@StrudelDsl
fun StrudelPattern.hurry(factor: PatternLike): StrudelPattern = this._hurry(listOf(factor).asStrudelDslArgs())

/** Speeds up pattern and multiplies the `speed` audio parameter by the same factor. */
@StrudelDsl
fun String.hurry(factor: PatternLike): StrudelPattern = this._hurry(listOf(factor).asStrudelDslArgs())

// -- fastGap() --------------------------------------------------------------------------------------------------------

fun applyFastGap(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) {
        return pattern
    }

    val factorProvider: ControlValueProvider =
        args.firstOrNull().asControlValueProvider(Rational.ONE.asVoiceValue())

    return FastGapPattern(source = pattern, factorProvider = factorProvider)
}

internal val _fastGap by dslPatternFunction { args, /* callInfo */ _ ->
    if (args.size < 2) {
        return@dslPatternFunction silence
    }

    val factor = args[0].value?.asRationalOrNull() ?: Rational.ONE

    val pattern = args.drop(1).toPattern(voiceValueModifier)

    if (factor <= Rational.ZERO || factor == Rational.ONE) {
        pattern
    } else {
        FastGapPattern.static(source = pattern, factor = factor)
    }
}

internal val StrudelPattern._fastGap by dslPatternExtension { p, args, /* callInfo */ _ -> applyFastGap(p, args) }
internal val String._fastGap by dslStringExtension { p, args, callInfo -> p._fastGap(args, callInfo) }

internal val _densityGap by dslPatternFunction { args, callInfo -> _fastGap(args, callInfo) }
internal val StrudelPattern._densityGap by dslPatternExtension { p, args, callInfo -> p._fastGap(args, callInfo) }
internal val String._densityGap by dslStringExtension { p, args, callInfo -> p._densityGap(args, callInfo) }

/**
 * Speeds up the pattern by `factor` but plays it only once per cycle, leaving a gap.
 *
 * Unlike `fast(n)` which tiles the pattern `n` times to fill the cycle, `fastGap(n)` compresses
 * the pattern into the first `1/n` of the cycle and leaves silence in the remaining space. As a
 * top-level function the second argument is the source pattern.
 *
 * @return A pattern compressed into the first `1/factor` of each cycle with silence after.
 *
 * ```KlangScript
 * s("bd sd hh cp").fastGap(2)         // 4 events in first half, silence in second half
 * ```
 *
 * ```KlangScript
 * note("c d e f g").fastGap(4)        // all events squeezed into first quarter
 * ```
 *
 * @alias densityGap
 * @category tempo
 * @tags fastGap, fast, gap, silence, compress, density
 */
@StrudelDsl
fun fastGap(factor: PatternLike, pattern: PatternLike): StrudelPattern =
    _fastGap(listOf(factor, pattern).asStrudelDslArgs())

/** Speeds up the pattern but plays it only once per cycle, leaving a gap. */
@StrudelDsl
fun StrudelPattern.fastGap(factor: PatternLike): StrudelPattern = this._fastGap(listOf(factor).asStrudelDslArgs())

/** Speeds up the pattern but plays it only once per cycle, leaving a gap. */
@StrudelDsl
fun String.fastGap(factor: PatternLike): StrudelPattern = this._fastGap(listOf(factor).asStrudelDslArgs())

/**
 * Alias for `fastGap`.
 *
 * @alias fastGap
 * @category tempo
 * @tags densityGap, fastGap, gap, compress, density
 */
@StrudelDsl
fun densityGap(factor: PatternLike, pattern: PatternLike): StrudelPattern =
    _densityGap(listOf(factor, pattern).asStrudelDslArgs())

/** Alias for [fastGap]. */
@StrudelDsl
fun StrudelPattern.densityGap(factor: PatternLike): StrudelPattern =
    this._densityGap(listOf(factor).asStrudelDslArgs())

/** Alias for [fastGap]. */
@StrudelDsl
fun String.densityGap(factor: PatternLike): StrudelPattern = this._densityGap(listOf(factor).asStrudelDslArgs())

// -- inside() ---------------------------------------------------------------------------------------------------------

fun applyInside(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) return pattern

    val factorArg = args[0]
    val func = args[1].toPatternMapper() ?: return pattern

    // Use apply functions directly to avoid double-wrapping StrudelDslArg
    val slowed = applySlow(pattern, listOf(factorArg))
    val transformed = func(slowed)

    return applyFast(transformed, listOf(factorArg))
}

internal val StrudelPattern._inside by dslPatternExtension { p, args, /* callInfo */ _ -> applyInside(p, args) }

/**
 * Applies a transformation inside a zoomed-in view of the cycle.
 *
 * Slows the pattern by `factor`, applies `transform`, then speeds it back up to the original
 * tempo. The net effect is that `transform` sees a pattern spread over `factor` cycles,
 * allowing operations like `rev()` to work across a larger musical phrase while the result
 * still fits in one cycle.
 *
 * ```KlangScript
 * note("0 1 2 3").inside(4) { it.rev() }       // reverse across 4-cycle span, then compress back
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").inside(2) { it.slow(2) }    // double-slow inside = no net change in tempo
 * ```
 *
 * @category tempo
 * @tags inside, transform, zoom, slow, fast
 */
@StrudelDsl
fun StrudelPattern.inside(factor: PatternLike, transform: PatternMapper): StrudelPattern =
    this._inside(listOf(factor, transform).asStrudelDslArgs())

// -- outside() --------------------------------------------------------------------------------------------------------

fun applyOutside(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.size < 2) {
        return pattern
    }

    // TODO: support control pattern for "factor"
    val factor = args[0].value?.asRationalOrNull() ?: Rational.ONE

    val func = args[1].toPatternMapper() ?: return pattern

    val sped = applyFast(pattern, listOf(StrudelDslArg.of(factor)))
    val transformed = func(sped)

    return applySlow(transformed, listOf(StrudelDslArg.of(factor)))
}

internal val StrudelPattern._outside by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyOutside(p, args)
}

/**
 * Applies a transformation outside the current cycle, across a wider temporal context.
 *
 * Speeds the pattern by `factor`, applies `transform`, then slows it back down. The net effect
 * is that `transform` sees only `1/factor` of the original pattern per cycle, allowing
 * operations like `rev()` to work on a globally coarser time scale.
 *
 * ```KlangScript
 * note("0 1 2 3").outside(4) { it.rev() }      // reverse on 1/4 speed, then speed back up
 * ```
 *
 * ```KlangScript
 * s("bd sd hh cp").outside(2) { it.fast(2) }   // double-fast outside = no net change in tempo
 * ```
 *
 * @category tempo
 * @tags outside, transform, zoom, fast, slow
 */
@StrudelDsl
fun StrudelPattern.outside(factor: PatternLike, transform: PatternMapper): StrudelPattern =
    this._outside(listOf(factor, transform).asStrudelDslArgs())

// -- swingBy() --------------------------------------------------------------------------------------------------------

fun applySwingBy(pattern: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return pattern._innerJoin(args) { source, swing, n ->
        val swingValue = swing?.asRational ?: return@_innerJoin silence
        val nVal = n?.asRational ?: Rational.ONE

        val timing = seq(Rational.ZERO, swingValue / 2)
        val stretch = seq(Rational.ONE + swingValue, Rational.ONE - swingValue)

        val transform: PatternMapper = { innerPat ->
            if (swingValue >= Rational.ZERO) {
                innerPat.lateInCycle(timing).stretchBy(stretch)
            } else {
                innerPat.stretchBy(stretch).lateInCycle(timing)
            }
        }

        source.inside(nVal, transform)
    }
}

internal val StrudelPattern._swingBy by dslPatternExtension { p, args, /* callInfo */ _ ->
    applySwingBy(p, args)
}

internal val String._swingBy by dslStringExtension { p, args, callInfo -> p._swingBy(args, callInfo) }

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
 * ```KlangScript
 * s("hh*8").swingBy(1/3, 4)               // classic swing on hi-hats, 4 subdivisions
 * ```
 *
 * ```KlangScript
 * note("c d e f").swingBy(-0.25, 2)       // reverse swing on notes, 2 subdivisions
 * ```
 *
 * @category tempo
 * @tags swingBy, swing, shuffle, rhythm, timing, groove
 */
@StrudelDsl
fun StrudelPattern.swingBy(swing: PatternLike, n: PatternLike): StrudelPattern =
    this._swingBy(listOf(swing, n).asStrudelDslArgs())

/** Creates a swing rhythm with custom amount; see `swingBy` for details. */
@StrudelDsl
fun String.swingBy(swing: PatternLike, n: PatternLike): StrudelPattern =
    this._swingBy(listOf(swing, n).asStrudelDslArgs())

// -- swing() ----------------------------------------------------------------------------------------------------------

internal val StrudelPattern._swing by dslPatternExtension { p, args, /* callInfo */ _ ->
    if (args.isEmpty()) {
        return@dslPatternExtension p
    }

    val nArg = args.getOrNull(0)

    // swing(n) = swingBy(1/3, n)
    applySwingBy(p, listOf(StrudelDslArg.of(1.0 / 3.0), nArg ?: StrudelDslArg.of(1.0)))
}

internal val String._swing by dslStringExtension { p, args, callInfo -> p._swing(args, callInfo) }

/**
 * Shorthand for `swingBy(1/3, n)` — classic jazz swing feel.
 *
 * Applies a 1/3 swing amount across `n` subdivisions per cycle. Equivalent to
 * `swingBy(1/3, n)`, creating the characteristic "long-short" groove.
 *
 * @param n Number of subdivisions per cycle.
 *
 * ```KlangScript
 * s("hh*8").swing(4)                  // classic swing on hi-hats, 4 subdivisions
 * ```
 *
 * ```KlangScript
 * note("c d e f g a b c").swing(2)    // swing feel on a melody, 2 subdivisions
 * ```
 *
 * @category tempo
 * @tags swing, swingBy, shuffle, rhythm, timing, groove
 */
@StrudelDsl
fun StrudelPattern.swing(n: PatternLike): StrudelPattern = this._swing(listOf(n).asStrudelDslArgs())

/** Shorthand for `swingBy(1/3, n)` — classic jazz swing feel. */
@StrudelDsl
fun String.swing(n: PatternLike): StrudelPattern = this._swing(listOf(n).asStrudelDslArgs())

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
fun applyBrak(pattern: StrudelPattern): StrudelPattern {
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

internal val _brak by dslPatternFunction { args, /* callInfo */ _ ->
    val pattern = args.toPattern(voiceValueModifier)
    applyBrak(pattern)
}

internal val StrudelPattern._brak by dslPatternExtension { p, /* args */ _, /* callInfo */ _ -> applyBrak(p) }
internal val String._brak by dslStringExtension { p, args, callInfo -> p._brak(args, callInfo) }

/**
 * Makes every other cycle syncopated — a classic breakbeat effect.
 *
 * Cycle 0, 2, 4, … play normally. Cycle 1, 3, 5, … compress the pattern into the first half
 * and delay it by a quarter cycle, creating an off-beat syncopation reminiscent of amen
 * break-style patterns.
 *
 * ```KlangScript
 * s("bd sd hh cp").brak()             // alternating straight and syncopated cycles
 * ```
 *
 * ```KlangScript
 * note("c d e f").brak()              // melodic pattern with every-other-cycle offset
 * ```
 *
 * @category tempo
 * @tags brak, breakbeat, syncopation, rhythm, offset, amen
 */
@StrudelDsl
fun StrudelPattern.brak(): StrudelPattern = this._brak(emptyList())

/** Makes every other cycle syncopated — a classic breakbeat effect. */
@StrudelDsl
fun String.brak(): StrudelPattern = this._brak(emptyList())

/**
 * Makes every other cycle syncopated — a classic breakbeat effect.
 * @param pattern The pattern to apply breakbeat syncopation to
 */
@StrudelDsl
fun brak(pattern: PatternLike): StrudelPattern = _brak(listOf(pattern).asStrudelDslArgs())
