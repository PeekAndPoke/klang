@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.liftNumericField

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
    return source.liftNumericField(args, lpfMutation)
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
    return source.liftNumericField(args, hpfMutation)
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
    return source.liftNumericField(args, bandfMutation)
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
    return source.liftNumericField(args, notchfMutation)
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
    return source.liftNumericField(args, lpattackMutation)
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
    return source.liftNumericField(args, lpdecayMutation)
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
    return source.liftNumericField(args, lpsustainMutation)
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
    return source.liftNumericField(args, lpreleaseMutation)
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
    return source.liftNumericField(args, lpenvMutation)
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

// -- hpattack() - High Pass Filter Envelope Attack ----------------------------------------------------------------------

private val hpattackMutation = voiceModifier {
    copy(hpattack = it?.asDoubleOrNull())
}

private fun applyHpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, hpattackMutation)
}

/** Sets the high pass filter envelope attack time. */
@StrudelDsl
val StrudelPattern.hpattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpattack(p, args) }

/** Sets the high pass filter envelope attack time. */
@StrudelDsl
val hpattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpattackMutation) }

/** Sets the high pass filter envelope attack time on a string. */
@StrudelDsl
val String.hpattack by dslStringExtension { p, args, callInfo -> p.hpattack(args, callInfo) }

/** Alias for [hpattack] */
@StrudelDsl
val StrudelPattern.hpa by dslPatternExtension { p, args, callInfo -> p.hpattack(args, callInfo) }

/** Alias for [hpattack] */
@StrudelDsl
val hpa by dslFunction { args, callInfo -> hpattack(args, callInfo) }

/** Alias for [hpattack] on a string */
@StrudelDsl
val String.hpa by dslStringExtension { p, args, callInfo -> p.hpattack(args, callInfo) }

// -- hpdecay() - High Pass Filter Envelope Decay ------------------------------------------------------------------------

private val hpdecayMutation = voiceModifier {
    copy(hpdecay = it?.asDoubleOrNull())
}

private fun applyHpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, hpdecayMutation)
}

/** Sets the high pass filter envelope decay time. */
@StrudelDsl
val StrudelPattern.hpdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpdecay(p, args) }

/** Sets the high pass filter envelope decay time. */
@StrudelDsl
val hpdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpdecayMutation) }

/** Sets the high pass filter envelope decay time on a string. */
@StrudelDsl
val String.hpdecay by dslStringExtension { p, args, callInfo -> p.hpdecay(args, callInfo) }

/** Alias for [hpdecay] */
@StrudelDsl
val StrudelPattern.hpd by dslPatternExtension { p, args, callInfo -> p.hpdecay(args, callInfo) }

/** Alias for [hpdecay] */
@StrudelDsl
val hpd by dslFunction { args, callInfo -> hpdecay(args, callInfo) }

/** Alias for [hpdecay] on a string */
@StrudelDsl
val String.hpd by dslStringExtension { p, args, callInfo -> p.hpdecay(args, callInfo) }

// -- hpsustain() - High Pass Filter Envelope Sustain --------------------------------------------------------------------

private val hpsustainMutation = voiceModifier {
    copy(hpsustain = it?.asDoubleOrNull())
}

private fun applyHpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, hpsustainMutation)
}

/** Sets the high pass filter envelope sustain level. */
@StrudelDsl
val StrudelPattern.hpsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpsustain(p, args) }

/** Sets the high pass filter envelope sustain level. */
@StrudelDsl
val hpsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpsustainMutation) }

/** Sets the high pass filter envelope sustain level on a string. */
@StrudelDsl
val String.hpsustain by dslStringExtension { p, args, callInfo -> p.hpsustain(args, callInfo) }

/** Alias for [hpsustain] */
@StrudelDsl
val StrudelPattern.hps by dslPatternExtension { p, args, callInfo -> p.hpsustain(args, callInfo) }

/** Alias for [hpsustain] */
@StrudelDsl
val hps by dslFunction { args, callInfo -> hpsustain(args, callInfo) }

/** Alias for [hpsustain] on a string */
@StrudelDsl
val String.hps by dslStringExtension { p, args, callInfo -> p.hpsustain(args, callInfo) }

// -- hprelease() - High Pass Filter Envelope Release --------------------------------------------------------------------

private val hpreleaseMutation = voiceModifier {
    copy(hprelease = it?.asDoubleOrNull())
}

private fun applyHprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, hpreleaseMutation)
}

/** Sets the high pass filter envelope release time. */
@StrudelDsl
val StrudelPattern.hprelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyHprelease(p, args) }

/** Sets the high pass filter envelope release time. */
@StrudelDsl
val hprelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpreleaseMutation) }

/** Sets the high pass filter envelope release time on a string. */
@StrudelDsl
val String.hprelease by dslStringExtension { p, args, callInfo -> p.hprelease(args, callInfo) }

/** Alias for [hprelease] */
@StrudelDsl
val StrudelPattern.hpr by dslPatternExtension { p, args, callInfo -> p.hprelease(args, callInfo) }

/** Alias for [hprelease] */
@StrudelDsl
val hpr by dslFunction { args, callInfo -> hprelease(args, callInfo) }

/** Alias for [hprelease] on a string */
@StrudelDsl
val String.hpr by dslStringExtension { p, args, callInfo -> p.hprelease(args, callInfo) }

// -- hpenv() - High Pass Filter Envelope Depth --------------------------------------------------------------------------

private val hpenvMutation = voiceModifier {
    copy(hpenv = it?.asDoubleOrNull())
}

private fun applyHpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, hpenvMutation)
}

/** Sets the high pass filter envelope depth/amount. */
@StrudelDsl
val StrudelPattern.hpenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpenv(p, args) }

/** Sets the high pass filter envelope depth/amount. */
@StrudelDsl
val hpenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpenvMutation) }

/** Sets the high pass filter envelope depth/amount on a string. */
@StrudelDsl
val String.hpenv by dslStringExtension { p, args, callInfo -> p.hpenv(args, callInfo) }

/** Alias for [hpenv] */
@StrudelDsl
val StrudelPattern.hpe by dslPatternExtension { p, args, callInfo -> p.hpenv(args, callInfo) }

/** Alias for [hpenv] */
@StrudelDsl
val hpe by dslFunction { args, callInfo -> hpenv(args, callInfo) }

/** Alias for [hpenv] on a string */
@StrudelDsl
val String.hpe by dslStringExtension { p, args, callInfo -> p.hpenv(args, callInfo) }

// -- bpattack() - Band Pass Filter Envelope Attack ----------------------------------------------------------------------

private val bpattackMutation = voiceModifier {
    copy(bpattack = it?.asDoubleOrNull())
}

private fun applyBpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, bpattackMutation)
}

/** Sets the band pass filter envelope attack time. */
@StrudelDsl
val StrudelPattern.bpattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpattack(p, args) }

/** Sets the band pass filter envelope attack time. */
@StrudelDsl
val bpattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpattackMutation) }

/** Sets the band pass filter envelope attack time on a string. */
@StrudelDsl
val String.bpattack by dslStringExtension { p, args, callInfo -> p.bpattack(args, callInfo) }

/** Alias for [bpattack] */
@StrudelDsl
val StrudelPattern.bpa by dslPatternExtension { p, args, callInfo -> p.bpattack(args, callInfo) }

/** Alias for [bpattack] */
@StrudelDsl
val bpa by dslFunction { args, callInfo -> bpattack(args, callInfo) }

/** Alias for [bpattack] on a string */
@StrudelDsl
val String.bpa by dslStringExtension { p, args, callInfo -> p.bpattack(args, callInfo) }

// -- bpdecay() - Band Pass Filter Envelope Decay ------------------------------------------------------------------------

private val bpdecayMutation = voiceModifier {
    copy(bpdecay = it?.asDoubleOrNull())
}

private fun applyBpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, bpdecayMutation)
}

/** Sets the band pass filter envelope decay time. */
@StrudelDsl
val StrudelPattern.bpdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpdecay(p, args) }

/** Sets the band pass filter envelope decay time. */
@StrudelDsl
val bpdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpdecayMutation) }

/** Sets the band pass filter envelope decay time on a string. */
@StrudelDsl
val String.bpdecay by dslStringExtension { p, args, callInfo -> p.bpdecay(args, callInfo) }

/** Alias for [bpdecay] */
@StrudelDsl
val StrudelPattern.bpd by dslPatternExtension { p, args, callInfo -> p.bpdecay(args, callInfo) }

/** Alias for [bpdecay] */
@StrudelDsl
val bpd by dslFunction { args, callInfo -> bpdecay(args, callInfo) }

/** Alias for [bpdecay] on a string */
@StrudelDsl
val String.bpd by dslStringExtension { p, args, callInfo -> p.bpdecay(args, callInfo) }

// -- bpsustain() - Band Pass Filter Envelope Sustain --------------------------------------------------------------------

private val bpsustainMutation = voiceModifier {
    copy(bpsustain = it?.asDoubleOrNull())
}

private fun applyBpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, bpsustainMutation)
}

/** Sets the band pass filter envelope sustain level. */
@StrudelDsl
val StrudelPattern.bpsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpsustain(p, args) }

/** Sets the band pass filter envelope sustain level. */
@StrudelDsl
val bpsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpsustainMutation) }

/** Sets the band pass filter envelope sustain level on a string. */
@StrudelDsl
val String.bpsustain by dslStringExtension { p, args, callInfo -> p.bpsustain(args, callInfo) }

/** Alias for [bpsustain] */
@StrudelDsl
val StrudelPattern.bps by dslPatternExtension { p, args, callInfo -> p.bpsustain(args, callInfo) }

/** Alias for [bpsustain] */
@StrudelDsl
val bps by dslFunction { args, callInfo -> bpsustain(args, callInfo) }

/** Alias for [bpsustain] on a string */
@StrudelDsl
val String.bps by dslStringExtension { p, args, callInfo -> p.bpsustain(args, callInfo) }

// -- bprelease() - Band Pass Filter Envelope Release --------------------------------------------------------------------

private val bpreleaseMutation = voiceModifier {
    copy(bprelease = it?.asDoubleOrNull())
}

private fun applyBprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, bpreleaseMutation)
}

/** Sets the band pass filter envelope release time. */
@StrudelDsl
val StrudelPattern.bprelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyBprelease(p, args) }

/** Sets the band pass filter envelope release time. */
@StrudelDsl
val bprelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpreleaseMutation) }

/** Sets the band pass filter envelope release time on a string. */
@StrudelDsl
val String.bprelease by dslStringExtension { p, args, callInfo -> p.bprelease(args, callInfo) }

/** Alias for [bprelease] */
@StrudelDsl
val StrudelPattern.bpr by dslPatternExtension { p, args, callInfo -> p.bprelease(args, callInfo) }

/** Alias for [bprelease] */
@StrudelDsl
val bpr by dslFunction { args, callInfo -> bprelease(args, callInfo) }

/** Alias for [bprelease] on a string */
@StrudelDsl
val String.bpr by dslStringExtension { p, args, callInfo -> p.bprelease(args, callInfo) }

// -- bpenv() - Band Pass Filter Envelope Depth --------------------------------------------------------------------------

private val bpenvMutation = voiceModifier {
    copy(bpenv = it?.asDoubleOrNull())
}

private fun applyBpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, bpenvMutation)
}

/** Sets the band pass filter envelope depth/amount. */
@StrudelDsl
val StrudelPattern.bpenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpenv(p, args) }

/** Sets the band pass filter envelope depth/amount. */
@StrudelDsl
val bpenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpenvMutation) }

/** Sets the band pass filter envelope depth/amount on a string. */
@StrudelDsl
val String.bpenv by dslStringExtension { p, args, callInfo -> p.bpenv(args, callInfo) }

/** Alias for [bpenv] */
@StrudelDsl
val StrudelPattern.bpe by dslPatternExtension { p, args, callInfo -> p.bpenv(args, callInfo) }

/** Alias for [bpenv] */
@StrudelDsl
val bpe by dslFunction { args, callInfo -> bpenv(args, callInfo) }

/** Alias for [bpenv] on a string */
@StrudelDsl
val String.bpe by dslStringExtension { p, args, callInfo -> p.bpenv(args, callInfo) }

// -- nfattack() - Notch Filter Envelope Attack (NOT IN ORIGINAL STRUDEL) -------------------------------------------------

private val nfattackMutation = voiceModifier {
    copy(nfattack = it?.asDoubleOrNull())
}

private fun applyNfattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, nfattackMutation)
}

/** Sets the notch filter envelope attack time (not in original Strudel JS implementation). */
@StrudelDsl
val StrudelPattern.nfattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfattack(p, args) }

/** Sets the notch filter envelope attack time (not in original Strudel JS implementation). */
@StrudelDsl
val nfattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfattackMutation) }

/** Sets the notch filter envelope attack time on a string (not in original Strudel JS implementation). */
@StrudelDsl
val String.nfattack by dslStringExtension { p, args, callInfo -> p.nfattack(args, callInfo) }

/** Alias for [nfattack] */
@StrudelDsl
val StrudelPattern.nfa by dslPatternExtension { p, args, callInfo -> p.nfattack(args, callInfo) }

/** Alias for [nfattack] */
@StrudelDsl
val nfa by dslFunction { args, callInfo -> nfattack(args, callInfo) }

/** Alias for [nfattack] on a string */
@StrudelDsl
val String.nfa by dslStringExtension { p, args, callInfo -> p.nfattack(args, callInfo) }

// -- nfdecay() - Notch Filter Envelope Decay (NOT IN ORIGINAL STRUDEL) ---------------------------------------------------

private val nfdecayMutation = voiceModifier {
    copy(nfdecay = it?.asDoubleOrNull())
}

private fun applyNfdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, nfdecayMutation)
}

/** Sets the notch filter envelope decay time (not in original Strudel JS implementation). */
@StrudelDsl
val StrudelPattern.nfdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfdecay(p, args) }

/** Sets the notch filter envelope decay time (not in original Strudel JS implementation). */
@StrudelDsl
val nfdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfdecayMutation) }

/** Sets the notch filter envelope decay time on a string (not in original Strudel JS implementation). */
@StrudelDsl
val String.nfdecay by dslStringExtension { p, args, callInfo -> p.nfdecay(args, callInfo) }

/** Alias for [nfdecay] */
@StrudelDsl
val StrudelPattern.nfd by dslPatternExtension { p, args, callInfo -> p.nfdecay(args, callInfo) }

/** Alias for [nfdecay] */
@StrudelDsl
val nfd by dslFunction { args, callInfo -> nfdecay(args, callInfo) }

/** Alias for [nfdecay] on a string */
@StrudelDsl
val String.nfd by dslStringExtension { p, args, callInfo -> p.nfdecay(args, callInfo) }

// -- nfsustain() - Notch Filter Envelope Sustain (NOT IN ORIGINAL STRUDEL) -----------------------------------------------

private val nfsustainMutation = voiceModifier {
    copy(nfsustain = it?.asDoubleOrNull())
}

private fun applyNfsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, nfsustainMutation)
}

/** Sets the notch filter envelope sustain level (not in original Strudel JS implementation). */
@StrudelDsl
val StrudelPattern.nfsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfsustain(p, args) }

/** Sets the notch filter envelope sustain level (not in original Strudel JS implementation). */
@StrudelDsl
val nfsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfsustainMutation) }

/** Sets the notch filter envelope sustain level on a string (not in original Strudel JS implementation). */
@StrudelDsl
val String.nfsustain by dslStringExtension { p, args, callInfo -> p.nfsustain(args, callInfo) }

/** Alias for [nfsustain] */
@StrudelDsl
val StrudelPattern.nfs by dslPatternExtension { p, args, callInfo -> p.nfsustain(args, callInfo) }

/** Alias for [nfsustain] */
@StrudelDsl
val nfs by dslFunction { args, callInfo -> nfsustain(args, callInfo) }

/** Alias for [nfsustain] on a string */
@StrudelDsl
val String.nfs by dslStringExtension { p, args, callInfo -> p.nfsustain(args, callInfo) }

// -- nfrelease() - Notch Filter Envelope Release (NOT IN ORIGINAL STRUDEL) -----------------------------------------------

private val nfreleaseMutation = voiceModifier {
    copy(nfrelease = it?.asDoubleOrNull())
}

private fun applyNfrelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, nfreleaseMutation)
}

/** Sets the notch filter envelope release time (not in original Strudel JS implementation). */
@StrudelDsl
val StrudelPattern.nfrelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfrelease(p, args) }

/** Sets the notch filter envelope release time (not in original Strudel JS implementation). */
@StrudelDsl
val nfrelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfreleaseMutation) }

/** Sets the notch filter envelope release time on a string (not in original Strudel JS implementation). */
@StrudelDsl
val String.nfrelease by dslStringExtension { p, args, callInfo -> p.nfrelease(args, callInfo) }

/** Alias for [nfrelease] */
@StrudelDsl
val StrudelPattern.nfr by dslPatternExtension { p, args, callInfo -> p.nfrelease(args, callInfo) }

/** Alias for [nfrelease] */
@StrudelDsl
val nfr by dslFunction { args, callInfo -> nfrelease(args, callInfo) }

/** Alias for [nfrelease] on a string */
@StrudelDsl
val String.nfr by dslStringExtension { p, args, callInfo -> p.nfrelease(args, callInfo) }

// -- nfenv() - Notch Filter Envelope Depth (NOT IN ORIGINAL STRUDEL) -----------------------------------------------------

private val nfenvMutation = voiceModifier {
    copy(nfenv = it?.asDoubleOrNull())
}

private fun applyNfenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.liftNumericField(args, nfenvMutation)
}

/** Sets the notch filter envelope depth/amount (not in original Strudel JS implementation). */
@StrudelDsl
val StrudelPattern.nfenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfenv(p, args) }

/** Sets the notch filter envelope depth/amount (not in original Strudel JS implementation). */
@StrudelDsl
val nfenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfenvMutation) }

/** Sets the notch filter envelope depth/amount on a string (not in original Strudel JS implementation). */
@StrudelDsl
val String.nfenv by dslStringExtension { p, args, callInfo -> p.nfenv(args, callInfo) }

/** Alias for [nfenv] */
@StrudelDsl
val StrudelPattern.nfe by dslPatternExtension { p, args, callInfo -> p.nfenv(args, callInfo) }

/** Alias for [nfenv] */
@StrudelDsl
val nfe by dslFunction { args, callInfo -> nfenv(args, callInfo) }

/** Alias for [nfenv] on a string */
@StrudelDsl
val String.nfe by dslStringExtension { p, args, callInfo -> p.nfenv(args, callInfo) }
