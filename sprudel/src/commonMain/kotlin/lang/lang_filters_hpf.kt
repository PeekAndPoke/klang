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

// -- hpf -------------------------------------------------------------------------------------------------------------

private val hpfFreqMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    hcutoff = str.toDoubleOrNull()
}

private fun applyHpfFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hcutoff }, update = hpfFreqMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfFreqMutation)
}

private val hpfQMutation = voiceSetter { hresonance = it?.asDoubleOrNull() }
private fun applyHpfQ(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hresonance }, update = hpfQMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfQMutation)
}

private val hpfPassesMutation = voiceSetter { hpPasses = it?.asDoubleOrNull() }
private fun applyHpfPasses(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hpPasses }, update = hpfPassesMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfPassesMutation)
}

private val hpfEnvMutation = voiceSetter { hpenv = it?.asDoubleOrNull() }
private fun applyHpfEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hpenv }, update = hpfEnvMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfEnvMutation)
}

private val hpfAttackMutation = voiceSetter { hpattack = it?.asDoubleOrNull() ?: hpattack }
private fun applyHpfAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hpattack }, update = hpfAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfAttackMutation)
}

private val hpfDecayMutation = voiceSetter { hpdecay = it?.asDoubleOrNull() ?: hpdecay }
private fun applyHpfDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hpdecay }, update = hpfDecayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfDecayMutation)
}

private val hpfSustainMutation = voiceSetter { hpsustain = it?.asDoubleOrNull() ?: hpsustain }
private fun applyHpfSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hpsustain }, update = hpfSustainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfSustainMutation)
}

private val hpfReleaseMutation = voiceSetter { hprelease = it?.asDoubleOrNull() ?: hprelease }
private fun applyHpfRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.hprelease }, update = hpfReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, hpfReleaseMutation)
}

/**
 * The highpass filter: cutoff, resonance, cascade and the cutoff envelope.
 *
 * Only frequencies above the cutoff pass, so higher values sound thinner. Every note carries its
 * own cutoff, resonance and envelope, [per voice](/manuals/lexikon/voice).
 *
 * The envelope sweeps `freq` up by `env` semitones: attack is the time to reach full depth, decay
 * the fall to the sustain share, release the fall back to `freq` after the note ends. `env = 12`
 * doubles the cutoff, a negative `env` sweeps down, and without `env` the filter rests at `freq`.
 *
 * `passes` cascades the filter: `2` is 24 dB/oct, `3` is 36, omit it for one 12 dB/oct stage. It is
 * rounded and coerced to 1..16, a resource count, never an error. Resonance compounds across the
 * stages, so `hpf(200, 10, 4)` peaks far louder than `hpf(200, 10)`.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`hpf(q = mul(2))`), and every slot reads back as a child: `hpf.freq`, `hpf.q`, `hpf.passes`, `hpf.env`, `hpf.attack`, `hpf.decay`, `hpf.sustain`, `hpf.release`.
 * With no argument at all, the pattern's own values are reinterpreted as `freq`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").hpf(400, 6)                                          // cutoff and resonance
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2*4").s("saw").hpf(freq = 200, env = -24, attack = 0.01, decay = 0.4, sustain = 0)   // the low end swells back after each hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").hpf("200 800").lpf(hpf.freq.mul(4))                  // a two-octave band above the highpass
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
 * @param-tool freq SprudelHpFilterEditor, SprudelHpFilterSequenceEditor
 * @param-tool q SprudelHpResonanceEditor, SprudelHpResonanceSequenceEditor
 * @param-tool env SprudelHpEnvEditor, SprudelHpEnvSequenceEditor
 *
 * @scope voice
 * @category effects
 * @tags hpf, freq, q, passes, env, attack, decay, sustain, release, hcutoff, high pass filter, filter, envelope
 */
@KlangScript.Function
fun SprudelPattern.hpf(
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
            applyHpfFreq(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
        } else {
            this
        }
    if (q != null) p = applyHpfQ(p, listOf<Any?>(q).asSprudelDslArgs(callInfo?.forParam(1)))
    if (passes != null) p = applyHpfPasses(p, listOf<Any?>(passes).asSprudelDslArgs(callInfo?.forParam(2)))
    if (env != null) p = applyHpfEnv(p, listOf<Any?>(env).asSprudelDslArgs(callInfo?.forParam(3)))
    if (attack != null) p = applyHpfAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(4)))
    if (decay != null) p = applyHpfDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(5)))
    if (sustain != null) p = applyHpfSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(6)))
    if (release != null) p = applyHpfRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(7)))
    return p
}

/** Parses this string as a pattern, then applies [hpf]. */
@KlangScript.Function
fun String.hpf(
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
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Chains a [hpf] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.hpf(
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
    this.chain { p -> p.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }

/**
 * The `hpf` object: `hpf(...)` sets the slots, and each slot reads back as a child,
 * `hpf.freq`, `hpf.q`, `hpf.passes`, `hpf.env`, `hpf.attack`, `hpf.decay`, `hpf.sustain`, `hpf.release`.
 *
 * @scope voice
 * @category effects
 * @tags hpf, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("hpf")
object hpf {

    /** The freq slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val freq: FieldAccessor = FieldAccessor { it.hcutoff }

    /** The q slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val q: FieldAccessor = FieldAccessor { it.hresonance }

    /** The passes slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val passes: FieldAccessor = FieldAccessor { it.hpPasses }

    /** The env slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val env: FieldAccessor = FieldAccessor { it.hpenv }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.hpattack }

    /** The decay slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.hpdecay }

    /** The sustain slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.hpsustain }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.hprelease }

    /** The setter, see [SprudelPattern.hpf]. */
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
        { p -> p.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }
}

// -- highpass --------------------------------------------------------------------------------------------------------

/**
 * Applies a highpass filter, the long name of [hpf]; both are the same door, use whichever
 * reads better in the line you are writing.
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").highpass(800)
 * ```
 *
 * @scope voice
 * @category effects
 * @tags highpass, hpf.freq, filter
 */
@KlangScript.Function
fun SprudelPattern.highpass(
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
    hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Parses this string as a pattern, then applies [highpass]. */
@KlangScript.Function
fun String.highpass(
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
    this.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/**
 * Alias of [hpf]: the same object under its long name.
 *
 * @scope voice
 * @category effects
 * @tags highpass, hpf.freq, accessor
 */
@KlangScript.Constant
val highpass: hpf = hpf

/** Chains a highpass step onto this [PatternMapperFn] (see [SprudelPattern.highpass]). */
@KlangScript.Function
fun PatternMapperFn.highpass(
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
    this.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)
