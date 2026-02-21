@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._liftNumericField

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangFiltersInit = false

// -- lpf() ------------------------------------------------------------------------------------------------------------

private val lpfMutation = voiceModifier { copy(cutoff = it?.asDoubleOrNull()) }

fun applyLpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpfMutation)
}

/** Applies a Low Pass Filter to this pattern with the given cutoff frequency in Hz. */
@StrudelDsl
val StrudelPattern.lpf by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpf(p, args) }

/**
 * Applies a Low Pass Filter (LPF) with the given cutoff frequency in Hz.
 *
 * Only frequencies below the cutoff pass through. Lower values produce a darker, more
 * muffled sound; higher values let more signal through. Use [resonance] to add emphasis
 * at the cutoff frequency.
 *
 * ```KlangScript
 * s("bd sd hh").lpf(500)             // dark, muffled sound
 * ```
 *
 * ```KlangScript
 * note("c4 e4").lpf("<200 2000>")    // alternating cutoff frequencies per cycle
 * ```
 *
 * @alias cutoff, ctf, lp
 * @category effects
 * @tags lpf, cutoff, low pass filter, filter, frequency
 */
@StrudelDsl
val lpf by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpfMutation) }

/** Applies a Low Pass Filter to a string pattern with the given cutoff frequency in Hz. */
@StrudelDsl
val String.lpf by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] on this pattern. */
@StrudelDsl
val StrudelPattern.cutoff by dslPatternExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @alias lpf, ctf, lp
 * @category effects
 * @tags cutoff, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
val cutoff by dslFunction { args, callInfo -> lpf(args, callInfo) }

/** Alias for [lpf] on a string pattern. */
@StrudelDsl
val String.cutoff by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] on this pattern. */
@StrudelDsl
val StrudelPattern.ctf by dslPatternExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @alias lpf, cutoff, lp
 * @category effects
 * @tags ctf, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
val ctf by dslFunction { args, callInfo -> lpf(args, callInfo) }

/** Alias for [lpf] on a string pattern. */
@StrudelDsl
val String.ctf by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/** Alias for [lpf] on this pattern. */
@StrudelDsl
val StrudelPattern.lp by dslPatternExtension { p, args, callInfo -> p.lpf(args, callInfo) }

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @alias lpf, cutoff, ctf
 * @category effects
 * @tags lp, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
val lp by dslFunction { args, callInfo -> lpf(args, callInfo) }

/** Alias for [lpf] on a string pattern. */
@StrudelDsl
val String.lp by dslStringExtension { p, args, callInfo -> p.lpf(args, callInfo) }

// -- hpf() ------------------------------------------------------------------------------------------------------------

private val hpfMutation = voiceModifier { copy(hcutoff = it?.asDoubleOrNull()) }

fun applyHpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpfMutation)
}

/** Applies a High Pass Filter to this pattern with the given cutoff frequency in Hz. */
@StrudelDsl
val StrudelPattern.hpf by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpf(p, args) }

/**
 * Applies a High Pass Filter (HPF) with the given cutoff frequency in Hz.
 *
 * Only frequencies above the cutoff pass through. Higher values produce a thinner, brighter
 * sound by removing low-frequency content. Use [hresonance] to add emphasis at the cutoff.
 *
 * ```KlangScript
 * s("bd sd").hpf(300)              // removes bass frequencies, thin sound
 * ```
 *
 * ```KlangScript
 * note("c4 e4").hpf("<100 800>")   // alternating high-pass cutoffs per cycle
 * ```
 *
 * @alias hp, hcutoff
 * @category effects
 * @tags hpf, hcutoff, high pass filter, filter, frequency
 */
@StrudelDsl
val hpf by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpfMutation) }

/** Applies a High Pass Filter to a string pattern with the given cutoff frequency in Hz. */
@StrudelDsl
val String.hpf by dslStringExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/** Alias for [hpf] on this pattern. */
@StrudelDsl
val StrudelPattern.hp by dslPatternExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @alias hpf, hcutoff
 * @category effects
 * @tags hp, hpf, high pass filter, filter, frequency
 */
@StrudelDsl
val hp by dslFunction { args, callInfo -> hpf(args, callInfo) }

/** Alias for [hpf] on a string pattern. */
@StrudelDsl
val String.hp by dslStringExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/** Alias for [hpf] on this pattern. */
@StrudelDsl
val StrudelPattern.hcutoff by dslPatternExtension { p, args, callInfo -> p.hpf(args, callInfo) }

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @alias hpf, hp
 * @category effects
 * @tags hcutoff, hpf, high pass filter, filter, frequency
 */
@StrudelDsl
val hcutoff by dslFunction { args, callInfo -> hpf(args, callInfo) }

/** Alias for [hpf] on a string pattern. */
@StrudelDsl
val String.hcutoff by dslStringExtension { p, args, callInfo -> p.hpf(args, callInfo) }

// -- bandf() / bpf() --------------------------------------------------------------------------------------------------

private val bandfMutation = voiceModifier { copy(bandf = it?.asDoubleOrNull()) }

fun applyBandf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bandfMutation)
}

/** Applies a Band Pass Filter to this pattern with the given centre frequency in Hz. */
@StrudelDsl
val StrudelPattern.bandf by dslPatternExtension { p, args, /* callInfo */ _ -> applyBandf(p, args) }

/**
 * Applies a Band Pass Filter (BPF) with the given centre frequency in Hz.
 *
 * Only a band of frequencies around the centre frequency passes through. Use [bandq] to
 * control the bandwidth (Q factor); higher Q values create a narrower band.
 *
 * ```KlangScript
 * s("sd").bandf(1000)               // emphasise mid-range around 1 kHz
 * ```
 *
 * ```KlangScript
 * note("c4").bandf("<500 2000>")    // alternating band-pass centre per cycle
 * ```
 *
 * @alias bpf, bp
 * @category effects
 * @tags bandf, bpf, band pass filter, filter, frequency
 */
@StrudelDsl
val bandf by dslFunction { args, /* callInfo */ _ -> args.toPattern(bandfMutation) }

/** Applies a Band Pass Filter to a string pattern with the given centre frequency in Hz. */
@StrudelDsl
val String.bandf by dslStringExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/** Alias for [bandf] on this pattern. */
@StrudelDsl
val StrudelPattern.bpf by dslPatternExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @alias bandf, bp
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@StrudelDsl
val bpf by dslFunction { args, callInfo -> bandf(args, callInfo) }

/** Alias for [bandf] on a string pattern. */
@StrudelDsl
val String.bpf by dslStringExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/** Alias for [bandf] on this pattern. */
@StrudelDsl
val StrudelPattern.bp by dslPatternExtension { p, args, callInfo -> p.bandf(args, callInfo) }

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @alias bandf, bpf
 * @category effects
 * @tags bp, bandf, band pass filter, filter, frequency
 */
@StrudelDsl
val bp by dslFunction { args, callInfo -> bandf(args, callInfo) }

/** Alias for [bandf] on a string pattern. */
@StrudelDsl
val String.bp by dslStringExtension { p, args, callInfo -> p.bandf(args, callInfo) }

// -- notchf() ---------------------------------------------------------------------------------------------------------

private val notchfMutation = voiceModifier { copy(notchf = it?.asDoubleOrNull()) }

fun applyNotchf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, notchfMutation)
}

/** Applies a Notch Filter to this pattern with the given centre frequency in Hz. */
@StrudelDsl
val StrudelPattern.notchf by dslPatternExtension { p, args, /* callInfo */ _ -> applyNotchf(p, args) }

/**
 * Applies a Notch Filter with the given centre frequency in Hz.
 *
 * Attenuates a narrow band of frequencies around the centre while passing everything else.
 * This is the opposite of a band pass filter. Use [nresonance] to control the notch width.
 *
 * ```KlangScript
 * note("c4 e4").notchf(1000)           // notch out 1 kHz
 * ```
 *
 * ```KlangScript
 * s("bd sd").notchf("<500 2000>")      // alternating notch centre per cycle
 * ```
 *
 * @category effects
 * @tags notchf, notch filter, filter, frequency
 */
@StrudelDsl
val notchf by dslFunction { args, /* callInfo */ _ -> args.toPattern(notchfMutation) }

/** Applies a Notch Filter to a string pattern with the given centre frequency in Hz. */
@StrudelDsl
val String.notchf by dslStringExtension { p, args, callInfo -> p.notchf(args, callInfo) }

// -- resonance() / res() - Low Pass Filter resonance -----------------------------------------------------------------

private val resonanceMutation = voiceModifier { copy(resonance = it?.asDoubleOrNull()) }

fun applyResonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, resonanceMutation)
}

/** Sets the LPF resonance/Q on this pattern. */
@StrudelDsl
val StrudelPattern.resonance by dslPatternExtension { p, args, /* callInfo */ _ -> applyResonance(p, args) }

/**
 * Sets the resonance (Q factor) of the Low Pass Filter.
 *
 * Resonance adds emphasis (a peak) at the filter's cutoff frequency. Higher values create a
 * more pronounced ringing effect. Use with [lpf] to set the cutoff frequency.
 *
 * ```KlangScript
 * note("c4 e4").lpf(800).resonance(15)    // LPF with high resonance peak
 * ```
 *
 * ```KlangScript
 * s("bd").lpf(500).resonance("<0 20>")    // resonance sweeps from flat to peaked
 * ```
 *
 * @alias res, lpq
 * @category effects
 * @tags resonance, res, lpq, low pass filter, Q, resonance
 */
@StrudelDsl
val resonance by dslFunction { args, /* callInfo */ _ -> args.toPattern(resonanceMutation) }

/** Sets the LPF resonance/Q on a string pattern. */
@StrudelDsl
val String.resonance by dslStringExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/** Alias for [resonance] on this pattern. */
@StrudelDsl
val StrudelPattern.res by dslPatternExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @alias resonance, lpq
 * @category effects
 * @tags res, resonance, lpq, low pass filter, Q
 */
@StrudelDsl
val res by dslFunction { args, callInfo -> resonance(args, callInfo) }

/** Alias for [resonance] on a string pattern. */
@StrudelDsl
val String.res by dslStringExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/** Alias for [resonance] on this pattern. */
@StrudelDsl
val StrudelPattern.lpq by dslPatternExtension { p, args, callInfo -> p.resonance(args, callInfo) }

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @alias resonance, res
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@StrudelDsl
val lpq by dslFunction { args, callInfo -> resonance(args, callInfo) }

/** Alias for [resonance] on a string pattern. */
@StrudelDsl
val String.lpq by dslStringExtension { p, args, callInfo -> p.resonance(args, callInfo) }

// -- hresonance() / hres() - High Pass Filter resonance --------------------------------------------------------------

private val hresonanceMutation = voiceModifier { copy(hresonance = it?.asDoubleOrNull()) }

fun applyHresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hresonanceMutation)
}

/** Sets the HPF resonance/Q on this pattern. */
@StrudelDsl
val StrudelPattern.hresonance by dslPatternExtension { p, args, /* callInfo */ _ -> applyHresonance(p, args) }

/**
 * Sets the resonance (Q factor) of the High Pass Filter.
 *
 * Resonance adds emphasis at the HPF's cutoff frequency, creating a peak effect.
 * Higher values make the resonance more pronounced. Use with [hpf] to set the cutoff.
 *
 * ```KlangScript
 * note("c4").hpf(300).hresonance(15)        // HPF with strong resonance peak
 * ```
 *
 * ```KlangScript
 * s("sd").hpf(200).hresonance("<0 20>")     // resonance sweeps per cycle
 * ```
 *
 * @alias hres, hpq
 * @category effects
 * @tags hresonance, hres, hpq, high pass filter, Q, resonance
 */
@StrudelDsl
val hresonance by dslFunction { args, /* callInfo */ _ -> args.toPattern(hresonanceMutation) }

/** Sets the HPF resonance/Q on a string pattern. */
@StrudelDsl
val String.hresonance by dslStringExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/** Alias for [hresonance] on this pattern. */
@StrudelDsl
val StrudelPattern.hres by dslPatternExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @alias hresonance, hpq
 * @category effects
 * @tags hres, hresonance, hpq, high pass filter, Q
 */
@StrudelDsl
val hres by dslFunction { args, callInfo -> hresonance(args, callInfo) }

/** Alias for [hresonance] on a string pattern. */
@StrudelDsl
val String.hres by dslStringExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/** Alias for [hresonance] on this pattern. */
@StrudelDsl
val StrudelPattern.hpq by dslPatternExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @alias hresonance, hres
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@StrudelDsl
val hpq by dslFunction { args, callInfo -> hresonance(args, callInfo) }

/** Alias for [hresonance] on a string pattern. */
@StrudelDsl
val String.hpq by dslStringExtension { p, args, callInfo -> p.hresonance(args, callInfo) }

// -- bandq() - Band Pass Filter resonance ----------------------------------------------------------------------------

private val bandqMutation = voiceModifier { copy(bandq = it?.asDoubleOrNull()) }

fun applyBandq(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bandqMutation)
}

/** Sets the BPF Q (bandwidth) on this pattern. */
@StrudelDsl
val StrudelPattern.bandq by dslPatternExtension { p, args, /* callInfo */ _ -> applyBandq(p, args) }

/**
 * Sets the Q factor (bandwidth) of the Band Pass Filter.
 *
 * Higher Q values create a narrower, more selective frequency band. Lower values let a
 * wider range through. Use with [bandf] to set the centre frequency.
 *
 * ```KlangScript
 * note("c4").bandf(1000).bandq(5)         // narrow band pass at 1 kHz
 * ```
 *
 * ```KlangScript
 * s("sd").bandf(800).bandq("<1 20>")      // Q sweeps from wide to narrow
 * ```
 *
 * @alias bpq
 * @category effects
 * @tags bandq, bpq, band pass filter, Q, bandwidth
 */
@StrudelDsl
val bandq by dslFunction { args, /* callInfo */ _ -> args.toPattern(bandqMutation) }

/** Sets the BPF Q (bandwidth) on a string pattern. */
@StrudelDsl
val String.bandq by dslStringExtension { p, args, callInfo -> p.bandq(args, callInfo) }

/** Alias for [bandq] on this pattern. */
@StrudelDsl
val StrudelPattern.bpq by dslPatternExtension { p, args, callInfo -> p.bandq(args, callInfo) }

/**
 * Alias for [bandq]. Sets the BPF Q (bandwidth).
 *
 * @alias bandq
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@StrudelDsl
val bpq by dslFunction { args, callInfo -> bandq(args, callInfo) }

/** Alias for [bandq] on a string pattern. */
@StrudelDsl
val String.bpq by dslStringExtension { p, args, callInfo -> p.bandq(args, callInfo) }

// -- nresonance() / nres() - Notch Filter resonance ------------------------------------------------------------------

private val nresonanceMutation = voiceModifier { copy(nresonance = it?.asDoubleOrNull()) }

fun applyNresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nresonanceMutation)
}

/** Sets the notch filter Q on this pattern. */
@StrudelDsl
val StrudelPattern.nresonance by dslPatternExtension { p, args, /* callInfo */ _ -> applyNresonance(p, args) }

/**
 * Sets the resonance (Q factor) of the Notch Filter.
 *
 * Controls how narrow the notch is. Higher values create a narrower, deeper notch.
 * Use with [notchf] to set the notch frequency.
 *
 * ```KlangScript
 * note("c4").notchf(1000).nresonance(10)   // narrow deep notch at 1 kHz
 * ```
 *
 * ```KlangScript
 * s("bd").notchf(500).nresonance("<5 20>") // Q sweeps from wide to narrow per cycle
 * ```
 *
 * @alias nres
 * @category effects
 * @tags nresonance, nres, notch filter, Q, resonance
 */
@StrudelDsl
val nresonance by dslFunction { args, /* callInfo */ _ -> args.toPattern(nresonanceMutation) }

/** Sets the notch filter Q on a string pattern. */
@StrudelDsl
val String.nresonance by dslStringExtension { p, args, callInfo -> p.nresonance(args, callInfo) }

/** Alias for [nresonance] on this pattern. */
@StrudelDsl
val StrudelPattern.nres by dslPatternExtension { p, args, callInfo -> p.nresonance(args, callInfo) }

/**
 * Alias for [nresonance]. Sets the notch filter Q.
 *
 * @alias nresonance
 * @category effects
 * @tags nres, nresonance, notch filter, Q
 */
@StrudelDsl
val nres by dslFunction { args, callInfo -> nresonance(args, callInfo) }

/** Alias for [nresonance] on a string pattern. */
@StrudelDsl
val String.nres by dslStringExtension { p, args, callInfo -> p.nresonance(args, callInfo) }

// -- lpattack() - Low Pass Filter Envelope Attack -----------------------------------------------------------------------

private val lpattackMutation = voiceModifier { copy(lpattack = it?.asDoubleOrNull()) }

fun applyLpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpattackMutation)
}

/** Sets the LPF envelope attack time on this pattern. */
@StrudelDsl
val StrudelPattern.lpattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpattack(p, args) }

/**
 * Sets the LPF envelope attack time in seconds.
 *
 * Controls how quickly the low pass filter cutoff sweeps from its baseline to the peak
 * at note onset. Use with [lpenv], [lpdecay], [lpsustain], [lprelease].
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript
 * note("c4").lpattack("<0.01 0.5>")              // fast vs slow filter attack per cycle
 * ```
 *
 * @alias lpa
 * @category effects
 * @tags lpattack, lpa, low pass filter, envelope, attack
 */
@StrudelDsl
val lpattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpattackMutation) }

/** Sets the LPF envelope attack time on a string pattern. */
@StrudelDsl
val String.lpattack by dslStringExtension { p, args, callInfo -> p.lpattack(args, callInfo) }

/** Alias for [lpattack] on this pattern. */
@StrudelDsl
val StrudelPattern.lpa by dslPatternExtension { p, args, callInfo -> p.lpattack(args, callInfo) }

/**
 * Alias for [lpattack]. Sets the LPF envelope attack time.
 *
 * @alias lpattack
 * @category effects
 * @tags lpa, lpattack, low pass filter, envelope, attack
 */
@StrudelDsl
val lpa by dslFunction { args, callInfo -> lpattack(args, callInfo) }

/** Alias for [lpattack] on a string pattern. */
@StrudelDsl
val String.lpa by dslStringExtension { p, args, callInfo -> p.lpattack(args, callInfo) }

// -- lpdecay() - Low Pass Filter Envelope Decay -------------------------------------------------------------------------

private val lpdecayMutation = voiceModifier { copy(lpdecay = it?.asDoubleOrNull()) }

fun applyLpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpdecayMutation)
}

/** Sets the LPF envelope decay time on this pattern. */
@StrudelDsl
val StrudelPattern.lpdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpdecay(p, args) }

/**
 * Sets the LPF envelope decay time in seconds.
 *
 * Controls how quickly the filter cutoff moves from peak to sustain level after the attack.
 * Use with [lpattack], [lpsustain], [lprelease], [lpenv].
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript
 * note("c4").lpdecay("<0.05 0.5>")              // short vs long filter decay per cycle
 * ```
 *
 * @alias lpd
 * @category effects
 * @tags lpdecay, lpd, low pass filter, envelope, decay
 */
@StrudelDsl
val lpdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpdecayMutation) }

/** Sets the LPF envelope decay time on a string pattern. */
@StrudelDsl
val String.lpdecay by dslStringExtension { p, args, callInfo -> p.lpdecay(args, callInfo) }

/** Alias for [lpdecay] on this pattern. */
@StrudelDsl
val StrudelPattern.lpd by dslPatternExtension { p, args, callInfo -> p.lpdecay(args, callInfo) }

/**
 * Alias for [lpdecay]. Sets the LPF envelope decay time.
 *
 * @alias lpdecay
 * @category effects
 * @tags lpd, lpdecay, low pass filter, envelope, decay
 */
@StrudelDsl
val lpd by dslFunction { args, callInfo -> lpdecay(args, callInfo) }

/** Alias for [lpdecay] on a string pattern. */
@StrudelDsl
val String.lpd by dslStringExtension { p, args, callInfo -> p.lpdecay(args, callInfo) }

// -- lpsustain() - Low Pass Filter Envelope Sustain ---------------------------------------------------------------------

private val lpsustainMutation = voiceModifier { copy(lpsustain = it?.asDoubleOrNull()) }

fun applyLpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpsustainMutation)
}

/** Sets the LPF envelope sustain level on this pattern. */
@StrudelDsl
val StrudelPattern.lpsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpsustain(p, args) }

/**
 * Sets the LPF envelope sustain level (0–1).
 *
 * Controls the filter cutoff level during the sustained portion of the note. `1` holds
 * the filter open; `0` closes it back to baseline. Use with the lpattack/lpdecay/lprelease.
 *
 * ```KlangScript
 * note("c4").lpf(200).lpenv(4000).lpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").lpsustain("<0 1>")                     // closed vs fully open sustain
 * ```
 *
 * @alias lps
 * @category effects
 * @tags lpsustain, lps, low pass filter, envelope, sustain
 */
@StrudelDsl
val lpsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpsustainMutation) }

/** Sets the LPF envelope sustain level on a string pattern. */
@StrudelDsl
val String.lpsustain by dslStringExtension { p, args, callInfo -> p.lpsustain(args, callInfo) }

/** Alias for [lpsustain] on this pattern. */
@StrudelDsl
val StrudelPattern.lps by dslPatternExtension { p, args, callInfo -> p.lpsustain(args, callInfo) }

/**
 * Alias for [lpsustain]. Sets the LPF envelope sustain level.
 *
 * @alias lpsustain
 * @category effects
 * @tags lps, lpsustain, low pass filter, envelope, sustain
 */
@StrudelDsl
val lps by dslFunction { args, callInfo -> lpsustain(args, callInfo) }

/** Alias for [lpsustain] on a string pattern. */
@StrudelDsl
val String.lps by dslStringExtension { p, args, callInfo -> p.lpsustain(args, callInfo) }

// -- lprelease() - Low Pass Filter Envelope Release ---------------------------------------------------------------------

private val lpreleaseMutation = voiceModifier { copy(lprelease = it?.asDoubleOrNull()) }

fun applyLprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpreleaseMutation)
}

/** Sets the LPF envelope release time on this pattern. */
@StrudelDsl
val StrudelPattern.lprelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyLprelease(p, args) }

/**
 * Sets the LPF envelope release time in seconds.
 *
 * Controls how quickly the low pass filter cutoff returns to baseline after the note ends.
 * Use with [lpattack], [lpdecay], [lpsustain], [lpenv].
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(5000).lprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript
 * note("c4").lprelease("<0.05 1.0>")              // short vs long filter release per cycle
 * ```
 *
 * @alias lpr
 * @category effects
 * @tags lprelease, lpr, low pass filter, envelope, release
 */
@StrudelDsl
val lprelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpreleaseMutation) }

/** Sets the LPF envelope release time on a string pattern. */
@StrudelDsl
val String.lprelease by dslStringExtension { p, args, callInfo -> p.lprelease(args, callInfo) }

/** Alias for [lprelease] on this pattern. */
@StrudelDsl
val StrudelPattern.lpr by dslPatternExtension { p, args, callInfo -> p.lprelease(args, callInfo) }

/**
 * Alias for [lprelease]. Sets the LPF envelope release time.
 *
 * @alias lprelease
 * @category effects
 * @tags lpr, lprelease, low pass filter, envelope, release
 */
@StrudelDsl
val lpr by dslFunction { args, callInfo -> lprelease(args, callInfo) }

/** Alias for [lprelease] on a string pattern. */
@StrudelDsl
val String.lpr by dslStringExtension { p, args, callInfo -> p.lprelease(args, callInfo) }

// -- lpenv() - Low Pass Filter Envelope Depth ---------------------------------------------------------------------------

private val lpenvMutation = voiceModifier { copy(lpenv = it?.asDoubleOrNull()) }

fun applyLpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpenvMutation)
}

/** Sets the LPF envelope depth/amount on this pattern. */
@StrudelDsl
val StrudelPattern.lpenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyLpenv(p, args) }

/**
 * Sets the LPF envelope depth (modulation amount) in Hz.
 *
 * Determines how far above the base [lpf] cutoff the filter sweeps when the envelope is
 * fully open. A larger value creates a more dramatic filter sweep. Use with [lpattack],
 * [lpdecay], [lpsustain], [lprelease].
 *
 * ```KlangScript
 * s("bd").lpf(200).lpenv(4000)                // sweeps up to 4.2 kHz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").lpf(300).lpenv("<1000 8000>")    // subtle vs dramatic sweep per cycle
 * ```
 *
 * @alias lpe
 * @category effects
 * @tags lpenv, lpe, low pass filter, envelope, depth, modulation
 */
@StrudelDsl
val lpenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(lpenvMutation) }

/** Sets the LPF envelope depth/amount on a string pattern. */
@StrudelDsl
val String.lpenv by dslStringExtension { p, args, callInfo -> p.lpenv(args, callInfo) }

/** Alias for [lpenv] on this pattern. */
@StrudelDsl
val StrudelPattern.lpe by dslPatternExtension { p, args, callInfo -> p.lpenv(args, callInfo) }

/**
 * Alias for [lpenv]. Sets the LPF envelope depth.
 *
 * @alias lpenv
 * @category effects
 * @tags lpe, lpenv, low pass filter, envelope, depth, modulation
 */
@StrudelDsl
val lpe by dslFunction { args, callInfo -> lpenv(args, callInfo) }

/** Alias for [lpenv] on a string pattern. */
@StrudelDsl
val String.lpe by dslStringExtension { p, args, callInfo -> p.lpenv(args, callInfo) }

// -- hpattack() - High Pass Filter Envelope Attack ----------------------------------------------------------------------

private val hpattackMutation = voiceModifier { copy(hpattack = it?.asDoubleOrNull()) }

fun applyHpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpattackMutation)
}

/** Sets the HPF envelope attack time on this pattern. */
@StrudelDsl
val StrudelPattern.hpattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpattack(p, args) }

/**
 * Sets the HPF envelope attack time in seconds.
 *
 * Controls how quickly the high pass filter cutoff sweeps from its baseline to the peak
 * at note onset. Use with [hpenv], [hpdecay], [hpsustain], [hprelease].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(2000).hpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript
 * note("c4").hpattack("<0.01 0.5>")              // fast vs slow filter attack per cycle
 * ```
 *
 * @alias hpa
 * @category effects
 * @tags hpattack, hpa, high pass filter, envelope, attack
 */
@StrudelDsl
val hpattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpattackMutation) }

/** Sets the HPF envelope attack time on a string pattern. */
@StrudelDsl
val String.hpattack by dslStringExtension { p, args, callInfo -> p.hpattack(args, callInfo) }

/** Alias for [hpattack] on this pattern. */
@StrudelDsl
val StrudelPattern.hpa by dslPatternExtension { p, args, callInfo -> p.hpattack(args, callInfo) }

/**
 * Alias for [hpattack]. Sets the HPF envelope attack time.
 *
 * @alias hpattack
 * @category effects
 * @tags hpa, hpattack, high pass filter, envelope, attack
 */
@StrudelDsl
val hpa by dslFunction { args, callInfo -> hpattack(args, callInfo) }

/** Alias for [hpattack] on a string pattern. */
@StrudelDsl
val String.hpa by dslStringExtension { p, args, callInfo -> p.hpattack(args, callInfo) }

// -- hpdecay() - High Pass Filter Envelope Decay ------------------------------------------------------------------------

private val hpdecayMutation = voiceModifier { copy(hpdecay = it?.asDoubleOrNull()) }

fun applyHpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpdecayMutation)
}

/** Sets the HPF envelope decay time on this pattern. */
@StrudelDsl
val StrudelPattern.hpdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpdecay(p, args) }

/**
 * Sets the HPF envelope decay time in seconds.
 *
 * Controls how quickly the filter cutoff moves from peak to sustain level after the attack.
 * Use with [hpattack], [hpsustain], [hprelease], [hpenv].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(2000).hpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript
 * note("c4").hpdecay("<0.05 0.5>")              // short vs long filter decay per cycle
 * ```
 *
 * @alias hpd
 * @category effects
 * @tags hpdecay, hpd, high pass filter, envelope, decay
 */
@StrudelDsl
val hpdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpdecayMutation) }

/** Sets the HPF envelope decay time on a string pattern. */
@StrudelDsl
val String.hpdecay by dslStringExtension { p, args, callInfo -> p.hpdecay(args, callInfo) }

/** Alias for [hpdecay] on this pattern. */
@StrudelDsl
val StrudelPattern.hpd by dslPatternExtension { p, args, callInfo -> p.hpdecay(args, callInfo) }

/**
 * Alias for [hpdecay]. Sets the HPF envelope decay time.
 *
 * @alias hpdecay
 * @category effects
 * @tags hpd, hpdecay, high pass filter, envelope, decay
 */
@StrudelDsl
val hpd by dslFunction { args, callInfo -> hpdecay(args, callInfo) }

/** Alias for [hpdecay] on a string pattern. */
@StrudelDsl
val String.hpd by dslStringExtension { p, args, callInfo -> p.hpdecay(args, callInfo) }

// -- hpsustain() - High Pass Filter Envelope Sustain --------------------------------------------------------------------

private val hpsustainMutation = voiceModifier { copy(hpsustain = it?.asDoubleOrNull()) }

fun applyHpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpsustainMutation)
}

/** Sets the HPF envelope sustain level on this pattern. */
@StrudelDsl
val StrudelPattern.hpsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpsustain(p, args) }

/**
 * Sets the HPF envelope sustain level (0–1).
 *
 * Controls the filter cutoff level during the sustained portion of the note. `1` holds the
 * filter open at the envelope peak; `0` closes it back to baseline. Use with
 * [hpattack]/[hpdecay]/[hprelease].
 *
 * ```KlangScript
 * note("c4").hpf(100).hpenv(3000).hpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").hpsustain("<0 1>")                     // closed vs fully open sustain
 * ```
 *
 * @alias hps
 * @category effects
 * @tags hpsustain, hps, high pass filter, envelope, sustain
 */
@StrudelDsl
val hpsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpsustainMutation) }

/** Sets the HPF envelope sustain level on a string pattern. */
@StrudelDsl
val String.hpsustain by dslStringExtension { p, args, callInfo -> p.hpsustain(args, callInfo) }

/** Alias for [hpsustain] on this pattern. */
@StrudelDsl
val StrudelPattern.hps by dslPatternExtension { p, args, callInfo -> p.hpsustain(args, callInfo) }

/**
 * Alias for [hpsustain]. Sets the HPF envelope sustain level.
 *
 * @alias hpsustain
 * @category effects
 * @tags hps, hpsustain, high pass filter, envelope, sustain
 */
@StrudelDsl
val hps by dslFunction { args, callInfo -> hpsustain(args, callInfo) }

/** Alias for [hpsustain] on a string pattern. */
@StrudelDsl
val String.hps by dslStringExtension { p, args, callInfo -> p.hpsustain(args, callInfo) }

// -- hprelease() - High Pass Filter Envelope Release --------------------------------------------------------------------

private val hpreleaseMutation = voiceModifier { copy(hprelease = it?.asDoubleOrNull()) }

fun applyHprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpreleaseMutation)
}

/** Sets the HPF envelope release time on this pattern. */
@StrudelDsl
val StrudelPattern.hprelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyHprelease(p, args) }

/**
 * Sets the HPF envelope release time in seconds.
 *
 * Controls how quickly the high pass filter cutoff returns to baseline after the note ends.
 * Use with [hpattack], [hpdecay], [hpsustain], [hpenv].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(2000).hprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript
 * note("c4").hprelease("<0.05 1.0>")              // short vs long filter release per cycle
 * ```
 *
 * @alias hpr
 * @category effects
 * @tags hprelease, hpr, high pass filter, envelope, release
 */
@StrudelDsl
val hprelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpreleaseMutation) }

/** Sets the HPF envelope release time on a string pattern. */
@StrudelDsl
val String.hprelease by dslStringExtension { p, args, callInfo -> p.hprelease(args, callInfo) }

/** Alias for [hprelease] on this pattern. */
@StrudelDsl
val StrudelPattern.hpr by dslPatternExtension { p, args, callInfo -> p.hprelease(args, callInfo) }

/**
 * Alias for [hprelease]. Sets the HPF envelope release time.
 *
 * @alias hprelease
 * @category effects
 * @tags hpr, hprelease, high pass filter, envelope, release
 */
@StrudelDsl
val hpr by dslFunction { args, callInfo -> hprelease(args, callInfo) }

/** Alias for [hprelease] on a string pattern. */
@StrudelDsl
val String.hpr by dslStringExtension { p, args, callInfo -> p.hprelease(args, callInfo) }

// -- hpenv() - High Pass Filter Envelope Depth --------------------------------------------------------------------------

private val hpenvMutation = voiceModifier { copy(hpenv = it?.asDoubleOrNull()) }

fun applyHpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpenvMutation)
}

/** Sets the HPF envelope depth/amount on this pattern. */
@StrudelDsl
val StrudelPattern.hpenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyHpenv(p, args) }

/**
 * Sets the HPF envelope depth (modulation amount) in Hz.
 *
 * Determines how far above the base [hpf] cutoff the filter sweeps when the envelope is
 * fully open. A larger value creates a more dramatic filter sweep. Use with [hpattack],
 * [hpdecay], [hpsustain], [hprelease].
 *
 * ```KlangScript
 * s("sd").hpf(100).hpenv(3000)                // sweeps up to 3.1 kHz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").hpf(200).hpenv("<500 5000>")     // subtle vs dramatic sweep per cycle
 * ```
 *
 * @alias hpe
 * @category effects
 * @tags hpenv, hpe, high pass filter, envelope, depth, modulation
 */
@StrudelDsl
val hpenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(hpenvMutation) }

/** Sets the HPF envelope depth/amount on a string pattern. */
@StrudelDsl
val String.hpenv by dslStringExtension { p, args, callInfo -> p.hpenv(args, callInfo) }

/** Alias for [hpenv] on this pattern. */
@StrudelDsl
val StrudelPattern.hpe by dslPatternExtension { p, args, callInfo -> p.hpenv(args, callInfo) }

/**
 * Alias for [hpenv]. Sets the HPF envelope depth.
 *
 * @alias hpenv
 * @category effects
 * @tags hpe, hpenv, high pass filter, envelope, depth, modulation
 */
@StrudelDsl
val hpe by dslFunction { args, callInfo -> hpenv(args, callInfo) }

/** Alias for [hpenv] on a string pattern. */
@StrudelDsl
val String.hpe by dslStringExtension { p, args, callInfo -> p.hpenv(args, callInfo) }

// -- bpattack() - Band Pass Filter Envelope Attack ----------------------------------------------------------------------

private val bpattackMutation = voiceModifier { copy(bpattack = it?.asDoubleOrNull()) }

fun applyBpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpattackMutation)
}

/** Sets the BPF envelope attack time on this pattern. */
@StrudelDsl
val StrudelPattern.bpattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpattack(p, args) }

/**
 * Sets the BPF envelope attack time in seconds.
 *
 * Controls how quickly the band pass filter centre frequency sweeps from its baseline to
 * the peak at note onset. Use with [bpenv], [bpdecay], [bpsustain], [bprelease].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(2000).bpattack(0.1)   // filter opens over 100 ms
 * ```
 *
 * ```KlangScript
 * note("c4").bpattack("<0.01 0.5>")               // fast vs slow filter attack per cycle
 * ```
 *
 * @alias bpa
 * @category effects
 * @tags bpattack, bpa, band pass filter, envelope, attack
 */
@StrudelDsl
val bpattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpattackMutation) }

/** Sets the BPF envelope attack time on a string pattern. */
@StrudelDsl
val String.bpattack by dslStringExtension { p, args, callInfo -> p.bpattack(args, callInfo) }

/** Alias for [bpattack] on this pattern. */
@StrudelDsl
val StrudelPattern.bpa by dslPatternExtension { p, args, callInfo -> p.bpattack(args, callInfo) }

/**
 * Alias for [bpattack]. Sets the BPF envelope attack time.
 *
 * @alias bpattack
 * @category effects
 * @tags bpa, bpattack, band pass filter, envelope, attack
 */
@StrudelDsl
val bpa by dslFunction { args, callInfo -> bpattack(args, callInfo) }

/** Alias for [bpattack] on a string pattern. */
@StrudelDsl
val String.bpa by dslStringExtension { p, args, callInfo -> p.bpattack(args, callInfo) }

// -- bpdecay() - Band Pass Filter Envelope Decay ------------------------------------------------------------------------

private val bpdecayMutation = voiceModifier { copy(bpdecay = it?.asDoubleOrNull()) }

fun applyBpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpdecayMutation)
}

/** Sets the BPF envelope decay time on this pattern. */
@StrudelDsl
val StrudelPattern.bpdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpdecay(p, args) }

/**
 * Sets the BPF envelope decay time in seconds.
 *
 * Controls how quickly the filter centre frequency moves from peak to sustain level after
 * the attack. Use with [bpattack], [bpsustain], [bprelease], [bpenv].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(2000).bpdecay(0.2)   // filter decays over 200 ms
 * ```
 *
 * ```KlangScript
 * note("c4").bpdecay("<0.05 0.5>")               // short vs long filter decay per cycle
 * ```
 *
 * @alias bpd
 * @category effects
 * @tags bpdecay, bpd, band pass filter, envelope, decay
 */
@StrudelDsl
val bpdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpdecayMutation) }

/** Sets the BPF envelope decay time on a string pattern. */
@StrudelDsl
val String.bpdecay by dslStringExtension { p, args, callInfo -> p.bpdecay(args, callInfo) }

/** Alias for [bpdecay] on this pattern. */
@StrudelDsl
val StrudelPattern.bpd by dslPatternExtension { p, args, callInfo -> p.bpdecay(args, callInfo) }

/**
 * Alias for [bpdecay]. Sets the BPF envelope decay time.
 *
 * @alias bpdecay
 * @category effects
 * @tags bpd, bpdecay, band pass filter, envelope, decay
 */
@StrudelDsl
val bpd by dslFunction { args, callInfo -> bpdecay(args, callInfo) }

/** Alias for [bpdecay] on a string pattern. */
@StrudelDsl
val String.bpd by dslStringExtension { p, args, callInfo -> p.bpdecay(args, callInfo) }

// -- bpsustain() - Band Pass Filter Envelope Sustain --------------------------------------------------------------------

private val bpsustainMutation = voiceModifier { copy(bpsustain = it?.asDoubleOrNull()) }

fun applyBpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpsustainMutation)
}

/** Sets the BPF envelope sustain level on this pattern. */
@StrudelDsl
val StrudelPattern.bpsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpsustain(p, args) }

/**
 * Sets the BPF envelope sustain level (0–1).
 *
 * Controls the filter centre frequency level during the sustained portion of the note.
 * `1` holds the filter at the envelope peak; `0` returns to baseline. Use with
 * [bpattack]/[bpdecay]/[bprelease].
 *
 * ```KlangScript
 * note("c4").bandf(500).bpenv(3000).bpsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").bpsustain("<0 1>")                      // closed vs fully open sustain
 * ```
 *
 * @alias bps
 * @category effects
 * @tags bpsustain, bps, band pass filter, envelope, sustain
 */
@StrudelDsl
val bpsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpsustainMutation) }

/** Sets the BPF envelope sustain level on a string pattern. */
@StrudelDsl
val String.bpsustain by dslStringExtension { p, args, callInfo -> p.bpsustain(args, callInfo) }

/** Alias for [bpsustain] on this pattern. */
@StrudelDsl
val StrudelPattern.bps by dslPatternExtension { p, args, callInfo -> p.bpsustain(args, callInfo) }

/**
 * Alias for [bpsustain]. Sets the BPF envelope sustain level.
 *
 * @alias bpsustain
 * @category effects
 * @tags bps, bpsustain, band pass filter, envelope, sustain
 */
@StrudelDsl
val bps by dslFunction { args, callInfo -> bpsustain(args, callInfo) }

/** Alias for [bpsustain] on a string pattern. */
@StrudelDsl
val String.bps by dslStringExtension { p, args, callInfo -> p.bpsustain(args, callInfo) }

// -- bprelease() - Band Pass Filter Envelope Release --------------------------------------------------------------------

private val bpreleaseMutation = voiceModifier { copy(bprelease = it?.asDoubleOrNull()) }

fun applyBprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpreleaseMutation)
}

/** Sets the BPF envelope release time on this pattern. */
@StrudelDsl
val StrudelPattern.bprelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyBprelease(p, args) }

/**
 * Sets the BPF envelope release time in seconds.
 *
 * Controls how quickly the band pass filter centre frequency returns to baseline after the
 * note ends. Use with [bpattack], [bpdecay], [bpsustain], [bpenv].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(2000).bprelease(0.4)   // filter closes slowly after note
 * ```
 *
 * ```KlangScript
 * note("c4").bprelease("<0.05 1.0>")               // short vs long filter release per cycle
 * ```
 *
 * @alias bpr
 * @category effects
 * @tags bprelease, bpr, band pass filter, envelope, release
 */
@StrudelDsl
val bprelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpreleaseMutation) }

/** Sets the BPF envelope release time on a string pattern. */
@StrudelDsl
val String.bprelease by dslStringExtension { p, args, callInfo -> p.bprelease(args, callInfo) }

/** Alias for [bprelease] on this pattern. */
@StrudelDsl
val StrudelPattern.bpr by dslPatternExtension { p, args, callInfo -> p.bprelease(args, callInfo) }

/**
 * Alias for [bprelease]. Sets the BPF envelope release time.
 *
 * @alias bprelease
 * @category effects
 * @tags bpr, bprelease, band pass filter, envelope, release
 */
@StrudelDsl
val bpr by dslFunction { args, callInfo -> bprelease(args, callInfo) }

/** Alias for [bprelease] on a string pattern. */
@StrudelDsl
val String.bpr by dslStringExtension { p, args, callInfo -> p.bprelease(args, callInfo) }

// -- bpenv() - Band Pass Filter Envelope Depth --------------------------------------------------------------------------

private val bpenvMutation = voiceModifier { copy(bpenv = it?.asDoubleOrNull()) }

fun applyBpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpenvMutation)
}

/** Sets the BPF envelope depth/amount on this pattern. */
@StrudelDsl
val StrudelPattern.bpenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyBpenv(p, args) }

/**
 * Sets the BPF envelope depth (modulation amount) in Hz.
 *
 * Determines how far above the base [bandf] centre frequency the filter sweeps when the
 * envelope is fully open. A larger value creates a more dramatic sweep. Use with [bpattack],
 * [bpdecay], [bpsustain], [bprelease].
 *
 * ```KlangScript
 * s("sd").bandf(500).bpenv(3000)                // sweeps up to 3.5 kHz at peak
 * ```
 *
 * ```KlangScript
 * note("c4").bandf(300).bpenv("<500 5000>")     // subtle vs dramatic sweep per cycle
 * ```
 *
 * @alias bpe
 * @category effects
 * @tags bpenv, bpe, band pass filter, envelope, depth, modulation
 */
@StrudelDsl
val bpenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(bpenvMutation) }

/** Sets the BPF envelope depth/amount on a string pattern. */
@StrudelDsl
val String.bpenv by dslStringExtension { p, args, callInfo -> p.bpenv(args, callInfo) }

/** Alias for [bpenv] on this pattern. */
@StrudelDsl
val StrudelPattern.bpe by dslPatternExtension { p, args, callInfo -> p.bpenv(args, callInfo) }

/**
 * Alias for [bpenv]. Sets the BPF envelope depth.
 *
 * @alias bpenv
 * @category effects
 * @tags bpe, bpenv, band pass filter, envelope, depth, modulation
 */
@StrudelDsl
val bpe by dslFunction { args, callInfo -> bpenv(args, callInfo) }

/** Alias for [bpenv] on a string pattern. */
@StrudelDsl
val String.bpe by dslStringExtension { p, args, callInfo -> p.bpenv(args, callInfo) }

// -- nfattack() - Notch Filter Envelope Attack (NOT IN ORIGINAL STRUDEL) -------------------------------------------------

private val nfattackMutation = voiceModifier { copy(nfattack = it?.asDoubleOrNull()) }

fun applyNfattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfattackMutation)
}

/** Sets the notch filter envelope attack time on this pattern. */
@StrudelDsl
val StrudelPattern.nfattack by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfattack(p, args) }

/**
 * Sets the notch filter envelope attack time in seconds.
 *
 * Controls how quickly the notch filter centre frequency sweeps from its baseline to the
 * peak at note onset. Use with [nfenv], [nfdecay], [nfsustain], [nfrelease].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfattack(0.1)   // notch sweeps open over 100 ms
 * ```
 *
 * ```KlangScript
 * s("bd").nfattack("<0.01 0.5>")                        // fast vs slow attack per cycle
 * ```
 *
 * @alias nfa
 * @category effects
 * @tags nfattack, nfa, notch filter, envelope, attack
 */
@StrudelDsl
val nfattack by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfattackMutation) }

/** Sets the notch filter envelope attack time on a string pattern. */
@StrudelDsl
val String.nfattack by dslStringExtension { p, args, callInfo -> p.nfattack(args, callInfo) }

/** Alias for [nfattack] on this pattern. */
@StrudelDsl
val StrudelPattern.nfa by dslPatternExtension { p, args, callInfo -> p.nfattack(args, callInfo) }

/**
 * Alias for [nfattack]. Sets the notch filter envelope attack time.
 *
 * @alias nfattack
 * @category effects
 * @tags nfa, nfattack, notch filter, envelope, attack
 */
@StrudelDsl
val nfa by dslFunction { args, callInfo -> nfattack(args, callInfo) }

/** Alias for [nfattack] on a string pattern. */
@StrudelDsl
val String.nfa by dslStringExtension { p, args, callInfo -> p.nfattack(args, callInfo) }

// -- nfdecay() - Notch Filter Envelope Decay (NOT IN ORIGINAL STRUDEL) ---------------------------------------------------

private val nfdecayMutation = voiceModifier { copy(nfdecay = it?.asDoubleOrNull()) }

fun applyNfdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfdecayMutation)
}

/** Sets the notch filter envelope decay time on this pattern. */
@StrudelDsl
val StrudelPattern.nfdecay by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfdecay(p, args) }

/**
 * Sets the notch filter envelope decay time in seconds.
 *
 * Controls how quickly the notch filter centre frequency moves from peak to sustain level
 * after the attack. Use with [nfattack], [nfsustain], [nfrelease], [nfenv].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfdecay(0.2)   // notch decays over 200 ms
 * ```
 *
 * ```KlangScript
 * s("bd").nfdecay("<0.05 0.5>")                        // short vs long decay per cycle
 * ```
 *
 * @alias nfd
 * @category effects
 * @tags nfdecay, nfd, notch filter, envelope, decay
 */
@StrudelDsl
val nfdecay by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfdecayMutation) }

/** Sets the notch filter envelope decay time on a string pattern. */
@StrudelDsl
val String.nfdecay by dslStringExtension { p, args, callInfo -> p.nfdecay(args, callInfo) }

/** Alias for [nfdecay] on this pattern. */
@StrudelDsl
val StrudelPattern.nfd by dslPatternExtension { p, args, callInfo -> p.nfdecay(args, callInfo) }

/**
 * Alias for [nfdecay]. Sets the notch filter envelope decay time.
 *
 * @alias nfdecay
 * @category effects
 * @tags nfd, nfdecay, notch filter, envelope, decay
 */
@StrudelDsl
val nfd by dslFunction { args, callInfo -> nfdecay(args, callInfo) }

/** Alias for [nfdecay] on a string pattern. */
@StrudelDsl
val String.nfd by dslStringExtension { p, args, callInfo -> p.nfdecay(args, callInfo) }

// -- nfsustain() - Notch Filter Envelope Sustain (NOT IN ORIGINAL STRUDEL) -----------------------------------------------

private val nfsustainMutation = voiceModifier { copy(nfsustain = it?.asDoubleOrNull()) }

fun applyNfsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfsustainMutation)
}

/** Sets the notch filter envelope sustain level on this pattern. */
@StrudelDsl
val StrudelPattern.nfsustain by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfsustain(p, args) }

/**
 * Sets the notch filter envelope sustain level (0–1).
 *
 * Controls the notch centre frequency level during the sustained portion of the note.
 * `1` holds the notch at the envelope peak; `0` returns to baseline. Use with
 * [nfattack]/[nfdecay]/[nfrelease].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfsustain(0.5)  // sustain at half depth
 * ```
 *
 * ```KlangScript
 * note("c4").nfsustain("<0 1>")                         // closed vs fully open sustain
 * ```
 *
 * @alias nfs
 * @category effects
 * @tags nfsustain, nfs, notch filter, envelope, sustain
 */
@StrudelDsl
val nfsustain by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfsustainMutation) }

/** Sets the notch filter envelope sustain level on a string pattern. */
@StrudelDsl
val String.nfsustain by dslStringExtension { p, args, callInfo -> p.nfsustain(args, callInfo) }

/** Alias for [nfsustain] on this pattern. */
@StrudelDsl
val StrudelPattern.nfs by dslPatternExtension { p, args, callInfo -> p.nfsustain(args, callInfo) }

/**
 * Alias for [nfsustain]. Sets the notch filter envelope sustain level.
 *
 * @alias nfsustain
 * @category effects
 * @tags nfs, nfsustain, notch filter, envelope, sustain
 */
@StrudelDsl
val nfs by dslFunction { args, callInfo -> nfsustain(args, callInfo) }

/** Alias for [nfsustain] on a string pattern. */
@StrudelDsl
val String.nfs by dslStringExtension { p, args, callInfo -> p.nfsustain(args, callInfo) }

// -- nfrelease() - Notch Filter Envelope Release (NOT IN ORIGINAL STRUDEL) -----------------------------------------------

private val nfreleaseMutation = voiceModifier { copy(nfrelease = it?.asDoubleOrNull()) }

fun applyNfrelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfreleaseMutation)
}

/** Sets the notch filter envelope release time on this pattern. */
@StrudelDsl
val StrudelPattern.nfrelease by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfrelease(p, args) }

/**
 * Sets the notch filter envelope release time in seconds.
 *
 * Controls how quickly the notch filter centre frequency returns to baseline after the
 * note ends. Use with [nfattack], [nfdecay], [nfsustain], [nfenv].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(3000).nfrelease(0.4)   // notch closes slowly after note
 * ```
 *
 * ```KlangScript
 * s("bd").nfrelease("<0.05 1.0>")                        // short vs long release per cycle
 * ```
 *
 * @alias nfr
 * @category effects
 * @tags nfrelease, nfr, notch filter, envelope, release
 */
@StrudelDsl
val nfrelease by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfreleaseMutation) }

/** Sets the notch filter envelope release time on a string pattern. */
@StrudelDsl
val String.nfrelease by dslStringExtension { p, args, callInfo -> p.nfrelease(args, callInfo) }

/** Alias for [nfrelease] on this pattern. */
@StrudelDsl
val StrudelPattern.nfr by dslPatternExtension { p, args, callInfo -> p.nfrelease(args, callInfo) }

/**
 * Alias for [nfrelease]. Sets the notch filter envelope release time.
 *
 * @alias nfrelease
 * @category effects
 * @tags nfr, nfrelease, notch filter, envelope, release
 */
@StrudelDsl
val nfr by dslFunction { args, callInfo -> nfrelease(args, callInfo) }

/** Alias for [nfrelease] on a string pattern. */
@StrudelDsl
val String.nfr by dslStringExtension { p, args, callInfo -> p.nfrelease(args, callInfo) }

// -- nfenv() - Notch Filter Envelope Depth (NOT IN ORIGINAL STRUDEL) -----------------------------------------------------

private val nfenvMutation = voiceModifier { copy(nfenv = it?.asDoubleOrNull()) }

fun applyNfenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfenvMutation)
}

/** Sets the notch filter envelope depth/amount on this pattern. */
@StrudelDsl
val StrudelPattern.nfenv by dslPatternExtension { p, args, /* callInfo */ _ -> applyNfenv(p, args) }

/**
 * Sets the notch filter envelope depth (modulation amount) in Hz.
 *
 * Determines how far above the base [notchf] centre frequency the notch sweeps when the
 * envelope is fully open. Use with [nfattack], [nfdecay], [nfsustain], [nfrelease].
 *
 * ```KlangScript
 * note("c4").notchf(1000).nfenv(4000)              // notch sweeps up to 5 kHz at peak
 * ```
 *
 * ```KlangScript
 * s("bd").notchf(500).nfenv("<1000 8000>")         // subtle vs dramatic sweep per cycle
 * ```
 *
 * @alias nfe
 * @category effects
 * @tags nfenv, nfe, notch filter, envelope, depth, modulation
 */
@StrudelDsl
val nfenv by dslFunction { args, /* callInfo */ _ -> args.toPattern(nfenvMutation) }

/** Sets the notch filter envelope depth/amount on a string pattern. */
@StrudelDsl
val String.nfenv by dslStringExtension { p, args, callInfo -> p.nfenv(args, callInfo) }

/** Alias for [nfenv] on this pattern. */
@StrudelDsl
val StrudelPattern.nfe by dslPatternExtension { p, args, callInfo -> p.nfenv(args, callInfo) }

/**
 * Alias for [nfenv]. Sets the notch filter envelope depth.
 *
 * @alias nfenv
 * @category effects
 * @tags nfe, nfenv, notch filter, envelope, depth, modulation
 */
@StrudelDsl
val nfe by dslFunction { args, callInfo -> nfenv(args, callInfo) }

/** Alias for [nfenv] on a string pattern. */
@StrudelDsl
val String.nfe by dslStringExtension { p, args, callInfo -> p.nfenv(args, callInfo) }
