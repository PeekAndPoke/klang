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
 * Waveshaper distortion: drive and shape, per voice.
 *
 * This is not a bus effect. Unlike reverb and delay, distortion runs inside each voice's own chain,
 * so two voices sharing an orbit can be driven differently.
 *
 * `shape` picks the transfer curve. Symmetric and soft: `soft` (tanh), `gentle`, `softsat`,
 * `cubic`, `exp`, `sineshaper`. Symmetric and hard, plus wavefolding: `hard`, `zerosquare`,
 * `chebyshev`, `fold`, `linearfold`. Asymmetric, so with even harmonics: `diode`, `tube`, `asym`,
 * `stompbox`, `rectify`. It runs at the voice's base rate, so a hard drive keeps some aliased grit.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`distort(amount = mul(2))`), and the numeric slot reads back as `distort.amount`.
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
 * @tool SprudelDistortEditor
 * @tool amount: SprudelDistortEditor, SprudelDistortSequenceEditor
 * @tool shape: SprudelDistortShapeEditor, SprudelDistortShapeSequenceEditor
 *
 * @scope voice
 * @category effects
 * @tags distort, amount, shape
 */
@KlangScript.Function
fun SprudelPattern.distort(
    amount: PatternLike? = null,
    shape: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch amount: reinterpret runs only on a fully bare call.
    var p = if (amount != null || shape == null) {
        applyDistort(this, listOfNotNull(amount).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (shape != null) p = applyDistortShape(p, listOf<Any?>(shape).asSprudelDslArgs(callInfo?.forParam(1)))
    return p
}

/** Parses this string as a pattern, then applies [distort]. */
@KlangScript.Function
fun String.distort(
    amount: PatternLike? = null,
    shape: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distort(amount, shape, callInfo)

/** Chains a [distort] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.distort(
    amount: PatternLike? = null,
    shape: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.distort(amount, shape, callInfo) }

/**
 * The `distort` object: `distort(...)` sets the slots, and the numeric slot reads back as a child,
 * `distort.amount`.
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

    /**
     * The setter, see [SprudelPattern.distort].
     *
     * @param amount Drive, 0 is clean, 1 is heavy. Higher is allowed.
     * @param shape Transfer curve by name, for example `tube`.
     */
    @KlangScript.Invoke
    operator fun invoke(
        amount: PatternLike? = null,
        shape: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.distort(amount, shape, callInfo) }
}

// -- distort, the shape slot -----------------------------------------------------------------------------------------

private val distortShapeMutation = voiceSetter { shape -> distortShape = shape?.toString()?.lowercase() }

private fun applyDistortShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, distortShapeMutation) { src, ctrl ->
        src.distortShape = ctrl.distortShape
        src
    }
}

// -- crush, the bits slot --------------------------------------------------------------------------------------------

private val crushMutation = voiceSetter { crush = it?.asDoubleOrNull() ?: crush }

private fun applyCrush(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.crush }, update = crushMutation)
    }

    return source._liftOrReinterpretNumericalField(args, crushMutation)
}


/**
 * Bit crusher: the bit depth, per voice.
 *
 * Fewer bits means a coarser, grittier signal.
 *
 * The slot is patternable; a named slot takes a mapper (`crush(bits = mul(2))`), and it reads back as `crush.bits`.
 * With no argument at all, the pattern's own values are reinterpreted as `bits`.
 *
 * ```KlangScript(Playable)
 * s("bd*4").crush(8).crush(bits = mul("1 0.5 1 0.5"))                  // every second hit coarser
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*4").crush("4 12").lpf(crush.bits.mul(500))                      // fewer bits, darker
 * ```
 *
 * @param bits Bit depth. Fewer bits are harsher, typically 1 to 16.
 *
 * @scope voice
 * @category effects
 * @tags crush, bits
 */
@KlangScript.Function
fun SprudelPattern.crush(bits: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCrush(this, listOfNotNull(bits).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern, then applies [crush]. */
@KlangScript.Function
fun String.crush(bits: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).crush(bits, callInfo)

/** Chains a [crush] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.crush(bits: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.crush(bits, callInfo) }

/**
 * The `crush` object: `crush(...)` sets the slot, and it reads back as a child, `crush.bits`.
 *
 * @scope voice
 * @category effects
 * @tags crush, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("crush")
object crush {

    /** The bits slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val bits: FieldAccessor = FieldAccessor { it.crush }

    /**
     * The setter, see [SprudelPattern.crush].
     *
     * @param bits Bit depth. Fewer bits are harsher, typically 1 to 16.
     */
    @KlangScript.Invoke
    operator fun invoke(bits: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.crush(bits, callInfo) }
}

// -- coarse, the factor slot -----------------------------------------------------------------------------------------

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
 * The divisor throws away samples: 1 is off, 4 runs the voice at a quarter of the rate.
 *
 * The slot is patternable; a named slot takes a mapper (`coarse(factor = mul(2))`), and it reads back as
 * `coarse.factor`. With no argument at all, the pattern's own values are reinterpreted as `factor`.
 *
 * ```KlangScript(Playable)
 * s("hh*8").coarse(4).coarse(factor = mul(perlin.seg(8).range(1, 3)))   // a decimator that wanders
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*4").coarse("2 8").lpf(coarse.factor.mul(1000))                    // coarser, but brighter
 * ```
 *
 * @param factor Sample rate divisor, 1 is off, higher is coarser.
 *
 * @scope voice
 * @category effects
 * @tags coarse, factor
 */
@KlangScript.Function
fun SprudelPattern.coarse(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyCoarse(this, listOfNotNull(factor).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern, then applies [coarse]. */
@KlangScript.Function
fun String.coarse(factor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).coarse(factor, callInfo)

/** Chains a [coarse] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.coarse(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.coarse(factor, callInfo) }

/**
 * The `coarse` object: `coarse(...)` sets the slot, and it reads back as a child, `coarse.factor`.
 *
 * @scope voice
 * @category effects
 * @tags coarse, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("coarse")
object coarse {

    /** The factor slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val factor: FieldAccessor = FieldAccessor { it.coarse }

    /**
     * The setter, see [SprudelPattern.coarse].
     *
     * @param factor Sample rate divisor, 1 is off, higher is coarser.
     */
    @KlangScript.Invoke
    operator fun invoke(factor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.coarse(factor, callInfo) }
}
