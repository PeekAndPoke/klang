@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyVowel(this, listOfNotNull(vowel).asSprudelDslArgs(callInfo))

/** Sets the vowel formant filter on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).vowel(vowel, callInfo)

/** Returns a [PatternMapperFn] that sets the vowel formant filter. */
@SprudelDsl
@KlangScript.Function
fun vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn = { p -> p.vowel(vowel, callInfo) }

/** Chains a vowel step onto this [PatternMapperFn]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.vowel(vowel: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.vowel(vowel, callInfo) }
