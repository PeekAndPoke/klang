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

/*
To listen to the FM synthesis implementation, you can write KlangScript patterns using the new DSL functions (`fmh`, `fmenv`, `fmatt`, etc.).

Here are a few recipes to try out different FM characters. For the cleanest results, start with a sine wave carrier (`s("sine")`), as complex waves like sawtooths can get muddy quickly when modulated.

### 1. The Classic FM Bell
This is the "Hello World" of FM synthesis. Non-integer ratios create inharmonic partials that sound metallic.

```javascript
// A ratio of ~1.4 creates distinct bell tones
// High modulation depth + long decay = ringing sound
note("c3 e3 g3 b3")
  .s("sine")
  .fmh(1.4)          // Inharmonic ratio
  .fmenv(1000)       // Heavy modulation depth (Hz)
  .fmatt(0.01)       // Instant attack
  .fmdec(2.0)        // Long decay
  .fmsus(0.0)        // No sustain (percussive)
```

### 2. Aggressive "Growl" Bass
Using integer ratios creates harmonic, rich spectra useful for bass.

```javascript
note("c2 c2 [c2*2] c2")
  .s("triangle")
  .fmh(1)            // 1:1 ratio adds square-like harmonics
  .fmenv(500)        // Moderate depth adds "grit"
  .lpf(2000)         // Tame the harsh highs
```

### 3. Evolving Textures
You can use continuous patterns (LFOs) to modulate the FM parameters over time.

```javascript
note("c3")
  .s("sine")
  .dur(4)
  .fmh(sine.range(0.5, 4.0))   // Sweep the ratio slowly
  .fmenv(saw.range(0, 800))    // Sweep the depth
```

### 4. Sequencing Timbre
You can sequence the FM parameters just like notes to create a melody of timbres.

```javascript
// Changing the ratio per step changes the "material" of the sound
note("c3*4")
  .s("sine")
  .fmh("<1 2 3.5 0.5>")
  .fmenv(600)
```

**DSL Reference:**
*   **`fmh(ratio)`**: Harmonicity ratio (Carrier / Modulator).
    *   `1, 2, 3` = Harmonic (cleaner).
    *   `1.4, 2.7` = Inharmonic (metallic/bells).
*   **`fmenv(depth)`**: Modulation amount in Hz. Higher = brighter/noisier.
*   **`fmatt(sec)`**, **`fmdec(sec)`**, **`fmsus(0..1)`**: Shaping the "brightness" envelope independent of the volume envelope.
 */

// -- fmh() ------------------------------------------------------------------------------------------------------------

private val fmhMutation = voiceModifier { copy(fmh = it?.asDoubleOrNull()) }

fun applyFmh(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmhMutation)
}

/**
 * Sets the FM synthesis harmonicity ratio (carrier-to-modulator frequency ratio).
 *
 * The harmonicity ratio determines the spectral relationship between the carrier and modulator.
 * Integer ratios (1, 2, 3) produce harmonic spectra (clean, pitched sounds); non-integer
 * ratios (1.4, 2.7) produce inharmonic spectra (metallic, bell-like tones).
 *
 * ```KlangScript
 * note("c3").s("sine").fmh(2).fmenv(100)    // 2:1 ratio — FM brass/organ character
 * ```
 *
 * ```KlangScript
 * note("c3").s("sine").fmh(1.4).fmenv(500)  // non-integer ratio — FM bell tones
 * ```
 *
 * @category synthesis
 * @tags fmh, FM, harmonicity, ratio, synthesis, modulator
 */
@StrudelDsl
val fmh by dslFunction { args, _ -> args.toPattern(fmhMutation) }

/** Sets the FM harmonicity ratio on this pattern. */
@StrudelDsl
val StrudelPattern.fmh by dslPatternExtension { p, args, _ -> applyFmh(p, args) }

/** Sets the FM harmonicity ratio on a string pattern. */
@StrudelDsl
val String.fmh by dslStringExtension { p, args, callInfo -> p.fmh(args, callInfo) }

// -- fmattack() / fmatt() ---------------------------------------------------------------------------------------------

private val fmattackMutation = voiceModifier { copy(fmAttack = it?.asDoubleOrNull()) }

fun applyFmattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmattackMutation)
}

/**
 * Sets the attack time for the FM modulation envelope in seconds.
 *
 * Controls how quickly the FM modulation depth rises from 0 to its peak when a note starts.
 * Short values create percussive, bright attacks; longer values create gradual timbre sweeps.
 * Use with [fmenv], [fmdecay], [fmsustain].
 *
 * ```KlangScript
 * note("c3").s("sine").fmenv(500).fmattack(0.01)   // instant FM attack — plucky
 * ```
 *
 * ```KlangScript
 * note("c3").s("sine").fmenv(300).fmattack(0.5)    // slow FM attack — timbre sweep
 * ```
 *
 * @alias fmatt
 * @category synthesis
 * @tags fmattack, fmatt, FM, attack, envelope, synthesis
 */
@StrudelDsl
val fmattack by dslFunction { args, _ -> args.toPattern(fmattackMutation) }

/** Sets the FM modulation envelope attack time on this pattern. */
@StrudelDsl
val StrudelPattern.fmattack by dslPatternExtension { p, args, _ -> applyFmattack(p, args) }

/** Sets the FM modulation envelope attack time on a string pattern. */
@StrudelDsl
val String.fmattack by dslStringExtension { p, args, callInfo -> p.fmattack(args, callInfo) }

/** Alias for [fmattack] on this pattern. */
@StrudelDsl
val StrudelPattern.fmatt by dslPatternExtension { p, args, callInfo -> p.fmattack(args, callInfo) }

/**
 * Alias for [fmattack]. Sets the FM modulation envelope attack time.
 *
 * @alias fmattack
 * @category synthesis
 * @tags fmatt, fmattack, FM, attack, envelope, synthesis
 */
@StrudelDsl
val fmatt by dslFunction { args, callInfo -> fmattack(args, callInfo) }

/** Alias for [fmattack] on a string pattern. */
@StrudelDsl
val String.fmatt by dslStringExtension { p, args, callInfo -> p.fmattack(args, callInfo) }

// -- fmdecay() / fmdec() ----------------------------------------------------------------------------------------------

private val fmdecayMutation = voiceModifier { copy(fmDecay = it?.asDoubleOrNull()) }

fun applyFmdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmdecayMutation)
}

/**
 * Sets the decay time for the FM modulation envelope in seconds.
 *
 * Controls how quickly the FM modulation depth falls from its peak to the sustain level
 * after the attack phase. Shorter decay produces a brighter, more percussive sound.
 * Use with [fmattack], [fmsustain], [fmenv].
 *
 * ```KlangScript
 * note("c3").s("sine").fmenv(500).fmattack(0.01).fmdecay(0.1)  // plucky FM
 * ```
 *
 * ```KlangScript
 * note("c3").s("sine").fmenv(300).fmdecay("<0.1 1.0>")          // short vs long decay
 * ```
 *
 * @alias fmdec
 * @category synthesis
 * @tags fmdecay, fmdec, FM, decay, envelope, synthesis
 */
@StrudelDsl
val fmdecay by dslFunction { args, _ -> args.toPattern(fmdecayMutation) }

/** Sets the FM modulation envelope decay time on this pattern. */
@StrudelDsl
val StrudelPattern.fmdecay by dslPatternExtension { p, args, _ -> applyFmdecay(p, args) }

/** Sets the FM modulation envelope decay time on a string pattern. */
@StrudelDsl
val String.fmdecay by dslStringExtension { p, args, callInfo -> p.fmdecay(args, callInfo) }

/** Alias for [fmdecay] on this pattern. */
@StrudelDsl
val StrudelPattern.fmdec by dslPatternExtension { p, args, callInfo -> p.fmdecay(args, callInfo) }

/**
 * Alias for [fmdecay]. Sets the FM modulation envelope decay time.
 *
 * @alias fmdecay
 * @category synthesis
 * @tags fmdec, fmdecay, FM, decay, envelope, synthesis
 */
@StrudelDsl
val fmdec by dslFunction { args, callInfo -> fmdecay(args, callInfo) }

/** Alias for [fmdecay] on a string pattern. */
@StrudelDsl
val String.fmdec by dslStringExtension { p, args, callInfo -> p.fmdecay(args, callInfo) }

// -- fmsustain() / fmsus() --------------------------------------------------------------------------------------------

private val fmsustainMutation = voiceModifier { copy(fmSustain = it?.asDoubleOrNull()) }

fun applyFmsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmsustainMutation)
}

/**
 * Sets the sustain level for the FM modulation envelope (0–1).
 *
 * Determines the FM modulation depth that is held after the attack and decay phases while
 * the note is sustained. `0` produces no sustained modulation (percussive); `1` holds
 * the peak modulation. Use with [fmattack], [fmdecay], [fmenv].
 *
 * ```KlangScript
 * note("c3").s("sine").fmenv(400).fmsustain(0.0)  // percussive FM — no sustain
 * ```
 *
 * ```KlangScript
 * note("c3").s("sine").fmenv(400).fmsustain(0.7)  // sustained FM — held brightness
 * ```
 *
 * @alias fmsus
 * @category synthesis
 * @tags fmsustain, fmsus, FM, sustain, envelope, synthesis
 */
@StrudelDsl
val fmsustain by dslFunction { args, _ -> args.toPattern(fmsustainMutation) }

/** Sets the FM modulation envelope sustain level on this pattern. */
@StrudelDsl
val StrudelPattern.fmsustain by dslPatternExtension { p, args, _ -> applyFmsustain(p, args) }

/** Sets the FM modulation envelope sustain level on a string pattern. */
@StrudelDsl
val String.fmsustain by dslStringExtension { p, args, callInfo -> p.fmsustain(args, callInfo) }

/** Alias for [fmsustain] on this pattern. */
@StrudelDsl
val StrudelPattern.fmsus by dslPatternExtension { p, args, callInfo -> p.fmsustain(args, callInfo) }

/**
 * Alias for [fmsustain]. Sets the FM modulation envelope sustain level.
 *
 * @alias fmsustain
 * @category synthesis
 * @tags fmsus, fmsustain, FM, sustain, envelope, synthesis
 */
@StrudelDsl
val fmsus by dslFunction { args, callInfo -> fmsustain(args, callInfo) }

/** Alias for [fmsustain] on a string pattern. */
@StrudelDsl
val String.fmsus by dslStringExtension { p, args, callInfo -> p.fmsustain(args, callInfo) }

// -- fmenv() / fmmod() ------------------------------------------------------------------------------------------------

private val fmenvMutation = voiceModifier { copy(fmEnv = it?.asDoubleOrNull()) }

fun applyFmenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmenvMutation)
}

/**
 * Sets the FM modulation depth (the peak modulation amount in Hz).
 *
 * This is the primary intensity control for FM synthesis. Low values (10–100 Hz) add subtle
 * harmonic richness; high values (500+ Hz) create complex, metallic, or noise-like timbres.
 * Can be driven by a continuous pattern for dynamic timbre evolution.
 *
 * ```KlangScript
 * note("c3").s("sine").fmh(2).fmenv(50)               // light FM — subtle richness
 * ```
 *
 * ```KlangScript
 * note("c3").s("sine").fmh(1.4).fmenv(500)             // heavy FM — complex timbre
 * ```
 *
 * @alias fmmod
 * @category synthesis
 * @tags fmenv, fmmod, FM, modulation, depth, amount, synthesis
 */
@StrudelDsl
val fmenv by dslFunction { args, _ -> args.toPattern(fmenvMutation) }

/** Sets the FM modulation depth on this pattern. */
@StrudelDsl
val StrudelPattern.fmenv by dslPatternExtension { p, args, _ -> applyFmenv(p, args) }

/** Sets the FM modulation depth on a string pattern. */
@StrudelDsl
val String.fmenv by dslStringExtension { p, args, callInfo -> p.fmenv(args, callInfo) }

/** Alias for [fmenv] on this pattern. */
@StrudelDsl
val StrudelPattern.fmmod by dslPatternExtension { p, args, callInfo -> p.fmenv(args, callInfo) }

/**
 * Alias for [fmenv]. Sets the FM modulation depth.
 *
 * @alias fmenv
 * @category synthesis
 * @tags fmmod, fmenv, FM, modulation, depth, amount, synthesis
 */
@StrudelDsl
val fmmod by dslFunction { args, callInfo -> fmenv(args, callInfo) }

/** Alias for [fmenv] on a string pattern. */
@StrudelDsl
val String.fmmod by dslStringExtension { p, args, callInfo -> p.fmenv(args, callInfo) }
