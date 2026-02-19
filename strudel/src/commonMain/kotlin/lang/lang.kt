@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.lang.addons.*
import io.peekandpoke.klang.strudel.lang.docs.registerStrudelDocs

@DslMarker
annotation class StrudelDsl

/**
 * Type alias for pattern-like values that can be converted to patterns.
 * Accepts: StrudelPattern, String, Number, Boolean, and other types that can be converted to patterns.
 */
typealias PatternLike = Any

/**
 * Registers all Strudel DSL functions by accessing the init properties of all lang_*.kt files.
 * This forces the initialization of all delegate properties, which in turn registers them in StrudelRegistry.
 */
fun initStrudelLang() {
    // Access all init properties to force initialization

    // Register core strudel functions
    strudelLangArithmeticInit = true
    strudelLangConditionalInit = true
    strudelLangContinuousInit = true
    strudelLangDynamicsInit = true
    strudelLangEffectsInit = true
    strudelLangFiltersInit = true
    strudelLangHelpersInit = true
    strudelLangMiscInit = true
    strudelLangSampleInit = true
    strudelLangPatternPickingInit = true
    strudelLangRandomInit = true
    strudelLangStructuralInit = true
    strudelLangSynthesisInit = true
    strudelLangTempoInit = true
    strudelLangTonalInit = true
    strudelLangVowelInit = true

    // register non-strudel addon functions
    strudelLangArithmeticAddonsInit = true
    strudelLangContinuousAddonsInit = true
    strudelLangOscAddonsInit = true
    strudelLangStructuralAddonsInit = true
    strudelLangTempoAddonsInit = true

    // Register DSL documentation
    registerStrudelDocs()
}
