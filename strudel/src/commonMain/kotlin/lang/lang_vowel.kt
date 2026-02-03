@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._applyControlFromParams

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangVowelInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Vowel Formant Synthesis
// ///

// -- vowel() ----------------------------------------------------------------------------------------------------------

private val vowelMutation = voiceModifier { vowel ->
    val newVowel = vowel?.toString()?.lowercase()
    copy(vowel = newVowel)
}

private fun applyVowel(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._applyControlFromParams(args, vowelMutation) { src, ctrl ->
        src.copy(vowel = ctrl.vowel)
    }
}

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
 * **Examples:**
 * ```kotlin
 * note("c3").vowel("a")             // Soprano 'a' (default)
 * note("c3").vowel("bass:o")        // Bass 'o'
 * note("c3").vowel("tenor:ue")      // Tenor 'ü' (German Umlaut)
 * vowel("a e i o u")                // Sequence different vowels
 * vowel("soprano:a bass:u")         // Sequence different voices and vowels
 * ```
 */
@StrudelDsl
val StrudelPattern.vowel by dslPatternExtension { p, args, /* callInfo */ _ -> applyVowel(p, args) }

/**
 * Creates a pattern with vowel formant filter.
 */
@StrudelDsl
val vowel by dslFunction { args, /* callInfo */ _ -> args.toPattern(vowelMutation) }

/**
 * Sets the vowel formant filter on a string pattern.
 */
@StrudelDsl
val String.vowel by dslStringExtension { p, args, callInfo -> p.vowel(args, callInfo) }
