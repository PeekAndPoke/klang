@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.*

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
    return source.applyNumericalParam(
        args = args,
        modify = warmthMutation,
        getValue = { warmth },
        setValue = { v, _ -> copy(warmth = v) },
    )
}

/** Controls the oscillator warmth (low-pass filtering amount). 0.0 = bright, 1.0 = muffled */
@StrudelDsl
val StrudelPattern.warmth by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyWarmth(p, args)
}

/** Creates a pattern with warmth values */
@StrudelDsl
val warmth by dslFunction { args, /* callInfo */ _ -> args.toPattern(warmthMutation) }

/** Modifies the warmth of a pattern defined by a string */
@StrudelDsl
val String.warmth by dslStringExtension { p, args, callInfo ->
    p.warmth(args, callInfo)
}
