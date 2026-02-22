@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._liftNumericField
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

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

internal val _lpf by dslPatternFunction { args, _ -> args.toPattern(lpfMutation) }
internal val StrudelPattern._lpf by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._lpf by dslStringExtension { p, args, callInfo -> p._lpf(args, callInfo) }

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
fun lpf(freq: PatternLike): StrudelPattern = _lpf(listOf(freq).asStrudelDslArgs())

/** Applies a Low Pass Filter to this pattern with the given cutoff frequency in Hz. */
@StrudelDsl
fun StrudelPattern.lpf(freq: PatternLike): StrudelPattern = this._lpf(listOf(freq).asStrudelDslArgs())

/** Applies a Low Pass Filter to a string pattern with the given cutoff frequency in Hz. */
@StrudelDsl
fun String.lpf(freq: PatternLike): StrudelPattern = this._lpf(listOf(freq).asStrudelDslArgs())

internal val _cutoff by dslPatternFunction { args, _ -> args.toPattern(lpfMutation) }
internal val StrudelPattern._cutoff by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._cutoff by dslStringExtension { p, args, callInfo -> p._cutoff(args, callInfo) }

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @alias lpf, ctf, lp
 * @category effects
 * @tags cutoff, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
fun cutoff(freq: PatternLike): StrudelPattern = _cutoff(listOf(freq).asStrudelDslArgs())

/** Alias for [lpf] on this pattern. */
@StrudelDsl
fun StrudelPattern.cutoff(freq: PatternLike): StrudelPattern = this._cutoff(listOf(freq).asStrudelDslArgs())

/** Alias for [lpf] on a string pattern. */
@StrudelDsl
fun String.cutoff(freq: PatternLike): StrudelPattern = this._cutoff(listOf(freq).asStrudelDslArgs())

internal val _ctf by dslPatternFunction { args, _ -> args.toPattern(lpfMutation) }
internal val StrudelPattern._ctf by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._ctf by dslStringExtension { p, args, callInfo -> p._ctf(args, callInfo) }

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @alias lpf, cutoff, lp
 * @category effects
 * @tags ctf, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
fun ctf(freq: PatternLike): StrudelPattern = _ctf(listOf(freq).asStrudelDslArgs())

/** Alias for [lpf] on this pattern. */
@StrudelDsl
fun StrudelPattern.ctf(freq: PatternLike): StrudelPattern = this._ctf(listOf(freq).asStrudelDslArgs())

/** Alias for [lpf] on a string pattern. */
@StrudelDsl
fun String.ctf(freq: PatternLike): StrudelPattern = this._ctf(listOf(freq).asStrudelDslArgs())

internal val _lp by dslPatternFunction { args, _ -> args.toPattern(lpfMutation) }
internal val StrudelPattern._lp by dslPatternExtension { p, args, _ -> applyLpf(p, args) }
internal val String._lp by dslStringExtension { p, args, callInfo -> p._lp(args, callInfo) }

/**
 * Alias for [lpf]. Applies a Low Pass Filter with the given cutoff frequency.
 *
 * @alias lpf, cutoff, ctf
 * @category effects
 * @tags lp, lpf, low pass filter, filter, frequency
 */
@StrudelDsl
fun lp(freq: PatternLike): StrudelPattern = _lp(listOf(freq).asStrudelDslArgs())

/** Alias for [lpf] on this pattern. */
@StrudelDsl
fun StrudelPattern.lp(freq: PatternLike): StrudelPattern = this._lp(listOf(freq).asStrudelDslArgs())

/** Alias for [lpf] on a string pattern. */
@StrudelDsl
fun String.lp(freq: PatternLike): StrudelPattern = this._lp(listOf(freq).asStrudelDslArgs())

// -- hpf() ------------------------------------------------------------------------------------------------------------

private val hpfMutation = voiceModifier { copy(hcutoff = it?.asDoubleOrNull()) }

fun applyHpf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpfMutation)
}

internal val _hpf by dslPatternFunction { args, _ -> args.toPattern(hpfMutation) }
internal val StrudelPattern._hpf by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hpf by dslStringExtension { p, args, callInfo -> p._hpf(args, callInfo) }

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
fun hpf(freq: PatternLike): StrudelPattern = _hpf(listOf(freq).asStrudelDslArgs())

/** Applies a High Pass Filter to this pattern with the given cutoff frequency in Hz. */
@StrudelDsl
fun StrudelPattern.hpf(freq: PatternLike): StrudelPattern = this._hpf(listOf(freq).asStrudelDslArgs())

/** Applies a High Pass Filter to a string pattern with the given cutoff frequency in Hz. */
@StrudelDsl
fun String.hpf(freq: PatternLike): StrudelPattern = this._hpf(listOf(freq).asStrudelDslArgs())

internal val _hp by dslPatternFunction { args, _ -> args.toPattern(hpfMutation) }
internal val StrudelPattern._hp by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hp by dslStringExtension { p, args, callInfo -> p._hp(args, callInfo) }

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @alias hpf, hcutoff
 * @category effects
 * @tags hp, hpf, high pass filter, filter, frequency
 */
@StrudelDsl
fun hp(freq: PatternLike): StrudelPattern = _hp(listOf(freq).asStrudelDslArgs())

/** Alias for [hpf] on this pattern. */
@StrudelDsl
fun StrudelPattern.hp(freq: PatternLike): StrudelPattern = this._hp(listOf(freq).asStrudelDslArgs())

/** Alias for [hpf] on a string pattern. */
@StrudelDsl
fun String.hp(freq: PatternLike): StrudelPattern = this._hp(listOf(freq).asStrudelDslArgs())

internal val _hcutoff by dslPatternFunction { args, _ -> args.toPattern(hpfMutation) }
internal val StrudelPattern._hcutoff by dslPatternExtension { p, args, _ -> applyHpf(p, args) }
internal val String._hcutoff by dslStringExtension { p, args, callInfo -> p._hcutoff(args, callInfo) }

/**
 * Alias for [hpf]. Applies a High Pass Filter with the given cutoff frequency.
 *
 * @alias hpf, hp
 * @category effects
 * @tags hcutoff, hpf, high pass filter, filter, frequency
 */
@StrudelDsl
fun hcutoff(freq: PatternLike): StrudelPattern = _hcutoff(listOf(freq).asStrudelDslArgs())

/** Alias for [hpf] on this pattern. */
@StrudelDsl
fun StrudelPattern.hcutoff(freq: PatternLike): StrudelPattern = this._hcutoff(listOf(freq).asStrudelDslArgs())

/** Alias for [hpf] on a string pattern. */
@StrudelDsl
fun String.hcutoff(freq: PatternLike): StrudelPattern = this._hcutoff(listOf(freq).asStrudelDslArgs())

// -- bandf() / bpf() --------------------------------------------------------------------------------------------------

private val bandfMutation = voiceModifier { copy(bandf = it?.asDoubleOrNull()) }

fun applyBandf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bandfMutation)
}

internal val _bandf by dslPatternFunction { args, _ -> args.toPattern(bandfMutation) }
internal val StrudelPattern._bandf by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bandf by dslStringExtension { p, args, callInfo -> p._bandf(args, callInfo) }

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
fun bandf(freq: PatternLike): StrudelPattern = _bandf(listOf(freq).asStrudelDslArgs())

/** Applies a Band Pass Filter to this pattern with the given centre frequency in Hz. */
@StrudelDsl
fun StrudelPattern.bandf(freq: PatternLike): StrudelPattern = this._bandf(listOf(freq).asStrudelDslArgs())

/** Applies a Band Pass Filter to a string pattern with the given centre frequency in Hz. */
@StrudelDsl
fun String.bandf(freq: PatternLike): StrudelPattern = this._bandf(listOf(freq).asStrudelDslArgs())

internal val _bpf by dslPatternFunction { args, _ -> args.toPattern(bandfMutation) }
internal val StrudelPattern._bpf by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bpf by dslStringExtension { p, args, callInfo -> p._bpf(args, callInfo) }

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @alias bandf, bp
 * @category effects
 * @tags bpf, bandf, band pass filter, filter, frequency
 */
@StrudelDsl
fun bpf(freq: PatternLike): StrudelPattern = _bpf(listOf(freq).asStrudelDslArgs())

/** Alias for [bandf] on this pattern. */
@StrudelDsl
fun StrudelPattern.bpf(freq: PatternLike): StrudelPattern = this._bpf(listOf(freq).asStrudelDslArgs())

/** Alias for [bandf] on a string pattern. */
@StrudelDsl
fun String.bpf(freq: PatternLike): StrudelPattern = this._bpf(listOf(freq).asStrudelDslArgs())

internal val _bp by dslPatternFunction { args, _ -> args.toPattern(bandfMutation) }
internal val StrudelPattern._bp by dslPatternExtension { p, args, _ -> applyBandf(p, args) }
internal val String._bp by dslStringExtension { p, args, callInfo -> p._bp(args, callInfo) }

/**
 * Alias for [bandf]. Applies a Band Pass Filter with the given centre frequency.
 *
 * @alias bandf, bpf
 * @category effects
 * @tags bp, bandf, band pass filter, filter, frequency
 */
@StrudelDsl
fun bp(freq: PatternLike): StrudelPattern = _bp(listOf(freq).asStrudelDslArgs())

/** Alias for [bandf] on this pattern. */
@StrudelDsl
fun StrudelPattern.bp(freq: PatternLike): StrudelPattern = this._bp(listOf(freq).asStrudelDslArgs())

/** Alias for [bandf] on a string pattern. */
@StrudelDsl
fun String.bp(freq: PatternLike): StrudelPattern = this._bp(listOf(freq).asStrudelDslArgs())

// -- notchf() ---------------------------------------------------------------------------------------------------------

private val notchfMutation = voiceModifier { copy(notchf = it?.asDoubleOrNull()) }

fun applyNotchf(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, notchfMutation)
}

internal val _notchf by dslPatternFunction { args, _ -> args.toPattern(notchfMutation) }
internal val StrudelPattern._notchf by dslPatternExtension { p, args, _ -> applyNotchf(p, args) }
internal val String._notchf by dslStringExtension { p, args, callInfo -> p._notchf(args, callInfo) }

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
fun notchf(freq: PatternLike): StrudelPattern = _notchf(listOf(freq).asStrudelDslArgs())

/** Applies a Notch Filter to this pattern with the given centre frequency in Hz. */
@StrudelDsl
fun StrudelPattern.notchf(freq: PatternLike): StrudelPattern = this._notchf(listOf(freq).asStrudelDslArgs())

/** Applies a Notch Filter to a string pattern with the given centre frequency in Hz. */
@StrudelDsl
fun String.notchf(freq: PatternLike): StrudelPattern = this._notchf(listOf(freq).asStrudelDslArgs())

// -- resonance() / res() - Low Pass Filter resonance -----------------------------------------------------------------

private val resonanceMutation = voiceModifier { copy(resonance = it?.asDoubleOrNull()) }

fun applyResonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, resonanceMutation)
}

internal val _resonance by dslPatternFunction { args, _ -> args.toPattern(resonanceMutation) }
internal val StrudelPattern._resonance by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._resonance by dslStringExtension { p, args, callInfo -> p._resonance(args, callInfo) }

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
fun resonance(q: PatternLike): StrudelPattern = _resonance(listOf(q).asStrudelDslArgs())

/** Sets the LPF resonance/Q on this pattern. */
@StrudelDsl
fun StrudelPattern.resonance(q: PatternLike): StrudelPattern = this._resonance(listOf(q).asStrudelDslArgs())

/** Sets the LPF resonance/Q on a string pattern. */
@StrudelDsl
fun String.resonance(q: PatternLike): StrudelPattern = this._resonance(listOf(q).asStrudelDslArgs())

internal val _res by dslPatternFunction { args, _ -> args.toPattern(resonanceMutation) }
internal val StrudelPattern._res by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._res by dslStringExtension { p, args, callInfo -> p._res(args, callInfo) }

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @alias resonance, lpq
 * @category effects
 * @tags res, resonance, lpq, low pass filter, Q
 */
@StrudelDsl
fun res(q: PatternLike): StrudelPattern = _res(listOf(q).asStrudelDslArgs())

/** Alias for [resonance] on this pattern. */
@StrudelDsl
fun StrudelPattern.res(q: PatternLike): StrudelPattern = this._res(listOf(q).asStrudelDslArgs())

/** Alias for [resonance] on a string pattern. */
@StrudelDsl
fun String.res(q: PatternLike): StrudelPattern = this._res(listOf(q).asStrudelDslArgs())

internal val _lpq by dslPatternFunction { args, _ -> args.toPattern(resonanceMutation) }
internal val StrudelPattern._lpq by dslPatternExtension { p, args, _ -> applyResonance(p, args) }
internal val String._lpq by dslStringExtension { p, args, callInfo -> p._lpq(args, callInfo) }

/**
 * Alias for [resonance]. Sets the LPF resonance/Q.
 *
 * @alias resonance, res
 * @category effects
 * @tags lpq, resonance, res, low pass filter, Q
 */
@StrudelDsl
fun lpq(q: PatternLike): StrudelPattern = _lpq(listOf(q).asStrudelDslArgs())

/** Alias for [resonance] on this pattern. */
@StrudelDsl
fun StrudelPattern.lpq(q: PatternLike): StrudelPattern = this._lpq(listOf(q).asStrudelDslArgs())

/** Alias for [resonance] on a string pattern. */
@StrudelDsl
fun String.lpq(q: PatternLike): StrudelPattern = this._lpq(listOf(q).asStrudelDslArgs())

// -- hresonance() / hres() - High Pass Filter resonance --------------------------------------------------------------

private val hresonanceMutation = voiceModifier { copy(hresonance = it?.asDoubleOrNull()) }

fun applyHresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hresonanceMutation)
}

internal val _hresonance by dslPatternFunction { args, _ -> args.toPattern(hresonanceMutation) }
internal val StrudelPattern._hresonance by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hresonance by dslStringExtension { p, args, callInfo -> p._hresonance(args, callInfo) }

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
fun hresonance(q: PatternLike): StrudelPattern = _hresonance(listOf(q).asStrudelDslArgs())

/** Sets the HPF resonance/Q on this pattern. */
@StrudelDsl
fun StrudelPattern.hresonance(q: PatternLike): StrudelPattern = this._hresonance(listOf(q).asStrudelDslArgs())

/** Sets the HPF resonance/Q on a string pattern. */
@StrudelDsl
fun String.hresonance(q: PatternLike): StrudelPattern = this._hresonance(listOf(q).asStrudelDslArgs())

internal val _hres by dslPatternFunction { args, _ -> args.toPattern(hresonanceMutation) }
internal val StrudelPattern._hres by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hres by dslStringExtension { p, args, callInfo -> p._hres(args, callInfo) }

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @alias hresonance, hpq
 * @category effects
 * @tags hres, hresonance, hpq, high pass filter, Q
 */
@StrudelDsl
fun hres(q: PatternLike): StrudelPattern = _hres(listOf(q).asStrudelDslArgs())

/** Alias for [hresonance] on this pattern. */
@StrudelDsl
fun StrudelPattern.hres(q: PatternLike): StrudelPattern = this._hres(listOf(q).asStrudelDslArgs())

/** Alias for [hresonance] on a string pattern. */
@StrudelDsl
fun String.hres(q: PatternLike): StrudelPattern = this._hres(listOf(q).asStrudelDslArgs())

internal val _hpq by dslPatternFunction { args, _ -> args.toPattern(hresonanceMutation) }
internal val StrudelPattern._hpq by dslPatternExtension { p, args, _ -> applyHresonance(p, args) }
internal val String._hpq by dslStringExtension { p, args, callInfo -> p._hpq(args, callInfo) }

/**
 * Alias for [hresonance]. Sets the HPF resonance/Q.
 *
 * @alias hresonance, hres
 * @category effects
 * @tags hpq, hresonance, hres, high pass filter, Q
 */
@StrudelDsl
fun hpq(q: PatternLike): StrudelPattern = _hpq(listOf(q).asStrudelDslArgs())

/** Alias for [hresonance] on this pattern. */
@StrudelDsl
fun StrudelPattern.hpq(q: PatternLike): StrudelPattern = this._hpq(listOf(q).asStrudelDslArgs())

/** Alias for [hresonance] on a string pattern. */
@StrudelDsl
fun String.hpq(q: PatternLike): StrudelPattern = this._hpq(listOf(q).asStrudelDslArgs())

// -- bandq() - Band Pass Filter resonance ----------------------------------------------------------------------------

private val bandqMutation = voiceModifier { copy(bandq = it?.asDoubleOrNull()) }

fun applyBandq(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bandqMutation)
}

internal val _bandq by dslPatternFunction { args, _ -> args.toPattern(bandqMutation) }
internal val StrudelPattern._bandq by dslPatternExtension { p, args, _ -> applyBandq(p, args) }
internal val String._bandq by dslStringExtension { p, args, callInfo -> p._bandq(args, callInfo) }

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
fun bandq(q: PatternLike): StrudelPattern = _bandq(listOf(q).asStrudelDslArgs())

/** Sets the BPF Q (bandwidth) on this pattern. */
@StrudelDsl
fun StrudelPattern.bandq(q: PatternLike): StrudelPattern = this._bandq(listOf(q).asStrudelDslArgs())

/** Sets the BPF Q (bandwidth) on a string pattern. */
@StrudelDsl
fun String.bandq(q: PatternLike): StrudelPattern = this._bandq(listOf(q).asStrudelDslArgs())

internal val _bpq by dslPatternFunction { args, _ -> args.toPattern(bandqMutation) }
internal val StrudelPattern._bpq by dslPatternExtension { p, args, _ -> applyBandq(p, args) }
internal val String._bpq by dslStringExtension { p, args, callInfo -> p._bpq(args, callInfo) }

/**
 * Alias for [bandq]. Sets the BPF Q (bandwidth).
 *
 * @alias bandq
 * @category effects
 * @tags bpq, bandq, band pass filter, Q, bandwidth
 */
@StrudelDsl
fun bpq(q: PatternLike): StrudelPattern = _bpq(listOf(q).asStrudelDslArgs())

/** Alias for [bandq] on this pattern. */
@StrudelDsl
fun StrudelPattern.bpq(q: PatternLike): StrudelPattern = this._bpq(listOf(q).asStrudelDslArgs())

/** Alias for [bandq] on a string pattern. */
@StrudelDsl
fun String.bpq(q: PatternLike): StrudelPattern = this._bpq(listOf(q).asStrudelDslArgs())

// -- nresonance() / nres() - Notch Filter resonance ------------------------------------------------------------------

private val nresonanceMutation = voiceModifier { copy(nresonance = it?.asDoubleOrNull()) }

fun applyNresonance(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nresonanceMutation)
}

internal val _nresonance by dslPatternFunction { args, _ -> args.toPattern(nresonanceMutation) }
internal val StrudelPattern._nresonance by dslPatternExtension { p, args, _ -> applyNresonance(p, args) }
internal val String._nresonance by dslStringExtension { p, args, callInfo -> p._nresonance(args, callInfo) }

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
fun nresonance(q: PatternLike): StrudelPattern = _nresonance(listOf(q).asStrudelDslArgs())

/** Sets the notch filter Q on this pattern. */
@StrudelDsl
fun StrudelPattern.nresonance(q: PatternLike): StrudelPattern = this._nresonance(listOf(q).asStrudelDslArgs())

/** Sets the notch filter Q on a string pattern. */
@StrudelDsl
fun String.nresonance(q: PatternLike): StrudelPattern = this._nresonance(listOf(q).asStrudelDslArgs())

internal val _nres by dslPatternFunction { args, _ -> args.toPattern(nresonanceMutation) }
internal val StrudelPattern._nres by dslPatternExtension { p, args, _ -> applyNresonance(p, args) }
internal val String._nres by dslStringExtension { p, args, callInfo -> p._nres(args, callInfo) }

/**
 * Alias for [nresonance]. Sets the notch filter Q.
 *
 * @alias nresonance
 * @category effects
 * @tags nres, nresonance, notch filter, Q
 */
@StrudelDsl
fun nres(q: PatternLike): StrudelPattern = _nres(listOf(q).asStrudelDslArgs())

/** Alias for [nresonance] on this pattern. */
@StrudelDsl
fun StrudelPattern.nres(q: PatternLike): StrudelPattern = this._nres(listOf(q).asStrudelDslArgs())

/** Alias for [nresonance] on a string pattern. */
@StrudelDsl
fun String.nres(q: PatternLike): StrudelPattern = this._nres(listOf(q).asStrudelDslArgs())

// -- lpattack() - Low Pass Filter Envelope Attack -----------------------------------------------------------------------

private val lpattackMutation = voiceModifier { copy(lpattack = it?.asDoubleOrNull()) }

fun applyLpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpattackMutation)
}

internal val _lpattack by dslPatternFunction { args, _ -> args.toPattern(lpattackMutation) }
internal val StrudelPattern._lpattack by dslPatternExtension { p, args, _ -> applyLpattack(p, args) }
internal val String._lpattack by dslStringExtension { p, args, callInfo -> p._lpattack(args, callInfo) }

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
fun lpattack(seconds: PatternLike): StrudelPattern = _lpattack(listOf(seconds).asStrudelDslArgs())

/** Sets the LPF envelope attack time on this pattern. */
@StrudelDsl
fun StrudelPattern.lpattack(seconds: PatternLike): StrudelPattern = this._lpattack(listOf(seconds).asStrudelDslArgs())

/** Sets the LPF envelope attack time on a string pattern. */
@StrudelDsl
fun String.lpattack(seconds: PatternLike): StrudelPattern = this._lpattack(listOf(seconds).asStrudelDslArgs())

internal val _lpa by dslPatternFunction { args, _ -> args.toPattern(lpattackMutation) }
internal val StrudelPattern._lpa by dslPatternExtension { p, args, _ -> applyLpattack(p, args) }
internal val String._lpa by dslStringExtension { p, args, callInfo -> p._lpa(args, callInfo) }

/**
 * Alias for [lpattack]. Sets the LPF envelope attack time.
 *
 * @alias lpattack
 * @category effects
 * @tags lpa, lpattack, low pass filter, envelope, attack
 */
@StrudelDsl
fun lpa(seconds: PatternLike): StrudelPattern = _lpa(listOf(seconds).asStrudelDslArgs())

/** Alias for [lpattack] on this pattern. */
@StrudelDsl
fun StrudelPattern.lpa(seconds: PatternLike): StrudelPattern = this._lpa(listOf(seconds).asStrudelDslArgs())

/** Alias for [lpattack] on a string pattern. */
@StrudelDsl
fun String.lpa(seconds: PatternLike): StrudelPattern = this._lpa(listOf(seconds).asStrudelDslArgs())

// -- lpdecay() - Low Pass Filter Envelope Decay -------------------------------------------------------------------------

private val lpdecayMutation = voiceModifier { copy(lpdecay = it?.asDoubleOrNull()) }

fun applyLpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpdecayMutation)
}

internal val _lpdecay by dslPatternFunction { args, _ -> args.toPattern(lpdecayMutation) }
internal val StrudelPattern._lpdecay by dslPatternExtension { p, args, _ -> applyLpdecay(p, args) }
internal val String._lpdecay by dslStringExtension { p, args, callInfo -> p._lpdecay(args, callInfo) }

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
fun lpdecay(seconds: PatternLike): StrudelPattern = _lpdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the LPF envelope decay time on this pattern. */
@StrudelDsl
fun StrudelPattern.lpdecay(seconds: PatternLike): StrudelPattern = this._lpdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the LPF envelope decay time on a string pattern. */
@StrudelDsl
fun String.lpdecay(seconds: PatternLike): StrudelPattern = this._lpdecay(listOf(seconds).asStrudelDslArgs())

internal val _lpd by dslPatternFunction { args, _ -> args.toPattern(lpdecayMutation) }
internal val StrudelPattern._lpd by dslPatternExtension { p, args, _ -> applyLpdecay(p, args) }
internal val String._lpd by dslStringExtension { p, args, callInfo -> p._lpd(args, callInfo) }

/**
 * Alias for [lpdecay]. Sets the LPF envelope decay time.
 *
 * @alias lpdecay
 * @category effects
 * @tags lpd, lpdecay, low pass filter, envelope, decay
 */
@StrudelDsl
fun lpd(seconds: PatternLike): StrudelPattern = _lpd(listOf(seconds).asStrudelDslArgs())

/** Alias for [lpdecay] on this pattern. */
@StrudelDsl
fun StrudelPattern.lpd(seconds: PatternLike): StrudelPattern = this._lpd(listOf(seconds).asStrudelDslArgs())

/** Alias for [lpdecay] on a string pattern. */
@StrudelDsl
fun String.lpd(seconds: PatternLike): StrudelPattern = this._lpd(listOf(seconds).asStrudelDslArgs())

// -- lpsustain() - Low Pass Filter Envelope Sustain ---------------------------------------------------------------------

private val lpsustainMutation = voiceModifier { copy(lpsustain = it?.asDoubleOrNull()) }

fun applyLpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpsustainMutation)
}

internal val _lpsustain by dslPatternFunction { args, _ -> args.toPattern(lpsustainMutation) }
internal val StrudelPattern._lpsustain by dslPatternExtension { p, args, _ -> applyLpsustain(p, args) }
internal val String._lpsustain by dslStringExtension { p, args, callInfo -> p._lpsustain(args, callInfo) }

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
fun lpsustain(level: PatternLike): StrudelPattern = _lpsustain(listOf(level).asStrudelDslArgs())

/** Sets the LPF envelope sustain level on this pattern. */
@StrudelDsl
fun StrudelPattern.lpsustain(level: PatternLike): StrudelPattern = this._lpsustain(listOf(level).asStrudelDslArgs())

/** Sets the LPF envelope sustain level on a string pattern. */
@StrudelDsl
fun String.lpsustain(level: PatternLike): StrudelPattern = this._lpsustain(listOf(level).asStrudelDslArgs())

internal val _lps by dslPatternFunction { args, _ -> args.toPattern(lpsustainMutation) }
internal val StrudelPattern._lps by dslPatternExtension { p, args, _ -> applyLpsustain(p, args) }
internal val String._lps by dslStringExtension { p, args, callInfo -> p._lps(args, callInfo) }

/**
 * Alias for [lpsustain]. Sets the LPF envelope sustain level.
 *
 * @alias lpsustain
 * @category effects
 * @tags lps, lpsustain, low pass filter, envelope, sustain
 */
@StrudelDsl
fun lps(level: PatternLike): StrudelPattern = _lps(listOf(level).asStrudelDslArgs())

/** Alias for [lpsustain] on this pattern. */
@StrudelDsl
fun StrudelPattern.lps(level: PatternLike): StrudelPattern = this._lps(listOf(level).asStrudelDslArgs())

/** Alias for [lpsustain] on a string pattern. */
@StrudelDsl
fun String.lps(level: PatternLike): StrudelPattern = this._lps(listOf(level).asStrudelDslArgs())

// -- lprelease() - Low Pass Filter Envelope Release ---------------------------------------------------------------------

private val lpreleaseMutation = voiceModifier { copy(lprelease = it?.asDoubleOrNull()) }

fun applyLprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpreleaseMutation)
}

internal val _lprelease by dslPatternFunction { args, _ -> args.toPattern(lpreleaseMutation) }
internal val StrudelPattern._lprelease by dslPatternExtension { p, args, _ -> applyLprelease(p, args) }
internal val String._lprelease by dslStringExtension { p, args, callInfo -> p._lprelease(args, callInfo) }

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
fun lprelease(seconds: PatternLike): StrudelPattern = _lprelease(listOf(seconds).asStrudelDslArgs())

/** Sets the LPF envelope release time on this pattern. */
@StrudelDsl
fun StrudelPattern.lprelease(seconds: PatternLike): StrudelPattern = this._lprelease(listOf(seconds).asStrudelDslArgs())

/** Sets the LPF envelope release time on a string pattern. */
@StrudelDsl
fun String.lprelease(seconds: PatternLike): StrudelPattern = this._lprelease(listOf(seconds).asStrudelDslArgs())

internal val _lpr by dslPatternFunction { args, _ -> args.toPattern(lpreleaseMutation) }
internal val StrudelPattern._lpr by dslPatternExtension { p, args, _ -> applyLprelease(p, args) }
internal val String._lpr by dslStringExtension { p, args, callInfo -> p._lpr(args, callInfo) }

/**
 * Alias for [lprelease]. Sets the LPF envelope release time.
 *
 * @alias lprelease
 * @category effects
 * @tags lpr, lprelease, low pass filter, envelope, release
 */
@StrudelDsl
fun lpr(seconds: PatternLike): StrudelPattern = _lpr(listOf(seconds).asStrudelDslArgs())

/** Alias for [lprelease] on this pattern. */
@StrudelDsl
fun StrudelPattern.lpr(seconds: PatternLike): StrudelPattern = this._lpr(listOf(seconds).asStrudelDslArgs())

/** Alias for [lprelease] on a string pattern. */
@StrudelDsl
fun String.lpr(seconds: PatternLike): StrudelPattern = this._lpr(listOf(seconds).asStrudelDslArgs())

// -- lpenv() - Low Pass Filter Envelope Depth ---------------------------------------------------------------------------

private val lpenvMutation = voiceModifier { copy(lpenv = it?.asDoubleOrNull()) }

fun applyLpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, lpenvMutation)
}

internal val _lpenv by dslPatternFunction { args, _ -> args.toPattern(lpenvMutation) }
internal val StrudelPattern._lpenv by dslPatternExtension { p, args, _ -> applyLpenv(p, args) }
internal val String._lpenv by dslStringExtension { p, args, callInfo -> p._lpenv(args, callInfo) }

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
fun lpenv(depth: PatternLike): StrudelPattern = _lpenv(listOf(depth).asStrudelDslArgs())

/** Sets the LPF envelope depth/amount on this pattern. */
@StrudelDsl
fun StrudelPattern.lpenv(depth: PatternLike): StrudelPattern = this._lpenv(listOf(depth).asStrudelDslArgs())

/** Sets the LPF envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.lpenv(depth: PatternLike): StrudelPattern = this._lpenv(listOf(depth).asStrudelDslArgs())

internal val _lpe by dslPatternFunction { args, _ -> args.toPattern(lpenvMutation) }
internal val StrudelPattern._lpe by dslPatternExtension { p, args, _ -> applyLpenv(p, args) }
internal val String._lpe by dslStringExtension { p, args, callInfo -> p._lpe(args, callInfo) }

/**
 * Alias for [lpenv]. Sets the LPF envelope depth.
 *
 * @alias lpenv
 * @category effects
 * @tags lpe, lpenv, low pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun lpe(depth: PatternLike): StrudelPattern = _lpe(listOf(depth).asStrudelDslArgs())

/** Alias for [lpenv] on this pattern. */
@StrudelDsl
fun StrudelPattern.lpe(depth: PatternLike): StrudelPattern = this._lpe(listOf(depth).asStrudelDslArgs())

/** Alias for [lpenv] on a string pattern. */
@StrudelDsl
fun String.lpe(depth: PatternLike): StrudelPattern = this._lpe(listOf(depth).asStrudelDslArgs())

// -- hpattack() - High Pass Filter Envelope Attack ----------------------------------------------------------------------

private val hpattackMutation = voiceModifier { copy(hpattack = it?.asDoubleOrNull()) }

fun applyHpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpattackMutation)
}

internal val _hpattack by dslPatternFunction { args, _ -> args.toPattern(hpattackMutation) }
internal val StrudelPattern._hpattack by dslPatternExtension { p, args, _ -> applyHpattack(p, args) }
internal val String._hpattack by dslStringExtension { p, args, callInfo -> p._hpattack(args, callInfo) }

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
fun hpattack(seconds: PatternLike): StrudelPattern = _hpattack(listOf(seconds).asStrudelDslArgs())

/** Sets the HPF envelope attack time on this pattern. */
@StrudelDsl
fun StrudelPattern.hpattack(seconds: PatternLike): StrudelPattern = this._hpattack(listOf(seconds).asStrudelDslArgs())

/** Sets the HPF envelope attack time on a string pattern. */
@StrudelDsl
fun String.hpattack(seconds: PatternLike): StrudelPattern = this._hpattack(listOf(seconds).asStrudelDslArgs())

internal val _hpa by dslPatternFunction { args, _ -> args.toPattern(hpattackMutation) }
internal val StrudelPattern._hpa by dslPatternExtension { p, args, _ -> applyHpattack(p, args) }
internal val String._hpa by dslStringExtension { p, args, callInfo -> p._hpa(args, callInfo) }

/**
 * Alias for [hpattack]. Sets the HPF envelope attack time.
 *
 * @alias hpattack
 * @category effects
 * @tags hpa, hpattack, high pass filter, envelope, attack
 */
@StrudelDsl
fun hpa(seconds: PatternLike): StrudelPattern = _hpa(listOf(seconds).asStrudelDslArgs())

/** Alias for [hpattack] on this pattern. */
@StrudelDsl
fun StrudelPattern.hpa(seconds: PatternLike): StrudelPattern = this._hpa(listOf(seconds).asStrudelDslArgs())

/** Alias for [hpattack] on a string pattern. */
@StrudelDsl
fun String.hpa(seconds: PatternLike): StrudelPattern = this._hpa(listOf(seconds).asStrudelDslArgs())

// -- hpdecay() - High Pass Filter Envelope Decay ------------------------------------------------------------------------

private val hpdecayMutation = voiceModifier { copy(hpdecay = it?.asDoubleOrNull()) }

fun applyHpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpdecayMutation)
}

internal val _hpdecay by dslPatternFunction { args, _ -> args.toPattern(hpdecayMutation) }
internal val StrudelPattern._hpdecay by dslPatternExtension { p, args, _ -> applyHpdecay(p, args) }
internal val String._hpdecay by dslStringExtension { p, args, callInfo -> p._hpdecay(args, callInfo) }

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
fun hpdecay(seconds: PatternLike): StrudelPattern = _hpdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the HPF envelope decay time on this pattern. */
@StrudelDsl
fun StrudelPattern.hpdecay(seconds: PatternLike): StrudelPattern = this._hpdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the HPF envelope decay time on a string pattern. */
@StrudelDsl
fun String.hpdecay(seconds: PatternLike): StrudelPattern = this._hpdecay(listOf(seconds).asStrudelDslArgs())

internal val _hpd by dslPatternFunction { args, _ -> args.toPattern(hpdecayMutation) }
internal val StrudelPattern._hpd by dslPatternExtension { p, args, _ -> applyHpdecay(p, args) }
internal val String._hpd by dslStringExtension { p, args, callInfo -> p._hpd(args, callInfo) }

/**
 * Alias for [hpdecay]. Sets the HPF envelope decay time.
 *
 * @alias hpdecay
 * @category effects
 * @tags hpd, hpdecay, high pass filter, envelope, decay
 */
@StrudelDsl
fun hpd(seconds: PatternLike): StrudelPattern = _hpd(listOf(seconds).asStrudelDslArgs())

/** Alias for [hpdecay] on this pattern. */
@StrudelDsl
fun StrudelPattern.hpd(seconds: PatternLike): StrudelPattern = this._hpd(listOf(seconds).asStrudelDslArgs())

/** Alias for [hpdecay] on a string pattern. */
@StrudelDsl
fun String.hpd(seconds: PatternLike): StrudelPattern = this._hpd(listOf(seconds).asStrudelDslArgs())

// -- hpsustain() - High Pass Filter Envelope Sustain --------------------------------------------------------------------

private val hpsustainMutation = voiceModifier { copy(hpsustain = it?.asDoubleOrNull()) }

fun applyHpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpsustainMutation)
}

internal val _hpsustain by dslPatternFunction { args, _ -> args.toPattern(hpsustainMutation) }
internal val StrudelPattern._hpsustain by dslPatternExtension { p, args, _ -> applyHpsustain(p, args) }
internal val String._hpsustain by dslStringExtension { p, args, callInfo -> p._hpsustain(args, callInfo) }

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
fun hpsustain(level: PatternLike): StrudelPattern = _hpsustain(listOf(level).asStrudelDslArgs())

/** Sets the HPF envelope sustain level on this pattern. */
@StrudelDsl
fun StrudelPattern.hpsustain(level: PatternLike): StrudelPattern = this._hpsustain(listOf(level).asStrudelDslArgs())

/** Sets the HPF envelope sustain level on a string pattern. */
@StrudelDsl
fun String.hpsustain(level: PatternLike): StrudelPattern = this._hpsustain(listOf(level).asStrudelDslArgs())

internal val _hps by dslPatternFunction { args, _ -> args.toPattern(hpsustainMutation) }
internal val StrudelPattern._hps by dslPatternExtension { p, args, _ -> applyHpsustain(p, args) }
internal val String._hps by dslStringExtension { p, args, callInfo -> p._hps(args, callInfo) }

/**
 * Alias for [hpsustain]. Sets the HPF envelope sustain level.
 *
 * @alias hpsustain
 * @category effects
 * @tags hps, hpsustain, high pass filter, envelope, sustain
 */
@StrudelDsl
fun hps(level: PatternLike): StrudelPattern = _hps(listOf(level).asStrudelDslArgs())

/** Alias for [hpsustain] on this pattern. */
@StrudelDsl
fun StrudelPattern.hps(level: PatternLike): StrudelPattern = this._hps(listOf(level).asStrudelDslArgs())

/** Alias for [hpsustain] on a string pattern. */
@StrudelDsl
fun String.hps(level: PatternLike): StrudelPattern = this._hps(listOf(level).asStrudelDslArgs())

// -- hprelease() - High Pass Filter Envelope Release --------------------------------------------------------------------

private val hpreleaseMutation = voiceModifier { copy(hprelease = it?.asDoubleOrNull()) }

fun applyHprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpreleaseMutation)
}

internal val _hprelease by dslPatternFunction { args, _ -> args.toPattern(hpreleaseMutation) }
internal val StrudelPattern._hprelease by dslPatternExtension { p, args, _ -> applyHprelease(p, args) }
internal val String._hprelease by dslStringExtension { p, args, callInfo -> p._hprelease(args, callInfo) }

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
fun hprelease(seconds: PatternLike): StrudelPattern = _hprelease(listOf(seconds).asStrudelDslArgs())

/** Sets the HPF envelope release time on this pattern. */
@StrudelDsl
fun StrudelPattern.hprelease(seconds: PatternLike): StrudelPattern = this._hprelease(listOf(seconds).asStrudelDslArgs())

/** Sets the HPF envelope release time on a string pattern. */
@StrudelDsl
fun String.hprelease(seconds: PatternLike): StrudelPattern = this._hprelease(listOf(seconds).asStrudelDslArgs())

internal val _hpr by dslPatternFunction { args, _ -> args.toPattern(hpreleaseMutation) }
internal val StrudelPattern._hpr by dslPatternExtension { p, args, _ -> applyHprelease(p, args) }
internal val String._hpr by dslStringExtension { p, args, callInfo -> p._hpr(args, callInfo) }

/**
 * Alias for [hprelease]. Sets the HPF envelope release time.
 *
 * @alias hprelease
 * @category effects
 * @tags hpr, hprelease, high pass filter, envelope, release
 */
@StrudelDsl
fun hpr(seconds: PatternLike): StrudelPattern = _hpr(listOf(seconds).asStrudelDslArgs())

/** Alias for [hprelease] on this pattern. */
@StrudelDsl
fun StrudelPattern.hpr(seconds: PatternLike): StrudelPattern = this._hpr(listOf(seconds).asStrudelDslArgs())

/** Alias for [hprelease] on a string pattern. */
@StrudelDsl
fun String.hpr(seconds: PatternLike): StrudelPattern = this._hpr(listOf(seconds).asStrudelDslArgs())

// -- hpenv() - High Pass Filter Envelope Depth --------------------------------------------------------------------------

private val hpenvMutation = voiceModifier { copy(hpenv = it?.asDoubleOrNull()) }

fun applyHpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, hpenvMutation)
}

internal val _hpenv by dslPatternFunction { args, _ -> args.toPattern(hpenvMutation) }
internal val StrudelPattern._hpenv by dslPatternExtension { p, args, _ -> applyHpenv(p, args) }
internal val String._hpenv by dslStringExtension { p, args, callInfo -> p._hpenv(args, callInfo) }

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
fun hpenv(depth: PatternLike): StrudelPattern = _hpenv(listOf(depth).asStrudelDslArgs())

/** Sets the HPF envelope depth/amount on this pattern. */
@StrudelDsl
fun StrudelPattern.hpenv(depth: PatternLike): StrudelPattern = this._hpenv(listOf(depth).asStrudelDslArgs())

/** Sets the HPF envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.hpenv(depth: PatternLike): StrudelPattern = this._hpenv(listOf(depth).asStrudelDslArgs())

internal val _hpe by dslPatternFunction { args, _ -> args.toPattern(hpenvMutation) }
internal val StrudelPattern._hpe by dslPatternExtension { p, args, _ -> applyHpenv(p, args) }
internal val String._hpe by dslStringExtension { p, args, callInfo -> p._hpe(args, callInfo) }

/**
 * Alias for [hpenv]. Sets the HPF envelope depth.
 *
 * @alias hpenv
 * @category effects
 * @tags hpe, hpenv, high pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun hpe(depth: PatternLike): StrudelPattern = _hpe(listOf(depth).asStrudelDslArgs())

/** Alias for [hpenv] on this pattern. */
@StrudelDsl
fun StrudelPattern.hpe(depth: PatternLike): StrudelPattern = this._hpe(listOf(depth).asStrudelDslArgs())

/** Alias for [hpenv] on a string pattern. */
@StrudelDsl
fun String.hpe(depth: PatternLike): StrudelPattern = this._hpe(listOf(depth).asStrudelDslArgs())

// -- bpattack() - Band Pass Filter Envelope Attack ----------------------------------------------------------------------

private val bpattackMutation = voiceModifier { copy(bpattack = it?.asDoubleOrNull()) }

fun applyBpattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpattackMutation)
}

internal val _bpattack by dslPatternFunction { args, _ -> args.toPattern(bpattackMutation) }
internal val StrudelPattern._bpattack by dslPatternExtension { p, args, _ -> applyBpattack(p, args) }
internal val String._bpattack by dslStringExtension { p, args, callInfo -> p._bpattack(args, callInfo) }

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
fun bpattack(seconds: PatternLike): StrudelPattern = _bpattack(listOf(seconds).asStrudelDslArgs())

/** Sets the BPF envelope attack time on this pattern. */
@StrudelDsl
fun StrudelPattern.bpattack(seconds: PatternLike): StrudelPattern = this._bpattack(listOf(seconds).asStrudelDslArgs())

/** Sets the BPF envelope attack time on a string pattern. */
@StrudelDsl
fun String.bpattack(seconds: PatternLike): StrudelPattern = this._bpattack(listOf(seconds).asStrudelDslArgs())

internal val _bpa by dslPatternFunction { args, _ -> args.toPattern(bpattackMutation) }
internal val StrudelPattern._bpa by dslPatternExtension { p, args, _ -> applyBpattack(p, args) }
internal val String._bpa by dslStringExtension { p, args, callInfo -> p._bpa(args, callInfo) }

/**
 * Alias for [bpattack]. Sets the BPF envelope attack time.
 *
 * @alias bpattack
 * @category effects
 * @tags bpa, bpattack, band pass filter, envelope, attack
 */
@StrudelDsl
fun bpa(seconds: PatternLike): StrudelPattern = _bpa(listOf(seconds).asStrudelDslArgs())

/** Alias for [bpattack] on this pattern. */
@StrudelDsl
fun StrudelPattern.bpa(seconds: PatternLike): StrudelPattern = this._bpa(listOf(seconds).asStrudelDslArgs())

/** Alias for [bpattack] on a string pattern. */
@StrudelDsl
fun String.bpa(seconds: PatternLike): StrudelPattern = this._bpa(listOf(seconds).asStrudelDslArgs())

// -- bpdecay() - Band Pass Filter Envelope Decay ------------------------------------------------------------------------

private val bpdecayMutation = voiceModifier { copy(bpdecay = it?.asDoubleOrNull()) }

fun applyBpdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpdecayMutation)
}

internal val _bpdecay by dslPatternFunction { args, _ -> args.toPattern(bpdecayMutation) }
internal val StrudelPattern._bpdecay by dslPatternExtension { p, args, _ -> applyBpdecay(p, args) }
internal val String._bpdecay by dslStringExtension { p, args, callInfo -> p._bpdecay(args, callInfo) }

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
fun bpdecay(seconds: PatternLike): StrudelPattern = _bpdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the BPF envelope decay time on this pattern. */
@StrudelDsl
fun StrudelPattern.bpdecay(seconds: PatternLike): StrudelPattern = this._bpdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the BPF envelope decay time on a string pattern. */
@StrudelDsl
fun String.bpdecay(seconds: PatternLike): StrudelPattern = this._bpdecay(listOf(seconds).asStrudelDslArgs())

internal val _bpd by dslPatternFunction { args, _ -> args.toPattern(bpdecayMutation) }
internal val StrudelPattern._bpd by dslPatternExtension { p, args, _ -> applyBpdecay(p, args) }
internal val String._bpd by dslStringExtension { p, args, callInfo -> p._bpd(args, callInfo) }

/**
 * Alias for [bpdecay]. Sets the BPF envelope decay time.
 *
 * @alias bpdecay
 * @category effects
 * @tags bpd, bpdecay, band pass filter, envelope, decay
 */
@StrudelDsl
fun bpd(seconds: PatternLike): StrudelPattern = _bpd(listOf(seconds).asStrudelDslArgs())

/** Alias for [bpdecay] on this pattern. */
@StrudelDsl
fun StrudelPattern.bpd(seconds: PatternLike): StrudelPattern = this._bpd(listOf(seconds).asStrudelDslArgs())

/** Alias for [bpdecay] on a string pattern. */
@StrudelDsl
fun String.bpd(seconds: PatternLike): StrudelPattern = this._bpd(listOf(seconds).asStrudelDslArgs())

// -- bpsustain() - Band Pass Filter Envelope Sustain --------------------------------------------------------------------

private val bpsustainMutation = voiceModifier { copy(bpsustain = it?.asDoubleOrNull()) }

fun applyBpsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpsustainMutation)
}

internal val _bpsustain by dslPatternFunction { args, _ -> args.toPattern(bpsustainMutation) }
internal val StrudelPattern._bpsustain by dslPatternExtension { p, args, _ -> applyBpsustain(p, args) }
internal val String._bpsustain by dslStringExtension { p, args, callInfo -> p._bpsustain(args, callInfo) }

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
fun bpsustain(level: PatternLike): StrudelPattern = _bpsustain(listOf(level).asStrudelDslArgs())

/** Sets the BPF envelope sustain level on this pattern. */
@StrudelDsl
fun StrudelPattern.bpsustain(level: PatternLike): StrudelPattern = this._bpsustain(listOf(level).asStrudelDslArgs())

/** Sets the BPF envelope sustain level on a string pattern. */
@StrudelDsl
fun String.bpsustain(level: PatternLike): StrudelPattern = this._bpsustain(listOf(level).asStrudelDslArgs())

internal val _bps by dslPatternFunction { args, _ -> args.toPattern(bpsustainMutation) }
internal val StrudelPattern._bps by dslPatternExtension { p, args, _ -> applyBpsustain(p, args) }
internal val String._bps by dslStringExtension { p, args, callInfo -> p._bps(args, callInfo) }

/**
 * Alias for [bpsustain]. Sets the BPF envelope sustain level.
 *
 * @alias bpsustain
 * @category effects
 * @tags bps, bpsustain, band pass filter, envelope, sustain
 */
@StrudelDsl
fun bps(level: PatternLike): StrudelPattern = _bps(listOf(level).asStrudelDslArgs())

/** Alias for [bpsustain] on this pattern. */
@StrudelDsl
fun StrudelPattern.bps(level: PatternLike): StrudelPattern = this._bps(listOf(level).asStrudelDslArgs())

/** Alias for [bpsustain] on a string pattern. */
@StrudelDsl
fun String.bps(level: PatternLike): StrudelPattern = this._bps(listOf(level).asStrudelDslArgs())

// -- bprelease() - Band Pass Filter Envelope Release --------------------------------------------------------------------

private val bpreleaseMutation = voiceModifier { copy(bprelease = it?.asDoubleOrNull()) }

fun applyBprelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpreleaseMutation)
}

internal val _bprelease by dslPatternFunction { args, _ -> args.toPattern(bpreleaseMutation) }
internal val StrudelPattern._bprelease by dslPatternExtension { p, args, _ -> applyBprelease(p, args) }
internal val String._bprelease by dslStringExtension { p, args, callInfo -> p._bprelease(args, callInfo) }

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
fun bprelease(seconds: PatternLike): StrudelPattern = _bprelease(listOf(seconds).asStrudelDslArgs())

/** Sets the BPF envelope release time on this pattern. */
@StrudelDsl
fun StrudelPattern.bprelease(seconds: PatternLike): StrudelPattern = this._bprelease(listOf(seconds).asStrudelDslArgs())

/** Sets the BPF envelope release time on a string pattern. */
@StrudelDsl
fun String.bprelease(seconds: PatternLike): StrudelPattern = this._bprelease(listOf(seconds).asStrudelDslArgs())

internal val _bpr by dslPatternFunction { args, _ -> args.toPattern(bpreleaseMutation) }
internal val StrudelPattern._bpr by dslPatternExtension { p, args, _ -> applyBprelease(p, args) }
internal val String._bpr by dslStringExtension { p, args, callInfo -> p._bpr(args, callInfo) }

/**
 * Alias for [bprelease]. Sets the BPF envelope release time.
 *
 * @alias bprelease
 * @category effects
 * @tags bpr, bprelease, band pass filter, envelope, release
 */
@StrudelDsl
fun bpr(seconds: PatternLike): StrudelPattern = _bpr(listOf(seconds).asStrudelDslArgs())

/** Alias for [bprelease] on this pattern. */
@StrudelDsl
fun StrudelPattern.bpr(seconds: PatternLike): StrudelPattern = this._bpr(listOf(seconds).asStrudelDslArgs())

/** Alias for [bprelease] on a string pattern. */
@StrudelDsl
fun String.bpr(seconds: PatternLike): StrudelPattern = this._bpr(listOf(seconds).asStrudelDslArgs())

// -- bpenv() - Band Pass Filter Envelope Depth --------------------------------------------------------------------------

private val bpenvMutation = voiceModifier { copy(bpenv = it?.asDoubleOrNull()) }

fun applyBpenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, bpenvMutation)
}

internal val _bpenv by dslPatternFunction { args, _ -> args.toPattern(bpenvMutation) }
internal val StrudelPattern._bpenv by dslPatternExtension { p, args, _ -> applyBpenv(p, args) }
internal val String._bpenv by dslStringExtension { p, args, callInfo -> p._bpenv(args, callInfo) }

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
fun bpenv(depth: PatternLike): StrudelPattern = _bpenv(listOf(depth).asStrudelDslArgs())

/** Sets the BPF envelope depth/amount on this pattern. */
@StrudelDsl
fun StrudelPattern.bpenv(depth: PatternLike): StrudelPattern = this._bpenv(listOf(depth).asStrudelDslArgs())

/** Sets the BPF envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.bpenv(depth: PatternLike): StrudelPattern = this._bpenv(listOf(depth).asStrudelDslArgs())

internal val _bpe by dslPatternFunction { args, _ -> args.toPattern(bpenvMutation) }
internal val StrudelPattern._bpe by dslPatternExtension { p, args, _ -> applyBpenv(p, args) }
internal val String._bpe by dslStringExtension { p, args, callInfo -> p._bpe(args, callInfo) }

/**
 * Alias for [bpenv]. Sets the BPF envelope depth.
 *
 * @alias bpenv
 * @category effects
 * @tags bpe, bpenv, band pass filter, envelope, depth, modulation
 */
@StrudelDsl
fun bpe(depth: PatternLike): StrudelPattern = _bpe(listOf(depth).asStrudelDslArgs())

/** Alias for [bpenv] on this pattern. */
@StrudelDsl
fun StrudelPattern.bpe(depth: PatternLike): StrudelPattern = this._bpe(listOf(depth).asStrudelDslArgs())

/** Alias for [bpenv] on a string pattern. */
@StrudelDsl
fun String.bpe(depth: PatternLike): StrudelPattern = this._bpe(listOf(depth).asStrudelDslArgs())

// -- nfattack() - Notch Filter Envelope Attack (NOT IN ORIGINAL STRUDEL) -------------------------------------------------

private val nfattackMutation = voiceModifier { copy(nfattack = it?.asDoubleOrNull()) }

fun applyNfattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfattackMutation)
}

internal val _nfattack by dslPatternFunction { args, _ -> args.toPattern(nfattackMutation) }
internal val StrudelPattern._nfattack by dslPatternExtension { p, args, _ -> applyNfattack(p, args) }
internal val String._nfattack by dslStringExtension { p, args, callInfo -> p._nfattack(args, callInfo) }

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
fun nfattack(seconds: PatternLike): StrudelPattern = _nfattack(listOf(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope attack time on this pattern. */
@StrudelDsl
fun StrudelPattern.nfattack(seconds: PatternLike): StrudelPattern = this._nfattack(listOf(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope attack time on a string pattern. */
@StrudelDsl
fun String.nfattack(seconds: PatternLike): StrudelPattern = this._nfattack(listOf(seconds).asStrudelDslArgs())

internal val _nfa by dslPatternFunction { args, _ -> args.toPattern(nfattackMutation) }
internal val StrudelPattern._nfa by dslPatternExtension { p, args, _ -> applyNfattack(p, args) }
internal val String._nfa by dslStringExtension { p, args, callInfo -> p._nfa(args, callInfo) }

/**
 * Alias for [nfattack]. Sets the notch filter envelope attack time.
 *
 * @alias nfattack
 * @category effects
 * @tags nfa, nfattack, notch filter, envelope, attack
 */
@StrudelDsl
fun nfa(seconds: PatternLike): StrudelPattern = _nfa(listOf(seconds).asStrudelDslArgs())

/** Alias for [nfattack] on this pattern. */
@StrudelDsl
fun StrudelPattern.nfa(seconds: PatternLike): StrudelPattern = this._nfa(listOf(seconds).asStrudelDslArgs())

/** Alias for [nfattack] on a string pattern. */
@StrudelDsl
fun String.nfa(seconds: PatternLike): StrudelPattern = this._nfa(listOf(seconds).asStrudelDslArgs())

// -- nfdecay() - Notch Filter Envelope Decay (NOT IN ORIGINAL STRUDEL) ---------------------------------------------------

private val nfdecayMutation = voiceModifier { copy(nfdecay = it?.asDoubleOrNull()) }

fun applyNfdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfdecayMutation)
}

internal val _nfdecay by dslPatternFunction { args, _ -> args.toPattern(nfdecayMutation) }
internal val StrudelPattern._nfdecay by dslPatternExtension { p, args, _ -> applyNfdecay(p, args) }
internal val String._nfdecay by dslStringExtension { p, args, callInfo -> p._nfdecay(args, callInfo) }

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
fun nfdecay(seconds: PatternLike): StrudelPattern = _nfdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope decay time on this pattern. */
@StrudelDsl
fun StrudelPattern.nfdecay(seconds: PatternLike): StrudelPattern = this._nfdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope decay time on a string pattern. */
@StrudelDsl
fun String.nfdecay(seconds: PatternLike): StrudelPattern = this._nfdecay(listOf(seconds).asStrudelDslArgs())

internal val _nfd by dslPatternFunction { args, _ -> args.toPattern(nfdecayMutation) }
internal val StrudelPattern._nfd by dslPatternExtension { p, args, _ -> applyNfdecay(p, args) }
internal val String._nfd by dslStringExtension { p, args, callInfo -> p._nfd(args, callInfo) }

/**
 * Alias for [nfdecay]. Sets the notch filter envelope decay time.
 *
 * @alias nfdecay
 * @category effects
 * @tags nfd, nfdecay, notch filter, envelope, decay
 */
@StrudelDsl
fun nfd(seconds: PatternLike): StrudelPattern = _nfd(listOf(seconds).asStrudelDslArgs())

/** Alias for [nfdecay] on this pattern. */
@StrudelDsl
fun StrudelPattern.nfd(seconds: PatternLike): StrudelPattern = this._nfd(listOf(seconds).asStrudelDslArgs())

/** Alias for [nfdecay] on a string pattern. */
@StrudelDsl
fun String.nfd(seconds: PatternLike): StrudelPattern = this._nfd(listOf(seconds).asStrudelDslArgs())

// -- nfsustain() - Notch Filter Envelope Sustain (NOT IN ORIGINAL STRUDEL) -----------------------------------------------

private val nfsustainMutation = voiceModifier { copy(nfsustain = it?.asDoubleOrNull()) }

fun applyNfsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfsustainMutation)
}

internal val _nfsustain by dslPatternFunction { args, _ -> args.toPattern(nfsustainMutation) }
internal val StrudelPattern._nfsustain by dslPatternExtension { p, args, _ -> applyNfsustain(p, args) }
internal val String._nfsustain by dslStringExtension { p, args, callInfo -> p._nfsustain(args, callInfo) }

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
fun nfsustain(level: PatternLike): StrudelPattern = _nfsustain(listOf(level).asStrudelDslArgs())

/** Sets the notch filter envelope sustain level on this pattern. */
@StrudelDsl
fun StrudelPattern.nfsustain(level: PatternLike): StrudelPattern = this._nfsustain(listOf(level).asStrudelDslArgs())

/** Sets the notch filter envelope sustain level on a string pattern. */
@StrudelDsl
fun String.nfsustain(level: PatternLike): StrudelPattern = this._nfsustain(listOf(level).asStrudelDslArgs())

internal val _nfs by dslPatternFunction { args, _ -> args.toPattern(nfsustainMutation) }
internal val StrudelPattern._nfs by dslPatternExtension { p, args, _ -> applyNfsustain(p, args) }
internal val String._nfs by dslStringExtension { p, args, callInfo -> p._nfs(args, callInfo) }

/**
 * Alias for [nfsustain]. Sets the notch filter envelope sustain level.
 *
 * @alias nfsustain
 * @category effects
 * @tags nfs, nfsustain, notch filter, envelope, sustain
 */
@StrudelDsl
fun nfs(level: PatternLike): StrudelPattern = _nfs(listOf(level).asStrudelDslArgs())

/** Alias for [nfsustain] on this pattern. */
@StrudelDsl
fun StrudelPattern.nfs(level: PatternLike): StrudelPattern = this._nfs(listOf(level).asStrudelDslArgs())

/** Alias for [nfsustain] on a string pattern. */
@StrudelDsl
fun String.nfs(level: PatternLike): StrudelPattern = this._nfs(listOf(level).asStrudelDslArgs())

// -- nfrelease() - Notch Filter Envelope Release (NOT IN ORIGINAL STRUDEL) -----------------------------------------------

private val nfreleaseMutation = voiceModifier { copy(nfrelease = it?.asDoubleOrNull()) }

fun applyNfrelease(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfreleaseMutation)
}

internal val _nfrelease by dslPatternFunction { args, _ -> args.toPattern(nfreleaseMutation) }
internal val StrudelPattern._nfrelease by dslPatternExtension { p, args, _ -> applyNfrelease(p, args) }
internal val String._nfrelease by dslStringExtension { p, args, callInfo -> p._nfrelease(args, callInfo) }

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
fun nfrelease(seconds: PatternLike): StrudelPattern = _nfrelease(listOf(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope release time on this pattern. */
@StrudelDsl
fun StrudelPattern.nfrelease(seconds: PatternLike): StrudelPattern = this._nfrelease(listOf(seconds).asStrudelDslArgs())

/** Sets the notch filter envelope release time on a string pattern. */
@StrudelDsl
fun String.nfrelease(seconds: PatternLike): StrudelPattern = this._nfrelease(listOf(seconds).asStrudelDslArgs())

internal val _nfr by dslPatternFunction { args, _ -> args.toPattern(nfreleaseMutation) }
internal val StrudelPattern._nfr by dslPatternExtension { p, args, _ -> applyNfrelease(p, args) }
internal val String._nfr by dslStringExtension { p, args, callInfo -> p._nfr(args, callInfo) }

/**
 * Alias for [nfrelease]. Sets the notch filter envelope release time.
 *
 * @alias nfrelease
 * @category effects
 * @tags nfr, nfrelease, notch filter, envelope, release
 */
@StrudelDsl
fun nfr(seconds: PatternLike): StrudelPattern = _nfr(listOf(seconds).asStrudelDslArgs())

/** Alias for [nfrelease] on this pattern. */
@StrudelDsl
fun StrudelPattern.nfr(seconds: PatternLike): StrudelPattern = this._nfr(listOf(seconds).asStrudelDslArgs())

/** Alias for [nfrelease] on a string pattern. */
@StrudelDsl
fun String.nfr(seconds: PatternLike): StrudelPattern = this._nfr(listOf(seconds).asStrudelDslArgs())

// -- nfenv() - Notch Filter Envelope Depth (NOT IN ORIGINAL STRUDEL) -----------------------------------------------------

private val nfenvMutation = voiceModifier { copy(nfenv = it?.asDoubleOrNull()) }

fun applyNfenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, nfenvMutation)
}

internal val _nfenv by dslPatternFunction { args, _ -> args.toPattern(nfenvMutation) }
internal val StrudelPattern._nfenv by dslPatternExtension { p, args, _ -> applyNfenv(p, args) }
internal val String._nfenv by dslStringExtension { p, args, callInfo -> p._nfenv(args, callInfo) }

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
fun nfenv(depth: PatternLike): StrudelPattern = _nfenv(listOf(depth).asStrudelDslArgs())

/** Sets the notch filter envelope depth/amount on this pattern. */
@StrudelDsl
fun StrudelPattern.nfenv(depth: PatternLike): StrudelPattern = this._nfenv(listOf(depth).asStrudelDslArgs())

/** Sets the notch filter envelope depth/amount on a string pattern. */
@StrudelDsl
fun String.nfenv(depth: PatternLike): StrudelPattern = this._nfenv(listOf(depth).asStrudelDslArgs())

internal val _nfe by dslPatternFunction { args, _ -> args.toPattern(nfenvMutation) }
internal val StrudelPattern._nfe by dslPatternExtension { p, args, _ -> applyNfenv(p, args) }
internal val String._nfe by dslStringExtension { p, args, callInfo -> p._nfe(args, callInfo) }

/**
 * Alias for [nfenv]. Sets the notch filter envelope depth.
 *
 * @alias nfenv
 * @category effects
 * @tags nfe, nfenv, notch filter, envelope, depth, modulation
 */
@StrudelDsl
fun nfe(depth: PatternLike): StrudelPattern = _nfe(listOf(depth).asStrudelDslArgs())

/** Alias for [nfenv] on this pattern. */
@StrudelDsl
fun StrudelPattern.nfe(depth: PatternLike): StrudelPattern = this._nfe(listOf(depth).asStrudelDslArgs())

/** Alias for [nfenv] on a string pattern. */
@StrudelDsl
fun String.nfe(depth: PatternLike): StrudelPattern = this._nfe(listOf(depth).asStrudelDslArgs())
