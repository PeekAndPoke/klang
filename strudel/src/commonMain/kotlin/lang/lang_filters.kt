@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.StrudelPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangFiltersInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Filters
// ///

// -- lpf() ------------------------------------------------------------------------------------------------------------

private val lpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.LowPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyLpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = lpfMutation,
        getValue = { cutoff },
        setValue = { v, ctrl -> copy(resonance = ctrl.resonance ?: resonance).lpfMutation(v) },
    )
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.lpf by dslPatternExtension { p, args, callInfo ->
    applyLpf(p, args)
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val lpf by dslFunction { args, callInfo -> args.toPattern(lpfMutation) }

/** Adds a Low Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.lpf by dslStringExtension { p, args, callInfo ->
    applyLpf(p, args)
}

// -- hpf() ------------------------------------------------------------------------------------------------------------

private val hpfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.HighPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        hcutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyHpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = hpfMutation,
        getValue = { hcutoff },
        setValue = { v, ctrl -> copy(resonance = ctrl.resonance ?: resonance).hpfMutation(v) },
    )
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.hpf by dslPatternExtension { p, args, callInfo ->
    applyHpf(p, args)
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val hpf by dslFunction { args, callInfo -> args.toPattern(hpfMutation) }

/** Adds a High Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.hpf by dslStringExtension { p, args, callInfo ->
    applyHpf(p, args)
}

// -- bandf() / bpf() --------------------------------------------------------------------------------------------------

private val bandfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.BandPass(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        bandf = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyBandf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = bandfMutation,
        getValue = { bandf },
        setValue = { v, ctrl -> copy(resonance = ctrl.resonance ?: resonance).bandfMutation(v) },
    )
}

/** Adds a Band Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.bandf by dslPatternExtension { p, args, callInfo ->
    applyBandf(p, args)
}

/** Adds a Band Pass Filter with the given cutoff frequency. */
@StrudelDsl
val bandf by dslFunction { args, callInfo -> args.toPattern(bandfMutation) }

/** Adds a Band Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.bandf by dslStringExtension { p, args, callInfo ->
    applyBandf(p, args)
}

/** Alias for [bandf] */
@StrudelDsl
val StrudelPattern.bpf by dslPatternExtension { p, args, callInfo -> applyBandf(p, args) }

/** Alias for [bandf] */
@StrudelDsl
val bpf by dslFunction { args, callInfo -> args.toPattern(bandfMutation) }

/** Alias for [bandf] on a string */
@StrudelDsl
val String.bpf by dslStringExtension { p, args, callInfo -> applyBandf(p, args) }

// -- notchf() ---------------------------------------------------------------------------------------------------------

private val notchfMutation = voiceModifier {
    val cutoff = it?.asDoubleOrNull()
    val filter = FilterDef.Notch(cutoffHz = cutoff ?: 1000.0, q = resonance)

    copy(
        cutoff = cutoff,
        filters = filters.addOrReplace(filter),
    )
}

private fun applyNotchf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = notchfMutation,
        getValue = { cutoff },
        setValue = { v, ctrl -> copy(resonance = ctrl.resonance ?: resonance).notchfMutation(v) },
    )
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.notchf by dslPatternExtension { p, args, callInfo ->
    applyNotchf(p, args)
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val notchf by dslFunction { args, callInfo -> args.toPattern(notchfMutation) }

/** Adds a Notch Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.notchf by dslStringExtension { p, args, callInfo ->
    applyNotchf(p, args)
}

// -- resonance() / res() ----------------------------------------------------------------------------------------------

private val resonanceMutation = voiceModifier {
    val newQ = it?.asDoubleOrNull() ?: 1.0

    val newFilters = filters.modifyAll { filter ->
        when (filter) {
            is FilterDef.LowPass -> filter.copy(q = newQ)
            is FilterDef.HighPass -> filter.copy(q = newQ)
            is FilterDef.BandPass -> filter.copy(q = newQ)
            is FilterDef.Notch -> filter.copy(q = newQ)
        }
    }

    copy(resonance = newQ, filters = newFilters)
}

private fun applyResonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = resonanceMutation,
        getValue = { resonance },
        setValue = { v, _ -> resonanceMutation(v) }
    )
}

/** Sets the filter resonance. */
@StrudelDsl
val StrudelPattern.resonance by dslPatternExtension { p, args, callInfo ->
    applyResonance(p, args)
}

/** Sets the filter resonance. */
@StrudelDsl
val resonance by dslFunction { args, callInfo -> args.toPattern(resonanceMutation) }

/** Sets the filter resonance on a string. */
@StrudelDsl
val String.resonance by dslStringExtension { p, args, callInfo ->
    applyResonance(p, args)
}

/** Alias for [resonance] */
@StrudelDsl
val StrudelPattern.res by dslPatternExtension { p, args, callInfo -> applyResonance(p, args) }

/** Alias for [resonance] */
@StrudelDsl
val res by dslFunction { args, callInfo -> args.toPattern(resonanceMutation) }

/** Alias for [resonance] on a string */
@StrudelDsl
val String.res by dslStringExtension { p, args, callInfo -> applyResonance(p, args) }
