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

    strudelLangArithmeticInit = true
    strudelLangChoiceInit = true
    strudelLangContinuousInit = true
    strudelLangDynamicsInit = true
    strudelLangEffectsInit = true
    strudelLangFiltersInit = true
    strudelLangHelpersInit = true
    strudelLangRandomInit = true
    strudelLangStructuralInit = true
    strudelLangTempoInit = true
    strudelLangTonalInit = true
}
