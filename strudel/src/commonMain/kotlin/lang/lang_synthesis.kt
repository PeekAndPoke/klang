@file:Suppress("DuplicatedCode", "ObjectPropertyName")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel._liftNumericField
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs

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

internal val _fmh by dslFunction { args, _ -> args.toPattern(fmhMutation) }
internal val StrudelPattern._fmh by dslPatternExtension { p, args, _ -> applyFmh(p, args) }
internal val String._fmh by dslStringExtension { p, args, callInfo -> p._fmh(args, callInfo) }

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
fun fmh(ratio: PatternLike): StrudelPattern = _fmh(listOf(ratio).asStrudelDslArgs())

/** Sets the FM harmonicity ratio on this pattern. */
@StrudelDsl
fun StrudelPattern.fmh(ratio: PatternLike): StrudelPattern = this._fmh(listOf(ratio).asStrudelDslArgs())

/** Sets the FM harmonicity ratio on a string pattern. */
@StrudelDsl
fun String.fmh(ratio: PatternLike): StrudelPattern = this._fmh(listOf(ratio).asStrudelDslArgs())

// -- fmattack() / fmatt() ---------------------------------------------------------------------------------------------

private val fmattackMutation = voiceModifier { copy(fmAttack = it?.asDoubleOrNull()) }

fun applyFmattack(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmattackMutation)
}

internal val _fmattack by dslFunction { args, _ -> args.toPattern(fmattackMutation) }
internal val StrudelPattern._fmattack by dslPatternExtension { p, args, _ -> applyFmattack(p, args) }
internal val String._fmattack by dslStringExtension { p, args, callInfo -> p._fmattack(args, callInfo) }

internal val _fmatt by dslFunction { args, callInfo -> _fmattack(args, callInfo) }
internal val StrudelPattern._fmatt by dslPatternExtension { p, args, callInfo -> p._fmattack(args, callInfo) }
internal val String._fmatt by dslStringExtension { p, args, callInfo -> p._fmatt(args, callInfo) }

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
fun fmattack(seconds: PatternLike): StrudelPattern = _fmattack(listOf(seconds).asStrudelDslArgs())

/** Sets the FM modulation envelope attack time on this pattern. */
@StrudelDsl
fun StrudelPattern.fmattack(seconds: PatternLike): StrudelPattern = this._fmattack(listOf(seconds).asStrudelDslArgs())

/** Sets the FM modulation envelope attack time on a string pattern. */
@StrudelDsl
fun String.fmattack(seconds: PatternLike): StrudelPattern = this._fmattack(listOf(seconds).asStrudelDslArgs())

/**
 * Alias for [fmattack]. Sets the FM modulation envelope attack time.
 *
 * @alias fmattack
 * @category synthesis
 * @tags fmatt, fmattack, FM, attack, envelope, synthesis
 */
@StrudelDsl
fun fmatt(seconds: PatternLike): StrudelPattern = _fmatt(listOf(seconds).asStrudelDslArgs())

/** Alias for [fmattack] on this pattern. */
@StrudelDsl
fun StrudelPattern.fmatt(seconds: PatternLike): StrudelPattern = this._fmatt(listOf(seconds).asStrudelDslArgs())

/** Alias for [fmattack] on a string pattern. */
@StrudelDsl
fun String.fmatt(seconds: PatternLike): StrudelPattern = this._fmatt(listOf(seconds).asStrudelDslArgs())

// -- fmdecay() / fmdec() ----------------------------------------------------------------------------------------------

private val fmdecayMutation = voiceModifier { copy(fmDecay = it?.asDoubleOrNull()) }

fun applyFmdecay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmdecayMutation)
}

internal val _fmdecay by dslFunction { args, _ -> args.toPattern(fmdecayMutation) }
internal val StrudelPattern._fmdecay by dslPatternExtension { p, args, _ -> applyFmdecay(p, args) }
internal val String._fmdecay by dslStringExtension { p, args, callInfo -> p._fmdecay(args, callInfo) }

internal val _fmdec by dslFunction { args, callInfo -> _fmdecay(args, callInfo) }
internal val StrudelPattern._fmdec by dslPatternExtension { p, args, callInfo -> p._fmdecay(args, callInfo) }
internal val String._fmdec by dslStringExtension { p, args, callInfo -> p._fmdec(args, callInfo) }

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
fun fmdecay(seconds: PatternLike): StrudelPattern = _fmdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the FM modulation envelope decay time on this pattern. */
@StrudelDsl
fun StrudelPattern.fmdecay(seconds: PatternLike): StrudelPattern = this._fmdecay(listOf(seconds).asStrudelDslArgs())

/** Sets the FM modulation envelope decay time on a string pattern. */
@StrudelDsl
fun String.fmdecay(seconds: PatternLike): StrudelPattern = this._fmdecay(listOf(seconds).asStrudelDslArgs())

/**
 * Alias for [fmdecay]. Sets the FM modulation envelope decay time.
 *
 * @alias fmdecay
 * @category synthesis
 * @tags fmdec, fmdecay, FM, decay, envelope, synthesis
 */
@StrudelDsl
fun fmdec(seconds: PatternLike): StrudelPattern = _fmdec(listOf(seconds).asStrudelDslArgs())

/** Alias for [fmdecay] on this pattern. */
@StrudelDsl
fun StrudelPattern.fmdec(seconds: PatternLike): StrudelPattern = this._fmdec(listOf(seconds).asStrudelDslArgs())

/** Alias for [fmdecay] on a string pattern. */
@StrudelDsl
fun String.fmdec(seconds: PatternLike): StrudelPattern = this._fmdec(listOf(seconds).asStrudelDslArgs())

// -- fmsustain() / fmsus() --------------------------------------------------------------------------------------------

private val fmsustainMutation = voiceModifier { copy(fmSustain = it?.asDoubleOrNull()) }

fun applyFmsustain(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmsustainMutation)
}

internal val _fmsustain by dslFunction { args, _ -> args.toPattern(fmsustainMutation) }
internal val StrudelPattern._fmsustain by dslPatternExtension { p, args, _ -> applyFmsustain(p, args) }
internal val String._fmsustain by dslStringExtension { p, args, callInfo -> p._fmsustain(args, callInfo) }

internal val _fmsus by dslFunction { args, callInfo -> _fmsustain(args, callInfo) }
internal val StrudelPattern._fmsus by dslPatternExtension { p, args, callInfo -> p._fmsustain(args, callInfo) }
internal val String._fmsus by dslStringExtension { p, args, callInfo -> p._fmsus(args, callInfo) }

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
fun fmsustain(level: PatternLike): StrudelPattern = _fmsustain(listOf(level).asStrudelDslArgs())

/** Sets the FM modulation envelope sustain level on this pattern. */
@StrudelDsl
fun StrudelPattern.fmsustain(level: PatternLike): StrudelPattern = this._fmsustain(listOf(level).asStrudelDslArgs())

/** Sets the FM modulation envelope sustain level on a string pattern. */
@StrudelDsl
fun String.fmsustain(level: PatternLike): StrudelPattern = this._fmsustain(listOf(level).asStrudelDslArgs())

/**
 * Alias for [fmsustain]. Sets the FM modulation envelope sustain level.
 *
 * @alias fmsustain
 * @category synthesis
 * @tags fmsus, fmsustain, FM, sustain, envelope, synthesis
 */
@StrudelDsl
fun fmsus(level: PatternLike): StrudelPattern = _fmsus(listOf(level).asStrudelDslArgs())

/** Alias for [fmsustain] on this pattern. */
@StrudelDsl
fun StrudelPattern.fmsus(level: PatternLike): StrudelPattern = this._fmsus(listOf(level).asStrudelDslArgs())

/** Alias for [fmsustain] on a string pattern. */
@StrudelDsl
fun String.fmsus(level: PatternLike): StrudelPattern = this._fmsus(listOf(level).asStrudelDslArgs())

// -- fmenv() / fmmod() ------------------------------------------------------------------------------------------------

private val fmenvMutation = voiceModifier { copy(fmEnv = it?.asDoubleOrNull()) }

fun applyFmenv(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source._liftNumericField(args, fmenvMutation)
}

internal val _fmenv by dslFunction { args, _ -> args.toPattern(fmenvMutation) }
internal val StrudelPattern._fmenv by dslPatternExtension { p, args, _ -> applyFmenv(p, args) }
internal val String._fmenv by dslStringExtension { p, args, callInfo -> p._fmenv(args, callInfo) }

internal val _fmmod by dslFunction { args, callInfo -> _fmenv(args, callInfo) }
internal val StrudelPattern._fmmod by dslPatternExtension { p, args, callInfo -> p._fmenv(args, callInfo) }
internal val String._fmmod by dslStringExtension { p, args, callInfo -> p._fmmod(args, callInfo) }

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
fun fmenv(depth: PatternLike): StrudelPattern = _fmenv(listOf(depth).asStrudelDslArgs())

/** Sets the FM modulation depth on this pattern. */
@StrudelDsl
fun StrudelPattern.fmenv(depth: PatternLike): StrudelPattern = this._fmenv(listOf(depth).asStrudelDslArgs())

/** Sets the FM modulation depth on a string pattern. */
@StrudelDsl
fun String.fmenv(depth: PatternLike): StrudelPattern = this._fmenv(listOf(depth).asStrudelDslArgs())

/**
 * Alias for [fmenv]. Sets the FM modulation depth.
 *
 * @alias fmenv
 * @category synthesis
 * @tags fmmod, fmenv, FM, modulation, depth, amount, synthesis
 */
@StrudelDsl
fun fmmod(depth: PatternLike): StrudelPattern = _fmmod(listOf(depth).asStrudelDslArgs())

/** Alias for [fmenv] on this pattern. */
@StrudelDsl
fun StrudelPattern.fmmod(depth: PatternLike): StrudelPattern = this._fmmod(listOf(depth).asStrudelDslArgs())

/** Alias for [fmenv] on a string pattern. */
@StrudelDsl
fun String.fmmod(depth: PatternLike): StrudelPattern = this._fmmod(listOf(depth).asStrudelDslArgs())