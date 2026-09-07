/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.EuclideanMorphPattern
import io.peekandpoke.klang.sprudel.pattern.EuclideanPattern

// -- euclidLegato() ---------------------------------------------------------------------------------------------------

private fun applyEuclidLegatoFromArgs(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value

    val pulsesPattern: SprudelPattern = when (pulsesVal) {
        is SprudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()

    return if (staticPulses != null && staticSteps != null) {
        EuclideanPattern.createLegato(
            inner = p, pulses = staticPulses, steps = staticSteps, rotation = 0,
        )
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = true)
    }
}

/**
 * Like [euclid], but each pulse is held until the next pulse, so there are no gaps between notes.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A legato Euclidean rhythm pattern (no rests between onsets).
 *
 * ```KlangScript(Playable)
 * s("hh").euclidLegato(3, 8)  // 3-over-8 legato (held notes)
 * ```
 *
 * ```KlangScript(Playable)
 * note("c").euclidLegato(5, 8)  // 5 legato notes across 8 steps
 * ```
 *
 * @category structural
 * @tags euclidLegato, euclid, legato, rhythm, structure
 */
@KlangScript.Function
fun SprudelPattern.euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidLegatoFromArgs(this, listOf(pulses, steps).asSprudelDslArgs(callInfo))

/** Applies legato Euclidean structure to the mini-notation string. */
@KlangScript.Function
fun String.euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).euclidLegato(pulses, steps, callInfo)

/**
 * Returns a [PatternMapperFn] that applies legato Euclidean rhythm structure to the source pattern.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A [PatternMapperFn] that restructures the source as a legato Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(euclidLegato(3, 8))  // via mapper
 * ```
 *
 * @category structural
 * @tags euclidLegato, euclid, legato, rhythm, structure
 */
@KlangScript.Function
fun euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidLegato(pulses, steps, callInfo) }

/** Chains a euclidLegato onto this [PatternMapperFn]; applies legato Euclidean rhythm to the result. */
@KlangScript.Function
fun PatternMapperFn.euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.euclidLegato(pulses, steps, callInfo) }

/** Like [euclidLegato] as a top-level function taking an explicit pattern argument. */
fun euclidLegato(pulses: Int, steps: Int, pattern: PatternLike): SprudelPattern =
    euclidLegato(pulses, steps)(listOf(pattern).asSprudelDslArgs().toPattern())

// -- euclidLegatoRot() ------------------------------------------------------------------------------------------------

private fun applyEuclidLegatoRotFromArgs(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val rotationArg = args.getOrNull(2)
    val rotationVal = rotationArg?.value

    val pulsesPattern: SprudelPattern = when (pulsesVal) {
        is SprudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val rotationPattern: SprudelPattern = when (rotationVal) {
        is SprudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()
    val staticRotation = rotationVal?.asIntOrNull()

    return if (staticPulses != null && staticSteps != null && staticRotation != null) {
        EuclideanPattern.createLegato(
            inner = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation,
        )
    } else {
        EuclideanPattern.control(
            inner = p,
            pulsesPattern = pulsesPattern,
            stepsPattern = stepsPattern,
            rotationPattern = rotationPattern,
            legato = true
        )
    }
}

/**
 * Like [euclidLegato], but with rotation — each pulse is held until the next, with a step offset.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A legato Euclidean rhythm with rotation applied.
 *
 * ```KlangScript(Playable)
 * s("hh").euclidLegatoRot(3, 8, 2)  // legato 3-over-8, rotated by 2
 * ```
 *
 * ```KlangScript(Playable)
 * note("c").euclidLegatoRot(5, 8, 1)  // legato 5-over-8, rotated by 1
 * ```
 *
 * @category structural
 * @tags euclidLegatoRot, euclid, legato, rotation, rhythm
 */
@KlangScript.Function
fun SprudelPattern.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidLegatoRotFromArgs(this, listOf(pulses, steps, rotation).asSprudelDslArgs(callInfo))

/** Applies legato Euclidean structure with rotation to the mini-notation string. */
@KlangScript.Function
fun String.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).euclidLegatoRot(pulses, steps, rotation, callInfo)

/**
 * Returns a [PatternMapperFn] that applies legato Euclidean rhythm with rotation to the source pattern.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A [PatternMapperFn] that restructures the source as a rotated legato Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(euclidLegatoRot(3, 8, 2))  // via mapper
 * ```
 *
 * @category structural
 * @tags euclidLegatoRot, euclid, legato, rotation, rhythm
 */
@KlangScript.Function
fun euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidLegatoRot(pulses, steps, rotation, callInfo) }

/** Chains a euclidLegatoRot onto this [PatternMapperFn]; applies rotated legato Euclidean rhythm to the result. */
@KlangScript.Function
fun PatternMapperFn.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.euclidLegatoRot(pulses, steps, rotation, callInfo) }

/** Like [euclidLegatoRot] as a top-level function taking an explicit pattern argument. */
fun euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): SprudelPattern =
    euclidLegatoRot(pulses, steps, rotation)(listOf(pattern).asSprudelDslArgs().toPattern())

// -- euclidish() ------------------------------------------------------------------------------------------------------

private fun applyEuclidishFromArgs(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val grooveArg = args.getOrNull(2)
    val grooveVal = grooveArg?.value

    val pulsesPattern: SprudelPattern = when (pulsesVal) {
        is SprudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    // groove defaults to 0 (straight euclid)
    val groovePattern = when (grooveVal) {
        is SprudelPattern -> grooveVal
        else -> {
            parseMiniNotation(grooveArg ?: SprudelDslArg.of("0")) { text, _ ->
                AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
            }
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()

    return if (staticPulses != null && staticSteps != null) {
        // Static path: use original EuclideanMorphPattern
        if (staticPulses <= 0 || staticSteps <= 0) {
            silence
        } else {
            val structPattern = EuclideanMorphPattern.static(
                pulses = staticPulses, steps = staticSteps, groovePattern = groovePattern
            )

            source.struct(structPattern)
        }
    } else {
        val structPattern = EuclideanMorphPattern.control(
            pulsesPattern = pulsesPattern, stepsPattern = stepsPattern, groovePattern = groovePattern
        )

        source.struct(structPattern)
    }
}

/**
 * A `euclid` variant with a `groove` parameter that morphs the rhythm from strict (0) to even (1).
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict Euclidean) to 1 (completely even spacing).
 * @return A pattern with the morphed Euclidean rhythm applied as structure.
 *
 * ```KlangScript(Playable)
 * s("hh").euclidish(3, 8, 0.5)  // halfway between strict and even
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").euclidish(5, 16, 0.0)  // same as euclid(5, 16)
 * ```
 *
 * @alias eish
 * @category structural
 * @tags euclidish, euclid, groove, morph, rhythm
 */
@KlangScript.Function
fun SprudelPattern.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidishFromArgs(this, listOf(pulses, steps, groove).asSprudelDslArgs(callInfo))

/** Applies morphed Euclidean structure to the mini-notation string. */
@KlangScript.Function
fun String.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).euclidish(pulses, steps, groove, callInfo)

/**
 * Returns a [PatternMapperFn] that applies morphed Euclidean rhythm structure to the source pattern.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict Euclidean) to 1 (completely even spacing).
 * @return A [PatternMapperFn] that restructures the source as a morphed Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(euclidish(3, 8, 0.5))  // via mapper
 * ```
 *
 * @alias eish
 * @category structural
 * @tags euclidish, euclid, groove, morph, rhythm
 */
@KlangScript.Function
fun euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidish(pulses, steps, groove, callInfo) }

/** Chains a euclidish onto this [PatternMapperFn]; applies morphed Euclidean rhythm to the result. */
@KlangScript.Function
fun PatternMapperFn.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.euclidish(pulses, steps, groove, callInfo) }

/**
 * Alias for [euclidish] — `euclid` variant with groove morphing.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict) to 1 (even).
 * @return A pattern with the morphed Euclidean rhythm applied.
 *
 * ```KlangScript(Playable)
 * s("hh").eish(3, 8, 0.5)  // halfway between strict and even
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").eish(5, 16, 1.0)  // completely even spacing
 * ```
 *
 * @alias euclidish
 * @category structural
 * @tags eish, euclidish, euclid, groove, morph, rhythm
 */
@KlangScript.Function
fun SprudelPattern.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    this.euclidish(pulses, steps, groove, callInfo)

/** Alias for [euclidish]. */
@KlangScript.Function
fun String.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).eish(pulses, steps, groove, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [euclidish] — applies morphed Euclidean rhythm.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict) to 1 (even).
 * @return A [PatternMapperFn] that restructures the source as a morphed Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(eish(3, 8, 0.5))  // via mapper
 * ```
 *
 * @alias euclidish
 * @category structural
 * @tags eish, euclidish, euclid, groove, morph, rhythm
 */
@KlangScript.Function
fun eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): PatternMapperFn =
    euclidish(pulses, steps, groove, callInfo)

/** Chains an eish onto this [PatternMapperFn]; alias for [PatternMapperFn.euclidish]. */
@KlangScript.Function
fun PatternMapperFn.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.euclidish(pulses, steps, groove, callInfo)
