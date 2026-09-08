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
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// FM Synthesis
// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*
To listen to the FM synthesis implementation, you can write KlangScript patterns using the `fm(env, h, attack, decay, sustain)` door (one call per slot below, or all in one).

Here are a few recipes to try out different FM characters. For the cleanest results, start with a sine wave carrier (`s("sine")`), as complex waves like sawtooths can get muddy quickly when modulated.

### 1. The Classic FM Bell
This is the "Hello World" of FM synthesis. Non-integer ratios create inharmonic partials that sound metallic.

```javascript
// A ratio of ~1.4 creates distinct bell tones
// High modulation depth + long decay = ringing sound
note("c3 e3 g3 b3")
  .s("sine")
  .fm(h = 1.4)          // Inharmonic ratio
  .fm(env = 1000)       // Heavy modulation depth (Hz)
  .fm(attack = 0.01)       // Instant attack
  .fm(decay = 2.0)        // Long decay
  .fm(sustain = 0.0)        // No sustain (percussive)
```

### 2. Aggressive "Growl" Bass
Using integer ratios creates harmonic, rich spectra useful for bass.

```javascript
note("c2 c2 [c2*2] c2")
  .s("triangle")
  .fm(h = 1)            // 1:1 ratio adds square-like harmonics
  .fm(env = 500)        // Moderate depth adds "grit"
  .lpf(2000)         // Tame the harsh highs
```

### 3. Evolving Textures
You can use continuous patterns (LFOs) to modulate the FM parameters over time.

```javascript
note("c3")
  .s("sine")
  .dur(4)
  .fm(h = sine.range(0.5, 4.0))   // Sweep the ratio slowly
  .fm(env = saw.range(0, 800))    // Sweep the depth
```

### 4. Sequencing Timbre
You can sequence the FM parameters just like notes to create a melody of timbres.

```javascript
// Changing the ratio per step changes the "material" of the sound
note("c3*4")
  .s("sine")
  .fm(h = "<1 2 3.5 0.5>")
  .fm(env = 600)
```

**DSL Reference:**
*   **`fm(h = ratio)`**: Harmonicity ratio (Modulator / Carrier).
    *   `1, 2, 3` = Harmonic (cleaner).
    *   `1.4, 2.7` = Inharmonic (metallic/bells).
*   **`fm(env = depth)`**: Modulation amount in Hz. Higher = brighter/noisier.
*   **`fm(attack = sec)`**, **`fm(decay = sec)`**, **`fm(sustain = 0..1)`**: Shaping the "brightness" envelope independent of the volume envelope.
 */

// -- fm --------------------------------------------------------------------------------------------------------------

private val fmEnvMutation = voiceSetter { fmEnv = it?.asDoubleOrNull() ?: fmEnv }

private fun applyFmEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmEnv }, update = fmEnvMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmEnvMutation)
}

private val fmHMutation = voiceSetter { fmh = it?.asDoubleOrNull() }

private fun applyFmH(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmh }, update = fmHMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmHMutation)
}

private val fmAttackMutation = voiceSetter { fmAttack = it?.asDoubleOrNull() }

private fun applyFmAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmAttack }, update = fmAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmAttackMutation)
}

private val fmDecayMutation = voiceSetter { fmDecay = it?.asDoubleOrNull() }

private fun applyFmDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmDecay }, update = fmDecayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmDecayMutation)
}

private val fmSustainMutation = voiceSetter { fmSustain = it?.asDoubleOrNull() }

private fun applyFmSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmSustain }, update = fmSustainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmSustainMutation)
}

/**
 * FM synthesis: modulation depth, harmonicity and the modulation envelope.
 *
 * `env` is the peak modulation in Hz, `h` the modulator to carrier ratio, and the envelope shapes
 * the modulation over the note [per voice](/manuals/lexikon/voice). FM is active once `env` is set;
 * `h` defaults to 1, a modulator at the carrier's own pitch.
 *
 * A depth of 10 to 100 Hz is subtle, 200 to 500 brassy, above 500 metallic. Integer ratios are
 * harmonic, fractions inharmonic; a modulation sustain of 0 gives a percussive bell.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`fm(h = mul(2))`), and the numeric slots read back as `fm.env`, `fm.h`, `fm.attack`, `fm.decay`, `fm.sustain`.
 * With no argument at all, the pattern's own values are reinterpreted as `env`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fm(200, 2)                                     // a bright, harmonic FM tone
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fm(300, 1.4, 0.01, 0.3, 0)                     // a bell: the modulation dies away
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("sine").fm(200, 2).fm(env = mul("1 3"))                // the second note far more metallic
 * ```
 *
 * @param env Modulation depth in Hz.
 * @param h Harmonicity, the modulator to carrier ratio.
 * @param attack Modulation envelope attack in seconds.
 * @param decay Modulation envelope decay in seconds.
 * @param sustain Modulation envelope sustain, 0 to 1.
 *
 * @scope voice
 * @category synthesis
 * @tags fm, env, h, attack, decay, sustain
 */
@KlangScript.Function
fun SprudelPattern.fm(
    env: PatternLike? = null,
    h: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch env: reinterpret runs only on a fully bare call.
    var p = if (env != null || !(h != null || attack != null || decay != null || sustain != null)) {
        applyFmEnv(this, listOfNotNull(env).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (h != null) p = applyFmH(p, listOf<Any?>(h).asSprudelDslArgs(callInfo?.forParam(1)))
    if (attack != null) p = applyFmAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(2)))
    if (decay != null) p = applyFmDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(3)))
    if (sustain != null) p = applyFmSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [fm]. */
@KlangScript.Function
fun String.fm(
    env: PatternLike? = null,
    h: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fm(env, h, attack, decay, sustain, callInfo)

/** Chains a [fm] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.fm(
    env: PatternLike? = null,
    h: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.fm(env, h, attack, decay, sustain, callInfo) }

/**
 * The `fm` object: `fm(...)` sets the slots, and each numeric slot reads back as a child,
 * `fm.env`, `fm.h`, `fm.attack`, `fm.decay`, `fm.sustain`.
 *
 * @scope voice
 * @category synthesis
 * @tags fm, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("fm")
object fm {

    /** The env slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val env: FieldAccessor = FieldAccessor { it.fmEnv }

    /** The h slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val h: FieldAccessor = FieldAccessor { it.fmh }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.fmAttack }

    /** The decay slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.fmDecay }

    /** The sustain slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.fmSustain }

    /** The setter, see [SprudelPattern.fm]. */
    @KlangScript.Invoke
    operator fun invoke(
        env: PatternLike? = null,
        h: PatternLike? = null,
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        sustain: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.fm(env, h, attack, decay, sustain, callInfo) }
}
