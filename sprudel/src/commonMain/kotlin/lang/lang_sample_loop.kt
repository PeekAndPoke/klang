/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._innerJoin
import io.peekandpoke.klang.sprudel._liftData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceSetter { loop = it?.asVoiceValue()?.asBoolean }

private fun applyLoop(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(SprudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(loopMutation)
    return source._liftData(control)
}

/**
 * Enables continuous looping of the sample.
 *
 * When `loop` is set to a truthy value, the sample repeats continuously after reaching
 * the end (or the [end] position). Use [begin] and [end] to set the loop region.
 *
 * @param flag Loop flag; truthy = enable looping. Defaults to `true`.
 * @return A pattern with sample looping enabled.
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1)                    // loop the sample continuously
 * ```
 *
 * ```KlangScript(Playable)
 * s("pad").loop(1).begin(0.25).end(0.75)  // loop only the middle section
 * ```
 *
 * @category sampling
 * @tags loop, looping, repeat, sample
 */
@KlangScript.Function
fun SprudelPattern.loop(flag: PatternLike = true, callInfo: CallInfo? = null): SprudelPattern =
    applyLoop(this, listOf(flag).asSprudelDslArgs(callInfo))

/** Enables sample looping on a string pattern. */
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
@KlangScript.Function
fun loop(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loop(flag, callInfo) }

/** Chains a loop onto this [PatternMapperFn]; enables sample looping. */
@KlangScript.Function
fun PatternMapperFn.loop(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.loop(flag, callInfo) }

// -- loopAt() ---------------------------------------------------------------------------------------------------------

private val loopAtSpeedMutation = voiceSetter {
    val value = it?.asDoubleOrNull()
    speed = if (value == null) null else 1.0 / (2.0 * value)
}

private fun applyLoopAt(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    if (args.isEmpty()) return source

    // Get the loopAt factor
    val factor = args[0].value?.asDoubleOrNull() ?: return source

    // Apply slow() to stretch the events to the desired duration
    // loopAt(2) stretches events to 2 cycles, loopAt(0.5) compresses to 0.5 cycles
    val slowed = source.unit("c").slow(factor)

    // Then set the speed parameter and unit to compensate for sample playback
    // Compensate sample playback so it plays at its natural rate across factor cycles.
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
@KlangScript.Function
fun SprudelPattern.loopAt(cycles: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyLoopAt(this, listOf(cycles).asSprudelDslArgs(callInfo))

/** Fits the sample to the specified number of cycles on a string pattern. */
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
@KlangScript.Function
fun loopAt(cycles: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loopAt(cycles, callInfo) }

/** Chains a loopAt onto this [PatternMapperFn]; fits the sample to the given number of cycles. */
@KlangScript.Function
fun PatternMapperFn.loopAt(cycles: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.loopAt(cycles, callInfo) }

// -- loopAtCps() ------------------------------------------------------------------------------------------------------

private fun applyLoopAtCps(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._innerJoin(args) { pat, factorValue, cpsValue ->
        val factor = factorValue?.asDouble ?: return@_innerJoin silence
        val cps = cpsValue?.asDouble ?: 0.5

        // Calculate speed: (1 / factor) * cps
        val speed = (1.0 / factor) * cps

        // Compensate sample playback so it plays at its natural rate across factor cycles.
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
@KlangScript.Function
fun SprudelPattern.loopAtCps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyLoopAtCps(this, listOf(factor, cps).asSprudelDslArgs(callInfo))

/** Fits the sample to the given number of cycles and cps value on this pattern (numeric overload). */
fun SprudelPattern.loopAtCps(factor: Number, cps: Number = 0.5): SprudelPattern =
    this.loopAtCps(factor as PatternLike, cps as PatternLike)

/** Fits the sample to the given number of cycles and cps value on a string pattern. */
@KlangScript.Function
fun String.loopAtCps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopAtCps(factor, cps, callInfo)

/** Fits the sample to the given number of cycles and cps value on a string pattern (numeric overload). */
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
@KlangScript.Function
fun loopAtCps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.loopAtCps(factor, cps, callInfo) }

/** Chains a loopAtCps onto this [PatternMapperFn]; fits the sample to the given cycles and cps. */
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
@KlangScript.Function
fun SprudelPattern.loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.loopAtCps(factor, cps, callInfo)

/** Alias for [loopAtCps] on this pattern (numeric overload). */
fun SprudelPattern.loopatcps(factor: Number, cps: Number = 0.5): SprudelPattern = this.loopAtCps(factor, cps)

/** Alias for [loopAtCps] on a string pattern. */
@KlangScript.Function
fun String.loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).loopatcps(factor, cps, callInfo)

/** Alias for [loopAtCps] on a string pattern (numeric overload). */
fun String.loopatcps(factor: Number, cps: Number = 0.5): SprudelPattern = this.loopAtCps(factor, cps)

/** Alias for [loopAtCps] — returns a [PatternMapperFn]. */
@KlangScript.Function
fun loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    loopAtCps(factor, cps, callInfo)

/** Chains a loopatcps (alias for [loopAtCps]) onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.loopatcps(factor: PatternLike, cps: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.loopAtCps(factor, cps, callInfo)
