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
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- distort, the amount slot ----------------------------------------------------------------------------------------

private val distortMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    distort = str.toDoubleOrNull() ?: distort
}

private fun applyDistort(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.distort }, update = distortMutation)
    }

    return source._liftOrReinterpretNumericalField(args, distortMutation)
}


/**
 * Waveshaper distortion: drive, shape and oversampling, [per voice](/manuals/lexikon/voice).
 *
 * This is not a bus effect. Unlike reverb and delay, distortion runs inside each voice's own chain,
 * so two voices sharing an orbit can be driven differently.
 *
 * `shape` picks the transfer curve. Symmetric and soft: `soft` (tanh), `gentle`, `softsat`,
 * `cubic`, `exp`, `sineshaper`. Symmetric and hard, plus wavefolding: `hard`, `zerosquare`,
 * `chebyshev`, `fold`, `linearfold`. Asymmetric, so with even harmonics: `diode`, `tube`, `asym`,
 * `stompbox`, `rectify`. An `oversample` of 1 keeps the raw aliased character.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`distort(amount = mul(2))`), and the numeric slots read back as `distort.amount`, `distort.oversample`.
 * With no argument at all, the pattern's own values are reinterpreted as `amount`.
 *
 * ```KlangScript(Playable)
 * s("bd*4").distort(0.4).distort(amount = mul("1 2 1 2"))                // every second hit harder
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd*4").distort("0.2 0.8", "tube").pan(distort.amount)               // more drive, further right
 * ```
 *
 * @param amount Drive, 0 is clean, 1 is heavy. Higher is allowed.
 * @param shape Transfer curve by name, for example `tube`.
 * @param oversample Oversampling factor, 1, 2 or 4.
 * @param-tool amount SprudelDistortEditor, SprudelDistortSequenceEditor
 * @param-tool shape SprudelDistortShapeEditor, SprudelDistortShapeSequenceEditor
 *
 * @scope voice
 * @category effects
 * @tags distort, amount, shape, oversample
 */
@KlangScript.Function
fun SprudelPattern.distort(
    amount: PatternLike? = null,
    shape: PatternLike? = null,
    oversample: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch amount: reinterpret runs only on a fully bare call.
    var p = if (amount != null || !(shape != null || oversample != null)) {
        applyDistort(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (shape != null) p = applyDistortShape(p, listOf<Any?>(shape).asSprudelDslArgs(callInfo?.forParam(1)))
    if (oversample != null) p = applyDistortOversample(p, listOf<Any?>(oversample).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [distort]. */
@KlangScript.Function
fun String.distort(
    amount: PatternLike? = null,
    shape: PatternLike? = null,
    oversample: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distort(amount, shape, oversample, callInfo)

/** Chains a [distort] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.distort(
    amount: PatternLike? = null,
    shape: PatternLike? = null,
    oversample: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.distort(amount, shape, oversample, callInfo) }

/**
 * The `distort` object: `distort(...)` sets the slots, and each numeric slot reads back as a child,
 * `distort.amount`, `distort.oversample`.
 *
 * @scope voice
 * @category effects
 * @tags distort, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("distort")
object distort {

    /** The amount slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val amount: FieldAccessor = FieldAccessor { it.distort }

    /** The oversample slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val oversample: FieldAccessor = FieldAccessor { it.distortOversample?.toDouble() }

    /** The setter, see [SprudelPattern.distort]. */
    @KlangScript.Invoke
    operator fun invoke(
        amount: PatternLike? = null,
        shape: PatternLike? = null,
        oversample: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.distort(amount, shape, oversample, callInfo) }
}

// -- distort.oversample ----------------------------------------------------------------------------------------------

private val distortOversampleMutation = voiceSetter { distortOversample = it?.asIntOrNull() }

private fun applyDistortOversample(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.distortOversample?.toDouble() }, update = distortOversampleMutation)
    }

    return source._liftOrReinterpretNumericalField(args, distortOversampleMutation)
}

// -- distort, the shape slot -----------------------------------------------------------------------------------------

private val distortShapeMutation = voiceSetter { shape -> distortShape = shape?.toString()?.lowercase() }

private fun applyDistortShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, distortShapeMutation) { src, ctrl ->
        src.distortShape = ctrl.distortShape
        src
    }
}

// -- crush, the amount slot ------------------------------------------------------------------------------------------

private val crushMutation = voiceSetter { crush = it?.asDoubleOrNull() ?: crush }

private fun applyCrush(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.crush }, update = crushMutation)
    }

    return source._liftOrReinterpretNumericalField(args, crushMutation)
}


/**
 * Bit crusher: bit depth and oversampling, per voice.
 *
 * Fewer bits means a coarser, grittier signal. An `oversample` of 1 keeps the raw aliased
 * character, higher values tame it.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`crush(oversample = mul(2))`), and the numeric slots read back as `crush.amount`, `crush.oversample`.
 * With no argument at all, the pattern's own values are reinterpreted as `amount`.
 *
 * ```KlangScript(Playable)
 * s("bd*4").crush(8).crush(amount = mul("1 0.5 1 0.5"))                  // every second hit coarser
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*4").crush("4 12").lpf(crush.amount.mul(500))                      // fewer bits, darker
 * ```
 *
 * @param amount Bit depth, 1 to 16. Fewer bits are harsher.
 * @param oversample Oversampling factor, 1, 2 or 4.
 *
 * @scope voice
 * @category effects
 * @tags crush, amount, oversample
 */
@KlangScript.Function
fun SprudelPattern.crush(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch amount: reinterpret runs only on a fully bare call.
    var p = if (amount != null || !(oversample != null)) {
        applyCrush(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (oversample != null) p = applyCrushOversample(p, listOf<Any?>(oversample).asSprudelDslArgs(callInfo?.forParam(1)))
    return p
}

/** Parses this string as a pattern, then applies [crush]. */
@KlangScript.Function
fun String.crush(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).crush(amount, oversample, callInfo)

/** Chains a [crush] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.crush(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crush(amount, oversample, callInfo) }

/**
 * The `crush` object: `crush(...)` sets the slots, and each numeric slot reads back as a child,
 * `crush.amount`, `crush.oversample`.
 *
 * @scope voice
 * @category effects
 * @tags crush, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("crush")
object crush {

    /** The amount slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val amount: FieldAccessor = FieldAccessor { it.crush }

    /** The oversample slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val oversample: FieldAccessor = FieldAccessor { it.crushOversample?.toDouble() }

    /** The setter, see [SprudelPattern.crush]. */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.crush(amount, oversample, callInfo) }
}

// -- crush.oversample ------------------------------------------------------------------------------------------------

private val crushOversampleMutation = voiceSetter { crushOversample = it?.asIntOrNull() }

private fun applyCrushOversample(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.crushOversample?.toDouble() }, update = crushOversampleMutation)
    }

    return source._liftOrReinterpretNumericalField(args, crushOversampleMutation)
}

// -- coarse, the amount slot -----------------------------------------------------------------------------------------

private val coarseMutation = voiceSetter { coarse = it?.asDoubleOrNull() ?: coarse }

private fun applyCoarse(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.coarse }, update = coarseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, coarseMutation)
}


/**
 * Sample rate reduction, a decimator, per voice.
 *
 * The divisor throws away samples: 1 is off, 4 runs the voice at a quarter of the rate. An
 * `oversample` of 1 keeps the raw aliased character, higher values tame it.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`coarse(oversample = mul(2))`), and the numeric slots read back as `coarse.amount`, `coarse.oversample`.
 * With no argument at all, the pattern's own values are reinterpreted as `amount`.
 *
 * ```KlangScript(Playable)
 * s("hh*8").coarse(4).coarse(amount = mul(perlin.seg(8).range(1, 3)))   // a decimator that wanders
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*4").coarse("2 8").lpf(coarse.amount.mul(1000))                    // coarser, but brighter
 * ```
 *
 * @param amount Sample rate divisor, 1 is off, higher is coarser.
 * @param oversample Oversampling factor, 1, 2 or 4.
 *
 * @scope voice
 * @category effects
 * @tags coarse, amount, oversample
 */
@KlangScript.Function
fun SprudelPattern.coarse(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch amount: reinterpret runs only on a fully bare call.
    var p = if (amount != null || !(oversample != null)) {
        applyCoarse(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (oversample != null) p = applyCoarseOversample(p, listOf<Any?>(oversample).asSprudelDslArgs(callInfo?.forParam(1)))
    return p
}

/** Parses this string as a pattern, then applies [coarse]. */
@KlangScript.Function
fun String.coarse(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).coarse(amount, oversample, callInfo)

/** Chains a [coarse] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.coarse(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarse(amount, oversample, callInfo) }

/**
 * The `coarse` object: `coarse(...)` sets the slots, and each numeric slot reads back as a child,
 * `coarse.amount`, `coarse.oversample`.
 *
 * @scope voice
 * @category effects
 * @tags coarse, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("coarse")
object coarse {

    /** The amount slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val amount: FieldAccessor = FieldAccessor { it.coarse }

    /** The oversample slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val oversample: FieldAccessor = FieldAccessor { it.coarseOversample?.toDouble() }

    /** The setter, see [SprudelPattern.coarse]. */
    @KlangScript.Invoke
    operator fun invoke(amount: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.coarse(amount, oversample, callInfo) }
}

// -- coarse.oversample -----------------------------------------------------------------------------------------------

private val coarseOversampleMutation = voiceSetter { coarseOversample = it?.asIntOrNull() }

private fun applyCoarseOversample(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.coarseOversample?.toDouble() }, update = coarseOversampleMutation)
    }

    return source._liftOrReinterpretNumericalField(args, coarseOversampleMutation)
}
