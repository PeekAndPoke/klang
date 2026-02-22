@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._liftNumericField
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
    return source._liftNumericField(args, warmthMutation)
}

internal val StrudelPattern._warmth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyWarmth(p, args)
}

internal val _warmth by dslPatternFunction { args, /* callInfo */ _ -> args.toPattern(warmthMutation) }

internal val String._warmth by dslStringExtension { p, args, callInfo ->
    p._warmth(args, callInfo)
}

// ===== USER-FACING OVERLOADS =====

/**
 * Controls the oscillator warmth (low-pass filtering amount).
 *
 * A value of `0.0` gives a bright, unfiltered sound; `1.0` gives a muffled, warm sound.
 *
 * ```KlangScript
 * s("sawtooth").warmth(0.8)          // warm, muffled sawtooth
 * ```
 *
 * ```KlangScript
 * s("sawtooth").warmth("<0 0.5 1>")  // cycle through warmth values
 * ```
 *
 * @category tonal
 * @tags warmth, oscillator, filter, low-pass, addon
 */
@StrudelDsl
fun warmth(amount: PatternLike): StrudelPattern = _warmth(listOf(amount).asStrudelDslArgs())

/** Controls the oscillator warmth on this pattern. */
@StrudelDsl
fun StrudelPattern.warmth(amount: PatternLike): StrudelPattern = this._warmth(listOf(amount).asStrudelDslArgs())

/** Controls the oscillator warmth on a string pattern. */
@StrudelDsl
fun String.warmth(amount: PatternLike): StrudelPattern = this._warmth(listOf(amount).asStrudelDslArgs())
