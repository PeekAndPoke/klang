@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangVowelInit = false

// -- vowel() ----------------------------------------------------------------------------------------------------------

private val vowelMutation = voiceModifier { vowel ->
    val newVowel = vowel?.toString()?.lowercase()
    copy(vowel = newVowel)
}

fun applyVowel(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, vowelMutation) { src, ctrl ->
        src.copy(vowel = ctrl.vowel)
    }
}

internal val StrudelPattern._vowel by dslPatternExtension { p, args, /* callInfo */ _ -> applyVowel(p, args) }

internal val _vowel by dslFunction { args, /* callInfo */ _ -> args.toPattern(vowelMutation) }

internal val String._vowel by dslStringExtension { p, args, callInfo -> p._vowel(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * Sets the vowel formant filter.
 *
 * Applies a formant filter tuned to specific vowel sounds to create "singing" or vocal effects.
 * This filter mimics the resonant characteristics of the human vocal tract.
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
 * vowel("a e i o u")                // Sequence different vowels
 * ```
 *
 * @category tonal
 * @tags vowel, formant, vocal, filter, singing
 */
@StrudelDsl
fun vowel(vowel: PatternLike): StrudelPattern = _vowel(listOf(vowel).asStrudelDslArgs())

/** Sets the vowel formant filter on this pattern. */
@StrudelDsl
fun StrudelPattern.vowel(vowel: PatternLike): StrudelPattern = this._vowel(listOf(vowel).asStrudelDslArgs())

/** Sets the vowel formant filter on a string pattern. */
@StrudelDsl
fun String.vowel(vowel: PatternLike): StrudelPattern = this._vowel(listOf(vowel).asStrudelDslArgs())