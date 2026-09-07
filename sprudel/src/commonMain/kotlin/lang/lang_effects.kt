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
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice
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
 * Waveshaper distortion: drive, shape and oversampling.
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
 * @param amount Drive, 0 is clean, 1 is heavy; higher values are allowed.
 * @param shape Transfer curve by name. Symmetric soft: `soft` (tanh), `gentle`, `softsat`, `cubic`,
 *   `exp`, `sineshaper`. Symmetric hard and wavefolding: `hard`, `zerosquare`, `chebyshev`, `fold`,
 *   `linearfold`. Asymmetric (even harmonics): `diode`, `tube`, `asym`, `stompbox`, `rectify`.
 * @param oversample Oversampling factor (1, 2, 4); 1 keeps the raw aliased character.
 * @param-tool amount SprudelDistortEditor, SprudelDistortSequenceEditor
 * @param-tool shape SprudelDistortShapeEditor, SprudelDistortShapeSequenceEditor
 *
 * @category effects
 * @tags distort, amount, shape, oversample
 */
@KlangScript.Function
fun SprudelPattern.distort(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
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
fun String.distort(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).distort(amount, shape, oversample, callInfo)

/** Chains a [distort] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.distort(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.distort(amount, shape, oversample, callInfo) }

/**
 * The `distort` object: `distort(...)` sets the slots, and each numeric slot reads back as a child,
 * `distort.amount`, `distort.oversample`.
 *
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
    operator fun invoke(amount: PatternLike? = null, shape: PatternLike? = null, oversample: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
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
 * Bit crusher: bit depth and oversampling.
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
 * @param amount Bit depth, 1 to 16; fewer bits are harsher.
 * @param oversample Oversampling factor (1, 2, 4).

 *
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
 * Sample rate reduction (decimator) and oversampling.
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
 * @param amount Sample rate divisor; 1 is off, higher is coarser.
 * @param oversample Oversampling factor (1, 2, 4); 1 keeps the aliased character.

 *
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

// -- room, the wet slot ----------------------------------------------------------------------------------------------

private val roomMutation = voiceSetter {
    room = it?.toString()?.toDoubleOrNull() ?: room
}

private fun applyRoom(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.room }, update = roomMutation)
    }

    // No args: reinterpret pattern's own values as room mix (backward compat)
    if (args.isEmpty()) {
        return source.reinterpretVoice {
            it.clone().apply { room = value?.asDouble }
        }
    }

    return source._applyControlFromParams(args, roomMutation) { src, ctrl ->
        src.room = ctrl.room ?: src.room
        src
    }
}


/**
 * The orbit reverb: send, room size, tail, lowpass and damping.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`room(size = mul(2))`), and the numeric slots read back as `room.wet`, `room.size`, `room.fade`, `room.lowpass`, `room.dim`.
 * With no argument at all, the pattern's own values are reinterpreted as `wet`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").room(0.3, 4)                                                // send and size
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").room(0.3, 4).room(size = mul("<1 2>"))                         // twice the room every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").room("0.1 0.5").delay(wet = room.wet, time = 0.25)            // as much delay as reverb
 * ```
 *
 * @param wet Reverb send, 0 to 1.
 * @param size Room size, about 0 to 10 (the same scale as the master reverb).
 * @param fade Tail override, 0 to 1 (0 is about 0.7 s, 1 about 12.5 s); overrides `size`, bounded.
 * @param lowpass Lowpass on the reverb tail, Hz.
 * @param dim Damping frequency, Hz. Reserved: the engine does not read it yet.
 * @param-tool wet SprudelReverbEditor, SprudelReverbSequenceEditor
 * @param-tool size SprudelRoomSizeEditor, SprudelRoomSizeSequenceEditor
 *
 * @category effects
 * @tags room, wet, size, fade, lowpass, dim
 */
@KlangScript.Function
fun SprudelPattern.room(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(size != null || fade != null || lowpass != null || dim != null)) {
        applyRoom(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (size != null) p = applyRoomSize(p, listOf<Any?>(size).asSprudelDslArgs(callInfo?.forParam(1)))
    if (fade != null) p = applyRoomFade(p, listOf<Any?>(fade).asSprudelDslArgs(callInfo?.forParam(2)))
    if (lowpass != null) p = applyRoomLp(p, listOf<Any?>(lowpass).asSprudelDslArgs(callInfo?.forParam(3)))
    if (dim != null) p = applyRoomDim(p, listOf<Any?>(dim).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [room]. */
@KlangScript.Function
fun String.room(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).room(wet, size, fade, lowpass, dim, callInfo)

/** Chains a [room] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.room(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.room(wet, size, fade, lowpass, dim, callInfo) }

/**
 * The `room` object: `room(...)` sets the slots, and each numeric slot reads back as a child,
 * `room.wet`, `room.size`, `room.fade`, `room.lowpass`, `room.dim`.
 *
 * @category effects
 * @tags room, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("room")
object room {

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.room }

    /** The size slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val size: FieldAccessor = FieldAccessor { it.roomSize }

    /** The fade slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val fade: FieldAccessor = FieldAccessor { it.roomFade }

    /** The lowpass slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val lowpass: FieldAccessor = FieldAccessor { it.roomLp }

    /** The dim slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val dim: FieldAccessor = FieldAccessor { it.roomDim }

    /** The setter, see [SprudelPattern.room]. */
    @KlangScript.Invoke
    operator fun invoke(wet: PatternLike? = null, size: PatternLike? = null, fade: PatternLike? = null, lowpass: PatternLike? = null, dim: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.room(wet, size, fade, lowpass, dim, callInfo) }
}

// -- room.size -------------------------------------------------------------------------------------------------------

private val roomSizeMutation = voiceSetter { roomSize = it?.asDoubleOrNull() }

private fun applyRoomSize(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.roomSize }, update = roomSizeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, roomSizeMutation)
}

// -- room.fade -------------------------------------------------------------------------------------------------------

private val roomFadeMutation = voiceSetter { roomFade = it?.asDoubleOrNull() }

private fun applyRoomFade(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.roomFade }, update = roomFadeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, roomFadeMutation)
}

// -- room.lowpass ----------------------------------------------------------------------------------------------------

private val roomLpMutation = voiceSetter { roomLp = it?.asDoubleOrNull() }

private fun applyRoomLp(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.roomLp }, update = roomLpMutation)
    }

    return source._liftOrReinterpretNumericalField(args, roomLpMutation)
}

// -- room.dim --------------------------------------------------------------------------------------------------------

private val roomDimMutation = voiceSetter { roomDim = it?.asDoubleOrNull() }

private fun applyRoomDim(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.roomDim }, update = roomDimMutation)
    }

    return source._liftOrReinterpretNumericalField(args, roomDimMutation)
}

// -- iresponse() / ir() ----------------------------------------------------------------------------------------------

private val iResponseMutation = voiceSetter { response -> iResponse = response?.toString() }

private fun applyIResponse(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, iResponseMutation) { src, ctrl ->
        src.iResponse = ctrl.iResponse
        src
    }
}

/**
 * Sets the impulse response sample name for convolution reverb on this pattern.
 *
 * Uses a recorded impulse response to simulate the acoustics of a real space. The value
 * is the name of an IR sample loaded in the audio engine.
 *
 * @param name The name of the impulse response sample.
 * @return A new pattern with the impulse response applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").iresponse("church")     // church reverb IR
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").iresponse("<room hall plate>")  // cycle through IR types
 * ```
 *
 * @alias ir
 * @category effects
 * @tags iresponse, ir, impulse, convolution, reverb
 */
@KlangScript.Function
fun SprudelPattern.iresponse(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyIResponse(this, listOf(name).asSprudelDslArgs(callInfo))

/**
 * Parses this string as a pattern and sets the impulse response sample for convolution reverb.
 *
 * @param name The name of the impulse response sample.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".iresponse("church").note()   // church reverb IR on string pattern
 * ```
 */
@KlangScript.Function
fun String.iresponse(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iresponse(name, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the impulse response sample for convolution reverb.
 *
 * Use the returned mapper as a transform argument or apply it via `.apply(...)`.
 *
 * @param name The name of the impulse response sample.
 * @return A [PatternMapperFn] that sets the impulse response.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(iresponse("church"))   // church reverb via mapper
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, iresponse("hall"))   // hall reverb every 4th cycle
 * ```
 *
 * @alias ir
 * @category effects
 * @tags iresponse, ir, impulse, convolution, reverb
 */
@KlangScript.Function
fun iresponse(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.iresponse(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).iresponse("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).iresponse("hall"))   // hall IR every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.iresponse(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.iresponse(name, callInfo) }

/**
 * Alias for [iresponse]. Sets the impulse response sample name for convolution reverb on this pattern.
 *
 * @param name The name of the impulse response sample.
 * @return A new pattern with the impulse response applied.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").ir("church")     // church reverb IR
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").ir("<room hall plate>")  // cycle through IR types
 * ```
 *
 * @alias iresponse
 * @category effects
 * @tags ir, iresponse, impulse, convolution, reverb
 */
@KlangScript.Function
fun SprudelPattern.ir(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern = this.iresponse(name, callInfo)

/**
 * Alias for [iresponse]. Parses this string as a pattern and sets the impulse response sample.
 *
 * @param name The name of the impulse response sample.
 *
 * ```KlangScript(Playable)
 * "c3 e3 g3".ir("church").note()   // church reverb IR on string pattern
 * ```
 */
@KlangScript.Function
fun String.ir(name: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).iresponse(name, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the impulse response sample. Alias for [iresponse].
 *
 * @param name The name of the impulse response sample.
 * @return A [PatternMapperFn] that sets the impulse response.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").apply(ir("church"))   // church reverb via mapper
 * ```
 *
 * @alias iresponse
 * @category effects
 * @tags ir, iresponse, impulse, convolution, reverb
 */
@KlangScript.Function
fun ir(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.iresponse(name, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets the impulse response (alias for iresponse) after the previous mapper.
 *
 * @param name The name of the impulse response sample.
 * @return A new [PatternMapperFn] chaining this impulse response after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").apply(room(0.5).ir("church"))   // room then IR reverb
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").every(4, room(0.8).ir("hall"))   // hall IR every 4th cycle
 * ```
 */
@KlangScript.Function
fun PatternMapperFn.ir(name: PatternLike, callInfo: CallInfo? = null): PatternMapperFn = this.chain { p -> p.iresponse(name, callInfo) }


// -- delay, the wet slot ---------------------------------------------------------------------------------------------

private val delayMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    delay = str.toDoubleOrNull() ?: delay
}

private fun applyDelay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delay }, update = delayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayMutation)
}


/**
 * The orbit delay: send, time, feedback and the feedback cap.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`delay(time = mul(2))`), and the numeric slots read back as `delay.wet`, `delay.time`, `delay.feedback`, `delay.cap`.
 * With no argument at all, the pattern's own values are reinterpreted as `wet`.
 *
 * ```KlangScript(Playable)
 * s("hh*4").delay(0.3, 0.25, 0.4)                                        // send, time, feedback
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*4").delay(0.3, 0.25).delay(wet = mul("1 0 1 0"))                  // delay on every other hat only
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd sd").delay("0.1 0.4", 0.25).room(wet = delay.wet)                  // as much reverb as delay
 * ```
 *
 * @param wet Delay send, 0 to 1.
 * @param time Delay time in seconds (`pure(1/8).div(cps)` for tempo sync).
 * @param feedback Feedback, 0 to 1; above 1 builds up.
 * @param cap Feedback cap, the ceiling the repeats may not exceed.
 * @param-tool wet SprudelDelayEditor, SprudelDelaySequenceEditor
 * @param-tool time SprudelDelayTimeEditor, SprudelDelayTimeSequenceEditor
 * @param-tool feedback SprudelDelayFeedbackEditor, SprudelDelayFeedbackSequenceEditor
 *
 * @category effects
 * @tags delay, wet, time, feedback, cap
 */
@KlangScript.Function
fun SprudelPattern.delay(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, cap: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(time != null || feedback != null || cap != null)) {
        applyDelay(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (time != null) p = applyDelayTime(p, listOf<Any?>(time).asSprudelDslArgs(callInfo?.forParam(1)))
    if (feedback != null) p = applyDelayFeedback(p, listOf<Any?>(feedback).asSprudelDslArgs(callInfo?.forParam(2)))
    if (cap != null) p = applyDelayCap(p, listOf<Any?>(cap).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/** Parses this string as a pattern, then applies [delay]. */
@KlangScript.Function
fun String.delay(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, cap: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delay(wet, time, feedback, cap, callInfo)

/** Chains a [delay] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.delay(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, cap: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.delay(wet, time, feedback, cap, callInfo) }

/**
 * The `delay` object: `delay(...)` sets the slots, and each numeric slot reads back as a child,
 * `delay.wet`, `delay.time`, `delay.feedback`, `delay.cap`.
 *
 * @category effects
 * @tags delay, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("delay")
object delay {

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.delay }

    /** The time slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val time: FieldAccessor = FieldAccessor { it.delayTime }

    /** The feedback slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val feedback: FieldAccessor = FieldAccessor { it.delayFeedback }

    /** The cap slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val cap: FieldAccessor = FieldAccessor { it.delayCap }

    /** The setter, see [SprudelPattern.delay]. */
    @KlangScript.Invoke
    operator fun invoke(wet: PatternLike? = null, time: PatternLike? = null, feedback: PatternLike? = null, cap: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.delay(wet, time, feedback, cap, callInfo) }
}

// -- delay.time ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceSetter { delayTime = it?.asDoubleOrNull() }

private fun applyDelayTime(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayTime }, update = delayTimeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayTimeMutation)
}

// -- delay.feedback --------------------------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceSetter { delayFeedback = it?.asDoubleOrNull() }

private fun applyDelayFeedback(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayFeedback }, update = delayFeedbackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayFeedbackMutation)
}

// -- phaser, the rate slot -------------------------------------------------------------------------------------------

private val phaserMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    phaserRate = str.toDoubleOrNull() ?: phaserRate
}

private fun applyPhaser(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserRate }, update = phaserMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserMutation)
}


/**
 * The orbit phaser: rate, depth, centre, sweep and dry floor.
 *
 * The phaser is an ORBIT (bus) effect (2026-08-24: one sweep over the summed orbit, the
 * DAW-insert model) and orbit knobs are first-writer-wins: a voice that sets phaser knobs but does
 * not own its orbit's lease is not phased. Route to its own orbit for its own phaser.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`phaser(wet = mul(2))`), and the numeric slots read back as `phaser.rate`, `phaser.wet`, `phaser.center`, `phaser.sweep`, `phaser.floor`.
 * With no argument at all, the pattern's own values are reinterpreted as `rate`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").phaser(0.5, 0.5)                                 // rate and depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").phaser(0.5, 0.5).phaser(rate = mul("1 4"))        // the second note swirls faster
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").phaser("0.5 2", 0.5).delay(wet = phaser.wet, time = 0.25)   // as much delay as phaser
 * ```
 *
 * @param rate LFO rate in Hz.
 * @param wet Depth (wet amount), 0 to 1; the stage is built only above 0. The dry stays untouched
 *   by default (`floor` = 1), so the wet ADDS the swept notch on top.
 * @param center Centre frequency of the sweep, Hz.
 * @param sweep Sweep range around the centre, Hz.
 * @param floor Minimum dry share of the wet/dry law, 0 to 1; lower it to turn `wet` into a crossfade.
 * @param-tool rate SprudelPhaserEditor, SprudelPhaserSequenceEditor
 *
 * @category effects
 * @tags phaser, rate, wet, center, sweep, floor
 */
@KlangScript.Function
fun SprudelPattern.phaser(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch rate: reinterpret runs only on a fully bare call.
    var p = if (rate != null || !(wet != null || center != null || sweep != null || floor != null)) {
        applyPhaser(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (wet != null) p = applyPhaserWet(p, listOf<Any?>(wet).asSprudelDslArgs(callInfo?.forParam(1)))
    if (center != null) p = applyPhaserCenter(p, listOf<Any?>(center).asSprudelDslArgs(callInfo?.forParam(2)))
    if (sweep != null) p = applyPhaserSweep(p, listOf<Any?>(sweep).asSprudelDslArgs(callInfo?.forParam(3)))
    if (floor != null) p = applyPhaserFloor(p, listOf<Any?>(floor).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [phaser]. */
@KlangScript.Function
fun String.phaser(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaser(rate, wet, center, sweep, floor, callInfo)

/** Chains a [phaser] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.phaser(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.phaser(rate, wet, center, sweep, floor, callInfo) }

/**
 * The `phaser` object: `phaser(...)` sets the slots, and each numeric slot reads back as a child,
 * `phaser.rate`, `phaser.wet`, `phaser.center`, `phaser.sweep`, `phaser.floor`.
 *
 * @category effects
 * @tags phaser, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("phaser")
object phaser {

    /** The rate slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val rate: FieldAccessor = FieldAccessor { it.phaserRate }

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.phaserDepth }

    /** The center slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val center: FieldAccessor = FieldAccessor { it.phaserCenter }

    /** The sweep slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sweep: FieldAccessor = FieldAccessor { it.phaserSweep }

    /** The floor slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val floor: FieldAccessor = FieldAccessor { it.phaserFloor }

    /** The setter, see [SprudelPattern.phaser]. */
    @KlangScript.Invoke
    operator fun invoke(rate: PatternLike? = null, wet: PatternLike? = null, center: PatternLike? = null, sweep: PatternLike? = null, floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.phaser(rate, wet, center, sweep, floor, callInfo) }
}

// -- phaser.wet ------------------------------------------------------------------------------------------------------

private val phaserWetMutation = voiceSetter { phaserDepth = it?.asDoubleOrNull() }

private fun applyPhaserWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserDepth }, update = phaserWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserWetMutation)
}

// -- phaser.floor ----------------------------------------------------------------------------------------------------

private val phaserFloorMutation = voiceSetter { phaserFloor = it?.asDoubleOrNull() }

private fun applyPhaserFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserFloor }, update = phaserFloorMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserFloorMutation)
}

// -- phaser.center ---------------------------------------------------------------------------------------------------

private val phaserCenterMutation = voiceSetter { phaserCenter = it?.asDoubleOrNull() }

private fun applyPhaserCenter(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserCenter }, update = phaserCenterMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserCenterMutation)
}

// -- phaser.sweep ----------------------------------------------------------------------------------------------------

private val phaserSweepMutation = voiceSetter { phaserSweep = it?.asDoubleOrNull() }

private fun applyPhaserSweep(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserSweep }, update = phaserSweepMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserSweepMutation)
}

// -- tremolo.sync ----------------------------------------------------------------------------------------------------

private val tremoloSyncMutation = voiceSetter { tremoloSync = it?.asDoubleOrNull() }

private fun applyTremoloSync(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloSync }, update = tremoloSyncMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloSyncMutation)
}


/**
 * The tremolo: depth, rate, waveform, skew and phase.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`tremolo(sync = mul(2))`), and the numeric slots read back as `tremolo.depth`, `tremolo.sync`, `tremolo.skew`, `tremolo.phase`.
 * With no argument at all, the pattern's own values are reinterpreted as `depth`.
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").tremolo(0.6, 4)                                     // depth and rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").tremolo(0.6, 4).tremolo(sync = mul("<1 2>"))         // twice as fast every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").tremolo("0.3 0.9", 4).gain(tremolo.depth)         // deeper, louder
 * ```
 *
 * @param depth Depth, 0 to 1; the stage is built only above 0.
 * @param sync Rate in Hz.
 * @param shape LFO waveform by name (`sine`, `triangle`, `square`, ...).
 * @param skew Waveform skew, 0 to 1.
 * @param phase LFO start phase, 0 to 1.
 * @param-tool depth SprudelTremoloEditor, SprudelTremoloSequenceEditor
 * @param-tool shape SprudelWaveformEditor, SprudelWaveformSequenceEditor
 *
 * @category effects
 * @tags tremolo, depth, sync, shape, skew, phase
 */
@KlangScript.Function
fun SprudelPattern.tremolo(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch depth: reinterpret runs only on a fully bare call.
    var p = if (depth != null || !(sync != null || shape != null || skew != null || phase != null)) {
        applyTremoloDepth(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (sync != null) p = applyTremoloSync(p, listOf<Any?>(sync).asSprudelDslArgs(callInfo?.forParam(1)))
    if (shape != null) p = applyTremoloShape(p, listOf<Any?>(shape).asSprudelDslArgs(callInfo?.forParam(2)))
    if (skew != null) p = applyTremoloSkew(p, listOf<Any?>(skew).asSprudelDslArgs(callInfo?.forParam(3)))
    if (phase != null) p = applyTremoloPhase(p, listOf<Any?>(phase).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [tremolo]. */
@KlangScript.Function
fun String.tremolo(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolo(depth, sync, shape, skew, phase, callInfo)

/** Chains a [tremolo] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.tremolo(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.tremolo(depth, sync, shape, skew, phase, callInfo) }

/**
 * The `tremolo` object: `tremolo(...)` sets the slots, and each numeric slot reads back as a child,
 * `tremolo.depth`, `tremolo.sync`, `tremolo.skew`, `tremolo.phase`.
 *
 * @category effects
 * @tags tremolo, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("tremolo")
object tremolo {

    /** The depth slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val depth: FieldAccessor = FieldAccessor { it.tremoloDepth }

    /** The sync slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sync: FieldAccessor = FieldAccessor { it.tremoloSync }

    /** The skew slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val skew: FieldAccessor = FieldAccessor { it.tremoloSkew }

    /** The phase slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val phase: FieldAccessor = FieldAccessor { it.tremoloPhase }

    /** The setter, see [SprudelPattern.tremolo]. */
    @KlangScript.Invoke
    operator fun invoke(depth: PatternLike? = null, sync: PatternLike? = null, shape: PatternLike? = null, skew: PatternLike? = null, phase: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.tremolo(depth, sync, shape, skew, phase, callInfo) }
}

// -- tremolo.depth ---------------------------------------------------------------------------------------------------

private val tremoloDepthMutation = voiceSetter { tremoloDepth = it?.asDoubleOrNull() ?: tremoloDepth }

private fun applyTremoloDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloDepth }, update = tremoloDepthMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloDepthMutation)
}

// -- tremolo.skew ----------------------------------------------------------------------------------------------------

private val tremoloSkewMutation = voiceSetter { tremoloSkew = it?.asDoubleOrNull() }

private fun applyTremoloSkew(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloSkew }, update = tremoloSkewMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloSkewMutation)
}

// -- tremolo.phase ---------------------------------------------------------------------------------------------------

private val tremoloPhaseMutation = voiceSetter { tremoloPhase = it?.asDoubleOrNull() }

private fun applyTremoloPhase(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloPhase }, update = tremoloPhaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloPhaseMutation)
}

// -- tremolo, the shape slot -----------------------------------------------------------------------------------------

private val tremoloShapeMutation = voiceSetter { shape -> tremoloShape = shape?.toString()?.lowercase() }

private fun applyTremoloShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
        src.tremoloShape = ctrl.tremoloShape
        src
    }
}

// -- delay.cap -------------------------------------------------------------------------------------------------------

private val delayCapMutation = voiceSetter { delayCap = it?.asDoubleOrNull() }

private fun applyDelayCap(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayCap }, update = delayCapMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayCapMutation)
}
