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

// -- lpf -------------------------------------------------------------------------------------------------------------

private val lpfFreqMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    cutoff = str.toDoubleOrNull()
}

private fun applyLpfFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.cutoff }, update = lpfFreqMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfFreqMutation)
}

private val lpfQMutation = voiceSetter { resonance = it?.asDoubleOrNull() }
private fun applyLpfQ(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.resonance }, update = lpfQMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfQMutation)
}

private val lpfPassesMutation = voiceSetter { lpPasses = it?.asDoubleOrNull() }
private fun applyLpfPasses(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.lpPasses }, update = lpfPassesMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfPassesMutation)
}

private val lpfEnvMutation = voiceSetter { lpenv = it?.asDoubleOrNull() }
private fun applyLpfEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.lpenv }, update = lpfEnvMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfEnvMutation)
}

private val lpfAttackMutation = voiceSetter { lpattack = it?.asDoubleOrNull() ?: lpattack }
private fun applyLpfAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.lpattack }, update = lpfAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfAttackMutation)
}

private val lpfDecayMutation = voiceSetter { lpdecay = it?.asDoubleOrNull() ?: lpdecay }
private fun applyLpfDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.lpdecay }, update = lpfDecayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfDecayMutation)
}

private val lpfSustainMutation = voiceSetter { lpsustain = it?.asDoubleOrNull() ?: lpsustain }
private fun applyLpfSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.lpsustain }, update = lpfSustainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfSustainMutation)
}

private val lpfReleaseMutation = voiceSetter { lprelease = it?.asDoubleOrNull() ?: lprelease }
private fun applyLpfRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.lprelease }, update = lpfReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, lpfReleaseMutation)
}

/**
 * The lowpass filter: cutoff, resonance, cascade and the cutoff envelope.
 *
 * Only frequencies below the cutoff pass, so lower values sound darker. Every note carries its own
 * cutoff, resonance and envelope, [per voice](/manuals/lexikon/voice).
 *
 * The envelope sweeps `freq` up by `env` semitones: attack is the time to reach full depth, decay
 * the fall to the sustain share, release the fall back to `freq` after the note ends. `env = 12`
 * doubles the cutoff, a negative `env` sweeps down, and without `env` the filter rests at `freq`.
 *
 * `passes` cascades the filter: `2` is 24 dB/oct, `3` is 36, omit it for one 12 dB/oct stage. It is
 * rounded and coerced to 1..16, a resource count, never an error. Resonance compounds across the
 * stages, so `lpf(800, 10, 4)` peaks far louder than `lpf(800, 10)`.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`lpf(q = mul(2))`), and every slot reads back as a child: `lpf.freq`, `lpf.q`, `lpf.passes`, `lpf.env`, `lpf.attack`, `lpf.decay`, `lpf.sustain`, `lpf.release`.
 * With no argument at all, the pattern's own values are reinterpreted as `freq`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").lpf(800, 8)                                          // cutoff and resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("saw").lpf(freq = 300, env = 24, attack = 0.01, decay = 0.3, sustain = 0.2)   // a two-octave filter pluck
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").lpf(800).lpf(freq = mul(perlin.seg(4).range(0.5, 2)))       // the cutoff wanders
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").lpf("400 1600").hpf(lpf.freq.div(2))                 // highpass an octave below the cutoff
 * ```
 *
 * @param freq Cutoff in Hz.
 * @param q Resonance, higher emphasises the cutoff.
 * @param passes Cascade count, 1 to 16.
 * @param env Envelope depth in semitones.
 * @param attack Envelope attack in seconds.
 * @param decay Envelope decay in seconds.
 * @param sustain Envelope sustain, 0 to 1.
 * @param release Envelope release in seconds.
 * @param-tool freq SprudelLpFilterEditor, SprudelLpFilterSequenceEditor
 * @param-tool q SprudelLpResonanceEditor, SprudelLpResonanceSequenceEditor
 * @param-tool env SprudelLpEnvEditor, SprudelLpEnvSequenceEditor
 *
 * @scope voice
 * @category effects
 * @tags lpf, freq, q, passes, env, attack, decay, sustain, release, cutoff, low pass filter, filter, envelope
 */
@KlangScript.Function
fun SprudelPattern.lpf(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    passes: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    var p =
        if (freq != null || !(q != null || passes != null || env != null || attack != null || decay != null || sustain != null || release != null)) {
            applyLpfFreq(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
        } else {
            this
        }
    if (q != null) p = applyLpfQ(p, listOf<Any?>(q).asSprudelDslArgs(callInfo?.forParam(1)))
    if (passes != null) p = applyLpfPasses(p, listOf<Any?>(passes).asSprudelDslArgs(callInfo?.forParam(2)))
    if (env != null) p = applyLpfEnv(p, listOf<Any?>(env).asSprudelDslArgs(callInfo?.forParam(3)))
    if (attack != null) p = applyLpfAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(4)))
    if (decay != null) p = applyLpfDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(5)))
    if (sustain != null) p = applyLpfSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(6)))
    if (release != null) p = applyLpfRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(7)))
    return p
}

/** Parses this string as a pattern, then applies [lpf]. */
@KlangScript.Function
fun String.lpf(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    passes: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Chains a [lpf] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.lpf(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    passes: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }

/**
 * The `lpf` object: `lpf(...)` sets the slots, and each slot reads back as a child,
 * `lpf.freq`, `lpf.q`, `lpf.passes`, `lpf.env`, `lpf.attack`, `lpf.decay`, `lpf.sustain`, `lpf.release`.
 *
 * @scope voice
 * @category effects
 * @tags lpf, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("lpf")
object lpf {

    /** The freq slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val freq: FieldAccessor = FieldAccessor { it.cutoff }

    /** The q slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val q: FieldAccessor = FieldAccessor { it.resonance }

    /** The passes slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val passes: FieldAccessor = FieldAccessor { it.lpPasses }

    /** The env slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val env: FieldAccessor = FieldAccessor { it.lpenv }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.lpattack }

    /** The decay slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.lpdecay }

    /** The sustain slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.lpsustain }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.lprelease }

    /** The setter, see [SprudelPattern.lpf]. */
    @KlangScript.Invoke
    operator fun invoke(
        freq: PatternLike? = null,
        q: PatternLike? = null,
        passes: PatternLike? = null,
        env: PatternLike? = null,
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        sustain: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }
}

// -- lowpass ---------------------------------------------------------------------------------------------------------

/**
 * Applies a lowpass filter, the long name of [lpf]; both are the same door, use whichever
 * reads better in the line you are writing.
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").lowpass(800)
 * ```
 *
 * @scope voice
 * @category effects
 * @tags lowpass, lpf.freq, filter
 */
@KlangScript.Function
fun SprudelPattern.lowpass(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    passes: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Parses this string as a pattern, then applies [lowpass]. */
@KlangScript.Function
fun String.lowpass(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    passes: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/**
 * Alias of [lpf]: the same object under its long name.
 *
 * @scope voice
 * @category effects
 * @tags lowpass, lpf.freq, accessor
 */
@KlangScript.Constant
val lowpass: lpf = lpf

/** Chains a lowpass step onto this [PatternMapperFn] (see [SprudelPattern.lowpass]). */
@KlangScript.Function
fun PatternMapperFn.lowpass(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    passes: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)
