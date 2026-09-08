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
import io.peekandpoke.klang.sprudel._applyControlFromParams
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- phaser, the rate slot -------------------------------------------------------------------------------------------

private val phaserMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    phaserRate = str.toDoubleOrNull() ?: phaserRate
}

private fun applyPhaser(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserRate }, update = phaserMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserMutation)
}


/**
 * The orbit phaser: rate, depth, centre, sweep and dry floor.
 *
 * One sweep over the summed [orbit bus](/manuals/lexikon/orbit-bus), the DAW-insert model, so every
 * knob belongs to the orbit's owning voice: with the built-in pipelines, a voice that sets phaser
 * knobs without owning its orbit is not phased at all. Route it to its own orbit to give it its own
 * phaser. (A custom pipeline that adds a phaser stage does get a per-voice pass from its own knobs.)
 *
 * The dry signal stays untouched by default (`floor` is 1), so `wet` ADDS the swept notch on top
 * rather than crossfading into it. Lower `floor` to turn `wet` back into a crossfade.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`phaser(wet = mul(2))`), and the numeric slots read back as `phaser.rate`, `phaser.wet`, `phaser.center`, `phaser.sweep`, `phaser.floor`.
 * With no argument at all, the pattern's own values are reinterpreted as `rate`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").phaser(0.5, 0.5)                                 // rate and depth
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").phaser(0.5, 0.5).phaser(rate = mul("1 4"))        // the second note swirls faster
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").phaser("0.5 2", 0.5).delay(wet = phaser.wet, time = 0.25)   // as much delay as phaser
 * ```
 *
 * @param rate LFO rate in Hz.
 * @param wet Depth, 0 to 1. Engages above 0.01.
 * @param center Centre frequency of the sweep, Hz.
 * @param sweep Sweep range around the centre, Hz.
 * @param floor Minimum dry share kept in the mix, 0 to 1.
 * @param-tool rate SprudelPhaserEditor, SprudelPhaserSequenceEditor
 *
 * @scope orbit
 * @category effects
 * @tags phaser, rate, wet, center, sweep, floor
 */
@KlangScript.Function
fun SprudelPattern.phaser(
    rate: PatternLike? = null,
    wet: PatternLike? = null,
    center: PatternLike? = null,
    sweep: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch rate: reinterpret runs only on a fully bare call.
    var p = if (rate != null || !(wet != null || center != null || sweep != null || floor != null)) {
        applyPhaser(this, listOfNotNull(rate).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (wet != null) p = applyPhaserWet(p, listOf<Any?>(wet).asSprudelDslArgs(callInfo?.forParam(1)))
    if (center != null) p = applyPhaserCenter(p, listOf<Any?>(center).asSprudelDslArgs(callInfo?.forParam(2)))
    if (sweep != null) p = applyPhaserSweep(p, listOf<Any?>(sweep).asSprudelDslArgs(callInfo?.forParam(3)))
    if (floor != null) p = applyPhaserFloor(p, listOf<Any?>(floor).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [phaser]. */
@KlangScript.Function
fun String.phaser(
    rate: PatternLike? = null,
    wet: PatternLike? = null,
    center: PatternLike? = null,
    sweep: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).phaser(rate, wet, center, sweep, floor, callInfo)

/** Chains a [phaser] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.phaser(
    rate: PatternLike? = null,
    wet: PatternLike? = null,
    center: PatternLike? = null,
    sweep: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.phaser(rate, wet, center, sweep, floor, callInfo) }

/**
 * The `phaser` object: `phaser(...)` sets the slots, and each numeric slot reads back as a child,
 * `phaser.rate`, `phaser.wet`, `phaser.center`, `phaser.sweep`, `phaser.floor`.
 *
 * @scope orbit
 * @category effects
 * @tags phaser, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("phaser")
object phaser {

    /** The rate slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val rate: FieldAccessor = FieldAccessor { it.phaserRate }

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.phaserDepth }

    /** The center slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val center: FieldAccessor = FieldAccessor { it.phaserCenter }

    /** The sweep slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sweep: FieldAccessor = FieldAccessor { it.phaserSweep }

    /** The floor slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val floor: FieldAccessor = FieldAccessor { it.phaserFloor }

    /** The setter, see [SprudelPattern.phaser]. */
    @KlangScript.Invoke
    operator fun invoke(
        rate: PatternLike? = null,
        wet: PatternLike? = null,
        center: PatternLike? = null,
        sweep: PatternLike? = null,
        floor: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.phaser(rate, wet, center, sweep, floor, callInfo) }
}

// -- phaser.wet ------------------------------------------------------------------------------------------------------

private val phaserWetMutation = voiceSetter { phaserDepth = it?.asDoubleOrNull() }

private fun applyPhaserWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserDepth }, update = phaserWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserWetMutation)
}

// -- phaser.floor ----------------------------------------------------------------------------------------------------

private val phaserFloorMutation = voiceSetter { phaserFloor = it?.asDoubleOrNull() }

private fun applyPhaserFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserFloor }, update = phaserFloorMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserFloorMutation)
}

// -- phaser.center ---------------------------------------------------------------------------------------------------

private val phaserCenterMutation = voiceSetter { phaserCenter = it?.asDoubleOrNull() }

private fun applyPhaserCenter(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserCenter }, update = phaserCenterMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserCenterMutation)
}

// -- phaser.sweep ----------------------------------------------------------------------------------------------------

private val phaserSweepMutation = voiceSetter { phaserSweep = it?.asDoubleOrNull() }

private fun applyPhaserSweep(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.phaserSweep }, update = phaserSweepMutation)
    }

    return source._liftOrReinterpretNumericalField(args, phaserSweepMutation)
}

// -- tremolo.sync ----------------------------------------------------------------------------------------------------

private val tremoloSyncMutation = voiceSetter { tremoloSync = it?.asDoubleOrNull() }

private fun applyTremoloSync(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloSync }, update = tremoloSyncMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloSyncMutation)
}


/**
 * The tremolo: depth, rate, waveform, skew and phase.
 *
 * Unlike the phaser above it, the tremolo runs [per voice](/manuals/lexikon/voice): every note gets
 * its own LFO, so two notes on one orbit can wobble at different rates.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`tremolo(sync = mul(2))`), and the numeric slots read back as `tremolo.depth`, `tremolo.sync`, `tremolo.skew`, `tremolo.phase`.
 * With no argument at all, the pattern's own values are reinterpreted as `depth`.
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").tremolo(0.6, 4)                                     // depth and rate
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3").s("saw").tremolo(0.6, 4).tremolo(sync = mul("<1 2>"))         // twice as fast every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").tremolo("0.3 0.9", 4).gain(tremolo.depth)         // deeper, louder
 * ```
 *
 * @param depth Depth, 0 to 1. The stage is built only above 0.
 * @param sync Rate in Hz.
 * @param shape LFO waveform: `sine`, `triangle`, `square`, `sawtooth`, `ramp`.
 * @param skew Waveform skew, -1 to 1, 0 is symmetric.
 * @param phase LFO start phase, 0 to 1.
 * @param-tool depth SprudelTremoloEditor, SprudelTremoloSequenceEditor
 * @param-tool shape SprudelWaveformEditor, SprudelWaveformSequenceEditor
 *
 * @scope voice
 * @category effects
 * @tags tremolo, depth, sync, shape, skew, phase
 */
@KlangScript.Function
fun SprudelPattern.tremolo(
    depth: PatternLike? = null,
    sync: PatternLike? = null,
    shape: PatternLike? = null,
    skew: PatternLike? = null,
    phase: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch depth: reinterpret runs only on a fully bare call.
    var p = if (depth != null || !(sync != null || shape != null || skew != null || phase != null)) {
        applyTremoloDepth(this, listOfNotNull(depth).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (sync != null) p = applyTremoloSync(p, listOf<Any?>(sync).asSprudelDslArgs(callInfo?.forParam(1)))
    if (shape != null) p = applyTremoloShape(p, listOf<Any?>(shape).asSprudelDslArgs(callInfo?.forParam(2)))
    if (skew != null) p = applyTremoloSkew(p, listOf<Any?>(skew).asSprudelDslArgs(callInfo?.forParam(3)))
    if (phase != null) p = applyTremoloPhase(p, listOf<Any?>(phase).asSprudelDslArgs(callInfo?.forParam(4)))
    return p
}

/** Parses this string as a pattern, then applies [tremolo]. */
@KlangScript.Function
fun String.tremolo(
    depth: PatternLike? = null,
    sync: PatternLike? = null,
    shape: PatternLike? = null,
    skew: PatternLike? = null,
    phase: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).tremolo(depth, sync, shape, skew, phase, callInfo)

/** Chains a [tremolo] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.tremolo(
    depth: PatternLike? = null,
    sync: PatternLike? = null,
    shape: PatternLike? = null,
    skew: PatternLike? = null,
    phase: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.tremolo(depth, sync, shape, skew, phase, callInfo) }

/**
 * The `tremolo` object: `tremolo(...)` sets the slots, and each numeric slot reads back as a child,
 * `tremolo.depth`, `tremolo.sync`, `tremolo.skew`, `tremolo.phase`.
 *
 * @scope voice
 * @category effects
 * @tags tremolo, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("tremolo")
object tremolo {

    /** The depth slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val depth: FieldAccessor = FieldAccessor { it.tremoloDepth }

    /** The sync slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val sync: FieldAccessor = FieldAccessor { it.tremoloSync }

    /** The skew slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val skew: FieldAccessor = FieldAccessor { it.tremoloSkew }

    /** The phase slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val phase: FieldAccessor = FieldAccessor { it.tremoloPhase }

    /** The setter, see [SprudelPattern.tremolo]. */
    @KlangScript.Invoke
    operator fun invoke(
        depth: PatternLike? = null,
        sync: PatternLike? = null,
        shape: PatternLike? = null,
        skew: PatternLike? = null,
        phase: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.tremolo(depth, sync, shape, skew, phase, callInfo) }
}

// -- tremolo.depth ---------------------------------------------------------------------------------------------------

private val tremoloDepthMutation = voiceSetter { tremoloDepth = it?.asDoubleOrNull() ?: tremoloDepth }

private fun applyTremoloDepth(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloDepth }, update = tremoloDepthMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloDepthMutation)
}

// -- tremolo.skew ----------------------------------------------------------------------------------------------------

private val tremoloSkewMutation = voiceSetter { tremoloSkew = it?.asDoubleOrNull() }

private fun applyTremoloSkew(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloSkew }, update = tremoloSkewMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloSkewMutation)
}

// -- tremolo.phase ---------------------------------------------------------------------------------------------------

private val tremoloPhaseMutation = voiceSetter { tremoloPhase = it?.asDoubleOrNull() }

private fun applyTremoloPhase(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.tremoloPhase }, update = tremoloPhaseMutation)
    }

    return source._liftOrReinterpretNumericalField(args, tremoloPhaseMutation)
}

// -- tremolo, the shape slot -----------------------------------------------------------------------------------------

private val tremoloShapeMutation = voiceSetter { shape -> tremoloShape = shape?.toString()?.lowercase() }

private fun applyTremoloShape(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._applyControlFromParams(args, tremoloShapeMutation) { src, ctrl ->
        src.tremoloShape = ctrl.tremoloShape
        src
    }
}
