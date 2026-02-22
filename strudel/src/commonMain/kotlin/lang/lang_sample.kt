@file:Suppress("ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel._innerJoin
import io.peekandpoke.klang.strudel._liftData
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.math.Rational

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangSampleInit = false

// -- begin() ----------------------------------------------------------------------------------------------------------

private val beginMutation = voiceModifier { copy(begin = it?.asDoubleOrNull()) }

fun applyBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    // 1. Create a control pattern where 'begin' is already set correctly
    val control = args.toPattern(beginMutation)
    // 2. Use liftData to MERGE the control data into the source
    return source._liftData(control)
}

internal val _begin by dslFunction { args, /* callInfo */ _ -> args.toPattern(beginMutation) }
internal val StrudelPattern._begin by dslPatternExtension { p, args, /* callInfo */ _ -> applyBegin(p, args) }
internal val String._begin by dslStringExtension { p, args, callInfo -> p._begin(args, callInfo) }

/**
 * Sets the sample start position as a fraction of the total sample length (0–1).
 *
 * `0` starts playback from the very beginning; `1` would start at the very end (silence).
 * Useful for skipping intros or targeting specific sections of a longer sample. Combine
 * with [end] to play only a segment.
 *
 * ```KlangScript
 * s("breaks").begin(0.5)            // start halfway through the sample
 * ```
 *
 * ```KlangScript
 * s("breaks").begin("<0 0.25 0.5>") // cycle through three start positions
 * ```
 *
 * @category sampling
 * @tags begin, start, sample, position, offset
 */
@StrudelDsl
fun begin(pos: PatternLike): StrudelPattern = _begin(listOf(pos).asStrudelDslArgs())

/** Sets the sample start position (0–1) on this pattern. */
@StrudelDsl
fun StrudelPattern.begin(pos: PatternLike): StrudelPattern = this._begin(listOf(pos).asStrudelDslArgs())

/** Sets the sample start position (0–1) on a string pattern. */
@StrudelDsl
fun String.begin(pos: PatternLike): StrudelPattern = this._begin(listOf(pos).asStrudelDslArgs())

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceModifier { copy(end = it?.asDoubleOrNull()) }

fun applyEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(endMutation)
    return source._liftData(control)
}

internal val _end by dslFunction { args, /* callInfo */ _ -> args.toPattern(endMutation) }
internal val StrudelPattern._end by dslPatternExtension { p, args, /* callInfo */ _ -> applyEnd(p, args) }
internal val String._end by dslStringExtension { p, args, callInfo -> p._end(args, callInfo) }

/**
 * Sets the sample end position as a fraction of the total sample length (0–1).
 *
 * `1` plays to the very end of the sample; `0` would end immediately (silence). Use with
 * [begin] to play only a segment of a sample.
 *
 * ```KlangScript
 * s("breaks").end(0.5)            // play only the first half of the sample
 * ```
 *
 * ```KlangScript
 * s("breaks").begin(0.25).end(0.75)  // play the middle 50% of the sample
 * ```
 *
 * @category sampling
 * @tags end, stop, sample, position, offset
 */
@StrudelDsl
fun end(pos: PatternLike): StrudelPattern = _end(listOf(pos).asStrudelDslArgs())

/** Sets the sample end position (0–1) on this pattern. */
@StrudelDsl
fun StrudelPattern.end(pos: PatternLike): StrudelPattern = this._end(listOf(pos).asStrudelDslArgs())

/** Sets the sample end position (0–1) on a string pattern. */
@StrudelDsl
fun String.end(pos: PatternLike): StrudelPattern = this._end(listOf(pos).asStrudelDslArgs())

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceModifier { copy(speed = it?.asDoubleOrNull()) }

fun applySpeed(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(speedMutation)
    return source._liftData(control)
}

internal val _speed by dslFunction { args, /* callInfo */ _ -> args.toPattern(speedMutation) }
internal val StrudelPattern._speed by dslPatternExtension { p, args, /* callInfo */ _ -> applySpeed(p, args) }
internal val String._speed by dslStringExtension { p, args, callInfo -> p._speed(args, callInfo) }

/**
 * Sets the sample playback speed as a multiplier.
 *
 * `1` is normal speed; `2` doubles the speed (one octave up); `0.5` halves the speed
 * (one octave down). Negative values play the sample in reverse.
 *
 * ```KlangScript
 * s("breaks").speed(2)              // double speed, one octave up
 * ```
 *
 * ```KlangScript
 * s("bd").speed("<1 -1>")           // alternate forward and reverse per cycle
 * ```
 *
 * @category sampling
 * @tags speed, playback, pitch, rate, reverse
 */
@StrudelDsl
fun speed(rate: PatternLike): StrudelPattern = _speed(listOf(rate).asStrudelDslArgs())

/** Sets the sample playback speed on this pattern. */
@StrudelDsl
fun StrudelPattern.speed(rate: PatternLike): StrudelPattern = this._speed(listOf(rate).asStrudelDslArgs())

/** Sets the sample playback speed on a string pattern. */
@StrudelDsl
fun String.speed(rate: PatternLike): StrudelPattern = this._speed(listOf(rate).asStrudelDslArgs())

// -- unit() -----------------------------------------------------------------------------------------------------------

private val unitMutation = voiceModifier { copy(unit = it?.asVoiceValue()?.asString) }

fun applyUnit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(unitMutation)
    return source._liftData(control)
}

internal val _unit by dslFunction { args, /* callInfo */ _ -> args.toPattern(unitMutation) }
internal val StrudelPattern._unit by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnit(p, args) }
internal val String._unit by dslStringExtension { p, args, callInfo -> p._unit(args, callInfo) }

/**
 * Sets the time unit used for sample playback duration.
 *
 * Determines how [speed] and other timing-related parameters are interpreted. The value
 * `"c"` (cycles) makes the sample stretch or compress to fit the pattern's cycle timing.
 * Used internally by [loopAt] and [loopAtCps].
 *
 * ```KlangScript
 * s("breaks").unit("c").slow(2)     // stretch sample to fill 2 cycles
 * ```
 *
 * ```KlangScript
 * s("breaks").speed(0.5).unit("c")  // half-speed in cycle units
 * ```
 *
 * @category sampling
 * @tags unit, time unit, cycles, sample, timing
 */
@StrudelDsl
fun unit(value: PatternLike): StrudelPattern = _unit(listOf(value).asStrudelDslArgs())

/** Sets the time unit for sample playback on this pattern. */
@StrudelDsl
fun StrudelPattern.unit(value: PatternLike): StrudelPattern = this._unit(listOf(value).asStrudelDslArgs())

/** Sets the time unit for sample playback on a string pattern. */
@StrudelDsl
fun String.unit(value: PatternLike): StrudelPattern = this._unit(listOf(value).asStrudelDslArgs())

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceModifier { copy(loop = it?.asVoiceValue()?.asBoolean) }

fun applyLoop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(loopMutation)
    return source._liftData(control)
}

internal val _loop by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopMutation) }
internal val StrudelPattern._loop by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoop(p, args) }
internal val String._loop by dslStringExtension { p, args, callInfo -> p._loop(args, callInfo) }

/**
 * Enables continuous looping of the sample.
 *
 * When `loop` is set to a truthy value, the sample repeats continuously after reaching
 * the end (or [loopEnd] position). Use [loopBegin] and [loopEnd] to set the loop region.
 *
 * ```KlangScript
 * s("pad").loop(1)                    // loop the sample continuously
 * ```
 *
 * ```KlangScript
 * s("pad").loop(1).loopBegin(0.25).loopEnd(0.75)  // loop only the middle section
 * ```
 *
 * @category sampling
 * @tags loop, looping, repeat, sample
 */
@StrudelDsl
fun loop(flag: PatternLike = true): StrudelPattern = _loop(listOf(flag).asStrudelDslArgs())

/** Enables sample looping on this pattern. */
@StrudelDsl
fun StrudelPattern.loop(flag: PatternLike = true): StrudelPattern = this._loop(listOf(flag).asStrudelDslArgs())

/** Enables sample looping on a string pattern. */
@StrudelDsl
fun String.loop(flag: PatternLike = true): StrudelPattern = this._loop(listOf(flag).asStrudelDslArgs())

// -- loopBegin() / loopb() --------------------------------------------------------------------------------------------

private val loopBeginMutation = voiceModifier { copy(loopBegin = it?.asDoubleOrNull()) }

fun applyLoopBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(loopBeginMutation)
    return source._liftData(control)
}

internal val _loopBegin by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopBeginMutation) }
internal val StrudelPattern._loopBegin by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopBegin(p, args) }
internal val String._loopBegin by dslStringExtension { p, args, _ -> applyLoopBegin(p, args) }

internal val _loopb by dslFunction { args, callInfo -> _loopBegin(args, callInfo) }
internal val StrudelPattern._loopb by dslPatternExtension { p, args, callInfo -> p._loopBegin(args, callInfo) }
internal val String._loopb by dslStringExtension { p, args, callInfo -> p._loopb(args, callInfo) }

/**
 * Sets the loop start position as a fraction of the total sample length (0–1).
 *
 * Only relevant when [loop] is enabled. `0` starts looping from the very beginning of
 * the sample. Use together with [loopEnd] to define the looping region.
 *
 * ```KlangScript
 * s("pad").loop(1).loopBegin(0.25)              // loop starts at 25% into the sample
 * ```
 *
 * ```KlangScript
 * s("pad").loop(1).loopBegin("<0 0.5>")         // alternate loop start position per cycle
 * ```
 *
 * @alias loopb
 * @category sampling
 * @tags loopBegin, loopb, loop, start, sample, position
 */
@StrudelDsl
fun loopBegin(pos: PatternLike): StrudelPattern = _loopBegin(listOf(pos).asStrudelDslArgs())

/** Sets the loop start position (0–1) on this pattern. */
@StrudelDsl
fun StrudelPattern.loopBegin(pos: PatternLike): StrudelPattern = this._loopBegin(listOf(pos).asStrudelDslArgs())

/** Sets the loop start position (0–1) on a string pattern. */
@StrudelDsl
fun String.loopBegin(pos: PatternLike): StrudelPattern = this._loopBegin(listOf(pos).asStrudelDslArgs())

/**
 * Alias for [loopBegin]. Sets the loop start position.
 *
 * @alias loopBegin
 * @category sampling
 * @tags loopb, loopBegin, loop, start, sample, position
 */
@StrudelDsl
fun loopb(pos: PatternLike): StrudelPattern = _loopb(listOf(pos).asStrudelDslArgs())

/** Alias for [loopBegin] on this pattern. */
@StrudelDsl
fun StrudelPattern.loopb(pos: PatternLike): StrudelPattern = this._loopb(listOf(pos).asStrudelDslArgs())

/** Alias for [loopBegin] on a string pattern. */
@StrudelDsl
fun String.loopb(pos: PatternLike): StrudelPattern = this._loopb(listOf(pos).asStrudelDslArgs())

// -- loopEnd() / loope() ----------------------------------------------------------------------------------------------

private val loopEndMutation = voiceModifier { copy(loopEnd = it?.asDoubleOrNull()) }

fun applyLoopEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(loopEndMutation)
    return source._liftData(control)
}

internal val _loopEnd by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopEndMutation) }
internal val StrudelPattern._loopEnd by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopEnd(p, args) }
internal val String._loopEnd by dslStringExtension { p, args, _ -> applyLoopEnd(p, args) }

internal val _loope by dslFunction { args, callInfo -> _loopEnd(args, callInfo) }
internal val StrudelPattern._loope by dslPatternExtension { p, args, callInfo -> p._loopEnd(args, callInfo) }
internal val String._loope by dslStringExtension { p, args, callInfo -> p._loope(args, callInfo) }

/**
 * Sets the loop end position as a fraction of the total sample length (0–1).
 *
 * Only relevant when [loop] is enabled. `1` loops to the very end of the sample. Use
 * together with [loopBegin] to define the looping region.
 *
 * ```KlangScript
 * s("pad").loop(1).loopEnd(0.75)              // loop ends at 75% into the sample
 * ```
 *
 * ```KlangScript
 * s("pad").loop(1).loopEnd("<0.5 1>")         // alternate loop end position per cycle
 * ```
 *
 * @alias loope
 * @category sampling
 * @tags loopEnd, loope, loop, end, sample, position
 */
@StrudelDsl
fun loopEnd(pos: PatternLike): StrudelPattern = _loopEnd(listOf(pos).asStrudelDslArgs())

/** Sets the loop end position (0–1) on this pattern. */
@StrudelDsl
fun StrudelPattern.loopEnd(pos: PatternLike): StrudelPattern = this._loopEnd(listOf(pos).asStrudelDslArgs())

/** Sets the loop end position (0–1) on a string pattern. */
@StrudelDsl
fun String.loopEnd(pos: PatternLike): StrudelPattern = this._loopEnd(listOf(pos).asStrudelDslArgs())

/**
 * Alias for [loopEnd]. Sets the loop end position.
 *
 * @alias loopEnd
 * @category sampling
 * @tags loope, loopEnd, loop, end, sample, position
 */
@StrudelDsl
fun loope(pos: PatternLike): StrudelPattern = _loope(listOf(pos).asStrudelDslArgs())

/** Alias for [loopEnd] on this pattern. */
@StrudelDsl
fun StrudelPattern.loope(pos: PatternLike): StrudelPattern = this._loope(listOf(pos).asStrudelDslArgs())

/** Alias for [loopEnd] on a string pattern. */
@StrudelDsl
fun String.loope(pos: PatternLike): StrudelPattern = this._loope(listOf(pos).asStrudelDslArgs())

// -- loopAt() ---------------------------------------------------------------------------------------------------------

private val loopAtSpeedMutation = voiceModifier {
    val value = it?.asDoubleOrNull()
    if (value == null) copy(speed = null) else copy(speed = 1.0 / (2.0 * value))
}

fun applyLoopAt(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source

    // Get the loopAt factor
    val factor = args[0].value?.asRationalOrNull() ?: return source

    // Apply slow() to stretch the events to the desired duration
    // loopAt(2) stretches events to 2 cycles, loopAt(0.5) compresses to 0.5 cycles
    val slowed = source.unit("c").slow(factor)

    // Then set the speed parameter and unit to compensate for sample playback
    // JavaScript: pat.speed((1/factor) * cps).unit('c').slow(factor)
    // With default cps=0.5: speed = 1/(2*factor)
    val speedControl = args.toPattern(loopAtSpeedMutation)

    return slowed._liftData(speedControl)
}

internal val _loopAt by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopAtSpeedMutation) }
internal val StrudelPattern._loopAt by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAt(p, args) }
internal val String._loopAt by dslStringExtension { p, args, callInfo -> p._loopAt(args, callInfo) }

/**
 * Fits the sample playback duration to the given number of cycles.
 *
 * Adjusts the [speed] and [unit] automatically so that the sample stretches or compresses
 * to exactly fill the specified number of cycles. Useful for syncing loops to the pattern
 * tempo. `loopAt(1)` fills one cycle; `loopAt(2)` fills two cycles.
 *
 * ```KlangScript
 * s("breaks").loopAt(1)          // stretch/compress to exactly one cycle
 * ```
 *
 * ```KlangScript
 * s("breaks").loopAt(2).slow(2)  // fill two cycles, then play at half speed
 * ```
 *
 * @category sampling
 * @tags loopAt, loop, fit, cycles, tempo, stretch
 */
@StrudelDsl
fun loopAt(cycles: PatternLike): StrudelPattern = _loopAt(listOf(cycles).asStrudelDslArgs())

/** Fits the sample to the specified number of cycles on this pattern. */
@StrudelDsl
fun StrudelPattern.loopAt(cycles: PatternLike): StrudelPattern = this._loopAt(listOf(cycles).asStrudelDslArgs())

/** Fits the sample to the specified number of cycles on a string pattern. */
@StrudelDsl
fun String.loopAt(cycles: PatternLike): StrudelPattern = this._loopAt(listOf(cycles).asStrudelDslArgs())

// -- loopAtCps() ------------------------------------------------------------------------------------------------------

fun applyLoopAtCps(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._innerJoin(args) { pat, factorValue, cpsValue ->
        val factor = factorValue?.asRational ?: return@_innerJoin silence
        val cps = cpsValue?.asRational ?: Rational.HALF

        // Calculate speed: (1 / factor) * cps
        val speed = (Rational.ONE / factor) * cps

        // JavaScript: pat.speed((1/factor) * cps).unit('c').slow(factor)
        pat.speed(speed).unit("c").slow(factor)
    }
}

internal val _loopAtCps by dslFunction { args, /* callInfo */ _ ->
    if (args.isEmpty()) {
        return@dslFunction silence
    }

    // In JavaScript: function (factor, cps, pat)
    // So args are: [factor, cps, ...pattern parts]
    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyLoopAtCps(pattern, args.take(2))
}

internal val StrudelPattern._loopAtCps by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAtCps(p, args) }
internal val String._loopAtCps by dslStringExtension { p, args, callInfo -> p._loopAtCps(args, callInfo) }

internal val _loopatcps by dslFunction { args, callInfo -> _loopAtCps(args, callInfo) }
internal val StrudelPattern._loopatcps by dslPatternExtension { p, args, callInfo -> p._loopAtCps(args, callInfo) }
internal val String._loopatcps by dslStringExtension { p, args, callInfo -> p._loopatcps(args, callInfo) }

/**
 * Fits the sample to the given number of cycles, taking the current cycles-per-second into account.
 *
 * Like [loopAt] but also accepts a `cps` (cycles per second) argument to compute the exact
 * playback [speed]. This makes the sample lock to the live-coding clock at a specific
 * tempo. Default `cps` is `0.5`.
 *
 * ```KlangScript
 * s("breaks").loopAtCps(1, 0.5)    // fit to 1 cycle at 0.5 cps (default tempo)
 * ```
 *
 * ```KlangScript
 * s("breaks").loopAtCps(2, 0.75)   // fit to 2 cycles at 0.75 cps
 * ```
 *
 * @alias loopatcps
 * @category sampling
 * @tags loopAtCps, loopatcps, loop, fit, cycles, cps, tempo, stretch
 */
@StrudelDsl
fun loopAtCps(factor: PatternLike, cps: PatternLike): StrudelPattern =
    _loopAtCps(listOf(factor, cps).asStrudelDslArgs())

/** Fits the sample to the given number of cycles and cps value on this pattern. */
@StrudelDsl
fun StrudelPattern.loopAtCps(factor: PatternLike, cps: PatternLike): StrudelPattern =
    this._loopAtCps(listOf(factor, cps).asStrudelDslArgs())

/** Fits the sample to the given number of cycles and cps value on this pattern (numeric overload). */
@StrudelDsl
fun StrudelPattern.loopAtCps(factor: Number, cps: Number = 0.5): StrudelPattern =
    this._loopAtCps(listOf(factor, cps).asStrudelDslArgs())

/** Fits the sample to the given number of cycles and cps value on a string pattern. */
@StrudelDsl
fun String.loopAtCps(factor: PatternLike, cps: PatternLike): StrudelPattern =
    this._loopAtCps(listOf(factor, cps).asStrudelDslArgs())

/** Fits the sample to the given number of cycles and cps value on a string pattern (numeric overload). */
@StrudelDsl
fun String.loopAtCps(factor: Number, cps: Number = 0.5): StrudelPattern =
    this._loopAtCps(listOf(factor, cps).asStrudelDslArgs())

/**
 * Alias for [loopAtCps]. Fits the sample to the given number of cycles and cps.
 *
 * @alias loopAtCps
 * @category sampling
 * @tags loopatcps, loopAtCps, loop, fit, cycles, cps, tempo, stretch
 */
@StrudelDsl
fun loopatcps(factor: PatternLike, cps: PatternLike): StrudelPattern =
    _loopatcps(listOf(factor, cps).asStrudelDslArgs())

/** Alias for [loopAtCps] on this pattern. */
@StrudelDsl
fun StrudelPattern.loopatcps(factor: PatternLike, cps: PatternLike): StrudelPattern =
    this._loopatcps(listOf(factor, cps).asStrudelDslArgs())

/** Alias for [loopAtCps] on this pattern (numeric overload). */
@StrudelDsl
fun StrudelPattern.loopatcps(factor: Number, cps: Number = 0.5): StrudelPattern = this.loopAtCps(factor, cps)

/** Alias for [loopAtCps] on a string pattern. */
@StrudelDsl
fun String.loopatcps(factor: PatternLike, cps: PatternLike): StrudelPattern =
    this._loopatcps(listOf(factor, cps).asStrudelDslArgs())

/** Alias for [loopAtCps] on a string pattern (numeric overload). */
@StrudelDsl
fun String.loopatcps(factor: Number, cps: Number = 0.5): StrudelPattern = this.loopAtCps(factor, cps)

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceModifier { copy(cut = it?.asIntOrNull()) }

fun applyCut(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(cutMutation)
    return source._liftData(control)
}

internal val _cut by dslFunction { args, /* callInfo */ _ -> args.toPattern(cutMutation) }
internal val StrudelPattern._cut by dslPatternExtension { p, args, /* callInfo */ _ -> applyCut(p, args) }
internal val String._cut by dslStringExtension { p, args, callInfo -> p._cut(args, callInfo) }

/**
 * Assigns the sample to a cut group (choke group) by number.
 *
 * Samples in the same cut group cut each other off when a new sample in the group triggers.
 * This is useful for hi-hats: an open hi-hat stops when the closed hi-hat hits. Group `0`
 * means no choke.
 *
 * ```KlangScript
 * stack(s("hh*4").cut(1), s("~ oh ~").cut(1))  // open hh choked by closed hh
 * ```
 *
 * ```KlangScript
 * s("bd sd").cut("<0 1>")                        // alternate between no-cut and cut-group-1
 * ```
 *
 * @category sampling
 * @tags cut, choke, group, hi-hat, sample
 */
@StrudelDsl
fun cut(group: PatternLike): StrudelPattern = _cut(listOf(group).asStrudelDslArgs())

/** Assigns the sample to a cut group (choke group) on this pattern. */
@StrudelDsl
fun StrudelPattern.cut(group: PatternLike): StrudelPattern = this._cut(listOf(group).asStrudelDslArgs())

/** Assigns the sample to a cut group (choke group) on a string pattern. */
@StrudelDsl
fun String.cut(group: PatternLike): StrudelPattern = this._cut(listOf(group).asStrudelDslArgs())

// -- slice() ----------------------------------------------------------------------------------------------------------

fun applySlice(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // TODO: support dynamic index pattern

    val indexArg = args.getOrNull(1)
    val indexVal = indexArg?.value?.asIntOrNull() ?: 0

    val start = indexVal.toDouble() / nVal
    val end = (indexVal + 1.0) / nVal

    return source.begin(start).end(end)
}

internal val StrudelPattern._slice by dslPatternExtension { p, args, /* callInfo */ _ -> applySlice(p, args) }
internal val String._slice by dslStringExtension { p, args, callInfo -> p._slice(args, callInfo) }

/**
 * Plays a specific slice of the sample by dividing it into equal parts.
 *
 * Splits the sample into `n` equal segments and plays only the one at `index` (0-based).
 * Implemented by setting [begin] and [end] appropriately. Combine with a pattern for
 * the index to sequence through different slices.
 *
 * ```KlangScript
 * s("breaks").slice(8, 0)                // play the first of 8 slices
 * ```
 *
 * ```KlangScript
 * s("breaks").slice(8, "0 1 2 3 4 5 6 7".i)  // sequence through all 8 slices
 * ```
 *
 * @category sampling
 * @tags slice, segment, chop, sample, begin, end
 */
@StrudelDsl
fun StrudelPattern.slice(n: PatternLike, index: PatternLike): StrudelPattern =
    this._slice(listOf(n, index).asStrudelDslArgs())

/**
 * Plays a specific slice of the sample on a string pattern.
 * See [StrudelPattern.slice] for full documentation.
 */
@StrudelDsl
fun String.slice(n: PatternLike, index: PatternLike): StrudelPattern =
    this._slice(listOf(n, index).asStrudelDslArgs())

// -- splice() ---------------------------------------------------------------------------------------------------------

fun applySplice(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Get number of slices (also used as speed multiplier)
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // Apply slice, then multiply speed by n to maintain timing
    return applySlice(source, args).speed(nVal.toDouble())
}

internal val StrudelPattern._splice by dslPatternExtension { p, args, /* callInfo */ _ -> applySplice(p, args) }
internal val String._splice by dslStringExtension { p, args, callInfo -> p._splice(args, callInfo) }

/**
 * Plays a specific slice of the sample at the original sample tempo.
 *
 * Like [slice], but multiplies [speed] by `n` to compensate for the shorter segment
 * duration, so each slice plays at the same pitch and rate as the original sample.
 * Useful for beat-slicing without pitch artifacts.
 *
 * ```KlangScript
 * s("breaks").splice(8, 0)               // first of 8 slices at original pitch
 * ```
 *
 * ```KlangScript
 * s("breaks").splice(8, "0 2 4 6".i)    // every other slice, original tempo
 * ```
 *
 * @category sampling
 * @tags splice, slice, chop, sample, speed, tempo
 */
@StrudelDsl
fun StrudelPattern.splice(n: PatternLike, index: PatternLike): StrudelPattern =
    this._splice(listOf(n, index).asStrudelDslArgs())

/**
 * Plays a specific slice of the sample at the original tempo on a string pattern.
 * See [StrudelPattern.splice] for full documentation.
 */
@StrudelDsl
fun String.splice(n: PatternLike, index: PatternLike): StrudelPattern =
    this._splice(listOf(n, index).asStrudelDslArgs())
