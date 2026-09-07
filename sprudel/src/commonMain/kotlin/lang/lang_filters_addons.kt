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

// -- notch -----------------------------------------------------------------------------------------------------------

private val notchFreqMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    notchf = str.toDoubleOrNull()
}
private fun applyNotchFreq(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.notchf }, update = notchFreqMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchFreqMutation)
}
private val notchQMutation = voiceSetter { nresonance = it?.asDoubleOrNull() }
private fun applyNotchQ(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nresonance }, update = notchQMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchQMutation)
}
private val notchEnvMutation = voiceSetter { nfenv = it?.asDoubleOrNull() }
private fun applyNotchEnv(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfenv }, update = notchEnvMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchEnvMutation)
}
private val notchAttackMutation = voiceSetter { nfattack = it?.asDoubleOrNull() ?: nfattack }
private fun applyNotchAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfattack }, update = notchAttackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchAttackMutation)
}
private val notchDecayMutation = voiceSetter { nfdecay = it?.asDoubleOrNull() ?: nfdecay }
private fun applyNotchDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfdecay }, update = notchDecayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchDecayMutation)
}
private val notchSustainMutation = voiceSetter { nfsustain = it?.asDoubleOrNull() ?: nfsustain }
private fun applyNotchSustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfsustain }, update = notchSustainMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchSustainMutation)
}
private val notchReleaseMutation = voiceSetter { nfrelease = it?.asDoubleOrNull() ?: nfrelease }
private fun applyNotchRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.nfrelease }, update = notchReleaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, notchReleaseMutation)
}

/**
 * The notch filter: centre, resonance and the centre envelope.
 *
 * A narrow band around the centre is cut, everything else passes: the opposite of a bandpass. The envelope sweeps `freq` up by `env` semitones along attack, decay,
 * sustain and release; without `env` the filter rests at `freq`.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`notch(q = mul(2))`), and every slot reads back as a child: `notch.freq`, `notch.q`, `notch.env`, `notch.attack`, `notch.decay`, `notch.sustain`, `notch.release`.
 * With no argument at all, the pattern's own values are reinterpreted as `freq`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").notch(1000, 8)                                       // a narrow cut at 1 kHz
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("saw").notch(freq = 500, q = 8, env = 24, attack = 0.01, decay = 0.4, sustain = 0.2)   // the notch sweeps two octaves on every hit
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").notch("800 1600").bpf(notch.freq.mul(2), 3)           // a band an octave above the notch
 * ```
 *
 * @param freq Centre frequency, Hz.
 * @param q Resonance (Q); higher values narrow the notch.
 * @param env Envelope depth in semitones above `freq` at full envelope (+12 doubles the centre, negative sweeps down).
 * @param attack Envelope attack, seconds: the time to sweep up to `env`.
 * @param decay Envelope decay, seconds: the time to fall back to the sustain share.
 * @param sustain Envelope sustain, 0 to 1: the share of `env` held while the note lasts.
 * @param release Envelope release, seconds: the time to fall back to `freq` after the note ends.
 * @param-tool freq SprudelNotchFilterEditor, SprudelNotchFilterSequenceEditor
 * @param-tool q SprudelNResonanceEditor, SprudelNResonanceSequenceEditor
 * @param-tool env SprudelNfEnvEditor, SprudelNfEnvSequenceEditor
 * @param-tool attack SprudelNfAttackEditor, SprudelNfAttackSequenceEditor
 * @param-tool decay SprudelNfDecayEditor, SprudelNfDecaySequenceEditor
 * @param-tool sustain SprudelNfSustainEditor, SprudelNfSustainSequenceEditor
 * @param-tool release SprudelNfReleaseEditor, SprudelNfReleaseSequenceEditor
 *
 * @category effects
 * @tags notch, freq, q, env, attack, decay, sustain, release, notch.freq, notch filter, filter, envelope
 */
@KlangScript.Function
fun SprudelPattern.notch(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern {
    // A tail-only call must not touch freq: reinterpret runs only on a fully bare call.
    var p = if (freq != null || !(q != null || env != null || attack != null || decay != null || sustain != null || release != null)) {
        applyNotchFreq(this, listOfNotNull(freq).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (q != null) p = applyNotchQ(p, listOf<Any?>(q).asSprudelDslArgs(callInfo?.forParam(1)))
    if (env != null) p = applyNotchEnv(p, listOf<Any?>(env).asSprudelDslArgs(callInfo?.forParam(2)))
    if (attack != null) p = applyNotchAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(3)))
    if (decay != null) p = applyNotchDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(4)))
    if (sustain != null) p = applyNotchSustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(5)))
    if (release != null) p = applyNotchRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(6)))
    return p
}

/** Parses this string as a pattern, then applies [notch]. */
@KlangScript.Function
fun String.notch(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).notch(freq, q, env, attack, decay, sustain, release, callInfo)

/** Chains a [notch] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.notch(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.notch(freq, q, env, attack, decay, sustain, release, callInfo) }

/**
 * The `notch` object: `notch(...)` sets the slots, and each slot reads back as a child,
 * `notch.freq`, `notch.q`, `notch.env`, `notch.attack`, `notch.decay`, `notch.sustain`, `notch.release`.
 *
 * @category effects
 * @tags notch, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("notch")
object notch {

    /** The freq slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val freq: FieldAccessor = FieldAccessor { it.notchf }

    /** The q slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val q: FieldAccessor = FieldAccessor { it.nresonance }

    /** The env slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val env: FieldAccessor = FieldAccessor { it.nfenv }

    /** The attack slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.nfattack }

    /** The decay slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.nfdecay }

    /** The sustain slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.nfsustain }

    /** The release slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.nfrelease }

    /** The setter, see [SprudelPattern.notch]. */
    @KlangScript.Invoke
    operator fun invoke(freq: PatternLike? = null, q: PatternLike? = null, env: PatternLike? = null, attack: PatternLike? = null, decay: PatternLike? = null, sustain: PatternLike? = null, release: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.notch(freq, q, env, attack, decay, sustain, release, callInfo) }
}
