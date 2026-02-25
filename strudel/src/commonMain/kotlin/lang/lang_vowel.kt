@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._liftOrReinterpretStringField
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangVowelInit = false

// -- vowel() ----------------------------------------------------------------------------------------------------------

fun applyVowel(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args) { v -> copy(vowel = v?.lowercase()) }
}

internal val StrudelPattern._vowel by dslPatternExtension { p, args, /* callInfo */ _ -> applyVowel(p, args) }

internal val String._vowel by dslStringExtension { p, args, callInfo -> p._vowel(args, callInfo) }

internal val _vowel by dslPatternMapper { args, callInfo -> { p -> p._vowel(args, callInfo) } }

internal val PatternMapperFn._vowel by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_vowel(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
 * ```KlangScript
 * note("c3").vowel("a")             // Soprano 'a' (default)
 * ```
 *
 * ```KlangScript
 * note("c3").apply(vowel("a e i"))  // mapper form — sequence vowels
 * ```
 *
 * @category tonal
 * @tags vowel, formant, vocal, filter, singing
 */
@StrudelDsl
fun StrudelPattern.vowel(vowel: PatternLike? = null): StrudelPattern =
    this._vowel(listOfNotNull(vowel).asStrudelDslArgs())

/** Sets the vowel formant filter on a string pattern. */
@StrudelDsl
fun String.vowel(vowel: PatternLike? = null): StrudelPattern =
    this._vowel(listOfNotNull(vowel).asStrudelDslArgs())

/** Returns a [PatternMapperFn] that sets the vowel formant filter. */
@StrudelDsl
fun vowel(vowel: PatternLike? = null): PatternMapperFn = _vowel(listOfNotNull(vowel).asStrudelDslArgs())

/** Chains a vowel step onto this [PatternMapperFn]. */
@StrudelDsl
fun PatternMapperFn.vowel(vowel: PatternLike? = null): PatternMapperFn =
    this._vowel(listOfNotNull(vowel).asStrudelDslArgs())
