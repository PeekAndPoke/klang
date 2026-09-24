/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.katalystParamsOrNew
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.putKatalystParam

// -- vowel -----------------------------------------------------------------------------------------------------------

/**
 * Fills the vowel stage's companions, `vowel.wet` and `vowel.floor`, from
 * `constants/BusEffectDefaults.kt`, the body door's twin with [VOWEL_WET] and [VOWEL_FLOOR]
 * (`/dsl-design` §4 is the rule; this is only what THIS door does). Called from the VOWEL setter
 * alone, because the vowel is this stage's name knob.
 *
 * Fills the voice FIELDS with the same two constants. The wire still carries them (as a
 * `FilterDef.Formant` in `filters`), but since step 5b-1 the orbit's formant bank reads the SLOTS
 * and nothing else, and since step 5b-3 no backend code reads that filter at all.
 *
 * Byte-identical to what the engine did with an unset field: `toVoiceData` reads a null `vowelMix`
 * as [VOWEL_WET], and a null floor means [VOWEL_FLOOR] to `FilterDef.Formant`; the chain takes the
 * same two through `KatalystSlots.vowelDef`.
 */
private fun SprudelVoiceData.fillVowelDefaults() {
    val slots = katalystParamsOrNew()

    slots.setOrDefault("vowel.wet", value = null, default = VOWEL_WET)
    slots.setOrDefault("vowel.floor", value = null, default = VOWEL_FLOOR)

    if (vowelMix == null) {
        vowelMix = VOWEL_WET
    }

    if (vowelFloor == null) {
        vowelFloor = VOWEL_FLOOR
    }
}

// The VOWEL goes into the orbit chain's slot state as a number: `vowel.vowel` carries the vowel's
// INDEX in `VowelBands.names`, and `VowelBands.indexOf` is the one conversion both doors use
// (Katalyst step 5a-2, 2026-09-18). A bare name is the soprano register, there as here. That is
// what keeps `vowel(vowel = "bass:a")` working on a DECLARED chain without a string ever reaching
// the wire as a slot; an unknown name is index 0, `none`, the same "off" the voice field gives it.
//
// Naming the vowel is what fills the rest of the stage. The vowel is not this door's head (the wet
// is, since step 3d(iii), 2026-09-24), so no bare call reaches this setter and nothing ever hands
// it a null: it has no clear arm and no reinterpret path. The off switch is
// `vowel(vowel = "none")`, index 0.
private fun applyVowel(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftStringField(args) { v ->
        val name = v?.lowercase() ?: return@_liftStringField this

        clone().also { data ->
            data.vowel = name
            data.putKatalystParam("vowel.vowel", VowelBands.indexOf(name))
            data.fillVowelDefaults()
        }
    }
}

// The two NUMBERS go into the slot state as well (`vowel.wet`, `vowel.floor`), which is what makes
// this door an alias of `katp` on a DECLARED chain (signal-flow plan §7, Katalyst step 5a). A
// wet-only or floor-only call writes only its own knob: the NAME KNOB of this stage is the VOWEL,
// and a door never invents a name knob.
//
// No setter here has a CLEAR arm, for the reason the body door spells out. The HEAD setter, the
// WET, is the one a bare call can hand a null; it then writes nothing (`?: return@voiceSetter`).

private val vowelWetMutation = voiceSetter {
    val wet = it?.asDoubleOrNull() ?: return@voiceSetter

    vowelMix = wet
    putKatalystParam("vowel.wet", wet)
}

private fun applyVowelWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vowelMix }, update = vowelWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, vowelWetMutation)
}

private val vowelFloorMutation = voiceSetter {
    vowelFloor = it?.asDoubleOrNull()
    putKatalystParam("vowel.floor", vowelFloor)
}

private fun applyVowelFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vowelFloor }, update = vowelFloorMutation)
    }

    return source._liftOrReinterpretNumericalField(args, vowelFloorMutation)
}

/**
 * The vowel formant filter: the vowel, its mix and its dry floor.
 *
 * Shapes the orbit like a mouth. One formant filter per [orbit bus](/manuals/lexikon/orbit-bus), so
 * ALL of it, the vowel included, is set once for everyone by the orbit's owning voice.
 *
 * `wet` here is a mix (and `floor` how much of the dry stays under it): the filter processes the
 * whole orbit mix. The `wet` of `reverb` and `delay` reads differently: it is how much of the orbit
 * goes INTO them, a [send](/manuals/lexikon/send), and their sound is added on top of the dry. Give a
 * pattern its own vowel by giving it its own orbit.
 *
 * Vowels: `a`, `e`, `i`, `o`, `u`, the umlauts `ae`/`ä`, `oe`/`ö`, `ue`/`ü`, and the diphthongs
 * `ei`, `au`, `eu`/`äu`. A prefix picks a voice type, `tenor:a`.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`vowel(floor = mul(2))`), and the numeric slots read back as `vowel.wet`, `vowel.floor`. `vowel` is a name and has no reader.
 * With no argument at all, the pattern's own values are reinterpreted as `wet`
 * (`seq("<0.2 0.5>").vowel()`); a value that is not a number writes nothing.
 *
 * `wet` comes first, as on every door that has one. The vowel is the second parameter, so a vowel
 * alone is written by name: `vowel(vowel = "a")`.
 *
 * Naming a vowel sets the whole stage: `wet` and `floor` take their shared defaults, 0.5 and 0.2,
 * unless an earlier call already set them, so `vowel(vowel = "a")` is as audible as it always was.
 * Naming `wet` or `floor` alone does NOT invent a vowel, because the vowel is what switches the
 * filter on. `vowel(vowel = "none")` switches it off again.
 *
 * Slots apply in order, `wet` first. A wet MAPPER in the same call as the vowel therefore maps a
 * wet that is not set yet, which does nothing: `vowel(mul(2), "a")` on a fresh note is wet 0.5,
 * the vowel's default. To double the wet, map it in a later call, once it is set:
 * `vowel(vowel = "a").vowel(wet = mul(2))` is wet 1.0.
 *
 * All three reach a DECLARED orbit chain as chain slots, the vowel as the index of its name in the
 * vowel catalogue (`vowel.vowel`, 0 = none), so the same call works whether the orbit declares a
 * chain or runs the historical one, down to the sample.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel(0.8, "a")                                   // an open ah
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel(0.8, "a e i o").vowel(wet = mul("<1 0.5>"))   // half as vocal every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("<0.2 0.9>", "o").reverb(wet = vowel.wet, size = 4)   // as much reverb as vowel
 * ```
 *
 * @param wet How much of the orbit runs through the filter, 0 to 1.
 * @param vowel Vowel name, see the list above. A prefix picks a voice type, `tenor:a`.
 * @param floor Minimum dry share kept in the mix, 0 to 1.
 *
 * @scope orbit
 * @category effects
 * @tags vowel, wet, floor
 */
@KlangScript.Function
fun SprudelPattern.vowel(
    wet: PatternLike? = null,
    vowel: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A call without a wet must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(vowel != null || floor != null)) {
        applyVowelWet(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (vowel != null) p = applyVowel(p, listOf<Any?>(vowel).asSprudelDslArgs(callInfo?.forParam(1)))
    if (floor != null) p = applyVowelFloor(p, listOf<Any?>(floor).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [vowel]. */
@KlangScript.Function
fun String.vowel(
    wet: PatternLike? = null,
    vowel: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vowel(wet, vowel, floor, callInfo)

/** Chains a [vowel] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vowel(
    wet: PatternLike? = null,
    vowel: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.vowel(wet, vowel, floor, callInfo) }

/**
 * The `vowel` object: `vowel(...)` sets the slots, and each numeric slot reads back as a child,
 * `vowel.wet`, `vowel.floor`.
 *
 * @scope orbit
 * @category effects
 * @tags vowel, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("vowel")
object vowel {

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.vowelMix }

    /** The floor slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val floor: FieldAccessor = FieldAccessor { it.vowelFloor }

    /** The setter, see [SprudelPattern.vowel]. */
    @KlangScript.Invoke
    operator fun invoke(
        wet: PatternLike? = null,
        vowel: PatternLike? = null,
        floor: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.vowel(wet, vowel, floor, callInfo) }
}
