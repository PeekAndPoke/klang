@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._liftData
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangSampleInit = false

// -- begin() ----------------------------------------------------------------------------------------------------------

private val beginMutation = voiceModifier { copy(begin = it?.asDoubleOrNull()) }

fun applyBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern =
    source._liftOrReinterpretNumericalField(args, beginMutation)

internal val _begin by dslPatternMapper { args, callInfo -> { p -> p._begin(args, callInfo) } }
internal val StrudelPattern._begin by dslPatternExtension { p, args, /* callInfo */ _ -> applyBegin(p, args) }
internal val String._begin by dslStringExtension { p, args, callInfo -> p._begin(args, callInfo) }
internal val PatternMapperFn._begin by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_begin(args, callInfo))
}

/**
 * Sets the sample start position as a fraction of the total sample length (0–1).
 *
 * `0` starts playback from the very beginning; `1` would start at the very end (silence).
 * Useful for skipping intros or targeting specific sections of a longer sample. Combine
 * with [end] to play only a segment.
 *
 * @param pos Start position in [0, 1]; 0 = beginning, 1 = end.
 * @return A pattern with the sample start position set.
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
fun StrudelPattern.begin(pos: PatternLike): StrudelPattern = this._begin(listOf(pos).asStrudelDslArgs())

/** Reinterprets this pattern's values as sample start positions (0–1). */
@StrudelDsl
fun StrudelPattern.begin(): StrudelPattern = this._begin(emptyList())

/** Sets the sample start position (0–1) on a string pattern. */
@StrudelDsl
fun String.begin(pos: PatternLike): StrudelPattern = this._begin(listOf(pos).asStrudelDslArgs())

/** Reinterprets this string pattern's values as sample start positions (0–1). */
@StrudelDsl
fun String.begin(): StrudelPattern = this._begin(emptyList())

/**
 * Returns a [PatternMapperFn] that sets the sample start position (0–1).
 *
 * @param pos Start position in [0, 1].
 * @return A [PatternMapperFn] that sets the begin field on the source pattern.
 *
 * ```KlangScript
 * s("breaks").apply(begin(0.5))     // via mapper
 * ```
 *
 * @category sampling
 * @tags begin, start, sample, position, offset
 */
@StrudelDsl
fun begin(pos: PatternLike): PatternMapperFn = _begin(listOf(pos).asStrudelDslArgs())

/** Returns a [PatternMapperFn] that reinterprets the source pattern's values as sample start positions. */
@StrudelDsl
fun begin(): PatternMapperFn = _begin(emptyList())

/** Chains a begin onto this [PatternMapperFn]; sets the sample start position (0–1). */
@StrudelDsl
fun PatternMapperFn.begin(pos: PatternLike): PatternMapperFn = this._begin(listOf(pos).asStrudelDslArgs())

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceModifier { copy(end = it?.asDoubleOrNull()) }

fun applyEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern =
    source._liftOrReinterpretNumericalField(args, endMutation)

internal val _end by dslPatternMapper { args, callInfo -> { p -> p._end(args, callInfo) } }
internal val StrudelPattern._end by dslPatternExtension { p, args, /* callInfo */ _ -> applyEnd(p, args) }
internal val String._end by dslStringExtension { p, args, callInfo -> p._end(args, callInfo) }
internal val PatternMapperFn._end by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_end(args, callInfo))
}

/**
 * Sets the sample end position as a fraction of the total sample length (0–1).
 *
 * `1` plays to the very end of the sample; `0` would end immediately (silence). Use with
 * [begin] to play only a segment of a sample.
 *
 * @param pos End position in [0, 1]; 1 = end of sample.
 * @return A pattern with the sample end position set.
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
fun StrudelPattern.end(pos: PatternLike): StrudelPattern = this._end(listOf(pos).asStrudelDslArgs())

/** Reinterprets this pattern's values as sample end positions (0–1). */
@StrudelDsl
fun StrudelPattern.end(): StrudelPattern = this._end(emptyList())

/** Sets the sample end position (0–1) on a string pattern. */
@StrudelDsl
fun String.end(pos: PatternLike): StrudelPattern = this._end(listOf(pos).asStrudelDslArgs())

/** Reinterprets this string pattern's values as sample end positions (0–1). */
@StrudelDsl
fun String.end(): StrudelPattern = this._end(emptyList())

/**
 * Returns a [PatternMapperFn] that sets the sample end position (0–1).
 *
 * @param pos End position in [0, 1].
 * @return A [PatternMapperFn] that sets the end field on the source pattern.
 *
 * ```KlangScript
 * s("breaks").apply(end(0.5))     // via mapper
 * ```
 *
 * @category sampling
 * @tags end, stop, sample, position, offset
 */
@StrudelDsl
fun end(pos: PatternLike): PatternMapperFn = _end(listOf(pos).asStrudelDslArgs())

/** Returns a [PatternMapperFn] that reinterprets the source pattern's values as sample end positions. */
@StrudelDsl
fun end(): PatternMapperFn = _end(emptyList())

/** Chains an end onto this [PatternMapperFn]; sets the sample end position (0–1). */
@StrudelDsl
fun PatternMapperFn.end(pos: PatternLike): PatternMapperFn = this._end(listOf(pos).asStrudelDslArgs())

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceModifier { copy(speed = it?.asDoubleOrNull()) }

fun applySpeed(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern =
    source._liftOrReinterpretNumericalField(args, speedMutation)

internal val _speed by dslPatternMapper { args, callInfo -> { p -> p._speed(args, callInfo) } }
internal val StrudelPattern._speed by dslPatternExtension { p, args, /* callInfo */ _ -> applySpeed(p, args) }
internal val String._speed by dslStringExtension { p, args, callInfo -> p._speed(args, callInfo) }
internal val PatternMapperFn._speed by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_speed(args, callInfo))
}

/**
 * Sets the sample playback speed as a multiplier.
 *
 * `1` is normal speed; `2` doubles the speed (one octave up); `0.5` halves the speed
 * (one octave down). Negative values play the sample in reverse.
 *
 * @param rate Speed multiplier; 1 = normal, 2 = double, -1 = reverse.
 * @return A pattern with the playback speed set.
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
fun StrudelPattern.speed(rate: PatternLike): StrudelPattern = this._speed(listOf(rate).asStrudelDslArgs())

/** Reinterprets this pattern's values as playback speed multipliers. */
@StrudelDsl
fun StrudelPattern.speed(): StrudelPattern = this._speed(emptyList())

/** Sets the sample playback speed on a string pattern. */
@StrudelDsl
fun String.speed(rate: PatternLike): StrudelPattern = this._speed(listOf(rate).asStrudelDslArgs())

/** Reinterprets this string pattern's values as playback speed multipliers. */
@StrudelDsl
fun String.speed(): StrudelPattern = this._speed(emptyList())

/**
 * Returns a [PatternMapperFn] that sets the sample playback speed.
 *
 * @param rate Speed multiplier; 1 = normal, 2 = double, -1 = reverse.
 * @return A [PatternMapperFn] that sets the speed field on the source pattern.
 *
 * ```KlangScript
 * s("breaks").apply(speed(2))       // double speed via mapper
 * ```
 *
 * @category sampling
 * @tags speed, playback, pitch, rate, reverse
 */
@StrudelDsl
fun speed(rate: PatternLike): PatternMapperFn = _speed(listOf(rate).asStrudelDslArgs())

/** Returns a [PatternMapperFn] that reinterprets the source pattern's values as playback speed multipliers. */
@StrudelDsl
fun speed(): PatternMapperFn = _speed(emptyList())

/** Chains a speed onto this [PatternMapperFn]; sets the sample playback speed. */
@StrudelDsl
fun PatternMapperFn.speed(rate: PatternLike): PatternMapperFn = this._speed(listOf(rate).asStrudelDslArgs())

// -- unit() -----------------------------------------------------------------------------------------------------------

private val unitMutation = voiceModifier { copy(unit = it?.asVoiceValue()?.asString) }

fun applyUnit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(unitMutation)
    return source._liftData(control)
}

internal val _unit by dslPatternMapper { args, callInfo -> { p -> p._unit(args, callInfo) } }
internal val StrudelPattern._unit by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnit(p, args) }
internal val String._unit by dslStringExtension { p, args, callInfo -> p._unit(args, callInfo) }
internal val PatternMapperFn._unit by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_unit(args, callInfo))
}

/**
 * Sets the time unit used for sample playback duration.
 *
 * Determines how [speed] and other timing-related parameters are interpreted. The value
 * `"c"` (cycles) makes the sample stretch or compress to fit the pattern's cycle timing.
 * Used internally by [loopAt] and [loopAtCps].
 *
 * @param value Time unit string — `"c"` for cycles, `"s"` for seconds.
 * @return A pattern with the time unit set.
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
fun StrudelPattern.unit(value: PatternLike): StrudelPattern = this._unit(listOf(value).asStrudelDslArgs())

/** Sets the time unit for sample playback on a string pattern. */
@StrudelDsl
fun String.unit(value: PatternLike): StrudelPattern = this._unit(listOf(value).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that sets the time unit for sample playback.
 *
 * @param value Time unit string — `"c"` for cycles, `"s"` for seconds.
 * @return A [PatternMapperFn] that sets the unit field on the source pattern.
 *
 * ```KlangScript
 * s("breaks").apply(unit("c")).slow(2)   // via mapper
 * ```
 *
 * @category sampling
 * @tags unit, time unit, cycles, sample, timing
 */
@StrudelDsl
fun unit(value: PatternLike): PatternMapperFn = _unit(listOf(value).asStrudelDslArgs())

/** Chains a unit onto this [PatternMapperFn]; sets the time unit for sample playback. */
@StrudelDsl
fun PatternMapperFn.unit(value: PatternLike): PatternMapperFn = this._unit(listOf(value).asStrudelDslArgs())

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceModifier { copy(loop = it?.asVoiceValue()?.asBoolean) }

fun applyLoop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(loopMutation)
    return source._liftData(control)
}

internal val _loop by dslPatternMapper { args, callInfo -> { p -> p._loop(args, callInfo) } }
internal val StrudelPattern._loop by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoop(p, args) }
internal val String._loop by dslStringExtension { p, args, callInfo -> p._loop(args, callInfo) }
internal val PatternMapperFn._loop by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loop(args, callInfo))
}

/**
 * Enables continuous looping of the sample.
 *
 * When `loop` is set to a truthy value, the sample repeats continuously after reaching
 * the end (or [loopEnd] position). Use [loopBegin] and [loopEnd] to set the loop region.
 *
 * @param flag Loop flag; truthy = enable looping. Defaults to `true`.
 * @return A pattern with sample looping enabled.
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
fun StrudelPattern.loop(flag: PatternLike = true): StrudelPattern = this._loop(listOf(flag).asStrudelDslArgs())

/** Enables sample looping on a string pattern. */
@StrudelDsl
fun String.loop(flag: PatternLike = true): StrudelPattern = this._loop(listOf(flag).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that enables sample looping.
 *
 * @param flag Loop flag; truthy = enable looping. Defaults to `true`.
 * @return A [PatternMapperFn] that sets the loop flag on the source pattern.
 *
 * ```KlangScript
 * s("pad").apply(loop(1))             // enable looping via mapper
 * ```
 *
 * @category sampling
 * @tags loop, looping, repeat, sample
 */
@StrudelDsl
fun loop(flag: PatternLike = true): PatternMapperFn = _loop(listOf(flag).asStrudelDslArgs())

/** Chains a loop onto this [PatternMapperFn]; enables sample looping. */
@StrudelDsl
fun PatternMapperFn.loop(flag: PatternLike = true): PatternMapperFn = this._loop(listOf(flag).asStrudelDslArgs())

// -- loopBegin() / loopb() --------------------------------------------------------------------------------------------

private val loopBeginMutation = voiceModifier { copy(loopBegin = it?.asDoubleOrNull()) }

fun applyLoopBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern =
    source._liftOrReinterpretNumericalField(args, loopBeginMutation)

internal val _loopBegin by dslPatternMapper { args, callInfo -> { p -> p._loopBegin(args, callInfo) } }
internal val StrudelPattern._loopBegin by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopBegin(p, args) }
internal val String._loopBegin by dslStringExtension { p, args, callInfo -> p._loopBegin(args, callInfo) }
internal val PatternMapperFn._loopBegin by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loopBegin(args, callInfo))
}

internal val _loopb by dslPatternMapper { args, callInfo -> _loopBegin(args, callInfo) }
internal val StrudelPattern._loopb by dslPatternExtension { p, args, callInfo -> p._loopBegin(args, callInfo) }
internal val String._loopb by dslStringExtension { p, args, callInfo -> p._loopb(args, callInfo) }
internal val PatternMapperFn._loopb by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loopBegin(args, callInfo))
}

/**
 * Sets the loop start position as a fraction of the total sample length (0–1).
 *
 * Only relevant when [loop] is enabled. `0` starts looping from the very beginning of
 * the sample. Use together with [loopEnd] to define the looping region.
 *
 * @param pos Loop start position in [0, 1].
 * @return A pattern with the loop start position set.
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
fun StrudelPattern.loopBegin(pos: PatternLike): StrudelPattern = this._loopBegin(listOf(pos).asStrudelDslArgs())

/** Reinterprets this pattern's values as loop start positions (0–1). */
@StrudelDsl
fun StrudelPattern.loopBegin(): StrudelPattern = this._loopBegin(emptyList())

/** Sets the loop start position (0–1) on a string pattern. */
@StrudelDsl
fun String.loopBegin(pos: PatternLike): StrudelPattern = this._loopBegin(listOf(pos).asStrudelDslArgs())

/** Reinterprets this string pattern's values as loop start positions (0–1). */
@StrudelDsl
fun String.loopBegin(): StrudelPattern = this._loopBegin(emptyList())

/**
 * Returns a [PatternMapperFn] that sets the loop start position (0–1).
 *
 * @param pos Loop start position in [0, 1].
 * @return A [PatternMapperFn] that sets the loopBegin field.
 *
 * ```KlangScript
 * s("pad").loop(1).apply(loopBegin(0.25))   // via mapper
 * ```
 *
 * @alias loopb
 * @category sampling
 * @tags loopBegin, loopb, loop, start, sample, position
 */
@StrudelDsl
fun loopBegin(pos: PatternLike): PatternMapperFn = _loopBegin(listOf(pos).asStrudelDslArgs())

/** Returns a [PatternMapperFn] that reinterprets source values as loop start positions. */
@StrudelDsl
fun loopBegin(): PatternMapperFn = _loopBegin(emptyList())

/** Chains a loopBegin onto this [PatternMapperFn]; sets the loop start position. */
@StrudelDsl
fun PatternMapperFn.loopBegin(pos: PatternLike): PatternMapperFn = this._loopBegin(listOf(pos).asStrudelDslArgs())

/**
 * Alias for [loopBegin]. Sets the loop start position.
 *
 * @alias loopBegin
 * @category sampling
 * @tags loopb, loopBegin, loop, start, sample, position
 */
@StrudelDsl
fun StrudelPattern.loopb(pos: PatternLike): StrudelPattern = this._loopb(listOf(pos).asStrudelDslArgs())

/** Alias for [loopBegin] on a string pattern. */
@StrudelDsl
fun String.loopb(pos: PatternLike): StrudelPattern = this._loopb(listOf(pos).asStrudelDslArgs())

/** Alias for [loopBegin] — returns a [PatternMapperFn]. */
@StrudelDsl
fun loopb(pos: PatternLike): PatternMapperFn = _loopb(listOf(pos).asStrudelDslArgs())

/** Chains a loopb (alias for [loopBegin]) onto this [PatternMapperFn]. */
@StrudelDsl
fun PatternMapperFn.loopb(pos: PatternLike): PatternMapperFn = this._loopb(listOf(pos).asStrudelDslArgs())

// -- loopEnd() / loope() ----------------------------------------------------------------------------------------------

private val loopEndMutation = voiceModifier { copy(loopEnd = it?.asDoubleOrNull()) }

fun applyLoopEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern =
    source._liftOrReinterpretNumericalField(args, loopEndMutation)

internal val _loopEnd by dslPatternMapper { args, callInfo -> { p -> p._loopEnd(args, callInfo) } }
internal val StrudelPattern._loopEnd by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopEnd(p, args) }
internal val String._loopEnd by dslStringExtension { p, args, callInfo -> p._loopEnd(args, callInfo) }
internal val PatternMapperFn._loopEnd by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loopEnd(args, callInfo))
}

internal val _loope by dslPatternMapper { args, callInfo -> _loopEnd(args, callInfo) }
internal val StrudelPattern._loope by dslPatternExtension { p, args, callInfo -> p._loopEnd(args, callInfo) }
internal val String._loope by dslStringExtension { p, args, callInfo -> p._loope(args, callInfo) }
internal val PatternMapperFn._loope by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loopEnd(args, callInfo))
}

/**
 * Sets the loop end position as a fraction of the total sample length (0–1).
 *
 * Only relevant when [loop] is enabled. `1` loops to the very end of the sample. Use
 * together with [loopBegin] to define the looping region.
 *
 * @param pos Loop end position in [0, 1].
 * @return A pattern with the loop end position set.
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
fun StrudelPattern.loopEnd(pos: PatternLike): StrudelPattern = this._loopEnd(listOf(pos).asStrudelDslArgs())

/** Reinterprets this pattern's values as loop end positions (0–1). */
@StrudelDsl
fun StrudelPattern.loopEnd(): StrudelPattern = this._loopEnd(emptyList())

/** Sets the loop end position (0–1) on a string pattern. */
@StrudelDsl
fun String.loopEnd(pos: PatternLike): StrudelPattern = this._loopEnd(listOf(pos).asStrudelDslArgs())

/** Reinterprets this string pattern's values as loop end positions (0–1). */
@StrudelDsl
fun String.loopEnd(): StrudelPattern = this._loopEnd(emptyList())

/**
 * Returns a [PatternMapperFn] that sets the loop end position (0–1).
 *
 * @param pos Loop end position in [0, 1].
 * @return A [PatternMapperFn] that sets the loopEnd field.
 *
 * ```KlangScript
 * s("pad").loop(1).apply(loopEnd(0.75))   // via mapper
 * ```
 *
 * @alias loope
 * @category sampling
 * @tags loopEnd, loope, loop, end, sample, position
 */
@StrudelDsl
fun loopEnd(pos: PatternLike): PatternMapperFn = _loopEnd(listOf(pos).asStrudelDslArgs())

/** Returns a [PatternMapperFn] that reinterprets source values as loop end positions. */
@StrudelDsl
fun loopEnd(): PatternMapperFn = _loopEnd(emptyList())

/** Chains a loopEnd onto this [PatternMapperFn]; sets the loop end position. */
@StrudelDsl
fun PatternMapperFn.loopEnd(pos: PatternLike): PatternMapperFn = this._loopEnd(listOf(pos).asStrudelDslArgs())

/**
 * Alias for [loopEnd]. Sets the loop end position.
 *
 * @alias loopEnd
 * @category sampling
 * @tags loope, loopEnd, loop, end, sample, position
 */
@StrudelDsl
fun StrudelPattern.loope(pos: PatternLike): StrudelPattern = this._loope(listOf(pos).asStrudelDslArgs())

/** Alias for [loopEnd] on a string pattern. */
@StrudelDsl
fun String.loope(pos: PatternLike): StrudelPattern = this._loope(listOf(pos).asStrudelDslArgs())

/** Alias for [loopEnd] — returns a [PatternMapperFn]. */
@StrudelDsl
fun loope(pos: PatternLike): PatternMapperFn = _loope(listOf(pos).asStrudelDslArgs())

/** Chains a loope (alias for [loopEnd]) onto this [PatternMapperFn]. */
@StrudelDsl
fun PatternMapperFn.loope(pos: PatternLike): PatternMapperFn = this._loope(listOf(pos).asStrudelDslArgs())

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

internal val _loopAt by dslPatternMapper { args, callInfo -> { p -> p._loopAt(args, callInfo) } }
internal val StrudelPattern._loopAt by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAt(p, args) }
internal val String._loopAt by dslStringExtension { p, args, callInfo -> p._loopAt(args, callInfo) }
internal val PatternMapperFn._loopAt by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loopAt(args, callInfo))
}

/**
 * Fits the sample playback duration to the given number of cycles.
 *
 * Adjusts the [speed] and [unit] automatically so that the sample stretches or compresses
 * to exactly fill the specified number of cycles. Useful for syncing loops to the pattern
 * tempo. `loopAt(1)` fills one cycle; `loopAt(2)` fills two cycles.
 *
 * @param cycles Number of cycles to fit the sample into.
 * @return A pattern with speed and unit adjusted to fill the given cycles.
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
fun StrudelPattern.loopAt(cycles: PatternLike): StrudelPattern = this._loopAt(listOf(cycles).asStrudelDslArgs())

/** Fits the sample to the specified number of cycles on a string pattern. */
@StrudelDsl
fun String.loopAt(cycles: PatternLike): StrudelPattern = this._loopAt(listOf(cycles).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that fits the sample to the given number of cycles.
 *
 * @param cycles Number of cycles to fit the sample into.
 * @return A [PatternMapperFn] that adjusts speed and unit for the given cycle count.
 *
 * ```KlangScript
 * s("breaks").apply(loopAt(1))   // fit to one cycle via mapper
 * ```
 *
 * @category sampling
 * @tags loopAt, loop, fit, cycles, tempo, stretch
 */
@StrudelDsl
fun loopAt(cycles: PatternLike): PatternMapperFn = _loopAt(listOf(cycles).asStrudelDslArgs())

/** Chains a loopAt onto this [PatternMapperFn]; fits the sample to the given number of cycles. */
@StrudelDsl
fun PatternMapperFn.loopAt(cycles: PatternLike): PatternMapperFn = this._loopAt(listOf(cycles).asStrudelDslArgs())

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

internal val _loopAtCps by dslPatternMapper { args, callInfo -> { p -> p._loopAtCps(args, callInfo) } }
internal val StrudelPattern._loopAtCps by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAtCps(p, args) }
internal val String._loopAtCps by dslStringExtension { p, args, callInfo -> p._loopAtCps(args, callInfo) }
internal val PatternMapperFn._loopAtCps by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loopAtCps(args, callInfo))
}

internal val _loopatcps by dslPatternMapper { args, callInfo -> _loopAtCps(args, callInfo) }
internal val StrudelPattern._loopatcps by dslPatternExtension { p, args, callInfo -> p._loopAtCps(args, callInfo) }
internal val String._loopatcps by dslStringExtension { p, args, callInfo -> p._loopatcps(args, callInfo) }
internal val PatternMapperFn._loopatcps by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_loopAtCps(args, callInfo))
}

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
 * Returns a [PatternMapperFn] that fits the sample to the given number of cycles and cps.
 *
 * @param factor Number of cycles to fit the sample into.
 * @param cps Cycles per second for speed calculation.
 * @return A [PatternMapperFn] that adjusts speed and unit for the given cycle count and cps.
 *
 * ```KlangScript
 * s("breaks").apply(loopAtCps(1, 0.5))   // fit to one cycle at default cps
 * ```
 *
 * @alias loopatcps
 * @category sampling
 * @tags loopAtCps, loopatcps, loop, fit, cycles, cps, tempo, stretch
 */
@StrudelDsl
fun loopAtCps(factor: PatternLike, cps: PatternLike): PatternMapperFn =
    _loopAtCps(listOf(factor, cps).asStrudelDslArgs())

/** Chains a loopAtCps onto this [PatternMapperFn]; fits the sample to the given cycles and cps. */
@StrudelDsl
fun PatternMapperFn.loopAtCps(factor: PatternLike, cps: PatternLike): PatternMapperFn =
    this._loopAtCps(listOf(factor, cps).asStrudelDslArgs())

/**
 * Alias for [loopAtCps]. Fits the sample to the given number of cycles and cps.
 *
 * @alias loopAtCps
 * @category sampling
 * @tags loopatcps, loopAtCps, loop, fit, cycles, cps, tempo, stretch
 */
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

/** Alias for [loopAtCps] — returns a [PatternMapperFn]. */
@StrudelDsl
fun loopatcps(factor: PatternLike, cps: PatternLike): PatternMapperFn =
    _loopatcps(listOf(factor, cps).asStrudelDslArgs())

/** Chains a loopatcps (alias for [loopAtCps]) onto this [PatternMapperFn]. */
@StrudelDsl
fun PatternMapperFn.loopatcps(factor: PatternLike, cps: PatternLike): PatternMapperFn =
    this._loopatcps(listOf(factor, cps).asStrudelDslArgs())

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceModifier { copy(cut = it?.asIntOrNull()) }

fun applyCut(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern =
    source._liftOrReinterpretNumericalField(args, cutMutation)

internal val _cut by dslPatternMapper { args, callInfo -> { p -> p._cut(args, callInfo) } }
internal val StrudelPattern._cut by dslPatternExtension { p, args, /* callInfo */ _ -> applyCut(p, args) }
internal val String._cut by dslStringExtension { p, args, callInfo -> p._cut(args, callInfo) }
internal val PatternMapperFn._cut by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_cut(args, callInfo))
}

/**
 * Assigns the sample to a cut group (choke group) by number.
 *
 * Samples in the same cut group cut each other off when a new sample in the group triggers.
 * This is useful for hi-hats: an open hi-hat stops when the closed hi-hat hits. Group `0`
 * means no choke.
 *
 * @param group Cut group number; 0 = no choke.
 * @return A pattern with the cut group assigned.
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
fun StrudelPattern.cut(group: PatternLike): StrudelPattern = this._cut(listOf(group).asStrudelDslArgs())

/** Reinterprets this pattern's values as cut group numbers. */
@StrudelDsl
fun StrudelPattern.cut(): StrudelPattern = this._cut(emptyList())

/** Assigns the sample to a cut group (choke group) on a string pattern. */
@StrudelDsl
fun String.cut(group: PatternLike): StrudelPattern = this._cut(listOf(group).asStrudelDslArgs())

/** Reinterprets this string pattern's values as cut group numbers. */
@StrudelDsl
fun String.cut(): StrudelPattern = this._cut(emptyList())

/**
 * Returns a [PatternMapperFn] that assigns the sample to a cut group.
 *
 * @param group Cut group number; 0 = no choke.
 * @return A [PatternMapperFn] that sets the cut field on the source pattern.
 *
 * ```KlangScript
 * s("hh*4").apply(cut(1))         // assign to cut group 1 via mapper
 * ```
 *
 * @category sampling
 * @tags cut, choke, group, hi-hat, sample
 */
@StrudelDsl
fun cut(group: PatternLike): PatternMapperFn = _cut(listOf(group).asStrudelDslArgs())

/** Returns a [PatternMapperFn] that reinterprets the source pattern's values as cut group numbers. */
@StrudelDsl
fun cut(): PatternMapperFn = _cut(emptyList())

/** Chains a cut onto this [PatternMapperFn]; assigns the sample to the given cut group. */
@StrudelDsl
fun PatternMapperFn.cut(group: PatternLike): PatternMapperFn = this._cut(listOf(group).asStrudelDslArgs())

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

internal val _slice by dslPatternMapper { args, callInfo -> { p -> p._slice(args, callInfo) } }
internal val StrudelPattern._slice by dslPatternExtension { p, args, /* callInfo */ _ -> applySlice(p, args) }
internal val String._slice by dslStringExtension { p, args, callInfo -> p._slice(args, callInfo) }
internal val PatternMapperFn._slice by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_slice(args, callInfo))
}

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

/**
 * Returns a [PatternMapperFn] that plays a specific slice of the sample.
 *
 * @param n Number of equal slices to divide the sample into.
 * @param index Zero-based index of the slice to play.
 * @return A [PatternMapperFn] that sets begin and end to the given slice.
 *
 * ```KlangScript
 * s("breaks").apply(slice(8, 0))   // first of 8 slices via mapper
 * ```
 *
 * @category sampling
 * @tags slice, segment, chop, sample, begin, end
 */
@StrudelDsl
fun slice(n: PatternLike, index: PatternLike): PatternMapperFn = _slice(listOf(n, index).asStrudelDslArgs())

/** Chains a slice onto this [PatternMapperFn]; plays the given slice of the sample. */
@StrudelDsl
fun PatternMapperFn.slice(n: PatternLike, index: PatternLike): PatternMapperFn =
    this._slice(listOf(n, index).asStrudelDslArgs())

// -- splice() ---------------------------------------------------------------------------------------------------------

fun applySplice(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Get number of slices (also used as speed multiplier)
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // Apply slice, then multiply speed by n to maintain timing
    return applySlice(source, args).speed(nVal.toDouble())
}

internal val _splice by dslPatternMapper { args, callInfo -> { p -> p._splice(args, callInfo) } }
internal val StrudelPattern._splice by dslPatternExtension { p, args, /* callInfo */ _ -> applySplice(p, args) }
internal val String._splice by dslStringExtension { p, args, callInfo -> p._splice(args, callInfo) }
internal val PatternMapperFn._splice by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_splice(args, callInfo))
}

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

/**
 * Returns a [PatternMapperFn] that plays a specific slice at the original sample tempo.
 *
 * @param n Number of equal slices to divide the sample into.
 * @param index Zero-based index of the slice to play.
 * @return A [PatternMapperFn] that sets begin, end, and compensates speed for the slice.
 *
 * ```KlangScript
 * s("breaks").apply(splice(8, 0))  // first of 8 slices at original pitch via mapper
 * ```
 *
 * @category sampling
 * @tags splice, slice, chop, sample, speed, tempo
 */
@StrudelDsl
fun splice(n: PatternLike, index: PatternLike): PatternMapperFn = _splice(listOf(n, index).asStrudelDslArgs())

/** Chains a splice onto this [PatternMapperFn]; plays the given slice at original sample tempo. */
@StrudelDsl
fun PatternMapperFn.splice(n: PatternLike, index: PatternLike): PatternMapperFn =
    this._splice(listOf(n, index).asStrudelDslArgs())
