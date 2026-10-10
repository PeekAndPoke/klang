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
To listen to the FM synthesis implementation, you can write KlangScript patterns using the `fm(depth, ratio, attack, decay, sustain, release)` door (one call per slot below, or all in one).

Here are a few recipes to try out different FM characters. For the cleanest results, start with a sine wave carrier (`s("sine")`), as complex waves like sawtooths can get muddy quickly when modulated.

### 1. The Classic FM Bell
This is the "Hello World" of FM synthesis. Non-integer ratios create inharmonic partials that sound metallic.

```javascript
// A ratio of ~1.4 creates distinct bell tones
// High modulation depth + long decay = ringing sound
note("c3 e3 g3 b3")
  .s("sine")
  .fm(ratio = 1.4)        // Inharmonic ratio
  .fm(depth = 1000)       // Heavy modulation depth (Hz)
  .fm(attack = 0.01)      // Instant attack
  .fm(decay = 2.0)        // Long decay
  .fm(sustain = 0.0)      // No sustain (percussive)
```

### 2. Aggressive "Growl" Bass
Using integer ratios creates harmonic, rich spectra useful for bass. With no modulation envelope the depth holds for the
whole note, its release tail included.

```javascript
note("c2 c2 [c2*2] c2")
  .s("triangle")
  .fm(ratio = 1)          // 1:1 ratio adds square-like harmonics
  .fm(depth = 500)        // Moderate depth adds "grit"
  .lpf(2000)              // Tame the harsh highs
```

### 3. Evolving Textures
You can use continuous patterns (LFOs) to modulate the FM parameters over time. Each note takes the values at its
onset and holds them for the whole note.

```javascript
note("c3")
  .s("sine")
  .dur(4)
  .fm(ratio = sine.range(0.5, 4.0))   // Sweep the ratio slowly
  .fm(depth = saw.range(0, 800))      // Sweep the depth
```

### 4. Sequencing Timbre
You can sequence the FM parameters just like notes to create a melody of timbres.

```javascript
// Changing the ratio per step changes the "material" of the sound
note("c3*4")
  .s("sine")
  .fm(ratio = "<1 2 3.5 0.5>")
  .fm(depth = 600)
```

**DSL Reference:**
*   **`fm(ratio = r)`**: The modulator's frequency as a multiple of the note (modulator / carrier).
    *   `1, 2, 3` = Harmonic (cleaner).
    *   `1.4, 2.7` = Inharmonic (metallic/bells).
*   **`fm(depth = hz)`**: Modulation amount in Hz. Higher = brighter/noisier.
*   **`fm(attack = sec)`**, **`fm(decay = sec)`**, **`fm(sustain = 0..1)`**, **`fm(release = sec)`**: Shaping the
    "brightness" envelope independent of the volume envelope. With every stage at its default (attack 0, decay 0,
    sustain 1 or more, release 0) the depth holds for the whole note.
 */

// -- fm --------------------------------------------------------------------------------------------------------------

private val fmDepthMutation = voiceSetter { fmEnv = it?.asDoubleOrNull() ?: fmEnv }

private fun applyFmDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmEnv }, update = fmDepthMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmDepthMutation)
}

private val fmRatioMutation = voiceSetter { fmh = it?.asDoubleOrNull() }

private fun applyFmRatio(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmh }, update = fmRatioMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmRatioMutation)
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

private val fmReleaseMutation = voiceSetter { fmRelease = it?.asDoubleOrNull() }

private fun applyFmRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.fmRelease }, update = fmReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, fmReleaseMutation)
}

/**
 * FM synthesis: modulation depth, ratio and the modulation envelope.
 *
 * `depth` is the peak modulation in Hz, `ratio` the modulator's frequency as a multiple of the note, and the envelope
 * shapes the depth over the note [per voice](/manuals/lexikon/voice). FM is active once `depth` is set and not 0;
 * `ratio` defaults to 1, a modulator at the carrier's own pitch.
 *
 * A depth of 10 to 100 Hz is subtle, 200 to 500 brassy, above 500 metallic. Integer ratios are
 * harmonic, fractions inharmonic; a modulation sustain of 0 gives a percussive bell. With every envelope stage at its
 * default (attack 0, decay 0, sustain 1 or more, release 0) no envelope runs and the depth holds for the whole note,
 * its release tail included; otherwise (attack above 0, decay above 0, sustain below 1 or release above 0) the
 * envelope runs and the depth falls back to 0 over `release` after the note ends (0 is at once).
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`fm(ratio = mul(2))`), and the slots read back as `fm.depth`, `fm.ratio`, `fm.attack`, `fm.decay`,
 * `fm.sustain`, `fm.release`. With no argument at all, the pattern's own values are reinterpreted as `depth`.
 *
 * The door fills the FM stage of `classic()` (the `fm.*` slots), the Ignitor `fm` node with a sine modulator; an
 * instrument without `classic()` ignores it, like the other `classic()` doors. The other pitch doors (`vib`, `penv`,
 * `accelerate`) move carrier and modulator together, so the ratio holds.
 *
 * The words are the Ignitor door's (`x.fm(modulator, ratio, depth, ...)`), the positional order is not: this door
 * leads with the depth and kept its order, so no positional call changed (`fm(300, 1.4)` is depth 300, ratio 1.4); the
 * Ignitor door leads with the modulator, its structure. A recorded asymmetry (`/dsl-design` section 4).
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
 * note("c3 e3").s("sine").fm(200, 2).fm(depth = mul("1 3"))              // the second note far more metallic
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 ~ e3 ~").s("sine").fm(400, 1, 0, 0, 1, 0.4).adsr(0.01, 0.1, 0.8, 0.6)   // the brightness fades with the tail
 * ```
 *
 * @param depth Modulation depth in Hz.
 * @param ratio The modulator's frequency as a multiple of the note.
 * @param attack Modulation envelope attack in seconds.
 * @param decay Modulation envelope decay in seconds.
 * @param sustain Modulation envelope sustain, 0 to 1.
 * @param release Modulation envelope release in seconds, after the note ends.
 *
 * @scope voice
 * @category synthesis
 * @tags fm, depth, ratio, attack, decay, sustain, release
 */
@KlangScript.Function
fun SprudelPattern.fm(
    depth: PatternLike? = null,
    ratio: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch depth: reinterpret runs only on a fully bare call.
    var p = if (depth != null || !(ratio != null || attack != null || decay != null || sustain != null || release != null)) {
        applyFmDepth(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (ratio != null) p = applyFmRatio(p, listOf<Any?>(ratio).asSprudelDslArgs(callInfo?.forParam(1)))
    if (attack != null) p = applyFmAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(2)))
    if (decay != null) p = applyFmDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(3)))
    if (sustain != null) p = applyFmSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(4)))
    if (release != null) p = applyFmRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(5)))
    return p
}

/** Parses this string as a pattern, then applies [fm]. */
@KlangScript.Function
fun String.fm(
    depth: PatternLike? = null,
    ratio: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).fm(depth, ratio, attack, decay, sustain, release, callInfo)

/** Chains a [fm] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.fm(
    depth: PatternLike? = null,
    ratio: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.fm(depth, ratio, attack, decay, sustain, release, callInfo) }

/**
 * The `fm` object: `fm(...)` sets the slots, and each numeric slot reads back as a child,
 * `fm.depth`, `fm.ratio`, `fm.attack`, `fm.decay`, `fm.sustain`, `fm.release`.
 *
 * @scope voice
 * @category synthesis
 * @tags fm, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("fm")
object fm {

    /** The depth slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val depth: FieldAccessor = FieldAccessor { it.fmEnv }

    /** The ratio slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val ratio: FieldAccessor = FieldAccessor { it.fmh }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.fmAttack }

    /** The decay slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.fmDecay }

    /** The sustain slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.fmSustain }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.fmRelease }

    /**
     * The setter, see [SprudelPattern.fm].
     *
     * @param depth Modulation depth in Hz.
     * @param ratio The modulator's frequency as a multiple of the note.
     * @param attack Modulation envelope attack in seconds.
     * @param decay Modulation envelope decay in seconds.
     * @param sustain Modulation envelope sustain, 0 to 1.
     * @param release Modulation envelope release in seconds, after the note ends.
     */
    @KlangScript.Invoke
    operator fun invoke(
        depth: PatternLike? = null,
        ratio: PatternLike? = null,
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        sustain: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.fm(depth, ratio, attack, decay, sustain, release, callInfo) }
}
