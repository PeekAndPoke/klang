/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.katalystParamsOrNew
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

// -- the call sets every slot ----------------------------------------------------------------------------------------

/**
 * Fills the reverb stage's companions from `constants/SendEffectDefaults.kt`, the same constants the
 * master reverb uses (`/dsl-design` §4 is the rule; this is only what THIS door does). Called from
 * every reverb setter, because the reverb has no name knob: `reverb(size = 4)` fills `wet` and the
 * room is ON.
 *
 * **`lowpass` has no constant** and is never filled: unset means no damping, and inventing a cutoff
 * would darken every room.
 *
 * Fills the voice FIELDS with the same constants, so the two sources agree on every knob the author
 * did not reach past. The orbit's room reads the SLOTS alone (the amount too, since step 5b-2), and
 * the fields leave the wire in 5b-3.
 *
 * Byte-identical to what the engine did with an unset field: `VoiceFactory` substituted exactly
 * these constants.
 */
private fun SprudelVoiceData.fillReverbDefaults() {
    val slots = katalystParamsOrNew()

    slots.setOrDefault("reverb.wet", value = null, default = REVERB_WET)
    slots.setOrDefault("reverb.size", value = null, default = REVERB_SIZE)

    if (reverb == null) {
        reverb = REVERB_WET
    }

    if (reverbSize == null) {
        reverbSize = REVERB_SIZE
    }
}

/** Writes the wet this call named, into the field and its slot, and fills the companions. */
private fun SprudelVoiceData.setReverbWet(wet: Double) {
    reverb = wet
    katalystParamsOrNew().set("reverb.wet", wet)
    fillReverbDefaults()
}

// -- reverb, the wet slot --------------------------------------------------------------------------------------------

private val reverbMutation = voiceSetter {
    val wet = it?.toString()?.toDoubleOrNull()

    if (wet != null) {
        setReverbWet(wet)
    }
}

private fun applyReverb(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.reverb }, update = reverbMutation)
    }

    // No args: reinterpret the pattern's own values as the wet. This is the one path that can hand
    // this door's HEAD setter a null, and it clears the voice field while leaving the slot: a
    // recorded asymmetry, unreachable from any spelling with an argument.
    if (args.isEmpty()) {
        return source.reinterpretVoice {
            it.clone().apply {
                reverb = value?.asDouble

                val wet = reverb

                if (wet != null) {
                    setReverbWet(wet)
                }
            }
        }
    }

    return source._applyControlFromParams(args, reverbMutation) { src, ctrl ->
        val wet = ctrl.reverb

        if (wet != null) {
            src.setReverbWet(wet)
        }

        src
    }
}


/**
 * The orbit reverb: how much of the orbit goes into the room, its size and its lowpass.
 *
 * One reverb per [orbit bus](/manuals/lexikon/orbit-bus), and all three are set once for everyone
 * by the orbit's owning voice. `wet` is how much of the orbit goes into the room: the room is added
 * on top of the dry sound, and every voice on the orbit is in it alike. So a pattern that should
 * stay dry, or sit in the room by a different amount, goes on an orbit of its own. The room hears
 * everything before it on the orbit: a `body`, a `vowel` and the `delay`'s echoes. When another
 * pattern with a reverb takes the orbit over, `wet` and `size` move smoothly to its values; a
 * pattern without one switches the room off, which does not fade yet (the tail rings out).
 *
 * The call sets every slot: the ones you leave out take the same default as on the master reverb,
 * wet 0.25 and size 5 (lowpass has none), unless an earlier call already set them. So a bare
 * `reverb(0.4)` already plays in a medium room, and `reverb.size` reads 5 after it. Slots apply in
 * order, wet first, so a mapper on a later slot sees a default an earlier slot of the same call
 * filled in (`reverb(0.3, size = mul(2))` is size 10). A call whose only slot rests in its control
 * pattern writes nothing on that event, and fills nothing. `size` sets the tail: 3 is
 * roughly 1 s, 5 roughly 1.4 s, 10 roughly 12.5 s, the longest there is. `lowpass` darkens the
 * tail: the lower the cutoff, the duller the room.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`reverb(size = mul(2))`), and the numeric slots read back as `reverb.wet`, `reverb.size`, `reverb.lowpass`.
 * With no argument at all, the pattern's own values are reinterpreted as `wet`.
 *
 * ```KlangScript(Playable)
 * s("bd sd").reverb(0.3, 4)                                                  // wet and size
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
 * s("bd sd").reverb("<0.1 0.5>", 4).delay(wet = reverb.wet, time = 0.25)     // as much delay as reverb
 * ```
 *
 * @param wet How much of the orbit goes into the room, 0 to 1, default 0.25. Orbit-wide.
 * @param size Tail length, about 0 to 10, default 5; above 10 is bounded at 10. Orbit-wide.
 * @param lowpass Lowpass on the tail, Hz. Lower is darker. Unset by default. Orbit-wide.
 * @param-tool wet SprudelReverbEditor, SprudelReverbSequenceEditor
 * @param-tool size SprudelReverbSizeEditor, SprudelReverbSizeSequenceEditor
 *
 * @scope orbit
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
 * @scope orbit
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

private val reverbSizeMutation = voiceSetter {
    reverbSize = it?.asDoubleOrNull()

    val size = reverbSize

    if (size != null) {
        katalystParamsOrNew().set("reverb.size", size)
        fillReverbDefaults()
    }
}

private fun applyReverbSize(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.reverbSize }, update = reverbSizeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, reverbSizeMutation)
}

// -- reverb.lowpass --------------------------------------------------------------------------------------------------

private val reverbLowpassMutation = voiceSetter {
    reverbLowpass = it?.asDoubleOrNull()

    val lowpass = reverbLowpass

    if (lowpass != null) {
        katalystParamsOrNew().set("reverb.lowpass", lowpass)
        fillReverbDefaults()
    }
}

private fun applyReverbLowpass(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.reverbLowpass }, update = reverbLowpassMutation)
    }

    return source._liftOrReinterpretNumericalField(args, reverbLowpassMutation)
}
