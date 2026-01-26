@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

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
    copy(cutoff = it?.asDoubleOrNull())
}

private fun applyLpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = lpfMutation,
        getValue = { cutoff },
        setValue = { v, _ -> copy(cutoff = v) },
    )
}

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.lpf by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpf(p, args) }

/** Adds a Low Pass Filter with the given cutoff frequency. */
@StrudelDsl
val lpf by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpfMutation) }

/** Adds a Low Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.lpf by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] */
@StrudelDsl
val StrudelPattern.cutoff by dslPatternExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] */
@StrudelDsl
val cutoff by dslFunction { args, callInfo -> lpf(args, callInfo) }

/** Alias for [lpf] on a string */
@StrudelDsl
val String.cutoff by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] */
@StrudelDsl
val StrudelPattern.ctf by dslPatternExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] */
@StrudelDsl
val ctf by dslFunction { args, callInfo -> lpf(args, callInfo) }

/** Alias for [lpf] on a string */
@StrudelDsl
val String.ctf by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] */
@StrudelDsl
val StrudelPattern.lp by dslPatternExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] */
@StrudelDsl
val lp by dslFunction { args, callInfo -> lpf(args, callInfo) }

/** Alias for [lpf] on a string */
@StrudelDsl
val String.lp by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

// -- hpf() ------------------------------------------------------------------------------------------------------------

private val hpfMutation = voiceModifier {
    copy(hcutoff = it?.asDoubleOrNull())
}

private fun applyHpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = hpfMutation,
        getValue = { hcutoff },
        setValue = { v, _ -> copy(hcutoff = v) },
    )
}

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.hpf by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpf(p, args) }

/** Adds a High Pass Filter with the given cutoff frequency. */
@StrudelDsl
val hpf by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpfMutation) }

/** Adds a High Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.hpf by dslStringExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/** Alias for [hpf] */
@StrudelDsl
val StrudelPattern.hp by dslPatternExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/** Alias for [hpf] */
@StrudelDsl
val hp by dslFunction { args, callInfo -> hpf(args, callInfo) }

/** Alias for [hpf] on a string */
@StrudelDsl
val String.hp by dslStringExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/** Alias for [hpf] */
@StrudelDsl
val StrudelPattern.hcutoff by dslPatternExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/** Alias for [hpf] */
@StrudelDsl
val hcutoff by dslFunction { args, callInfo -> hpf(args, callInfo) }

/** Alias for [hpf] on a string */
@StrudelDsl
val String.hcutoff by dslStringExtension { p, args, callInfo -> p.hpf(args, callInfo) }

// -- bandf() / bpf() --------------------------------------------------------------------------------------------------

private val bandfMutation = voiceModifier {
    copy(bandf = it?.asDoubleOrNull())
}

private fun applyBandf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = bandfMutation,
        getValue = { bandf },
        setValue = { v, _ -> copy(bandf = v) },
    )
}

/** Adds a Band Pass Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.bandf by dslPatternExtension { p, args, /* callInfo */ _ -> applyBandf(p, args) }

/** Adds a Band Pass Filter with the given cutoff frequency. */
@StrudelDsl
val bandf by dslFunction { args, /* callInfo */ _ -> args.toPattern(bandfMutation) }

/** Adds a Band Pass Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.bandf by dslStringExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/** Alias for [bandf] */
@StrudelDsl
val StrudelPattern.bpf by dslPatternExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/** Alias for [bandf] */
@StrudelDsl
val bpf by dslFunction { args, callInfo -> bandf(args, callInfo) }

/** Alias for [bandf] on a string */
@StrudelDsl
val String.bpf by dslStringExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/** Alias for [bandf] */
@StrudelDsl
val StrudelPattern.bp by dslPatternExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/** Alias for [bandf] */
@StrudelDsl
val bp by dslFunction { args, callInfo -> bandf(args, callInfo) }

/** Alias for [bandf] on a string */
@StrudelDsl
val String.bp by dslStringExtension { p, args, callInfo -> p.bandf(args, callInfo) }

// -- notchf() ---------------------------------------------------------------------------------------------------------

private val notchfMutation = voiceModifier {
    copy(notchf = it?.asDoubleOrNull())
}

private fun applyNotchf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = notchfMutation,
        getValue = { notchf },
        setValue = { v, _ -> copy(notchf = v) },
    )
}

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val StrudelPattern.notchf by dslPatternExtension { p, args, /* callInfo */ _ -> applyNotchf(p, args) }

/** Adds a Notch Filter with the given cutoff frequency. */
@StrudelDsl
val notchf by dslFunction { args, /* callInfo */ _ -> args.toPattern(notchfMutation) }

/** Adds a Notch Filter with the given cutoff frequency on a string. */
@StrudelDsl
val String.notchf by dslStringExtension { p, args, callInfo -> p.notchf(args, callInfo) }

// -- resonance() / res() - Low Pass Filter resonance -----------------------------------------------------------------

private val resonanceMutation = voiceModifier {
    copy(resonance = it?.asDoubleOrNull())
}

private fun applyResonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = resonanceMutation,
        getValue = { resonance },
        setValue = { v, _ -> copy(resonance = v) }
    )
}

/** Sets the low pass filter resonance/Q. */
@StrudelDsl
val StrudelPattern.resonance by dslPatternExtension { p, args, /* callInfo */ _ -> applyResonance(p, args) }

/** Sets the low pass filter resonance/Q. */
@StrudelDsl
val resonance by dslFunction { args, /* callInfo */ _ -> args.toPattern(resonanceMutation) }

/** Sets the low pass filter resonance/Q on a string. */
@StrudelDsl
val String.resonance by dslStringExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/** Alias for [resonance] */
@StrudelDsl
val StrudelPattern.res by dslPatternExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/** Alias for [resonance] */
@StrudelDsl
val res by dslFunction { args, callInfo -> resonance(args, callInfo) }

/** Alias for [resonance] on a string */
@StrudelDsl
val String.res by dslStringExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/** Alias for [resonance] */
@StrudelDsl
val StrudelPattern.lpq by dslPatternExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/** Alias for [resonance] */
@StrudelDsl
val lpq by dslFunction { args, callInfo -> resonance(args, callInfo) }

/** Alias for [resonance] on a string */
@StrudelDsl
val String.lpq by dslStringExtension { p, args, callInfo -> p.resonance(args, callInfo) }

// -- hresonance() / hres() - High Pass Filter resonance --------------------------------------------------------------

private val hresonanceMutation = voiceModifier {
    copy(hresonance = it?.asDoubleOrNull())
}

private fun applyHresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = hresonanceMutation,
        getValue = { hresonance },
        setValue = { v, _ -> copy(hresonance = v) }
    )
}

/** Sets the high pass filter resonance/Q. */
@StrudelDsl
val StrudelPattern.hresonance by dslPatternExtension { p, args, /* callInfo */ _ -> applyHresonance(p, args) }

/** Sets the high pass filter resonance/Q. */
@StrudelDsl
val hresonance by dslFunction { args, /* callInfo */ _ -> args.toPattern(hresonanceMutation) }

/** Sets the high pass filter resonance/Q on a string. */
@StrudelDsl
val String.hresonance by dslStringExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/** Alias for [hresonance] */
@StrudelDsl
val StrudelPattern.hres by dslPatternExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/** Alias for [hresonance] */
@StrudelDsl
val hres by dslFunction { args, callInfo -> hresonance(args, callInfo) }

/** Alias for [hresonance] on a string */
@StrudelDsl
val String.hres by dslStringExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/** Alias for [hresonance] */
@StrudelDsl
val StrudelPattern.hpq by dslPatternExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/** Alias for [hresonance] */
@StrudelDsl
val hpq by dslFunction { args, callInfo -> hresonance(args, callInfo) }

/** Alias for [hresonance] on a string */
@StrudelDsl
val String.hpq by dslStringExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

// -- bandq() - Band Pass Filter resonance ----------------------------------------------------------------------------

private val bandqMutation = voiceModifier {
    copy(bandq = it?.asDoubleOrNull())
}

private fun applyBandq(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = bandqMutation,
        getValue = { bandq },
        setValue = { v, _ -> copy(bandq = v) }
    )
}

/** Sets the band pass filter Q (resonance). */
@StrudelDsl
val StrudelPattern.bandq by dslPatternExtension { p, args, /* callInfo */ _ -> applyBandq(p, args) }

/** Sets the band pass filter Q (resonance). */
@StrudelDsl
val bandq by dslFunction { args, /* callInfo */ _ -> args.toPattern(bandqMutation) }

/** Sets the band pass filter Q (resonance) on a string. */
@StrudelDsl
val String.bandq by dslStringExtension { p, args, callInfo -> p.bandq(args, callInfo) }

/** Alias for [bandq] */
@StrudelDsl
val StrudelPattern.bpq by dslPatternExtension { p, args, callInfo -> p.bandq(args, callInfo) }

/** Alias for [bandq] */
@StrudelDsl
val bpq by dslFunction { args, callInfo -> bandq(args, callInfo) }

/** Alias for [bandq] on a string */
@StrudelDsl
val String.bpq by dslStringExtension { p, args, callInfo -> p.bandq(args, callInfo) }

// -- nresonance() / nres() - Notch Filter resonance ------------------------------------------------------------------

private val nresonanceMutation = voiceModifier {
    copy(nresonance = it?.asDoubleOrNull())
}

private fun applyNresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = nresonanceMutation,
        getValue = { nresonance },
        setValue = { v, _ -> copy(nresonance = v) }
    )
}

/** Sets the notch filter resonance/Q. */
@StrudelDsl
val StrudelPattern.nresonance by dslPatternExtension { p, args, /* callInfo */ _ -> applyNresonance(p, args) }

/** Sets the notch filter resonance/Q. */
@StrudelDsl
val nresonance by dslFunction { args, /* callInfo */ _ -> args.toPattern(nresonanceMutation) }

/** Sets the notch filter resonance/Q on a string. */
@StrudelDsl
val String.nresonance by dslStringExtension { p, args, callInfo -> p.nresonance(args, callInfo) }

/** Alias for [nresonance] */
@StrudelDsl
val StrudelPattern.nres by dslPatternExtension { p, args, callInfo -> p.nresonance(args, callInfo) }

/** Alias for [nresonance] */
@StrudelDsl
val nres by dslFunction { args, callInfo -> nresonance(args, callInfo) }

/** Alias for [nresonance] on a string */
@StrudelDsl
val String.nres by dslStringExtension { p, args, callInfo -> p.nresonance(args, callInfo) }

// -- lpattack() - Low Pass Filter Envelope Attack -----------------------------------------------------------------------

private val lpattackMutation = voiceModifier {
    copy(lpattack = it?.asDoubleOrNull())
}

private fun applyLpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = lpattackMutation,
        getValue = { lpattack },
        setValue = { v, _ -> copy(lpattack = v) },
    )
}

/** Sets the low pass filter envelope attack time. */
@StrudelDsl
val StrudelPattern.lpattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpattack(p, args) }

/** Sets the low pass filter envelope attack time. */
@StrudelDsl
val lpattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpattackMutation) }

/** Sets the low pass filter envelope attack time on a string. */
@StrudelDsl
val String.lpattack by dslStringExtension { p, args, callInfo -> p.lpattack(args, callInfo) }

/** Alias for [lpattack] */
@StrudelDsl
val StrudelPattern.lpa by dslPatternExtension { p, args, callInfo -> p.lpattack(args, callInfo) }

/** Alias for [lpattack] */
@StrudelDsl
val lpa by dslFunction { args, callInfo -> lpattack(args, callInfo) }

/** Alias for [lpattack] on a string */
@StrudelDsl
val String.lpa by dslStringExtension { p, args, callInfo -> p.lpattack(args, callInfo) }

// -- lpdecay() - Low Pass Filter Envelope Decay -------------------------------------------------------------------------

private val lpdecayMutation = voiceModifier {
    copy(lpdecay = it?.asDoubleOrNull())
}

private fun applyLpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = lpdecayMutation,
        getValue = { lpdecay },
        setValue = { v, _ -> copy(lpdecay = v) },
    )
}

/** Sets the low pass filter envelope decay time. */
@StrudelDsl
val StrudelPattern.lpdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpdecay(p, args) }

/** Sets the low pass filter envelope decay time. */
@StrudelDsl
val lpdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpdecayMutation) }

/** Sets the low pass filter envelope decay time on a string. */
@StrudelDsl
val String.lpdecay by dslStringExtension { p, args, callInfo -> p.lpdecay(args, callInfo) }

/** Alias for [lpdecay] */
@StrudelDsl
val StrudelPattern.lpd by dslPatternExtension { p, args, callInfo -> p.lpdecay(args, callInfo) }

/** Alias for [lpdecay] */
@StrudelDsl
val lpd by dslFunction { args, callInfo -> lpdecay(args, callInfo) }

/** Alias for [lpdecay] on a string */
@StrudelDsl
val String.lpd by dslStringExtension { p, args, callInfo -> p.lpdecay(args, callInfo) }

// -- lpsustain() - Low Pass Filter Envelope Sustain ---------------------------------------------------------------------

private val lpsustainMutation = voiceModifier {
    copy(lpsustain = it?.asDoubleOrNull())
}

private fun applyLpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = lpsustainMutation,
        getValue = { lpsustain },
        setValue = { v, _ -> copy(lpsustain = v) },
    )
}

/** Sets the low pass filter envelope sustain level. */
@StrudelDsl
val StrudelPattern.lpsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpsustain(p, args) }

/** Sets the low pass filter envelope sustain level. */
@StrudelDsl
val lpsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpsustainMutation) }

/** Sets the low pass filter envelope sustain level on a string. */
@StrudelDsl
val String.lpsustain by dslStringExtension { p, args, callInfo -> p.lpsustain(args, callInfo) }

/** Alias for [lpsustain] */
@StrudelDsl
val StrudelPattern.lps by dslPatternExtension { p, args, callInfo -> p.lpsustain(args, callInfo) }

/** Alias for [lpsustain] */
@StrudelDsl
val lps by dslFunction { args, callInfo -> lpsustain(args, callInfo) }

/** Alias for [lpsustain] on a string */
@StrudelDsl
val String.lps by dslStringExtension { p, args, callInfo -> p.lpsustain(args, callInfo) }

// -- lprelease() - Low Pass Filter Envelope Release ---------------------------------------------------------------------

private val lpreleaseMutation = voiceModifier {
    copy(lprelease = it?.asDoubleOrNull())
}

private fun applyLprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = lpreleaseMutation,
        getValue = { lprelease },
        setValue = { v, _ -> copy(lprelease = v) },
    )
}

/** Sets the low pass filter envelope release time. */
@StrudelDsl
val StrudelPattern.lprelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyLprelease(p, args) }

/** Sets the low pass filter envelope release time. */
@StrudelDsl
val lprelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpreleaseMutation) }

/** Sets the low pass filter envelope release time on a string. */
@StrudelDsl
val String.lprelease by dslStringExtension { p, args, callInfo -> p.lprelease(args, callInfo) }

/** Alias for [lprelease] */
@StrudelDsl
val StrudelPattern.lpr by dslPatternExtension { p, args, callInfo -> p.lprelease(args, callInfo) }

/** Alias for [lprelease] */
@StrudelDsl
val lpr by dslFunction { args, callInfo -> lprelease(args, callInfo) }

/** Alias for [lprelease] on a string */
@StrudelDsl
val String.lpr by dslStringExtension { p, args, callInfo -> p.lprelease(args, callInfo) }

// -- lpenv() - Low Pass Filter Envelope Depth ---------------------------------------------------------------------------

private val lpenvMutation = voiceModifier {
    copy(lpenv = it?.asDoubleOrNull())
}

private fun applyLpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = lpenvMutation,
        getValue = { lpenv },
        setValue = { v, _ -> copy(lpenv = v) },
    )
}

/** Sets the low pass filter envelope depth/amount. */
@StrudelDsl
val StrudelPattern.lpenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpenv(p, args) }

/** Sets the low pass filter envelope depth/amount. */
@StrudelDsl
val lpenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpenvMutation) }

/** Sets the low pass filter envelope depth/amount on a string. */
@StrudelDsl
val String.lpenv by dslStringExtension { p, args, callInfo -> p.lpenv(args, callInfo) }

/** Alias for [lpenv] */
@StrudelDsl
val StrudelPattern.lpe by dslPatternExtension { p, args, callInfo -> p.lpenv(args, callInfo) }

/** Alias for [lpenv] */
@StrudelDsl
val lpe by dslFunction { args, callInfo -> lpenv(args, callInfo) }

/** Alias for [lpenv] on a string */
@StrudelDsl
val String.lpe by dslStringExtension { p, args, callInfo -> p.lpenv(args, callInfo) }
