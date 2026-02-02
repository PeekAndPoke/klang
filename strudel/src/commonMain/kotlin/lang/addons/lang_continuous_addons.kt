package io.peekandpoke.klang.strudel.lang.addons

import io.peekandpoke.klang.strudel.lang.StrudelDsl
import io.peekandpoke.klang.strudel.lang.dslObject
import io.peekandpoke.klang.strudel.pattern.ContinuousPattern

/**
 * ADDONS: Continuous functions that are NOT available in the original strudel impl
 */

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangContinuousAddonsInit = false

// -- cps() ------------------------------------------------------------------------------------------------------------

/** Cosine oscillator: 0 to 1, period of 1 cycle */
@StrudelDsl
val cps by dslObject { ContinuousPattern { _, _, ctx -> ctx.getCps() } }
