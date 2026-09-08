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
 * One reverb per orbit, so `size`, `fade` and `lowpass` are set once for everyone by the orbit's
 * owning voice. `wet` is the exception and the thing to remember: it is a per-voice send, so a dry
 * voice on a wet orbit stays dry. Give a pattern its own reverb by giving it its own `orbit`.
 *
 * A bare `room(0.4)` is silent. The reverb only runs with a room to run in, so pair the send with
 * `size` or `fade`.
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
 * @param wet Send into the orbit reverb, 0 to 1. Per voice.
 * @param size Room size, about 0 to 10. Orbit-wide.
 * @param fade Tail length, 0 to 1. Overrides `size`. Orbit-wide.
 * @param lowpass Lowpass on the tail, Hz. Orbit-wide.
 * @param dim Damping frequency, Hz. Reserved, not read yet.
 * @param-tool wet SprudelReverbEditor, SprudelReverbSequenceEditor
 * @param-tool size SprudelRoomSizeEditor, SprudelRoomSizeSequenceEditor
 *
 * @scope orbit-send
 * @category effects
 * @tags room, wet, size, fade, lowpass, dim
 */
@KlangScript.Function
fun SprudelPattern.room(
    wet: PatternLike? = null,
    size: PatternLike? = null,
    fade: PatternLike? = null,
    lowpass: PatternLike? = null,
    dim: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
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
fun String.room(
    wet: PatternLike? = null,
    size: PatternLike? = null,
    fade: PatternLike? = null,
    lowpass: PatternLike? = null,
    dim: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).room(wet, size, fade, lowpass, dim, callInfo)

/** Chains a [room] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.room(
    wet: PatternLike? = null,
    size: PatternLike? = null,
    fade: PatternLike? = null,
    lowpass: PatternLike? = null,
    dim: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
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
    operator fun invoke(
        wet: PatternLike? = null,
        size: PatternLike? = null,
        fade: PatternLike? = null,
        lowpass: PatternLike? = null,
        dim: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
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
