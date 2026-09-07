/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftNumericField
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
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

private val fmhMutation = voiceSetter { fmh = it?.asDoubleOrNull() }

private fun applyFmh(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmh }, update = fmhMutation)
    }

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
@KlangScript.Function
fun SprudelPattern.fmh(ratio: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmh(this, listOfNotNull(ratio).asSprudelDslArgs(callInfo))

/** Sets the FM harmonicity ratio on a string pattern. */
@KlangScript.Function
fun String.fmh(ratio: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmh(ratio, callInfo)

/**
 * The FM harmonicity ratio of each event, as a value other setters can read.
 *
 * Bare `fmh` reads what the chain has set so far, so it comes after whatever set the field
 * (`fmh(...)` or an alias). Call it, `fmh(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").fmenv(200).fmh(2).fmh(mul("1 1.5 1 2"))          // the ratio changes per note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmenv(200).fmh("1 3").fmattack(fmh.div(10))    // higher ratio, slower attack
 * ```
 *
 * @category synthesis
 * @tags fmh, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("fmh")
object fmh : FieldAccessor({ it.fmh }) {

    /**
     * Returns a [PatternMapperFn] that sets the FM harmonicity ratio on the source pattern.
     *
     * ```KlangScript(Playable)
     * note("c3").s("sine").apply(fmh(2))  // via mapper
     * ```
     *
     * @param ratio Carrier-to-modulator frequency ratio. See [SprudelPattern.fmh].
     */
    @KlangScript.Invoke
    operator fun invoke(ratio: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.fmh(ratio, callInfo) }
}


/** Chains a fmh onto this [PatternMapperFn]; sets the FM harmonicity ratio on the result. */
@KlangScript.Function
fun PatternMapperFn.fmh(ratio: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmh(ratio, callInfo) }

// -- fmattack() / fmatt() ---------------------------------------------------------------------------------------------

private val fmattackMutation = voiceSetter { fmAttack = it?.asDoubleOrNull() }

private fun applyFmattack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmAttack }, update = fmattackMutation)
    }

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
@KlangScript.Function
fun SprudelPattern.fmattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmattack(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the FM modulation envelope attack time on a string pattern. */
@KlangScript.Function
fun String.fmattack(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmattack(seconds, callInfo)

/**
 * The FM envelope attack of each event, as a value other setters can read.
 *
 * Bare `fmattack` reads what the chain has set so far, so it comes after whatever set the field
 * (`fmattack(...)` or an alias). Call it, `fmattack(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `fmatt`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmenv(400).fmattack(0.05).fmattack(mul("1 4"))   // the second note swells
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmenv(400).fmattack("0.01 0.2").fmdecay(fmattack)   // decay follows attack
 * ```
 *
 * @category synthesis
 * @tags fmattack, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("fmattack")
object fmattack : FieldAccessor({ it.fmAttack }) {

    /**
     * Returns a [PatternMapperFn] that sets the FM modulation envelope attack time on the source pattern.
     *
     * ```KlangScript(Playable)
     * note("c3").s("sine").apply(fmattack(0.01))  // via mapper
     * ```
     *
     * @param seconds FM envelope attack time in seconds. See [SprudelPattern.fmattack].
     */
    @KlangScript.Invoke
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.fmattack(seconds, callInfo) }
}


/** Chains a fmattack onto this [PatternMapperFn]; sets the FM envelope attack time on the result. */
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
@KlangScript.Function
fun SprudelPattern.fmatt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.fmattack(seconds, callInfo)

/** Alias for [fmattack] on a string pattern. */
@KlangScript.Function
fun String.fmatt(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmatt(seconds, callInfo)

/**
 * Alias of [fmattack]: the same accessor under another name.
 *
 * @category synthesis
 * @tags fmatt, fmattack, accessor
 */
@KlangScript.Constant
val fmatt: fmattack = fmattack

/** Chains a fmatt onto this [PatternMapperFn]; alias for [PatternMapperFn.fmattack]. */
@KlangScript.Function
fun PatternMapperFn.fmatt(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmatt(seconds, callInfo) }

// -- fmdecay() / fmdec() ----------------------------------------------------------------------------------------------

private val fmdecayMutation = voiceSetter { fmDecay = it?.asDoubleOrNull() }

private fun applyFmdecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmDecay }, update = fmdecayMutation)
    }

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
@KlangScript.Function
fun SprudelPattern.fmdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmdecay(this, listOfNotNull(seconds).asSprudelDslArgs(callInfo))

/** Sets the FM modulation envelope decay time on a string pattern. */
@KlangScript.Function
fun String.fmdecay(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmdecay(seconds, callInfo)

/**
 * The FM envelope decay of each event, as a value other setters can read.
 *
 * Bare `fmdecay` reads what the chain has set so far, so it comes after whatever set the field
 * (`fmdecay(...)` or an alias). Call it, `fmdecay(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `fmdec`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmenv(400).fmattack(0.01).fmsustain(0.2).fmdecay(0.1).fmdecay(mul("1 3"))   // the second note rings
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmenv(400).fmdecay("0.1 0.4").fmsustain(fmdecay)   // sustain follows decay
 * ```
 *
 * @category synthesis
 * @tags fmdecay, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("fmdecay")
object fmdecay : FieldAccessor({ it.fmDecay }) {

    /**
     * Returns a [PatternMapperFn] that sets the FM modulation envelope decay time on the source pattern.
     *
     * ```KlangScript(Playable)
     * note("c3").s("sine").apply(fmdecay(0.1))  // via mapper
     * ```
     *
     * @param seconds FM envelope decay time in seconds. See [SprudelPattern.fmdecay].
     */
    @KlangScript.Invoke
    operator fun invoke(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.fmdecay(seconds, callInfo) }
}


/** Chains a fmdecay onto this [PatternMapperFn]; sets the FM envelope decay time on the result. */
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
@KlangScript.Function
fun SprudelPattern.fmdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.fmdecay(seconds, callInfo)

/** Alias for [fmdecay] on a string pattern. */
@KlangScript.Function
fun String.fmdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmdec(seconds, callInfo)

/**
 * Alias of [fmdecay]: the same accessor under another name.
 *
 * @category synthesis
 * @tags fmdec, fmdecay, accessor
 */
@KlangScript.Constant
val fmdec: fmdecay = fmdecay

/** Chains a fmdec onto this [PatternMapperFn]; alias for [PatternMapperFn.fmdecay]. */
@KlangScript.Function
fun PatternMapperFn.fmdec(seconds: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmdec(seconds, callInfo) }

// -- fmsustain() / fmsus() --------------------------------------------------------------------------------------------

private val fmsustainMutation = voiceSetter { fmSustain = it?.asDoubleOrNull() }

private fun applyFmsustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmSustain }, update = fmsustainMutation)
    }

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
@KlangScript.Function
fun SprudelPattern.fmsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyFmsustain(this, listOfNotNull(level).asSprudelDslArgs(callInfo))

/** Sets the FM modulation envelope sustain level on a string pattern. */
@KlangScript.Function
fun String.fmsustain(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmsustain(level, callInfo)

/**
 * The FM envelope sustain level of each event, as a value other setters can read.
 *
 * Bare `fmsustain` reads what the chain has set so far, so it comes after whatever set the field
 * (`fmsustain(...)` or an alias). Call it, `fmsustain(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * Aliases: `fmsus`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmenv(400).fmdecay(0.2).fmsustain(0.5).fmsustain(mul("1 0"))   // the second note percussive
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmenv(400).fmsustain("0.2 0.8").fmdecay(fmsustain)   // decay follows sustain
 * ```
 *
 * @category synthesis
 * @tags fmsustain, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("fmsustain")
object fmsustain : FieldAccessor({ it.fmSustain }) {

    /**
     * Returns a [PatternMapperFn] that sets the FM modulation envelope sustain level on the source pattern.
     *
     * ```KlangScript(Playable)
     * note("c3").s("sine").apply(fmsustain(0.0))  // via mapper
     * ```
     *
     * @param level FM envelope sustain level. See [SprudelPattern.fmsustain].
     */
    @KlangScript.Invoke
    operator fun invoke(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.fmsustain(level, callInfo) }
}


/** Chains a fmsustain onto this [PatternMapperFn]; sets the FM envelope sustain level on the result. */
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
@KlangScript.Function
fun SprudelPattern.fmsus(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.fmsustain(level, callInfo)

/** Alias for [fmsustain] on a string pattern. */
@KlangScript.Function
fun String.fmsus(level: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmsus(level, callInfo)

/**
 * Alias of [fmsustain]: the same accessor under another name.
 *
 * @category synthesis
 * @tags fmsus, fmsustain, accessor
 */
@KlangScript.Constant
val fmsus: fmsustain = fmsustain

/** Chains a fmsus onto this [PatternMapperFn]; alias for [PatternMapperFn.fmsustain]. */
@KlangScript.Function
fun PatternMapperFn.fmsus(level: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.fmsus(level, callInfo) }

// -- fmenv() / fmmod() ------------------------------------------------------------------------------------------------

private val fmenvMutation = voiceSetter { fmEnv = it?.asDoubleOrNull() }

private fun applyFmenv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmEnv }, update = fmenvMutation)
    }

    return source._liftNumericField(args, fmenvMutation)
}

/**
 * The FM envelope depth of each event, as a value other setters can read.
 *
 * Bare `fmenv` reads what the chain has set so far, so it comes after whatever set the field
 * (`note(...).fmenv(...)` or `fmmod`). Calling it, `fmenv(depth)`, builds a control pattern of
 * depths (like `note(...)`); a mapper argument applies to the field on the pattern form only,
 * `note("c3").fmenv(mul(2))`.
 *
 * Aliases: `fmmod`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmh(2).fmenv(200).fmenv(mul("1 3"))            // the second note brighter
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fmh(2).fmenv("100 400").fmattack(fmenv.div(2000))   // deeper, slower
 * ```
 *
 * @category synthesis
 * @tags fmenv, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("fmenv")
object fmenv : FieldAccessor({ it.fmEnv }) {

    /**
     * Sets the FM modulation depth (the peak modulation amount in Hz).
     *
     * This is the primary intensity control for FM synthesis. Low values (10–100 Hz) add subtle
     * harmonic richness; high values (500+ Hz) create complex, metallic, or noise-like timbres.
     * Can be driven by a continuous pattern for dynamic timbre evolution.
     *
     * ```KlangScript(Playable)
     * note("c3").s("sine").fmh(2).fmenv(50)               // light FM, subtle richness
     * ```
     *
     * ```KlangScript(Playable)
     * note("c3").s("sine").fmh(1.4).fmenv(500)             // heavy FM, complex timbre
     * ```
     *
     * @param depth FM modulation amount in Hz. 10–100 = subtle harmonic richness,
     *   200–500 = bright/brassy, 500+ = complex/metallic/noisy. Default: 0.0 (FM inactive).
     *   Typical range: 50–1000. FM is active when both fmh and fmenv are set.
     */
    @KlangScript.Invoke
    operator fun invoke(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
        listOf(depth).asSprudelDslArgs(callInfo).toPattern(fmenvMutation)
}


/** Sets the FM modulation depth on this pattern. */
@KlangScript.Function
fun SprudelPattern.fmenv(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    applyFmenv(this, listOf(depth).asSprudelDslArgs(callInfo))

/** Sets the FM modulation depth on a string pattern. */
@KlangScript.Function
fun String.fmenv(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmenv(depth, callInfo)

/**
 * Alias of [fmenv]: the same accessor under another name.
 *
 * @category synthesis
 * @tags fmmod, fmenv, accessor
 */
@KlangScript.Constant
val fmmod: fmenv = fmenv

/** Alias for [fmenv] on this pattern. */
@KlangScript.Function
fun SprudelPattern.fmmod(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.fmenv(depth, callInfo)

/** Alias for [fmenv] on a string pattern. */
@KlangScript.Function
fun String.fmmod(depth: PatternLike, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fmmod(depth, callInfo)
