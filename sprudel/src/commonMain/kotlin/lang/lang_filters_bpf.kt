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

// -- bpf -------------------------------------------------------------------------------------------------------------

private val bpfFreqMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    bandf = str.toDoubleOrNull()
}

private fun applyBpfFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bandf }, update = bpfFreqMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bpfFreqMutation)
}

private val bpfQMutation = voiceSetter { bandq = it?.asDoubleOrNull() }
private fun applyBpfQ(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bandq }, update = bpfQMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bpfQMutation)
}

private val bpfEnvMutation = voiceSetter { bpenv = it?.asDoubleOrNull() }
private fun applyBpfEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bpenv }, update = bpfEnvMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bpfEnvMutation)
}

private val bpfAttackMutation = voiceSetter { bpattack = it?.asDoubleOrNull() ?: bpattack }
private fun applyBpfAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bpattack }, update = bpfAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bpfAttackMutation)
}

private val bpfDecayMutation = voiceSetter { bpdecay = it?.asDoubleOrNull() ?: bpdecay }
private fun applyBpfDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bpdecay }, update = bpfDecayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bpfDecayMutation)
}

private val bpfSustainMutation = voiceSetter { bpsustain = it?.asDoubleOrNull() ?: bpsustain }
private fun applyBpfSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bpsustain }, update = bpfSustainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bpfSustainMutation)
}

private val bpfReleaseMutation = voiceSetter { bprelease = it?.asDoubleOrNull() ?: bprelease }
private fun applyBpfRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bprelease }, update = bpfReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bpfReleaseMutation)
}

/**
 * The bandpass filter: centre, resonance and the centre envelope.
 *
 * Only a band around the centre passes; the resonance sets its width. The envelope sweeps `freq` up by `env` semitones along attack, decay,
 * sustain and release; without `env` the filter rests at `freq`.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`bpf(q = mul(2))`), and every slot reads back as a child: `bpf.freq`, `bpf.q`, `bpf.env`, `bpf.attack`, `bpf.decay`, `bpf.sustain`, `bpf.release`.
 * With no argument at all, the pattern's own values are reinterpreted as `freq`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").bpf(1000, 4)                                         // centre and width
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("saw").bpf(freq = 500, q = 6, env = 24, attack = 0.01, decay = 0.3, sustain = 0.1)   // a wah on every note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").bpf(freq.mul(4), 3)                                 // a band two octaves above each note
 * ```
 *
 * @param freq Centre frequency, Hz.
 * @param q Resonance (Q); higher values narrow the band.
 * @param env Envelope depth in semitones above `freq` at full envelope (+12 doubles the centre, negative sweeps down).
 * @param attack Envelope attack, seconds: the time to sweep up to `env`.
 * @param decay Envelope decay, seconds: the time to fall back to the sustain share.
 * @param sustain Envelope sustain, 0 to 1: the share of `env` held while the note lasts.
 * @param release Envelope release, seconds: the time to fall back to `freq` after the note ends.
 * @param-tool freq SprudelBpFilterEditor, SprudelBpFilterSequenceEditor
 * @param-tool q SprudelBpQEditor, SprudelBpQSequenceEditor
 * @param-tool env SprudelBpEnvEditor, SprudelBpEnvSequenceEditor
 *
 * @category effects
 * @tags bpf, freq, q, env, attack, decay, sustain, release, bandf, band pass filter, filter, envelope
 */
@KlangScript.Function
fun SprudelPattern.bpf(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    var p = if (freq != null || !(q != null || env != null || attack != null || decay != null || sustain != null || release != null)) {
        applyBpfFreq(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (q != null) p = applyBpfQ(p, listOf<Any?>(q).asSprudelDslArgs(callInfo?.forParam(1)))
    if (env != null) p = applyBpfEnv(p, listOf<Any?>(env).asSprudelDslArgs(callInfo?.forParam(2)))
    if (attack != null) p = applyBpfAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(3)))
    if (decay != null) p = applyBpfDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(4)))
    if (sustain != null) p = applyBpfSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(5)))
    if (release != null) p = applyBpfRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(6)))
    return p
}

/** Parses this string as a pattern, then applies [bpf]. */
@KlangScript.Function
fun String.bpf(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpf(freq, q, env, attack, decay, sustain, release, callInfo)

/** Chains a [bpf] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bpf(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.bpf(freq, q, env, attack, decay, sustain, release, callInfo) }

/**
 * The `bpf` object: `bpf(...)` sets the slots, and each slot reads back as a child,
 * `bpf.freq`, `bpf.q`, `bpf.env`, `bpf.attack`, `bpf.decay`, `bpf.sustain`, `bpf.release`.
 *
 * @category effects
 * @tags bpf, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("bpf")
object bpf {

    /** The freq slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val freq: FieldAccessor = FieldAccessor { it.bandf }

    /** The q slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val q: FieldAccessor = FieldAccessor { it.bandq }

    /** The env slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val env: FieldAccessor = FieldAccessor { it.bpenv }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.bpattack }

    /** The decay slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.bpdecay }

    /** The sustain slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.bpsustain }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.bprelease }

    /** The setter, see [SprudelPattern.bpf]. */
    @KlangScript.Invoke
    operator fun invoke(
        freq: PatternLike? = null,
        q: PatternLike? = null,
        env: PatternLike? = null,
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        sustain: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.bpf(freq, q, env, attack, decay, sustain, release, callInfo) }
}

// -- bandpass --------------------------------------------------------------------------------------------------------

/**
 * Applies a bandpass filter, the long name of [bpf]; both are the same door, use whichever
 * reads better in the line you are writing.
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").bandpass(800)
 * ```
 *
 * @category effects
 * @tags bandpass, bpf.freq, filter
 */
@KlangScript.Function
fun SprudelPattern.bandpass(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    bpf(freq, q, env, attack, decay, sustain, release, callInfo)

/** Parses this string as a pattern, then applies [bandpass]. */
@KlangScript.Function
fun String.bandpass(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.bpf(freq, q, env, attack, decay, sustain, release, callInfo)

/**
 * Alias of [bpf]: the same object under its long name.
 *
 * @category effects
 * @tags bandpass, bpf.freq, accessor
 */
@KlangScript.Constant
val bandpass: bpf = bpf

/** Chains a bandpass step onto this [PatternMapperFn] (see [SprudelPattern.bandpass]). */
@KlangScript.Function
fun PatternMapperFn.bandpass(
    freq: PatternLike? = null,
    q: PatternLike? = null,
    env: PatternLike? = null,
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.bpf(freq, q, env, attack, decay, sustain, release, callInfo)
