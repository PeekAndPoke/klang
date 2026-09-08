/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.SprudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftData
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- ADSR stages ------------------------------------------------------------------------------------------------------
// attack, decay, sustain and release are slots of adsr(), not doors of their own (the single doors
// were removed 2026-09-07, see docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md). Read them as adsr.attack etc.

private val attackMutation = voiceSetter { attack = it?.asDoubleOrNull() }
private val decayMutation = voiceSetter { decay = it?.asDoubleOrNull() }
private val sustainMutation = voiceSetter { sustain = it?.asDoubleOrNull() }
private val releaseMutation = voiceSetter { release = it?.asDoubleOrNull() }

private fun applyStage(
    source: SprudelPattern,
    args: List<SprudelDslArg<Any?>>,
    read: (SprudelVoiceData) -> Double?,
    update: VoiceModifierFn,
): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = read, update = update)
    }

    return source._liftOrReinterpretStringField(args, update)
}

private fun applyAttack(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.attack }, update = attackMutation)

private fun applyDecay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.decay }, update = decayMutation)

private fun applySustain(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.sustain }, update = sustainMutation)

private fun applyRelease(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern =
    applyStage(source, args, read = { it.release }, update = releaseMutation)

// -- ADSR adsr() ------------------------------------------------------------------------------------------------------

/**
 * The amplitude envelope: attack, decay, sustain and release.
 *
 * The envelope shapes the loudness of one note [per voice](/manuals/lexikon/voice). Attack, decay
 * and release are times in seconds, sustain is a level from 0 to 1.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("sine").adsr(0.01, 0.2, 0.7, 0.5)          // standard ADSR
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3*4").adsr("<0.01 0.5>", "<0.1 0.5>", "<0.5 0.8>", "<0.2 1.0>")  // alternate envelopes
 * ```
 *
 * Every slot is independent and patternable: an omitted slot keeps its value, a mapper on a slot
 * applies to that slot alone, and the slots read back as `adsr.attack`, `adsr.decay`,
 * `adsr.sustain`, `adsr.release`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr(0.01, 0.2, 0.7, 0.5).adsr(attack = mul("1 10"))   // the second note swells
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr("0.05 0.3", 0.2, 0.7).adsr(release = adsr.attack)   // symmetric envelope
 * ```
 *
 * @param attack Attack in seconds, silence to full volume.
 * @param decay Decay in seconds, peak down to the sustain level.
 * @param sustain Sustain level, 0 to 1, held while the note is on.
 * @param release Release in seconds, the fade after note-off.
 * @param-tool attack SprudelAdsrEditor, SprudelAdsrSequenceEditor
 *
 * @scope voice
 * @category dynamics
 * @tags adsr, attack, decay, sustain, release, envelope
 */
@KlangScript.Function
fun SprudelPattern.adsr(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern {
    var p = this
    if (attack != null) p = applyAttack(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyDecay(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (sustain != null) p = applySustain(p, listOf<Any?>(sustain).asSprudelDslArgs(callInfo?.forParam(2)))
    if (release != null) p = applyRelease(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/**
 * Parses this string as a pattern and sets the ADSR envelope parameters.
 *
 * ```KlangScript(Playable)
 * "c3*4".adsr(0.01, 0.1, 0.5, 0.2).note()
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level, 0 to 1.
 * @param release Release time in seconds.
 */
@KlangScript.Function
fun String.adsr(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsr(attack, decay, sustain, release, callInfo)

/**
 * The amplitude envelope of each event: `adsr(attack, decay, sustain, release)` sets it, and its
 * four slots can be read back as `adsr.attack`, `adsr.decay`, `adsr.sustain`, `adsr.release`.
 *
 * Each slot is independent: an omitted slot keeps its value, a mapper on a slot applies to that
 * slot (`adsr(attack = mul(2))`), and a read comes after whatever set the slot in the chain.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr("0.05 0.3", 0.2, 0.7).adsr(release = adsr.attack)   // symmetric envelope
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").adsr("0.05 0.3", 0.2, 0.7, 0.4).lpf(adsr.attack.mul(8000))   // slower attack, brighter
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags adsr, attack, decay, sustain, release, envelope, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("adsr")
object adsr {

    /** The attack time of each event, as a value other setters can read. */
    @KlangScript.Property
    val attack: FieldAccessor = FieldAccessor { it.attack }

    /** The decay time of each event, as a value other setters can read. */
    @KlangScript.Property
    val decay: FieldAccessor = FieldAccessor { it.decay }

    /** The sustain level of each event, as a value other setters can read. */
    @KlangScript.Property
    val sustain: FieldAccessor = FieldAccessor { it.sustain }

    /** The release time of each event, as a value other setters can read. */
    @KlangScript.Property
    val release: FieldAccessor = FieldAccessor { it.release }

    /**
     * Creates a [PatternMapperFn] that sets the ADSR envelope parameters for each event.
     *
     * ```KlangScript(Playable)
     * note("c3*4").s("sine").apply(adsr(0.01, 0.1, 0.5, 0.2))
     * ```
     *
     * @param attack Attack time in seconds.
     * @param decay Decay time in seconds.
     * @param sustain Sustain level, 0 to 1.
     * @param release Release time in seconds.
     */
    @KlangScript.Invoke
    operator fun invoke(
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        sustain: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null,
    ): PatternMapperFn =
        { p -> p.adsr(attack, decay, sustain, release, callInfo) }
}

// -- ADSR curves ------------------------------------------------------------------------------------------------------

private fun parseAdsrCurveName(name: String?): AdsrCurve? = when (name?.trim()?.lowercase()) {
    "linear", "lin" -> AdsrCurve.Linear
    "square", "sq", "quad", "quadratic" -> AdsrCurve.Square
    "cube", "cb", "cubic" -> AdsrCurve.Cube
    "scurve", "s", "smooth", "sigmoid" -> AdsrCurve.SCurve
    "invsquare", "inv", "isquare", "concave" -> AdsrCurve.InvSquare
    "exponential", "exp", "expo" -> AdsrCurve.Exponential
    else -> null
}

private val attackCurveMutation = voiceSetter {
    attackCurve = parseAdsrCurveName(it?.toString()) ?: attackCurve
}

private val decayCurveMutation = voiceSetter {
    decayCurve = parseAdsrCurveName(it?.toString()) ?: decayCurve
}

private val releaseCurveMutation = voiceSetter {
    releaseCurve = parseAdsrCurveName(it?.toString()) ?: releaseCurve
}

private fun applyAttackCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, attackCurveMutation) { src, ctrl ->
        src.attackCurve = ctrl.attackCurve ?: src.attackCurve
        src
    }
}

private fun applyDecayCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, decayCurveMutation) { src, ctrl ->
        src.decayCurve = ctrl.decayCurve ?: src.decayCurve
        src
    }
}

private fun applyReleaseCurve(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, releaseCurveMutation) { src, ctrl ->
        src.releaseCurve = ctrl.releaseCurve ?: src.releaseCurve
        src
    }
}

/**
 * Sets the shape of each ADSR stage. Each stage is independent; an omitted stage keeps its
 * current curve, so `adsrCurves(release = "scurve")` changes only the release.
 *
 * Available curves (aliases in parentheses):
 *  - `linear` (`lin`): straight ramp.
 *  - `square` (`sq`, `quad`, `quadratic`): convex, a slow-in rise, a fast initial drop, long tail.
 *  - `cube` (`cb`, `cubic`): a more pronounced `square`.
 *  - `scurve` (`s`, `smooth`, `sigmoid`): ease-in-out with **zero slope at both ends**, no onset
 *    snap and no release "plop" (smoothest).
 *  - `invsquare` (`inv`, `isquare`, `concave`): concave mirror of `square`, a strong start, easing
 *    gently into the endpoint.
 *  - `exponential` (`exp`, `expo`): a true exponential (convex, long tail).
 *
 * Default when unset: `exp` on EVERY stage, the engine-wide default on every door
 * (maintainer decision, 2026-08-24).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").adsr(0.01, 0.2, 0.7, 0.5).adsrCurves("square", "exponential", "scurve")
 * ```
 *
 * @param attack Curve name for the attack stage.
 * @param decay Curve name for the decay stage.
 * @param release Curve name for the release stage.
 *
 * @scope voice
 * @category dynamics
 * @tags adsr, curve, envelope, shape
 */
@KlangScript.Function
fun SprudelPattern.adsrCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern {
    var p = this
    if (attack != null) p = applyAttackCurve(p, listOf<Any?>(attack).asSprudelDslArgs(callInfo?.forParam(0)))
    if (decay != null) p = applyDecayCurve(p, listOf<Any?>(decay).asSprudelDslArgs(callInfo?.forParam(1)))
    if (release != null) p = applyReleaseCurve(p, listOf<Any?>(release).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/**
 * Parses this string as a pattern and sets per-stage ADSR shape curves.
 *
 * @param attack Curve name for the attack stage.
 * @param decay Curve name for the decay stage.
 * @param release Curve name for the release stage.
 */
@KlangScript.Function
fun String.adsrCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsrCurves(attack, decay, release, callInfo)

/**
 * The `adsrCurves` object: `adsrCurves(attack, decay, release)` sets the stage curves by name.
 * The slots are names, not numbers, so the object carries the setter only and no readers
 * (maintainer decision, 2026-09-07).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").adsr(0.01, 0.2, 0.7, 0.5).apply(adsrCurves("square", "exponential", "scurve"))
 * ```
 *
 * @scope voice
 * @category dynamics
 * @tags adsr, curve, envelope, shape
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("adsrCurves")
object adsrCurves {

    /** The setter, see [SprudelPattern.adsrCurves]. */
    @KlangScript.Invoke
    operator fun invoke(
        attack: PatternLike? = null,
        decay: PatternLike? = null,
        release: PatternLike? = null,
        callInfo: CallInfo? = null,
    ): PatternMapperFn = { p -> p.adsrCurves(attack, decay, release, callInfo) }
}

/**
 * Creates a chained [PatternMapperFn] that sets per-stage ADSR shape curves after the previous mapper.
 *
 * @param attack Curve name for the attack stage. Omit to keep the current curve.
 * @param decay Curve name for the decay stage. Omit to keep the current curve.
 * @param release Curve name for the release stage. Omit to keep the current curve.
 */
@KlangScript.Function
fun PatternMapperFn.adsrCurves(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.adsrCurves(attack, decay, release, callInfo) }

/**
 * Creates a chained [PatternMapperFn] that sets all ADSR parameters after the previous mapper.
 *
 * ```KlangScript(Playable)
 * note("c3*4").s("sine").apply(gain(0.8).adsr(0.01, 0.2, 0.7, 0.5))  // gain + adsr chained
 * ```
 *
 * @param attack Attack time in seconds.
 * @param decay Decay time in seconds.
 * @param sustain Sustain level, 0 to 1.
 * @param release Release time in seconds.
 */
@KlangScript.Function
fun PatternMapperFn.adsr(
    attack: PatternLike? = null,
    decay: PatternLike? = null,
    sustain: PatternLike? = null,
    release: PatternLike? = null,
    callInfo: CallInfo? = null,
): PatternMapperFn =
    this.chain { p -> p.adsr(attack, decay, sustain, release, callInfo) }

// -- ADSR adsrOn() / adsrOff() ----------------------------------------------------------------------------------------

private val adsrOnMutation = voiceSetter { adsrOn = it?.asVoiceValue()?.asBoolean }

private fun applyAdsrOn(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(SprudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(adsrOnMutation)
    return source._liftData(control)
}

/**
 * Switches the voice's own amplitude envelope (the VCA) ON or OFF.
 *
 * Use [adsrOff] when the instrument already carries its own envelope: an ignitor built with
 * `.adsr(...)` shapes amplitude itself, and without this the voice envelope applies on top, so the
 * two multiply and every curve comes out twice as steep in dB.
 *
 * Switching it off does NOT throw the numbers away: `.adsr(0.005, 1.0, 1.0, 0.05).adsrOff()` keeps
 * them, so you can flip back with [adsrOn] and compare. Note lifetime is unaffected either way, the
 * engine covers an ignitor's release tail. The exception is an ignitor whose release time is itself
 * modulated, which has no single static value: there the voice's own `release` still governs and is
 * worth setting even with the envelope off.
 *
 * When unset, the engine's `Vca` stage decides (the built-in engines leave it on).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").s("supersaw").adsrOn()   // shape the note here, whatever the engine defaults to
 * ```
 *
 * The built-in sounds carry no envelope of their own, so they WANT the voice envelope. `adsrOff`
 * is for instruments you build with their own `.adsr(...)`.
 *
 * @param flag Truthy keeps the voice envelope. Default `true`.
 * @return A pattern with the VCA switched on.
 *
 * @scope voice
 * @category dynamics
 * @tags adsr, envelope, vca, gate
 */
@KlangScript.Function
fun SprudelPattern.adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): SprudelPattern =
    applyAdsrOn(this, listOf(flag).asSprudelDslArgs(callInfo))

/** Parses this string as a pattern and switches the voice's amplitude envelope on (see [adsrOn]). */
@KlangScript.Function
fun String.adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsrOn(flag, callInfo)

/** Creates a [PatternMapperFn] that switches the voice's amplitude envelope on (see [adsrOn]). */
@KlangScript.Function
fun adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.adsrOn(flag, callInfo) }

/** Chains onto an existing mapper, switching the voice's amplitude envelope on (see [adsrOn]). */
@KlangScript.Function
fun PatternMapperFn.adsrOn(flag: PatternLike = true, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.adsrOn(flag, callInfo) }

/**
 * Switches the voice's own amplitude envelope (the VCA) OFF, so the instrument shapes its own
 * amplitude. The counterpart of [adsrOn]; see there for the full story.
 *
 * With the voice envelope off, the voice's `release` window becomes a full-level HOLD rather than
 * a decay, so the instrument really does have to shape its own tail. And with a release under about
 * 4 ms the engine's teardown guard, not the instrument, owns the note-off.
 *
 * ```KlangScript(Playable)
 * let pluck = Osc.saw().lowpass(2500).adsr(0.005, 0.35, 0.0, 0.08)
 * note("c3 e3 g3 c4").sound(pluck).adsrOff().gain(0.4)
 * ```
 *
 * @return A pattern with the VCA switched off.
 *
 * @scope voice
 * @category dynamics
 * @tags adsr, envelope, vca, gate
 */
@KlangScript.Function
fun SprudelPattern.adsrOff(callInfo: CallInfo? = null): SprudelPattern = this.adsrOn(false, callInfo)

/** Parses this string as a pattern and switches the voice's amplitude envelope off (see [adsrOff]). */
@KlangScript.Function
fun String.adsrOff(callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).adsrOff(callInfo)

/** Creates a [PatternMapperFn] that switches the voice's amplitude envelope off (see [adsrOff]). */
@KlangScript.Function
fun adsrOff(callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.adsrOff(callInfo) }

/** Chains onto an existing mapper, switching the voice's amplitude envelope off (see [adsrOff]). */
@KlangScript.Function
fun PatternMapperFn.adsrOff(callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.adsrOff(callInfo) }
