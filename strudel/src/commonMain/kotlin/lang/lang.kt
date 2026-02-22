@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
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
 * Type alias for pattern transformation functions.
 * Takes a StrudelPattern as input and returns a modified StrudelPattern.
 */
typealias PatternMapper = (source: StrudelPattern) -> StrudelPattern

/**
 * Type alias for voice data transformation functions.
 * Takes a StrudelVoiceData as input and returns a modified StrudelVoiceData.
 */
typealias VoiceModifier = StrudelVoiceData.(Any?) -> StrudelVoiceData

/**
 * Type alias for voice data merging functions.
 * Takes two StrudelVoiceData as input and returns a merged StrudelVoiceData.
 */
typealias VoiceMerger = (source: StrudelVoiceData, control: StrudelVoiceData) -> StrudelVoiceData

/**
 * Type alias for a top level DSL function definitions.
 */
typealias StrudelDslTopLevelFn<T> = (args: List<StrudelDslArg<Any?>>, callInfo: CallInfo?) -> T

/**
 * Type alias for DSL extension function definitions.
 * Takes a receiver type R, a list of StrudelDslArg arguments, and a CallInfo, and returns a StrudelPattern.
 */
typealias StrudelDslExtFn<R> = (recv: R, args: List<StrudelDslArg<Any?>>, callInfo: CallInfo?) -> StrudelPattern

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
