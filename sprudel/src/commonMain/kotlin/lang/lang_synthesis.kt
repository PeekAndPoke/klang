@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftNumericField
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangSynthesisInit = false

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

private fun applyFmh(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, fmhMutation)
}

/**
 * Sets the FM synthesis harmonicity ratio (carrier-to-modulator frequency ratio).
 *
 * The harmonicity ratio determines the spectral relationship between the carrier and modulator.
 * Integer ratios (1, 2, 3) produce harmonic spectra (clean, pitched sounds); non-integer
 * ratios (1.4, 2.7) produce inharmonic spectra (metallic, bell-like tones).
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmh(2).fmenv(100)    // 2:1 ratio — FM brass/organ character
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmh(1.4).fmenv(500)  // non-integer ratio — FM bell tones
 * ```
 *
 * @param ratio Carrier-to-modulator frequency ratio. Integer values (1, 2, 3) = harmonic (clean);
 *   non-integer (1.4, 2.7) = inharmonic (metallic/bells). Typical range: 0.5–10.0.
 *   Default: none (FM inactive until both fmh and fmenv are set).
 * @category synthesis
 * @tags fmh, FM, harmonicity, ratio, synthesis, modulator
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmh(ratio: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmh(this, listOfNotNull(ratio).asSprudelDslArgs(callInfo))

/** Sets the FM harmonicity ratio on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmh(ratio: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmh(ratio, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the FM harmonicity ratio on the source pattern.
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").apply(fmh(2))  // via mapper
 * ```
 *
 * @param ratio Carrier-to-modulator frequency ratio. See [SprudelPattern.fmh].
 * @category synthesis
 * @tags fmh, FM, harmonicity, ratio, synthesis, modulator
 */
@SprudelDsl
@KlangScript.Function
fun fmh(ratio: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fmh(ratio, callInfo) }

/** Chains a fmh onto this [PatternMapperFn]; sets the FM harmonicity ratio on the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.fmh(ratio: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmh(ratio, callInfo) }

// -- fmattack() / fmatt() ---------------------------------------------------------------------------------------------

private val fmattackMutation = voiceModifier { copy(fmAttack = it?.asDoubleOrNull()) }

private fun applyFmattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, fmattackMutation)
}

/**
 * Sets the attack time for the FM modulation envelope in seconds.
 *
 * Controls how quickly the FM modulation depth rises from 0 to its peak when a note starts.
 * Short values create percussive, bright attacks; longer values create gradual timbre sweeps.
 * Use with [fmenv], [fmdecay], [fmsustain].
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmenv(500).fmattack(0.01)   // instant FM attack — plucky
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmenv(300).fmattack(0.5)    // slow FM attack — timbre sweep
 * ```
 *
 * @param seconds FM envelope attack time in seconds. 0.01 = instant (percussive),
 *   0.1 = snappy, 0.5+ = slow timbre sweep. Default: 0.0. Typical range: 0.001–2.0.
 * @alias fmatt
 * @category synthesis
 * @tags fmattack, fmatt, FM, attack, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmattack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the FM modulation envelope attack time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmattack(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the FM modulation envelope attack time on the source pattern.
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").apply(fmattack(0.01))  // via mapper
 * ```
 *
 * @param seconds FM envelope attack time in seconds. See [SprudelPattern.fmattack].
 * @alias fmatt
 * @category synthesis
 * @tags fmattack, fmatt, FM, attack, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fmattack(seconds, callInfo) }

/** Chains a fmattack onto this [PatternMapperFn]; sets the FM envelope attack time on the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.fmattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmattack(seconds, callInfo) }

/**
 * Alias for [fmattack]. Sets the FM modulation envelope attack time.
 *
 * @alias fmattack
 * @category synthesis
 * @tags fmatt, fmattack, FM, attack, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmatt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.fmattack(seconds, callInfo)

/** Alias for [fmattack] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmatt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmatt(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [fmattack].
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").apply(fmatt(0.01))  // via mapper
 * ```
 *
 * @alias fmattack
 * @category synthesis
 * @tags fmatt, fmattack, FM, attack, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmatt(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fmatt(seconds, callInfo) }

/** Chains a fmatt onto this [PatternMapperFn]; alias for [PatternMapperFn.fmattack]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.fmatt(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmatt(seconds, callInfo) }

// -- fmdecay() / fmdec() ----------------------------------------------------------------------------------------------

private val fmdecayMutation = voiceModifier { copy(fmDecay = it?.asDoubleOrNull()) }

private fun applyFmdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, fmdecayMutation)
}

/**
 * Sets the decay time for the FM modulation envelope in seconds.
 *
 * Controls how quickly the FM modulation depth falls from its peak to the sustain level
 * after the attack phase. Shorter decay produces a brighter, more percussive sound.
 * Use with [fmattack], [fmsustain], [fmenv].
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmenv(500).fmattack(0.01).fmdecay(0.1)  // plucky FM
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmenv(300).fmdecay("<0.1 1.0>")          // short vs long decay
 * ```
 *
 * @param seconds FM envelope decay time in seconds. 0.05 = snappy, 0.3 = moderate,
 *   1.0+ = long, evolving FM tail. Default: 0.0. Typical range: 0.01–5.0.
 * @alias fmdec
 * @category synthesis
 * @tags fmdecay, fmdec, FM, decay, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmdecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the FM modulation envelope decay time on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmdecay(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the FM modulation envelope decay time on the source pattern.
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").apply(fmdecay(0.1))  // via mapper
 * ```
 *
 * @param seconds FM envelope decay time in seconds. See [SprudelPattern.fmdecay].
 * @alias fmdec
 * @category synthesis
 * @tags fmdecay, fmdec, FM, decay, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fmdecay(seconds, callInfo) }

/** Chains a fmdecay onto this [PatternMapperFn]; sets the FM envelope decay time on the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.fmdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmdecay(seconds, callInfo) }

/**
 * Alias for [fmdecay]. Sets the FM modulation envelope decay time.
 *
 * @alias fmdecay
 * @category synthesis
 * @tags fmdec, fmdecay, FM, decay, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.fmdecay(seconds, callInfo)

/** Alias for [fmdecay] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmdec(seconds, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [fmdecay].
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").apply(fmdec(0.1))  // via mapper
 * ```
 *
 * @alias fmdecay
 * @category synthesis
 * @tags fmdec, fmdecay, FM, decay, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fmdec(seconds, callInfo) }

/** Chains a fmdec onto this [PatternMapperFn]; alias for [PatternMapperFn.fmdecay]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.fmdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmdec(seconds, callInfo) }

// -- fmsustain() / fmsus() --------------------------------------------------------------------------------------------

private val fmsustainMutation = voiceModifier { copy(fmSustain = it?.asDoubleOrNull()) }

private fun applyFmsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, fmsustainMutation)
}

/**
 * Sets the sustain level for the FM modulation envelope (0–1).
 *
 * Determines the FM modulation depth that is held after the attack and decay phases while
 * the note is sustained. `0` produces no sustained modulation (percussive); `1` holds
 * the peak modulation. Use with [fmattack], [fmdecay], [fmenv].
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmenv(400).fmsustain(0.0)  // percussive FM — no sustain
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmenv(400).fmsustain(0.7)  // sustained FM — held brightness
 * ```
 *
 * @param level FM envelope sustain level. 0.0 = no sustained modulation (percussive bell),
 *   0.5 = half modulation held, 1.0 = full peak modulation held. Default: 1.0.
 *   Range: 0.0–1.0.
 * @alias fmsus
 * @category synthesis
 * @tags fmsustain, fmsus, FM, sustain, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmsustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/** Sets the FM modulation envelope sustain level on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmsustain(level, callInfo)

/**
 * Returns a [PatternMapperFn] that sets the FM modulation envelope sustain level on the source pattern.
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").apply(fmsustain(0.0))  // via mapper
 * ```
 *
 * @param level FM envelope sustain level. See [SprudelPattern.fmsustain].
 * @alias fmsus
 * @category synthesis
 * @tags fmsustain, fmsus, FM, sustain, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fmsustain(level, callInfo) }

/** Chains a fmsustain onto this [PatternMapperFn]; sets the FM envelope sustain level on the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.fmsustain(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmsustain(level, callInfo) }

/**
 * Alias for [fmsustain]. Sets the FM modulation envelope sustain level.
 *
 * @alias fmsustain
 * @category synthesis
 * @tags fmsus, fmsustain, FM, sustain, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmsus(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.fmsustain(level, callInfo)

/** Alias for [fmsustain] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmsus(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmsus(level, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [fmsustain].
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").apply(fmsus(0.0))  // via mapper
 * ```
 *
 * @alias fmsustain
 * @category synthesis
 * @tags fmsus, fmsustain, FM, sustain, envelope, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmsus(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.fmsus(level, callInfo) }

/** Chains a fmsus onto this [PatternMapperFn]; alias for [PatternMapperFn.fmsustain]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.fmsus(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmsus(level, callInfo) }

// -- fmenv() / fmmod() ------------------------------------------------------------------------------------------------

private val fmenvMutation = voiceModifier { copy(fmEnv = it?.asDoubleOrNull()) }

private fun applyFmenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftNumericField(args, fmenvMutation)
}

/**
 * Sets the FM modulation depth (the peak modulation amount in Hz).
 *
 * This is the primary intensity control for FM synthesis. Low values (10–100 Hz) add subtle
 * harmonic richness; high values (500+ Hz) create complex, metallic, or noise-like timbres.
 * Can be driven by a continuous pattern for dynamic timbre evolution.
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmh(2).fmenv(50)               // light FM — subtle richness
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("sine").fmh(1.4).fmenv(500)             // heavy FM — complex timbre
 * ```
 *
 * @param depth FM modulation amount in Hz. 10–100 = subtle harmonic richness,
 *   200–500 = bright/brassy, 500+ = complex/metallic/noisy. Default: 0.0 (FM inactive).
 *   Typical range: 50–1000. FM is active when both fmh and fmenv are set.
 * @alias fmmod
 * @category synthesis
 * @tags fmenv, fmmod, FM, modulation, depth, amount, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmenv(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    listOf(depth).asSprudelDslArgs(callInfo).toPattern(fmenvMutation)

/** Sets the FM modulation depth on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmenv(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyFmenv(this, listOf(depth).asSprudelDslArgs(callInfo))

/** Sets the FM modulation depth on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmenv(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmenv(depth, callInfo)

/**
 * Alias for [fmenv]. Sets the FM modulation depth.
 *
 * @alias fmenv
 * @category synthesis
 * @tags fmmod, fmenv, FM, modulation, depth, amount, synthesis
 */
@SprudelDsl
@KlangScript.Function
fun fmmod(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    fmenv(depth, callInfo)

/** Alias for [fmenv] on this pattern. */
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.fmmod(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.fmenv(depth, callInfo)

/** Alias for [fmenv] on a string pattern. */
@SprudelDsl
@KlangScript.Function
fun String.fmmod(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmmod(depth, callInfo)
