/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._applyControlFromParams
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
 * Only a band around the centre passes, and the resonance sets its width: higher `q` is a narrower
 * band. Every note carries its own centre, width and envelope, [per voice](/manuals/lexikon/voice).
 *
 * The envelope sweeps `freq` up by `env` semitones: attack is the time to reach full depth, decay
 * the fall to the sustain share, release the fall back to `freq` after the note ends. `env = 12`
 * doubles the centre, a negative `env` sweeps down, and without `env` the filter rests at `freq`.
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
 * @param freq Centre frequency in Hz.
 * @param q Resonance, higher narrows the band.
 * @param env Envelope depth in semitones.
 * @param attack Envelope attack in seconds.
 * @param decay Envelope decay in seconds.
 * @param sustain Envelope sustain, 0 to 1.
 * @param release Envelope release in seconds.
 * @param-tool freq SprudelBpFilterEditor, SprudelBpFilterSequenceEditor
 * @param-tool q SprudelBpQEditor, SprudelBpQSequenceEditor
 * @param-tool env SprudelBpEnvEditor, SprudelBpEnvSequenceEditor
 *
 * @scope voice
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
 * @scope voice
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
 * @scope voice
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
 * @scope voice
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

// -- bpfCurves ------------------------------------------------------------------------------------------------------------

// The curve names have ONE home, `AdsrCurves` in audio_bridge; the rule is `adsrCurves`' (and `penvCurves`'):
// an unknown name, like an omitted stage, keeps the stage's current curve.
private val bpfAttackCurveMutation = voiceSetter {
    bpAttackCurve = AdsrCurves.curveOf(it?.toString()) ?: bpAttackCurve
}

private val bpfDecayCurveMutation = voiceSetter {
    bpDecayCurve = AdsrCurves.curveOf(it?.toString()) ?: bpDecayCurve
}

private val bpfReleaseCurveMutation = voiceSetter {
    bpReleaseCurve = AdsrCurves.curveOf(it?.toString()) ?: bpReleaseCurve
}

private fun applyBpAttackCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpfAttackCurveMutation) { src, ctrl ->
        src.bpAttackCurve = ctrl.bpAttackCurve ?: src.bpAttackCurve
        src
    }
}

private fun applyBpDecayCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpfDecayCurveMutation) { src, ctrl ->
        src.bpDecayCurve = ctrl.bpDecayCurve ?: src.bpDecayCurve
        src
    }
}

private fun applyBpReleaseCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, bpfReleaseCurveMutation) { src, ctrl ->
        src.bpReleaseCurve = ctrl.bpReleaseCurve ?: src.bpReleaseCurve
        src
    }
}

/**
 * Sets the shape of each stage of the bandpass filter's cutoff envelope ([bpf]'s `env`, `attack`,
 * `decay`, `sustain`, `release`), the way [adsrCurves] shapes the amplitude envelope. Each stage is
 * independent; an omitted stage, or an unknown curve name, keeps its current curve, so
 * `bpfCurves(decay = "linear")` changes only the decay.
 *
 * The curves are the same six as `adsrCurves`: `linear`, `square`, `cube`, `scurve`, `invsquare`,
 * `exponential`, with the same aliases. Unset, every stage is `exponential`, the default of every
 * modulation envelope on every surface (decision D3). A curve alone switches nothing on: without an
 * envelope on the `bpf` there is no sweep to shape. The Ignitor filters name the same curves inside
 * their envelope: `x => x.adsr(a, d, s, r, e => e.curves(...))`.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("saw").bpf(freq = 600, q = 4, env = 24, decay = 0.25, sustain = 0).bpfCurves(decay = "linear")   // a straight glide in semitones
 * ```
 *
 * @param attack Curve name for the attack stage.
 * @param decay Curve name for the decay stage.
 * @param release Curve name for the release stage.
 *
 * @scope voice
 * @category effects
 * @tags bpf, curve, envelope, shape, filter envelope
 */
@KlangScript.Function
fun SprudelPattern.bpfCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern {
    var p = this
    if (attack != null) p = applyBpAttackCurve(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyBpDecayCurve(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (release != null) p = applyBpReleaseCurve(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/**
 * Parses this string as a pattern and sets the bandpass filter envelope's stage curves (see [bpfCurves]).
 *
 * @param attack Curve name for the attack stage.
 * @param decay Curve name for the decay stage.
 * @param release Curve name for the release stage.
 */
@KlangScript.Function
fun String.bpfCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bpfCurves(attack, decay, release, callInfo)

/**
 * The `bpfCurves` object: `bpfCurves(attack, decay, release)` sets the bandpass filter envelope's stage curves
 * by name. The slots are names, not numbers, so the object carries the setter only and no readers
 * (the `adsrCurves` rule).
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("saw").bpf(freq = 600, q = 4, env = 24, decay = 0.25, sustain = 0).apply(bpfCurves(decay = "cube"))
 * ```
 *
 * @scope voice
 * @category effects
 * @tags bpf, curve, envelope, shape, filter envelope
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("bpfCurves")
object bpfCurves {

    /** The setter, see [SprudelPattern.bpfCurves]. */
    @KlangScript.Invoke
    operator fun invoke(
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null,
    ): PatternMapperFn = { p -> p.bpfCurves(attack, decay, release, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets the bandpass filter envelope's stage curves after the previous mapper.
 *
 * @param attack Curve name for the attack stage. Omit to keep the current curve.
 * @param decay Curve name for the decay stage. Omit to keep the current curve.
 * @param release Curve name for the release stage. Omit to keep the current curve.
 */
@KlangScript.Function
fun PatternMapperFn.bpfCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.bpfCurves(attack, decay, release, callInfo) }
