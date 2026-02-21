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
val begin by dslFunction { args, /* callInfo */ _ -> args.toPattern(beginMutation) }

/** Sets the sample start position (0–1) on this pattern. */
@StrudelDsl
val StrudelPattern.begin by dslPatternExtension { p, args, /* callInfo */ _ -> applyBegin(p, args) }

/** Sets the sample start position (0–1) on a string pattern. */
@StrudelDsl
val String.begin by dslStringExtension { p, args, callInfo -> p.begin(args, callInfo) }

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceModifier { copy(end = it?.asDoubleOrNull()) }

fun applyEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(endMutation)
    return source._liftData(control)
}

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
val end by dslFunction { args, /* callInfo */ _ -> args.toPattern(endMutation) }

/** Sets the sample end position (0–1) on this pattern. */
@StrudelDsl
val StrudelPattern.end by dslPatternExtension { p, args, /* callInfo */ _ -> applyEnd(p, args) }

/** Sets the sample end position (0–1) on a string pattern. */
@StrudelDsl
val String.end by dslStringExtension { p, args, callInfo -> p.end(args, callInfo) }

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceModifier { copy(speed = it?.asDoubleOrNull()) }

fun applySpeed(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(speedMutation)
    return source._liftData(control)
}

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
val speed by dslFunction { args, /* callInfo */ _ -> args.toPattern(speedMutation) }

/** Sets the sample playback speed on this pattern. */
@StrudelDsl
val StrudelPattern.speed by dslPatternExtension { p, args, /* callInfo */ _ -> applySpeed(p, args) }

/** Sets the sample playback speed on a string pattern. */
@StrudelDsl
val String.speed by dslStringExtension { p, args, callInfo -> p.speed(args, callInfo) }

// -- unit() -----------------------------------------------------------------------------------------------------------

private val unitMutation = voiceModifier { copy(unit = it?.asVoiceValue()?.asString) }

fun applyUnit(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(unitMutation)
    return source._liftData(control)
}

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
val unit by dslFunction { args, /* callInfo */ _ -> args.toPattern(unitMutation) }

/** Sets the time unit for sample playback on this pattern. */
@StrudelDsl
val StrudelPattern.unit by dslPatternExtension { p, args, /* callInfo */ _ -> applyUnit(p, args) }

/** Sets the time unit for sample playback on a string pattern. */
@StrudelDsl
val String.unit by dslStringExtension { p, args, callInfo -> p.unit(args, callInfo) }

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceModifier { copy(loop = it?.asVoiceValue()?.asBoolean) }

fun applyLoop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(loopMutation)
    return source._liftData(control)
}

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
val loop by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopMutation) }

/** Enables sample looping on this pattern. */
@StrudelDsl
val StrudelPattern.loop by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoop(p, args) }

/** Enables sample looping on a string pattern. */
@StrudelDsl
val String.loop by dslStringExtension { p, args, callInfo -> p.loop(args, callInfo) }

// -- loopBegin() / loopb() --------------------------------------------------------------------------------------------

private val loopBeginMutation = voiceModifier { copy(loopBegin = it?.asDoubleOrNull()) }

fun applyLoopBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(loopBeginMutation)
    return source._liftData(control)
}

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
val loopBegin by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopBeginMutation) }

/** Sets the loop start position (0–1) on this pattern. */
@StrudelDsl
val StrudelPattern.loopBegin by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopBegin(p, args) }

/** Sets the loop start position (0–1) on a string pattern. */
@StrudelDsl
val String.loopBegin by dslStringExtension { p, args, _ -> applyLoopBegin(p, args) }

/** Alias for [loopBegin] on this pattern. */
@StrudelDsl
val StrudelPattern.loopb by dslPatternExtension { p, args, callInfo -> p.loopBegin(args, callInfo) }

/**
 * Alias for [loopBegin]. Sets the loop start position.
 *
 * @alias loopBegin
 * @category sampling
 * @tags loopb, loopBegin, loop, start, sample, position
 */
@StrudelDsl
val loopb by dslFunction { args, callInfo -> loopBegin(args, callInfo) }

/** Alias for [loopBegin] on a string pattern. */
@StrudelDsl
val String.loopb by dslStringExtension { p, args, callInfo -> p.loopBegin(args, callInfo) }

// -- loopEnd() / loope() ----------------------------------------------------------------------------------------------

private val loopEndMutation = voiceModifier { copy(loopEnd = it?.asDoubleOrNull()) }

fun applyLoopEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(loopEndMutation)
    return source._liftData(control)
}

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
val loopEnd by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopEndMutation) }

/** Sets the loop end position (0–1) on this pattern. */
@StrudelDsl
val StrudelPattern.loopEnd by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopEnd(p, args) }

/** Sets the loop end position (0–1) on a string pattern. */
@StrudelDsl
val String.loopEnd by dslStringExtension { p, args, _ -> applyLoopEnd(p, args) }

/** Alias for [loopEnd] on this pattern. */
@StrudelDsl
val StrudelPattern.loope by dslPatternExtension { p, args, callInfo -> p.loopEnd(args, callInfo) }

/**
 * Alias for [loopEnd]. Sets the loop end position.
 *
 * @alias loopEnd
 * @category sampling
 * @tags loope, loopEnd, loop, end, sample, position
 */
@StrudelDsl
val loope by dslFunction { args, callInfo -> loopEnd(args, callInfo) }

/** Alias for [loopEnd] on a string pattern. */
@StrudelDsl
val String.loope by dslStringExtension { p, args, callInfo -> p.loopEnd(args, callInfo) }

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
val loopAt by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopAtSpeedMutation) }

/** Fits the sample to the specified number of cycles on this pattern. */
@StrudelDsl
val StrudelPattern.loopAt by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAt(p, args) }

/** Fits the sample to the specified number of cycles on a string pattern. */
@StrudelDsl
val String.loopAt by dslStringExtension { p, args, callInfo -> p.loopAt(args, callInfo) }

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
val loopAtCps by dslFunction { args, /* callInfo */ _ ->
    if (args.isEmpty()) {
        return@dslFunction silence
    }

    // In JavaScript: function (factor, cps, pat)
    // So args are: [factor, cps, ...pattern parts]
    val pattern = args.drop(2).toPattern(voiceValueModifier)
    applyLoopAtCps(pattern, args.take(2))
}

/** Fits the sample to the given number of cycles and cps value on this pattern. */
@StrudelDsl
val StrudelPattern.loopAtCps by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAtCps(p, args) }

/** Fits the sample to the given number of cycles and cps value on this pattern (numeric overload). */
@StrudelDsl
fun StrudelPattern.loopAtCps(factor: Number, cps: Number = 0.5): StrudelPattern {
    return this.loopAtCps(listOf(factor, cps).asStrudelDslArgs())
}

/** Fits the sample to the given number of cycles and cps value on a string pattern. */
@StrudelDsl
val String.loopAtCps by dslStringExtension { p, args, callInfo -> p.loopAtCps(args, callInfo) }

/** Fits the sample to the given number of cycles and cps value on a string pattern (numeric overload). */
@StrudelDsl
fun String.loopAtCps(factor: Number, cps: Number = 0.5): StrudelPattern {
    return this.loopAtCps(listOf(factor, cps).asStrudelDslArgs())
}

/**
 * Alias for [loopAtCps]. Fits the sample to the given number of cycles and cps.
 *
 * @alias loopAtCps
 * @category sampling
 * @tags loopatcps, loopAtCps, loop, fit, cycles, cps, tempo, stretch
 */
@StrudelDsl
val loopatcps by dslFunction { args, callInfo -> loopAtCps(args, callInfo) }

/** Alias for [loopAtCps] on this pattern. */
@StrudelDsl
val StrudelPattern.loopatcps by dslPatternExtension { p, args, callInfo -> p.loopAtCps(args, callInfo) }

/** Alias for [loopAtCps] on this pattern (numeric overload). */
@StrudelDsl
fun StrudelPattern.loopatcps(factor: Number, cps: Number = 0.5): StrudelPattern {
    return this.loopAtCps(factor, cps)
}

/** Alias for [loopAtCps] on a string pattern. */
@StrudelDsl
val String.loopatcps by dslStringExtension { p, args, callInfo -> p.loopAtCps(args, callInfo) }

/** Alias for [loopAtCps] on a string pattern (numeric overload). */
@StrudelDsl
fun String.loopatcps(factor: Number, cps: Number = 0.5): StrudelPattern {
    return this.loopAtCps(factor, cps)
}

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceModifier { copy(cut = it?.asIntOrNull()) }

fun applyCut(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(cutMutation)
    return source._liftData(control)
}

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
val cut by dslFunction { args, /* callInfo */ _ -> args.toPattern(cutMutation) }

/** Assigns the sample to a cut group (choke group) on this pattern. */
@StrudelDsl
val StrudelPattern.cut by dslPatternExtension { p, args, /* callInfo */ _ -> applyCut(p, args) }

/** Assigns the sample to a cut group (choke group) on a string pattern. */
@StrudelDsl
val String.cut by dslStringExtension { p, args, callInfo -> p.cut(args, callInfo) }

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
val StrudelPattern.slice by dslPatternExtension { p, args, /* callInfo */ _ -> applySlice(p, args) }

/**
 * Plays a specific slice of the sample on a string pattern.
 * See [StrudelPattern.slice] for full documentation.
 */
@StrudelDsl
val String.slice by dslStringExtension { p, args, callInfo -> p.slice(args, callInfo) }

// -- splice() ---------------------------------------------------------------------------------------------------------

fun applySplice(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Get number of slices (also used as speed multiplier)
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // Apply slice, then multiply speed by n to maintain timing
    return source.slice(args).speed(nVal.toDouble())
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
val StrudelPattern.splice by dslPatternExtension { p, args, /* callInfo */ _ -> applySplice(p, args) }

/**
 * Plays a specific slice of the sample at the original tempo on a string pattern.
 * See [StrudelPattern.splice] for full documentation.
 */
@StrudelDsl
val String.splice by dslStringExtension { p, args, callInfo -> p.splice(args, callInfo) }
