@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

@DslMarker
annotation class StrudelDsl

/**
 * Registers all Strudel DSL functions by accessing the init properties of all lang_*.kt files.
 * This forces the initialization of all delegate properties, which in turn registers them in StrudelRegistry.
 */
fun initStrudelLang() {
    // Access all init properties to force initialization
    strudelLangHelpersInit = true
    strudelLangContinuousInit = true
    strudelLangStructuralInit = true
    strudelLangTempoInit = true
    strudelLangTonalInit = true
    strudelLangDynamicsInit = true
    strudelLangFiltersInit = true
    strudelLangEffectsInit = true
    strudelLangArithmeticInit = true
}
