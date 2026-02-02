@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.lang.addons.*

@DslMarker
annotation class StrudelDsl

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
}
