@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
// -- vowel() ----------------------------------------------------------------------------------------------------------

private fun applyVowel(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { v -> copy(vowel = v?.lowercase()) }
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

// -- vowelMix() -------------------------------------------------------------------------------------------------------

private val vowelMixMutation = voiceSetter { vowelMix = it?.asDoubleOrNull() }

private fun applyVowelMix(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, vowelMixMutation)
}

/**
 * Sets how much vowel/formant colour is blended over the dry source (0.0 = none).
 *
 * Use with [vowel]. The vowel is a *source shaped by formants*, not replaced by them — the dry
 * always stays present (a broadband floor), so higher values add more vowel character without
 * losing the body of the sound. Start around 0.3–0.6. When omitted, the pattern's own numeric
 * values are reinterpreted as the amount.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").vowel("a").vowelMix(0.5)   // 'a' vowel blended over the source
 * ```
 *
 * @category effects
 * @tags vowel, formant, mix, dry, wet, vocal
 */
@KlangScript.Function
fun SprudelPattern.vowelMix(mix: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVowelMix(this, listOfNotNull(mix).asSprudelDslArgs(callInfo))

/** Sets the vowel formant mix on a string pattern. */
@KlangScript.Function
fun String.vowelMix(mix: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vowelMix(mix, callInfo)

/** Returns a [PatternMapperFn] that sets the vowel formant mix. */
@KlangScript.Function
fun vowelMix(mix: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.vowelMix(mix, callInfo) }

/** Chains a vowelMix step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.vowelMix(mix: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vowelMix(mix, callInfo) }
