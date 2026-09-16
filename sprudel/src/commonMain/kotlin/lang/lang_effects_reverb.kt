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

// -- reverb, the wet slot --------------------------------------------------------------------------------------------

private val reverbMutation = voiceSetter {
    reverb = it?.toString()?.toDoubleOrNull() ?: reverb
}

private fun applyReverb(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.reverb }, update = reverbMutation)
    }

    // No args: reinterpret pattern's own values as the reverb send
    if (args.isEmpty()) {
        return source.reinterpretVoice {
            it.clone().apply { reverb = value?.asDouble }
        }
    }

    return source._applyControlFromParams(args, reverbMutation) { src, ctrl ->
        src.reverb = ctrl.reverb ?: src.reverb
        src
    }
}


/**
 * The orbit reverb: send, size and lowpass.
 *
 * One reverb per orbit, so `size` and `lowpass` are set once for everyone by the orbit's owning
 * voice. `wet` is the exception and the thing to remember: it is a per-voice
 * [send](/manuals/lexikon/send), so a dry voice on a wet orbit stays dry. Give a pattern its own
 * reverb by giving it its own [orbit bus](/manuals/lexikon/orbit-bus).
 *
 * A bare `reverb(0.4)` is silent. The reverb only runs with a room to run in, so pair the send with
 * `size`: 3 is roughly 1 s of tail, 5 roughly 1.4 s, 10 roughly 12.5 s, the longest there is.
 * `lowpass` darkens the tail: the lower the cutoff, the duller the room.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`reverb(size = mul(2))`), and the numeric slots read back as `reverb.wet`, `reverb.size`, `reverb.lowpass`.
 * With no argument at all, the pattern's own values are reinterpreted as `wet`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").reverb(0.3, 4)                                                  // send and size
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").reverb(0.3, 4).reverb(size = mul("<1 2>"))                      // twice the room every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("saw").reverb(wet = 0.5, size = 6, lowpass = "<16000 1500>")    // a bright room, then a dark one
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd sd").reverb("0.1 0.5", 4).delay(wet = reverb.wet, time = 0.25)       // as much delay as reverb
 * ```
 *
 * @param wet Send into the orbit reverb, 0 to 1. Per voice.
 * @param size Tail length, about 0 to 10; above 10 is bounded at 10. Orbit-wide.
 * @param lowpass Lowpass on the tail, Hz. Lower is darker. Orbit-wide.
 * @param-tool wet SprudelReverbEditor, SprudelReverbSequenceEditor
 * @param-tool size SprudelReverbSizeEditor, SprudelReverbSizeSequenceEditor
 *
 * @scope orbit-send
 * @category effects
 * @tags reverb, wet, size, lowpass
 */
@KlangScript.Function
fun SprudelPattern.reverb(
    wet: PatternLike? = null,
    size: PatternLike? = null,
    lowpass: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(size != null || lowpass != null)) {
        applyReverb(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (size != null) p = applyReverbSize(p, listOf<Any?>(size).asSprudelDslArgs(callInfo?.forParam(1)))
    if (lowpass != null) p = applyReverbLowpass(p, listOf<Any?>(lowpass).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [reverb]. */
@KlangScript.Function
fun String.reverb(
    wet: PatternLike? = null,
    size: PatternLike? = null,
    lowpass: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).reverb(wet, size, lowpass, callInfo)

/** Chains a [reverb] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.reverb(
    wet: PatternLike? = null,
    size: PatternLike? = null,
    lowpass: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.reverb(wet, size, lowpass, callInfo) }

/**
 * The `reverb` object: `reverb(...)` sets the slots, and each numeric slot reads back as a child,
 * `reverb.wet`, `reverb.size`, `reverb.lowpass`.
 *
 * @scope orbit-send
 * @category effects
 * @tags reverb, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("reverb")
object reverb {

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.reverb }

    /** The size slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val size: FieldAccessor = FieldAccessor { it.reverbSize }

    /** The lowpass slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val lowpass: FieldAccessor = FieldAccessor { it.reverbLowpass }

    /** The setter, see [SprudelPattern.reverb]. */
    @KlangScript.Invoke
    operator fun invoke(
        wet: PatternLike? = null,
        size: PatternLike? = null,
        lowpass: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.reverb(wet, size, lowpass, callInfo) }
}

// -- reverb.size -----------------------------------------------------------------------------------------------------

private val reverbSizeMutation = voiceSetter { reverbSize = it?.asDoubleOrNull() }

private fun applyReverbSize(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.reverbSize }, update = reverbSizeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, reverbSizeMutation)
}

// -- reverb.lowpass --------------------------------------------------------------------------------------------------

private val reverbLowpassMutation = voiceSetter { reverbLowpass = it?.asDoubleOrNull() }

private fun applyReverbLowpass(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.reverbLowpass }, update = reverbLowpassMutation)
    }

    return source._liftOrReinterpretNumericalField(args, reverbLowpassMutation)
}
