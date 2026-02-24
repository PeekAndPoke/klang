@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._liftOrReinterpretStringField
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

/**
 * ADDONS: Oscillator-related functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangOscAddonsInit = false

// -- warmth() ---------------------------------------------------------------------------------------------------------

private val warmthMutation = voiceModifier {
    copy(warmth = it?.asDoubleOrNull())
}

private fun applyWarmth(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftOrReinterpretStringField(args, warmthMutation)
}

internal val StrudelPattern._warmth by dslPatternExtension { p, args, /* callInfo */ _ -> applyWarmth(p, args) }
internal val String._warmth by dslStringExtension { p, args, callInfo -> p._warmth(args, callInfo) }
internal val _warmth by dslPatternMapper { args, callInfo -> { p -> p._warmth(args, callInfo) } }
internal val PatternMapperFn._warmth by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(
        _warmth(
            args,
            callInfo
        )
    )
}

// ===== USER-FACING OVERLOADS =====

/**
 * Controls the oscillator warmth (low-pass filtering amount).
 *
 * A value of `0.0` gives a bright, unfiltered sound; `1.0` gives a muffled, warm sound.
 *
 * ```KlangScript
 * note("c d e f").warmth(0.8)          // warm, muffled sawtooth
 * ```
 *
 * ```KlangScript
 * note("c d e f").warmth("<0 0.5 1>")  // cycle through warmth values
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 *
 * @category tonal
 * @tags warmth, oscillator, filter, low-pass, addon
 */
@StrudelDsl
fun StrudelPattern.warmth(amount: PatternLike? = null): StrudelPattern =
    this._warmth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Parses this string as a pattern and sets the oscillator warmth.
 *
 * ```KlangScript
 * note("c d e f").s("square").warmth("<0 0.5 1>")  // cycle through warmth values
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 */
@StrudelDsl
fun String.warmth(amount: PatternLike? = null): StrudelPattern =
    this._warmth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Creates a [PatternMapperFn] that sets the oscillator warmth.
 *
 * ```KlangScript
 * note("c d e f").apply(warmth("<0 0.5 1>"))  // cycle through warmth values
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 */
@StrudelDsl
fun warmth(amount: PatternLike? = null): PatternMapperFn =
    _warmth(listOfNotNull(amount).asStrudelDslArgs())

/**
 * Chains a warmth-set onto this [PatternMapperFn], applying oscillator warmth after the previous step.
 *
 * ```KlangScript
 * seq("0.2 0.4").apply(mul(2).warmth())  // mul doubles values, warmth() reads them as warmth: 0.4, 0.8
 * ```
 *
 * @param amount The warmth amount between 0.0 (bright) and 1.0 (warm/muffled).
 */
@StrudelDsl
fun PatternMapperFn.warmth(amount: PatternLike? = null): PatternMapperFn =
    _warmth(listOfNotNull(amount).asStrudelDslArgs())
