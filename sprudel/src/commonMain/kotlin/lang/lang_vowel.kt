/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
// -- vowel() ----------------------------------------------------------------------------------------------------------

private fun applyVowel(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { v -> clone().also { it.vowel = v?.lowercase() } }
}

/**
 * Sets the vowel formant filter.
 *
 * Applies a formant filter tuned to specific vowel sounds to create "singing" or vocal effects.
 * This filter mimics the resonant characteristics of the human vocal tract.
 * When called with no argument, reinterprets the current event value as a vowel name.
 *
 * **Syntax:** `vowel("vowel")` or `vowel("voice:vowel")`
 *
 * **Supported Vowels:**
 * - Standard: `a`, `e`, `i`, `o`, `u`
 * - German Umlauts: `ae` (ä), `oe` (ö), `ue` (ü)
 * - German Diphthongs (nucleus): `ei` (→ a), `au` (→ a), `eu` / `äu` (→ open o)
 * - `none` — resets (removes the vowel filter)
 *
 * **Supported Voice Types:**
 * - `soprano` (default)
 * - `alto` (or `countertenor`)
 * - `tenor`
 * - `bass`
 *
 * ```KlangScript(Playable)
 * note("c3").vowel("a")             // Soprano 'a' (default)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").apply(vowel("a e i"))  // mapper form — sequence vowels
 * ```
 *
 * @category tonal
 * @tags vowel, formant, vocal, filter, singing
 */
@KlangScript.Function
fun SprudelPattern.vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVowel(this, listOfNotNull(vowel).asSprudelDslArgs(callInfo))

/** Sets the vowel formant filter on a string pattern. */
@KlangScript.Function
fun String.vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vowel(vowel, callInfo)

/** Returns a [PatternMapperFn] that sets the vowel formant filter. */
@KlangScript.Function
fun vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.vowel(vowel, callInfo) }

/** Chains a vowel step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vowel(vowel, callInfo) }

// -- vowelWet() -------------------------------------------------------------------------------------------------------

private val vowelWetMutation = voiceSetter { vowelMix = it?.asDoubleOrNull() }

private fun applyVowelWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vowelMix }, update = vowelWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, vowelWetMutation)
}

/**
 * Sets the vowel/formant wet/dry balance — the shared wet knob (C4), prefixed because sprudel
 * sets fields on one unordered voice. `0.0` = no vowel colour, `1.0` = formants at full level.
 *
 * Use with [vowel]. The vowel is a *source shaped by formants*, not replaced by them — the dry
 * never drops below its broadband floor ([vowelFloor]), so higher values add vowel character
 * without losing the body of the sound. The knob lives on `[0, 1]`; start around 0.3–0.6.
 * When omitted, the pattern's own numeric values are reinterpreted as the amount.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").vowel("a").vowelWet(0.5)   // 'a' vowel blended over the source
 * ```
 *
 * @category effects
 * @tags vowel, formant, mix, dry, wet, vocal
 */
@KlangScript.Function
fun SprudelPattern.vowelWet(wet: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVowelWet(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))

/** Sets the vowel formant wet balance on a string pattern. */
@KlangScript.Function
fun String.vowelWet(wet: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vowelWet(wet, callInfo)

/**
 * The vowel filter mix of each event, as a value other setters can read.
 *
 * Bare `vowelWet` reads what the chain has set so far, so it comes after whatever set the field
 * (`vowelWet(...)` or an alias). Call it, `vowelWet(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("a").vowelWet(0.5).vowelWet(mul("1 0.5"))   // the second note less vowel
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("a").vowelWet("0.3 0.9").vowelFloor(vowelWet.mul(0.5))   // floor follows mix
 * ```
 *
 * @category effects
 * @tags vowelWet, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("vowelWet")
object VowelWet : FieldAccessor({ it.vowelMix }) {

    /** Returns a [PatternMapperFn] that sets the vowel formant wet balance. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(wet: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.vowelWet(wet, callInfo) }
}

/** The [VowelWet] accessor as a value, so the Kotlin door reads like the script. */
val vowelWet: VowelWet = VowelWet

/** Chains a vowelWet step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vowelWet(wet: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vowelWet(wet, callInfo) }

// -- vowelFloor() -----------------------------------------------------------------------------------------------------

private val vowelFloorMutation = voiceSetter { vowelFloor = it?.asDoubleOrNull() }

private fun applyVowelFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.vowelFloor }, update = vowelFloorMutation)
    }

    return source._liftOrReinterpretNumericalField(args, vowelFloorMutation)
}

/**
 * Sets the vowel/formant broadband dry floor — how much untouched dry source stays between the
 * formants. Lower makes the formants dominate a thinner source (more overtly "vowel"); higher keeps
 * more of the original source audible between formants. When omitted, the engine default is used.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").vowel("a").vowelFloor(0.1)   // strong, dominant 'a' vowel
 * ```
 *
 * @category effects
 * @tags vowel, formant, floor, dry, wet, vocal
 */
@KlangScript.Function
fun SprudelPattern.vowelFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVowelFloor(this, listOfNotNull(floor).asSprudelDslArgs(callInfo))

/** Sets the vowel formant floor on a string pattern. */
@KlangScript.Function
fun String.vowelFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vowelFloor(floor, callInfo)

/**
 * The vowel filter floor of each event, as a value other setters can read.
 *
 * Bare `vowelFloor` reads what the chain has set so far, so it comes after whatever set the field
 * (`vowelFloor(...)` or an alias). Call it, `vowelFloor(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("a").vowelFloor(0.2).vowelFloor(add("0 0.4"))   // more dry on the second note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").vowel("a").vowelFloor("0.1 0.5").vowelWet(vowelFloor.add(0.4))   // mix follows floor
 * ```
 *
 * @category effects
 * @tags vowelFloor, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("vowelFloor")
object VowelFloor : FieldAccessor({ it.vowelFloor }) {

    /** Returns a [PatternMapperFn] that sets the vowel formant floor. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.vowelFloor(floor, callInfo) }
}

/** The [VowelFloor] accessor as a value, so the Kotlin door reads like the script. */
val vowelFloor: VowelFloor = VowelFloor

/** Chains a vowelFloor step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vowelFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vowelFloor(floor, callInfo) }
