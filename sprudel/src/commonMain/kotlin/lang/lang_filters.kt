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
 * Only frequencies below the cutoff pass; lower values are darker. The envelope sweeps `freq` up by `env` semitones along attack, decay,
 * sustain and release; without `env` the filter rests at `freq`.
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
 * @param freq Cutoff frequency, Hz.
 * @param q Resonance (Q); higher values emphasise the cutoff.
 * @param passes Cascade count: `2` is 24 dB/oct, `3` is 36; omit for one 12 dB/oct stage. Rounded and coerced to 1..16 (a resource count, never an error); a resonant `q` compounds across the stages, `lpf(800, 10, 4)` peaks far louder than `lpf(800, 10)`.
 * @param env Envelope depth in semitones above `freq` at full envelope (+12 doubles the cutoff, negative sweeps down).
 * @param attack Envelope attack, seconds: the time to sweep up to `env`.
 * @param decay Envelope decay, seconds: the time to fall back to the sustain share.
 * @param sustain Envelope sustain, 0 to 1: the share of `env` held while the note lasts.
 * @param release Envelope release, seconds: the time to fall back to `freq` after the note ends.
 * @param-tool freq SprudelLpFilterEditor, SprudelLpFilterSequenceEditor
 * @param-tool q SprudelLpResonanceEditor, SprudelLpResonanceSequenceEditor
 * @param-tool env SprudelLpEnvEditor, SprudelLpEnvSequenceEditor
 *
 * @category effects
 * @tags lpf, freq, q, passes, env, attack, decay, sustain, release, cutoff, low pass filter, filter, envelope
 */
@KlangScript.Function
fun SprudelPattern.lpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    var p = if (freq != null || !(q != null || passes != null || env != null || attack != null || decay != null || sustain != null || release != null)) {
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
fun String.lpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Chains a [lpf] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.lpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }

/**
 * The `lpf` object: `lpf(...)` sets the slots, and each slot reads back as a child,
 * `lpf.freq`, `lpf.q`, `lpf.passes`, `lpf.env`, `lpf.attack`, `lpf.decay`, `lpf.sustain`, `lpf.release`.
 *
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
    operator fun invoke(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }
}

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
 * Only frequencies above the cutoff pass; higher values are thinner. The envelope sweeps `freq` up by `env` semitones along attack, decay,
 * sustain and release; without `env` the filter rests at `freq`.
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
 * @param freq Cutoff frequency, Hz.
 * @param q Resonance (Q); higher values emphasise the cutoff.
 * @param passes Cascade count: `2` is 24 dB/oct, `3` is 36; omit for one 12 dB/oct stage. Rounded and coerced to 1..16 (a resource count, never an error); a resonant `q` compounds across the stages, `hpf(200, 10, 4)` peaks far louder than `hpf(200, 10)`.
 * @param env Envelope depth in semitones above `freq` at full envelope (+12 doubles the cutoff, negative sweeps down).
 * @param attack Envelope attack, seconds: the time to sweep up to `env`.
 * @param decay Envelope decay, seconds: the time to fall back to the sustain share.
 * @param sustain Envelope sustain, 0 to 1: the share of `env` held while the note lasts.
 * @param release Envelope release, seconds: the time to fall back to `freq` after the note ends.
 * @param-tool freq SprudelHpFilterEditor, SprudelHpFilterSequenceEditor
 * @param-tool q SprudelHpResonanceEditor, SprudelHpResonanceSequenceEditor
 * @param-tool env SprudelHpEnvEditor, SprudelHpEnvSequenceEditor
 *
 * @category effects
 * @tags hpf, freq, q, passes, env, attack, decay, sustain, release, hcutoff, high pass filter, filter, envelope
 */
@KlangScript.Function
fun SprudelPattern.hpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    var p = if (freq != null || !(q != null || passes != null || env != null || attack != null || decay != null || sustain != null || release != null)) {
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
fun String.hpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Chains a [hpf] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.hpf(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }

/**
 * The `hpf` object: `hpf(...)` sets the slots, and each slot reads back as a child,
 * `hpf.freq`, `hpf.q`, `hpf.passes`, `hpf.env`, `hpf.attack`, `hpf.decay`, `hpf.sustain`, `hpf.release`.
 *
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
    operator fun invoke(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo) }
}

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
fun SprudelPattern.bpf(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
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
fun String.bpf(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpf(freq, q, env, attack, decay, sustain, release, callInfo)

/** Chains a [bpf] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bpf(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
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
    operator fun invoke(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.bpf(freq, q, env, attack, decay, sustain, release, callInfo) }
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
 * @category effects
 * @tags lowpass, lpf.freq, filter
 */
@KlangScript.Function
fun SprudelPattern.lowpass(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Parses this string as a pattern, then applies [lowpass]. */
@KlangScript.Function
fun String.lowpass(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/**
 * Alias of [lpf]: the same object under its long name.
 *
 * @category effects
 * @tags lowpass, lpf.freq, accessor
 */
@KlangScript.Constant
val lowpass: lpf = lpf

/** Chains a lowpass step onto this [PatternMapperFn] (see [SprudelPattern.lowpass]). */
@KlangScript.Function
fun PatternMapperFn.lowpass(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.lpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

// -- highpass --------------------------------------------------------------------------------------------------------

/**
 * Applies a highpass filter, the long name of [hpf]; both are the same door, use whichever
 * reads better in the line you are writing.
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").highpass(800)
 * ```
 *
 * @category effects
 * @tags highpass, hpf.freq, filter
 */
@KlangScript.Function
fun SprudelPattern.highpass(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/** Parses this string as a pattern, then applies [highpass]. */
@KlangScript.Function
fun String.highpass(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

/**
 * Alias of [hpf]: the same object under its long name.
 *
 * @category effects
 * @tags highpass, hpf.freq, accessor
 */
@KlangScript.Constant
val highpass: hpf = hpf

/** Chains a highpass step onto this [PatternMapperFn] (see [SprudelPattern.highpass]). */
@KlangScript.Function
fun PatternMapperFn.highpass(freq: PatternLike? = null, q: PatternLike? = null, passes: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.hpf(freq, q, passes, env, attack, decay, sustain, release, callInfo)

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
fun SprudelPattern.bandpass(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    bpf(freq, q, env, attack, decay, sustain, release, callInfo)

/** Parses this string as a pattern, then applies [bandpass]. */
@KlangScript.Function
fun String.bandpass(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
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
fun PatternMapperFn.bandpass(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.bpf(freq, q, env, attack, decay, sustain, release, callInfo)
