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
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- compressor ------------------------------------------------------------------------------------------------------

private val compressorThresholdMutation = voiceSetter { compressorThreshold = it?.asDoubleOrNull() ?: compressorThreshold }

private fun applyCompressorThreshold(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorThreshold }, update = compressorThresholdMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorThresholdMutation)
}

private val compressorRatioMutation = voiceSetter { compressorRatio = it?.asDoubleOrNull() ?: compressorRatio }

private fun applyCompressorRatio(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorRatio }, update = compressorRatioMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorRatioMutation)
}

private val compressorKneeMutation = voiceSetter { compressorKnee = it?.asDoubleOrNull() ?: compressorKnee }

private fun applyCompressorKnee(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorKnee }, update = compressorKneeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorKneeMutation)
}

private val compressorAttackMutation = voiceSetter { compressorAttack = it?.asDoubleOrNull() ?: compressorAttack }

private fun applyCompressorAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorAttack }, update = compressorAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorAttackMutation)
}

private val compressorReleaseMutation = voiceSetter { compressorRelease = it?.asDoubleOrNull() ?: compressorRelease }

private fun applyCompressorRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.compressorRelease }, update = compressorReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, compressorReleaseMutation)
}

/**
 * The orbit compressor: threshold, ratio, knee, attack and release.
 *
 * Levels above the threshold are turned down by the ratio; the knee softens the onset, attack and release set how fast it moves.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`compressor(ratio = mul(2))`), and the numeric slots read back as `compressor.threshold`, `compressor.ratio`, `compressor.knee`, `compressor.attack`, `compressor.release`.
 * With no argument at all, the pattern's own values are reinterpreted as `threshold`.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").compressor(-20, 4, 6, 0.003, 0.1)                     // a firm hand on the drum bus
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").compressor(-20, 4).compressor(ratio = mul("<1 2>"))   // twice the ratio every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").compressor(-20, 4).postgain(compressor.threshold.mul(-0.02).add(1))   // make-up gain from the threshold
 * ```
 *
 * @param threshold Level in dB above which compression starts (for example -20).
 * @param ratio Compression ratio; 4 means 4:1 above the threshold.
 * @param knee Knee width in dB; 0 is a hard knee, 6 and above soft.
 * @param attack Attack in seconds, how fast the compression engages (for example 0.003).
 * @param release Release in seconds, how fast it lets go (for example 0.1).
 * @param-tool threshold SprudelCompressorEditor, SprudelCompressorSequenceEditor
 *
 * @category dynamics
 * @tags compressor, threshold, ratio, knee, attack, release
 */
@KlangScript.Function
fun SprudelPattern.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch threshold: reinterpret runs only on a fully bare call.
    var p = if (threshold != null || !(ratio != null || knee != null || attack != null || release != null)) {
        applyCompressorThreshold(this, listOfNotNull(threshold).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (ratio != null) p = applyCompressorRatio(p, listOf<Any?>(ratio).asSprudelDslArgs(callInfo?.forParam(1)))
    if (knee != null) p = applyCompressorKnee(p, listOf<Any?>(knee).asSprudelDslArgs(callInfo?.forParam(2)))
    if (attack != null) p = applyCompressorAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(3)))
    if (release != null) p = applyCompressorRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [compressor]. */
@KlangScript.Function
fun String.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).compressor(threshold, ratio, knee, attack, release, callInfo)

/** Chains a [compressor] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.compressor(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.compressor(threshold, ratio, knee, attack, release, callInfo) }

/**
 * The `compressor` object: `compressor(...)` sets the slots, and each numeric slot reads back as a child,
 * `compressor.threshold`, `compressor.ratio`, `compressor.knee`, `compressor.attack`, `compressor.release`.
 *
 * @category dynamics
 * @tags compressor, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("compressor")
object compressor {

    /** The threshold slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val threshold: FieldAccessor = FieldAccessor { it.compressorThreshold }

    /** The ratio slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val ratio: FieldAccessor = FieldAccessor { it.compressorRatio }

    /** The knee slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val knee: FieldAccessor = FieldAccessor { it.compressorKnee }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.compressorAttack }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.compressorRelease }

    /** The setter, see [SprudelPattern.compressor]. */
    @KlangScript.Invoke
    operator fun invoke(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.compressor(threshold, ratio, knee, attack, release, callInfo) }
}

/**
 * `comp`, the short name of [compressor]: the same door, use whichever reads better.
 *
 * ```KlangScript(Playable)
 * s("bd sd hh sd").comp(-20, 4, 6, 0.003, 0.1)
 * ```
 *
 * @category dynamics
 * @tags comp, compressor
 * @param-tool threshold SprudelCompressorEditor, SprudelCompressorSequenceEditor
 */
@KlangScript.Function
fun SprudelPattern.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    compressor(threshold, ratio, knee, attack, release, callInfo)

/** Parses this string as a pattern, then applies [comp]. */
@KlangScript.Function
fun String.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.compressor(threshold, ratio, knee, attack, release, callInfo)

/**
 * Alias of [compressor]: the same object under its short name.
 *
 * @category dynamics
 * @tags comp, compressor, accessor
 */
@KlangScript.Constant
val comp: compressor = compressor

/** Chains a [comp] step onto this [PatternMapperFn] (see [SprudelPattern.comp]). */
@KlangScript.Function
fun PatternMapperFn.comp(threshold: PatternLike? = null, ratio: PatternLike? = null, knee: PatternLike? = null, attack: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.compressor(threshold, ratio, knee, attack, release, callInfo)
