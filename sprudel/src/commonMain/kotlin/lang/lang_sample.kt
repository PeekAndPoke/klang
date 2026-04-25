@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._liftData
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangSampleInit = false

// -- begin() ----------------------------------------------------------------------------------------------------------

private val beginMutation = voiceModifier { copy(begin = it?.asDoubleOrNull()) }

private fun applyBegin(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    source._liftOrReinterpretNumericalField(args, beginMutation)

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
 * ```KlangScript(Playable)
 * s("breaks").begin(0.5)            // start halfway through the sample
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").begin("<0 0.25 0.5>") // cycle through three start positions
 * ```
 *
 * @category sampling
 * @tags begin, start, sample, position, offset
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.begin(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBegin(this, listOfNotNull(pos).asSprudelDslArgs(callInfo))

/** Sets the sample start position (0–1) on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.begin(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).begin(pos, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sample start position (0–1).
 *
 * @param pos Start position in [0, 1].
 * @return A [PatternMapperFn] that sets the begin field on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(begin(0.5))     // via mapper
 * ```
 *
 * @category sampling
 * @tags begin, start, sample, position, offset
 */
@SprudelDsl
@KlangScript.Function
fun begin(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.begin(pos, callInfo) }

/** Chains a begin onto this [PatternMapperFn]; sets the sample start position (0–1). */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.begin(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.begin(pos, callInfo) }

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceModifier { copy(end = it?.asDoubleOrNull()) }

private fun applyEnd(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    source._liftOrReinterpretNumericalField(args, endMutation)

/**
 * Sets the sample end position as a fraction of the total sample length (0–1).
 *
 * `1` plays to the very end of the sample; `0` would end immediately (silence). Use with
 * [begin] to play only a segment of a sample.
 *
 * @param pos End position in [0, 1]; 1 = end of sample.
 * @return A pattern with the sample end position set.
 *
 * ```KlangScript(Playable)
 * s("breaks").end(0.5)            // play only the first half of the sample
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").begin(0.25).end(0.75)  // play the middle 50% of the sample
 * ```
 *
 * @category sampling
 * @tags end, stop, sample, position, offset
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.end(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyEnd(this, listOfNotNull(pos).asSprudelDslArgs(callInfo))

/** Sets the sample end position (0–1) on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.end(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).end(pos, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sample end position (0–1).
 *
 * @param pos End position in [0, 1].
 * @return A [PatternMapperFn] that sets the end field on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(end(0.5))     // via mapper
 * ```
 *
 * @category sampling
 * @tags end, stop, sample, position, offset
 */
@SprudelDsl
@KlangScript.Function
fun end(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.end(pos, callInfo) }

/** Chains an end onto this [PatternMapperFn]; sets the sample end position (0–1). */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.end(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.end(pos, callInfo) }

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceModifier { copy(speed = it?.asDoubleOrNull()) }

private fun applySpeed(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    source._liftOrReinterpretNumericalField(args, speedMutation)

/**
 * Sets the sample playback speed as a multiplier.
 *
 * `1` is normal speed; `2` doubles the speed (one octave up); `0.5` halves the speed
 * (one octave down). Negative values play the sample in reverse.
 *
 * @param rate Speed multiplier; 1 = normal, 2 = double, -1 = reverse.
 * @return A pattern with the playback speed set.
 *
 * ```KlangScript(Playable)
 * s("breaks").speed(2)              // double speed, one octave up
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").speed("<1 -1>")           // alternate forward and reverse per cycle
 * ```
 *
 * @category sampling
 * @tags speed, playback, pitch, rate, reverse
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.speed(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applySpeed(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))

/** Sets the sample playback speed on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.speed(rate: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).speed(rate, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the sample playback speed.
 *
 * @param rate Speed multiplier; 1 = normal, 2 = double, -1 = reverse.
 * @return A [PatternMapperFn] that sets the speed field on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(speed(2))       // double speed via mapper
 * ```
 *
 * @category sampling
 * @tags speed, playback, pitch, rate, reverse
 */
@SprudelDsl
@KlangScript.Function
fun speed(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.speed(rate, callInfo) }

/** Chains a speed onto this [PatternMapperFn]; sets the sample playback speed. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.speed(rate: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.speed(rate, callInfo) }

// -- unit() -----------------------------------------------------------------------------------------------------------

private val unitMutation = voiceModifier { copy(unit = it?.asVoiceValue()?.asString) }

private fun applyUnit(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * @param value Time unit string — `"c"` for cycles, `"s"` for seconds.
 * @return A pattern with the time unit set.
 *
 * ```KlangScript(Playable)
 * s("breaks").unit("c").slow(2)     // stretch sample to fill 2 cycles
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").speed(0.5).unit("c")  // half-speed in cycle units
 * ```
 *
 * @category sampling
 * @tags unit, time unit, cycles, sample, timing
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.unit(value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyUnit(this, listOf(value).asSprudelDslArgs(callInfo))

/** Sets the time unit for sample playback on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.unit(value: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).unit(value, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the time unit for sample playback.
 *
 * @param value Time unit string — `"c"` for cycles, `"s"` for seconds.
 * @return A [PatternMapperFn] that sets the unit field on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(unit("c")).slow(2)   // via mapper
 * ```
 *
 * @category sampling
 * @tags unit, time unit, cycles, sample, timing
 */
@SprudelDsl
@KlangScript.Function
fun unit(value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.unit(value, callInfo) }

/** Chains a unit onto this [PatternMapperFn]; sets the time unit for sample playback. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.unit(value: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.unit(value, callInfo) }

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceModifier { copy(loop = it?.asVoiceValue()?.asBoolean) }

private fun applyLoop(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(SprudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(loopMutation)
    return source._liftData(control)
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
 * ```KlangScript(Playable)
 * s("pad").loop(1)                    // loop the sample continuously
 * ```
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).loopBegin(0.25).loopEnd(0.75)  // loop only the middle section
 * ```
 *
 * @category sampling
 * @tags loop, looping, repeat, sample
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loop(flag: PatternLike = true, callInfo: CallInfo? = null): SprudelPattern =
    applyLoop(this, listOf(flag).asSprudelDslArgs(callInfo))

/** Enables sample looping on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loop(flag: PatternLike = true, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loop(flag, callInfo)

/**
 * Returns a [PatternMapperFn] that enables sample looping.
 *
 * @param flag Loop flag; truthy = enable looping. Defaults to `true`.
 * @return A [PatternMapperFn] that sets the loop flag on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("pad").apply(loop(1))             // enable looping via mapper
 * ```
 *
 * @category sampling
 * @tags loop, looping, repeat, sample
 */
@SprudelDsl
@KlangScript.Function
fun loop(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loop(flag, callInfo) }

/** Chains a loop onto this [PatternMapperFn]; enables sample looping. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loop(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.loop(flag, callInfo) }

// -- loopBegin() / loopb() --------------------------------------------------------------------------------------------

private val loopBeginMutation = voiceModifier { copy(loopBegin = it?.asDoubleOrNull()) }

private fun applyLoopBegin(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    source._liftOrReinterpretNumericalField(args, loopBeginMutation)

/**
 * Sets the loop start position as a fraction of the total sample length (0–1).
 *
 * Only relevant when [loop] is enabled. `0` starts looping from the very beginning of
 * the sample. Use together with [loopEnd] to define the looping region.
 *
 * @param pos Loop start position in [0, 1].
 * @return A pattern with the loop start position set.
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).loopBegin(0.25)              // loop starts at 25% into the sample
 * ```
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).loopBegin("<0 0.5>")         // alternate loop start position per cycle
 * ```
 *
 * @alias loopb
 * @category sampling
 * @tags loopBegin, loopb, loop, start, sample, position
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loopBegin(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLoopBegin(this, listOfNotNull(pos).asSprudelDslArgs(callInfo))

/** Sets the loop start position (0–1) on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loopBegin(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopBegin(pos, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the loop start position (0–1).
 *
 * @param pos Loop start position in [0, 1].
 * @return A [PatternMapperFn] that sets the loopBegin field.
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).apply(loopBegin(0.25))   // via mapper
 * ```
 *
 * @alias loopb
 * @category sampling
 * @tags loopBegin, loopb, loop, start, sample, position
 */
@SprudelDsl
@KlangScript.Function
fun loopBegin(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loopBegin(pos, callInfo) }

/** Chains a loopBegin onto this [PatternMapperFn]; sets the loop start position. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loopBegin(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.loopBegin(pos, callInfo) }

/**
 * Alias for [loopBegin]. Sets the loop start position.
 *
 * @param pos Loop start position as a ratio of sample length. Default: same as begin(). Range: 0.0–1.0.
 * @alias loopBegin
 * @category sampling
 * @tags loopb, loopBegin, loop, start, sample, position
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loopb(pos: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.loopBegin(pos, callInfo)

/** Alias for [loopBegin] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loopb(pos: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopb(pos, callInfo)

/** Alias for [loopBegin] — returns a [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun loopb(pos: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    loopBegin(pos, callInfo)

/** Chains a loopb (alias for [loopBegin]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loopb(pos: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.loopBegin(pos, callInfo)

// -- loopEnd() / loope() ----------------------------------------------------------------------------------------------

private val loopEndMutation = voiceModifier { copy(loopEnd = it?.asDoubleOrNull()) }

private fun applyLoopEnd(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    source._liftOrReinterpretNumericalField(args, loopEndMutation)

/**
 * Sets the loop end position as a fraction of the total sample length (0–1).
 *
 * Only relevant when [loop] is enabled. `1` loops to the very end of the sample. Use
 * together with [loopBegin] to define the looping region.
 *
 * @param pos Loop end position in [0, 1].
 * @return A pattern with the loop end position set.
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).loopEnd(0.75)              // loop ends at 75% into the sample
 * ```
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).loopEnd("<0.5 1>")         // alternate loop end position per cycle
 * ```
 *
 * @alias loope
 * @category sampling
 * @tags loopEnd, loope, loop, end, sample, position
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loopEnd(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyLoopEnd(this, listOfNotNull(pos).asSprudelDslArgs(callInfo))

/** Sets the loop end position (0–1) on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loopEnd(pos: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopEnd(pos, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the loop end position (0–1).
 *
 * @param pos Loop end position in [0, 1].
 * @return A [PatternMapperFn] that sets the loopEnd field.
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).apply(loopEnd(0.75))   // via mapper
 * ```
 *
 * @alias loope
 * @category sampling
 * @tags loopEnd, loope, loop, end, sample, position
 */
@SprudelDsl
@KlangScript.Function
fun loopEnd(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loopEnd(pos, callInfo) }

/** Chains a loopEnd onto this [PatternMapperFn]; sets the loop end position. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loopEnd(pos: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.loopEnd(pos, callInfo) }

/**
 * Alias for [loopEnd]. Sets the loop end position.
 *
 * @param pos Loop end position as a ratio of sample length. Default: same as end(). Range: 0.0–1.0.
 * @alias loopEnd
 * @category sampling
 * @tags loope, loopEnd, loop, end, sample, position
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loope(pos: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.loopEnd(pos, callInfo)

/** Alias for [loopEnd] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loope(pos: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loope(pos, callInfo)

/** Alias for [loopEnd] — returns a [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun loope(pos: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    loopEnd(pos, callInfo)

/** Chains a loope (alias for [loopEnd]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loope(pos: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.loopEnd(pos, callInfo)

// -- loopAt() ---------------------------------------------------------------------------------------------------------

private val loopAtSpeedMutation = voiceModifier {
    val value = it?.asDoubleOrNull()
    if (value == null) copy(speed = null) else copy(speed = 1.0 / (2.0 * value))
}

private fun applyLoopAt(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * @param cycles Number of cycles to fit the sample into.
 * @return A pattern with speed and unit adjusted to fill the given cycles.
 *
 * ```KlangScript(Playable)
 * s("breaks").loopAt(1)          // stretch/compress to exactly one cycle
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").loopAt(2).slow(2)  // fill two cycles, then play at half speed
 * ```
 *
 * @category sampling
 * @tags loopAt, loop, fit, cycles, tempo, stretch
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loopAt(cycles: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyLoopAt(this, listOf(cycles).asSprudelDslArgs(callInfo))

/** Fits the sample to the specified number of cycles on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loopAt(cycles: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopAt(cycles, callInfo)

/**
 * Returns a [PatternMapperFn] that fits the sample to the given number of cycles.
 *
 * @param cycles Number of cycles to fit the sample into.
 * @return A [PatternMapperFn] that adjusts speed and unit for the given cycle count.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(loopAt(1))   // fit to one cycle via mapper
 * ```
 *
 * @category sampling
 * @tags loopAt, loop, fit, cycles, tempo, stretch
 */
@SprudelDsl
@KlangScript.Function
fun loopAt(cycles: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loopAt(cycles, callInfo) }

/** Chains a loopAt onto this [PatternMapperFn]; fits the sample to the given number of cycles. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loopAt(cycles: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.loopAt(cycles, callInfo) }

// -- loopAtCps() ------------------------------------------------------------------------------------------------------

private fun applyLoopAtCps(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * ```KlangScript(Playable)
 * s("breaks").loopAtCps(1, 0.5)    // fit to 1 cycle at 0.5 cps (default tempo)
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").loopAtCps(2, 0.75)   // fit to 2 cycles at 0.75 cps
 * ```
 *
 * @param factor Number of cycles to fit the sample into. Default: 1.
 * @param cps Cycles per second used for speed calculation. Default: 0.5.
 * @alias loopatcps
 * @category sampling
 * @tags loopAtCps, loopatcps, loop, fit, cycles, cps, tempo, stretch
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loopAtCps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyLoopAtCps(this, listOf(factor, cps).asSprudelDslArgs(callInfo))

/** Fits the sample to the given number of cycles and cps value on this pattern (numeric overload). */
@SprudelDsl
fun SprudelPattern.loopAtCps(factor: Number, cps: Number = 0.5): SprudelPattern =
    this.loopAtCps(factor as PatternLike, cps as PatternLike)

/** Fits the sample to the given number of cycles and cps value on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loopAtCps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopAtCps(factor, cps, callInfo)

/** Fits the sample to the given number of cycles and cps value on a string pattern (numeric overload). */
@SprudelDsl
fun String.loopAtCps(factor: Number, cps: Number = 0.5): SprudelPattern =
    this.loopAtCps(factor as PatternLike, cps as PatternLike)

/**
 * Returns a [PatternMapperFn] that fits the sample to the given number of cycles and cps.
 *
 * @param factor Number of cycles to fit the sample into.
 * @param cps Cycles per second for speed calculation.
 * @return A [PatternMapperFn] that adjusts speed and unit for the given cycle count and cps.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(loopAtCps(1, 0.5))   // fit to one cycle at default cps
 * ```
 *
 * @alias loopatcps
 * @category sampling
 * @tags loopAtCps, loopatcps, loop, fit, cycles, cps, tempo, stretch
 */
@SprudelDsl
@KlangScript.Function
fun loopAtCps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loopAtCps(factor, cps, callInfo) }

/** Chains a loopAtCps onto this [PatternMapperFn]; fits the sample to the given cycles and cps. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loopAtCps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.loopAtCps(factor, cps, callInfo) }

/**
 * Alias for [loopAtCps]. Fits the sample to the given number of cycles and cps.
 *
 * @param factor Number of cycles to fit the sample into. Default: 1.
 * @param cps Cycles per second used for speed calculation. Default: 0.5.
 * @alias loopAtCps
 * @category sampling
 * @tags loopatcps, loopAtCps, loop, fit, cycles, cps, tempo, stretch
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.loopAtCps(factor, cps, callInfo)

/** Alias for [loopAtCps] on this pattern (numeric overload). */
@SprudelDsl
fun SprudelPattern.loopatcps(factor: Number, cps: Number = 0.5): SprudelPattern = this.loopAtCps(factor, cps)

/** Alias for [loopAtCps] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopatcps(factor, cps, callInfo)

/** Alias for [loopAtCps] on a string pattern (numeric overload). */
@SprudelDsl
fun String.loopatcps(factor: Number, cps: Number = 0.5): SprudelPattern = this.loopAtCps(factor, cps)

/** Alias for [loopAtCps] — returns a [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    loopAtCps(factor, cps, callInfo)

/** Chains a loopatcps (alias for [loopAtCps]) onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.loopAtCps(factor, cps, callInfo)

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceModifier { copy(cut = it?.asIntOrNull()) }

private fun applyCut(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    source._liftOrReinterpretNumericalField(args, cutMutation)

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
 * ```KlangScript(Playable)
 * stack(s("hh*4").cut(1), s("~ oh ~").cut(1))  // open hh choked by closed hh
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").cut("<0 1>")                        // alternate between no-cut and cut-group-1
 * ```
 *
 * @category sampling
 * @tags cut, choke, group, hi-hat, sample
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.cut(group: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCut(this, listOfNotNull(group).asSprudelDslArgs(callInfo))

/** Assigns the sample to a cut group (choke group) on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.cut(group: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).cut(group, callInfo)

/**
 * Returns a [PatternMapperFn] that assigns the sample to a cut group.
 *
 * @param group Cut group number; 0 = no choke.
 * @return A [PatternMapperFn] that sets the cut field on the source pattern.
 *
 * ```KlangScript(Playable)
 * s("hh*4").apply(cut(1))         // assign to cut group 1 via mapper
 * ```
 *
 * @category sampling
 * @tags cut, choke, group, hi-hat, sample
 */
@SprudelDsl
@KlangScript.Function
fun cut(group: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.cut(group, callInfo) }

/** Chains a cut onto this [PatternMapperFn]; assigns the sample to the given cut group. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.cut(group: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.cut(group, callInfo) }

// -- slice() ----------------------------------------------------------------------------------------------------------

private fun applySlice(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
 * ```KlangScript(Playable)
 * s("breaks").slice(8, 0)                // play the first of 8 slices
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").slice(8, "0 1 2 3 4 5 6 7".i)  // sequence through all 8 slices
 * ```
 *
 * @param n Number of equal slices to divide the sample into. Integer.
 * @param index Zero-based index of the slice to play. Integer.
 * @category sampling
 * @tags slice, segment, chop, sample, begin, end
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySlice(this, listOf(n, index).asSprudelDslArgs(callInfo))

/**
 * Plays a specific slice of the sample on a string pattern.
 * See [SprudelPattern.slice] for full documentation.
 */
@SprudelDsl
@KlangScript.Function
fun String.slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).slice(n, index, callInfo)

/**
 * Returns a [PatternMapperFn] that plays a specific slice of the sample.
 *
 * @param n Number of equal slices to divide the sample into.
 * @param index Zero-based index of the slice to play.
 * @return A [PatternMapperFn] that sets begin and end to the given slice.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(slice(8, 0))   // first of 8 slices via mapper
 * ```
 *
 * @category sampling
 * @tags slice, segment, chop, sample, begin, end
 */
@SprudelDsl
@KlangScript.Function
fun slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.slice(n, index, callInfo) }

/** Chains a slice onto this [PatternMapperFn]; plays the given slice of the sample. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.slice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.slice(n, index, callInfo) }

// -- splice() ---------------------------------------------------------------------------------------------------------

private fun applySplice(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    // Get number of slices (also used as speed multiplier)
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // Apply slice, then multiply speed by n to maintain timing
    return applySlice(source, args).speed(nVal.toDouble())
}

/**
 * Plays a specific slice of the sample at the original sample tempo.
 *
 * Like [slice], but multiplies [speed] by `n` to compensate for the shorter segment
 * duration, so each slice plays at the same pitch and rate as the original sample.
 * Useful for beat-slicing without pitch artifacts.
 *
 * ```KlangScript(Playable)
 * s("breaks").splice(8, 0)               // first of 8 slices at original pitch
 * ```
 *
 * ```KlangScript(Playable)
 * s("breaks").splice(8, "0 2 4 6".i)    // every other slice, original tempo
 * ```
 *
 * @param n Number of equal slices to divide the sample into; also used as speed multiplier. Integer.
 * @param index Zero-based index of the slice to play. Integer.
 * @category sampling
 * @tags splice, slice, chop, sample, speed, tempo
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applySplice(this, listOf(n, index).asSprudelDslArgs(callInfo))

/**
 * Plays a specific slice of the sample at the original tempo on a string pattern.
 * See [SprudelPattern.splice] for full documentation.
 */
@SprudelDsl
@KlangScript.Function
fun String.splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).splice(n, index, callInfo)

/**
 * Returns a [PatternMapperFn] that plays a specific slice at the original sample tempo.
 *
 * @param n Number of equal slices to divide the sample into.
 * @param index Zero-based index of the slice to play.
 * @return A [PatternMapperFn] that sets begin, end, and compensates speed for the slice.
 *
 * ```KlangScript(Playable)
 * s("breaks").apply(splice(8, 0))  // first of 8 slices at original pitch via mapper
 * ```
 *
 * @category sampling
 * @tags splice, slice, chop, sample, speed, tempo
 */
@SprudelDsl
@KlangScript.Function
fun splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.splice(n, index, callInfo) }

/** Chains a splice onto this [PatternMapperFn]; plays the given slice at original sample tempo. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.splice(n: PatternLike, index: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.splice(n, index, callInfo) }
