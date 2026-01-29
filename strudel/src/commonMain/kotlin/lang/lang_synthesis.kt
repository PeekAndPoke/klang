@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._liftNumericField

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangSynthesisInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// FM Synthesis
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// -- fmh() ------------------------------------------------------------------------------------------------------------

/**
 * Sets the FM harmonicity ratio (carrier to modulator frequency ratio).
 *
 * The harmonicity ratio determines the relationship between the carrier and modulator frequencies
 * in FM synthesis. Ratios of 1, 2, 3, etc. produce harmonic sounds, while non-integer ratios
 * create inharmonic/bell-like timbres.
 *
 * @param ratio Frequency ratio (default ~1.0, typical range 0.5 to 8.0)
 *
 * @example
 * note("c3").fmh(2)
 * // Modulator is 2× carrier frequency (octave above) - classic FM brass sound
 *
 * @example
 * note("c3").fmh(1.5)
 * // Inharmonic ratio creates bell-like tones
 */
private val fmhMutation = voiceModifier {
    copy(fmh = it?.asDoubleOrNull())
}

private fun applyFmh(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmhMutation)
}

@StrudelDsl
val fmh by dslFunction { args, _ -> args.toPattern(fmhMutation) }

@StrudelDsl
val StrudelPattern.fmh by dslPatternExtension { p, args, _ -> applyFmh(p, args) }

@StrudelDsl
val String.fmh by dslStringExtension { p, args, callInfo -> p.fmh(args, callInfo) }

// -- fmattack() / fmatt() ---------------------------------------------------------------------------------------------

/**
 * Sets the attack time for the FM modulation envelope.
 *
 * Controls how quickly the FM modulation depth rises from 0 to its peak when a note starts.
 * Shorter times create percussive attacks, longer times create gradual pitch sweeps.
 *
 * @param seconds Attack time in seconds (typical range 0.0 to 2.0)
 *
 * @example
 * note("c3").fmattack(0.01).fmenv(100)
 * // Quick FM attack for plucked/struck sound
 *
 * @example
 * note("c3").fmattack(0.5).fmenv(100)
 * // Slow FM attack creates pitch sweep effect
 */
private val fmattackMutation = voiceModifier {
    copy(fmAttack = it?.asDoubleOrNull())
}

private fun applyFmattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmattackMutation)
}

@StrudelDsl
val fmattack by dslFunction { args, _ -> args.toPattern(fmattackMutation) }

@StrudelDsl
val StrudelPattern.fmattack by dslPatternExtension { p, args, _ -> applyFmattack(p, args) }

@StrudelDsl
val String.fmattack by dslStringExtension { p, args, callInfo -> p.fmattack(args, callInfo) }

/** Alias for [fmattack] */
@StrudelDsl
val fmatt by dslFunction { args, callInfo -> fmattack(args, callInfo) }

/** Alias for [fmattack] */
@StrudelDsl
val StrudelPattern.fmatt by dslPatternExtension { p, args, callInfo -> p.fmattack(args, callInfo) }

/** Alias for [fmattack] */
@StrudelDsl
val String.fmatt by dslStringExtension { p, args, callInfo -> p.fmattack(args, callInfo) }

// -- fmdecay() / fmdec() ----------------------------------------------------------------------------------------------

/**
 * Sets the decay time for the FM modulation envelope.
 *
 * Controls how quickly the FM modulation depth falls from its peak to the sustain level.
 * Affects the brightness evolution of the sound.
 *
 * @param seconds Decay time in seconds (typical range 0.0 to 2.0)
 *
 * @example
 * note("c3").fmattack(0.01).fmdecay(0.1).fmsustain(0.3).fmenv(100)
 * // Fast decay for plucky FM sound
 */
private val fmdecayMutation = voiceModifier {
    copy(fmDecay = it?.asDoubleOrNull())
}

private fun applyFmdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmdecayMutation)
}

@StrudelDsl
val fmdecay by dslFunction { args, _ -> args.toPattern(fmdecayMutation) }

@StrudelDsl
val StrudelPattern.fmdecay by dslPatternExtension { p, args, _ -> applyFmdecay(p, args) }

@StrudelDsl
val String.fmdecay by dslStringExtension { p, args, callInfo -> p.fmdecay(args, callInfo) }

/** Alias for [fmdecay] */
@StrudelDsl
val fmdec by dslFunction { args, callInfo -> fmdecay(args, callInfo) }

/** Alias for [fmdecay] */
@StrudelDsl
val StrudelPattern.fmdec by dslPatternExtension { p, args, callInfo -> p.fmdecay(args, callInfo) }

/** Alias for [fmdecay] */
@StrudelDsl
val String.fmdec by dslStringExtension { p, args, callInfo -> p.fmdecay(args, callInfo) }

// -- fmsustain() / fmsus() --------------------------------------------------------------------------------------------

/**
 * Sets the sustain level for the FM modulation envelope.
 *
 * Determines the FM modulation depth after the attack and decay phases, while the note is held.
 * Value is typically between 0.0 (no sustain) and 1.0 (full modulation).
 *
 * @param level Sustain level (0.0 to 1.0)
 *
 * @example
 * note("c3").fmsustain(0.0).fmenv(100)
 * // No sustained FM modulation - percussive sound
 *
 * @example
 * note("c3").fmsustain(0.7).fmenv(100)
 * // Sustained FM modulation - evolving timbre
 */
private val fmsustainMutation = voiceModifier {
    copy(fmSustain = it?.asDoubleOrNull())
}

private fun applyFmsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmsustainMutation)
}

@StrudelDsl
val fmsustain by dslFunction { args, _ -> args.toPattern(fmsustainMutation) }

@StrudelDsl
val StrudelPattern.fmsustain by dslPatternExtension { p, args, _ -> applyFmsustain(p, args) }

@StrudelDsl
val String.fmsustain by dslStringExtension { p, args, callInfo -> p.fmsustain(args, callInfo) }

/** Alias for [fmsustain] */
@StrudelDsl
val fmsus by dslFunction { args, callInfo -> fmsustain(args, callInfo) }

/** Alias for [fmsustain] */
@StrudelDsl
val StrudelPattern.fmsus by dslPatternExtension { p, args, callInfo -> p.fmsustain(args, callInfo) }

/** Alias for [fmsustain] */
@StrudelDsl
val String.fmsus by dslStringExtension { p, args, callInfo -> p.fmsustain(args, callInfo) }

// -- fmenv() / fmmod() ------------------------------------------------------------------------------------------------

/**
 * Sets the FM modulation depth/amount.
 *
 * This is the primary control for FM synthesis intensity. Higher values create more complex,
 * metallic, or noise-like timbres. Typically measured in Hz or as a modulation index.
 *
 * @param depth Modulation depth (typical range 0 to 1000+ Hz)
 *
 * @example
 * note("c3").fmh(2).fmenv(50)
 * // Light FM modulation - subtle harmonic richness
 *
 * @example
 * note("c3").fmh(1.4).fmenv(500)
 * // Heavy FM modulation - complex inharmonic timbre
 *
 * @example
 * note("c3 e3 g3").fmh(2).fmenv(sine.range(0, 200))
 * // Dynamic FM modulation via continuous pattern
 */
private val fmenvMutation = voiceModifier {
    copy(fmEnv = it?.asDoubleOrNull())
}

private fun applyFmenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmenvMutation)
}

@StrudelDsl
val fmenv by dslFunction { args, _ -> args.toPattern(fmenvMutation) }

@StrudelDsl
val StrudelPattern.fmenv by dslPatternExtension { p, args, _ -> applyFmenv(p, args) }

@StrudelDsl
val String.fmenv by dslStringExtension { p, args, callInfo -> p.fmenv(args, callInfo) }

/** Alias for [fmenv] */
@StrudelDsl
val fmmod by dslFunction { args, callInfo -> fmenv(args, callInfo) }

/** Alias for [fmenv] */
@StrudelDsl
val StrudelPattern.fmmod by dslPatternExtension { p, args, callInfo -> p.fmenv(args, callInfo) }

/** Alias for [fmenv] */
@StrudelDsl
val String.fmmod by dslStringExtension { p, args, callInfo -> p.fmenv(args, callInfo) }
