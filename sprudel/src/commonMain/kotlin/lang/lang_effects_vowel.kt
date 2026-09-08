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
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- vowel -----------------------------------------------------------------------------------------------------------

private fun applyVowel(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { v -> clone().also { it.vowel = v?.lowercase() } }
}

private val vowelWetMutation = voiceSetter { vowelMix = it?.asDoubleOrNull() }

private fun applyVowelWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vowelMix }, update = vowelWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, vowelWetMutation)
}

private val vowelFloorMutation = voiceSetter { vowelFloor = it?.asDoubleOrNull() }

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
 * `wet` here is a mix, not a [send](/manuals/lexikon/send): the filter processes the whole orbit
 * mix, so unlike `room` and `delay` there is no per-voice amount. Give a pattern its own vowel by
 * giving it its own orbit.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`vowel(floor = mul(2))`), and the numeric slots read back as `vowel.wet`, `vowel.floor`. `vowel` is a name and has no reader.
 * With no argument at all, the pattern's own values are reinterpreted as `vowel`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("a", 0.8)                                   // an open ah
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("a e i o", 0.8).vowel(wet = mul("<1 0.5>"))   // half as vocal every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("o", "0.2 0.9").room(wet = vowel.wet)       // as much reverb as vowel
 * ```
 *
 * @param vowel Vowel name: `a`, `e`, `i`, `o`, `u`. A prefix picks a voice type, `tenor:a`.
 * @param wet How much of the orbit runs through the filter, 0 to 1.
 * @param floor Minimum dry share kept in the mix, 0 to 1.
 *
 * @scope orbit
 * @category effects
 * @tags vowel, wet, floor
 */
@KlangScript.Function
fun SprudelPattern.vowel(
    vowel: PatternLike? = null,
    wet: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch vowel: reinterpret runs only on a fully bare call.
    var p = if (vowel != null || !(wet != null || floor != null)) {
        applyVowel(this, listOfNotNull(vowel).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (wet != null) p = applyVowelWet(p, listOf<Any?>(wet).asSprudelDslArgs(callInfo?.forParam(1)))
    if (floor != null) p = applyVowelFloor(p, listOf<Any?>(floor).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [vowel]. */
@KlangScript.Function
fun String.vowel(
    vowel: PatternLike? = null,
    wet: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vowel(vowel, wet, floor, callInfo)

/** Chains a [vowel] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vowel(
    vowel: PatternLike? = null,
    wet: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.vowel(vowel, wet, floor, callInfo) }

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
        vowel: PatternLike? = null,
        wet: PatternLike? = null,
        floor: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.vowel(vowel, wet, floor, callInfo) }
}
