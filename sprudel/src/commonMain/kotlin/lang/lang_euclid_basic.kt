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
import io.peekandpoke.klang.sprudel.pattern.EuclideanPattern

// -- euclid() ---------------------------------------------------------------------------------------------------------

private fun applyEuclid(source: SprudelPattern, pulses: Int, steps: Int, rotation: Int): SprudelPattern {
    return EuclideanPattern.create(
        inner = source,
        pulses = pulses,
        steps = steps,
        rotation = rotation,
    )
}

private fun applyEuclidFromArgs(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = 0)
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = false)
    }
}

/**
 * Changes the structure of the pattern to a Euclidean rhythm.
 *
 * Euclidean rhythms distribute `pulses` onsets as evenly as possible across `steps` steps using the
 * greatest common divisor algorithm.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A pattern with the Euclidean rhythm applied as structure.
 *
 * ```KlangScript(Playable)
 * s("hh").euclid(3, 8)  // classic 3-over-8 Euclidean rhythm
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").euclid(5, 16)  // 5 beats distributed across 16 steps
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
fun euclid(pulses: Int, steps: Int, pattern: PatternLike): SprudelPattern =
    euclid(pulses, steps)(listOf(pattern).asSprudelDslArgs().toPattern())

/**
 * Applies a Euclidean rhythm structure to the pattern.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A pattern with the Euclidean rhythm applied as structure.
 *
 * ```KlangScript(Playable)
 * s("hh").euclid(3, 8)  // classic 3-over-8 Euclidean rhythm
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@KlangScript.Function
fun SprudelPattern.euclid(pulses: Int, steps: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidFromArgs(this, listOf(pulses, steps).asSprudelDslArgs(callInfo))

/**
 * Applies a Euclidean rhythm structure to the mini-notation string.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A pattern with the Euclidean rhythm applied as structure.
 *
 * ```KlangScript(Playable)
 * "hh".euclid(3, 8).s()  // classic 3-over-8 Euclidean rhythm
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@KlangScript.Function
fun String.euclid(pulses: Int, steps: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).euclid(pulses, steps, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a Euclidean rhythm structure to the source pattern.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A [PatternMapperFn] that restructures the source as a Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(euclid(3, 8))  // via mapper
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@KlangScript.Function
fun euclid(pulses: Int, steps: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclid(pulses, steps, callInfo) }

/** Chains a euclid onto this [PatternMapperFn]; applies Euclidean rhythm structure to the result. */
@KlangScript.Function
fun PatternMapperFn.euclid(pulses: Int, steps: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.euclid(pulses, steps, callInfo) }

// -- euclidRot() ------------------------------------------------------------------------------------------------------

private fun applyEuclidRotFromArgs(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation)
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern, legato = false)
    }
}

/**
 * Like [euclid], but with an additional rotation parameter to offset the rhythm start point.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript(Playable)
 * s("hh").euclidRot(3, 8, 2)  // 3-over-8 rhythm, shifted by 2 steps
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").euclidRot(5, 16, 1)  // 5-over-16 shifted by 1 step
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
fun euclidRot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): SprudelPattern =
    euclidRot(pulses, steps, rotation)(listOf(pattern).asSprudelDslArgs().toPattern())

/**
 * Applies a rotated Euclidean rhythm structure to the pattern.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript(Playable)
 * s("hh").euclidRot(3, 8, 2)  // 3-over-8 rhythm, shifted by 2 steps
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@KlangScript.Function
fun SprudelPattern.euclidRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidRotFromArgs(this, listOf(pulses, steps, rotation).asSprudelDslArgs(callInfo))

/**
 * Applies a rotated Euclidean rhythm structure to the mini-notation string.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript(Playable)
 * "hh".euclidRot(3, 8, 2).s()  // 3-over-8 rhythm, shifted by 2 steps
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@KlangScript.Function
fun String.euclidRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).euclidRot(pulses, steps, rotation, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a rotated Euclidean rhythm to the source pattern.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A [PatternMapperFn] that restructures the source as a rotated Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(euclidRot(3, 8, 2))  // via mapper
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@KlangScript.Function
fun euclidRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidRot(pulses, steps, rotation, callInfo) }

/** Chains a euclidRot onto this [PatternMapperFn]; applies rotated Euclidean rhythm to the result. */
@KlangScript.Function
fun PatternMapperFn.euclidRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.euclidRot(pulses, steps, rotation, callInfo) }

/**
 * Alias for [euclidRot] — Euclidean rhythm with rotation.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript(Playable)
 * s("hh").euclidrot(3, 8, 2)  // lowercase alias
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd").euclidrot(5, 16, 3)  // 5-over-16, rotated by 3
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
fun euclidrot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): SprudelPattern =
    euclidrot(pulses, steps, rotation)(listOf(pattern).asSprudelDslArgs().toPattern())

/**
 * Alias for [euclidRot] applied to the pattern.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript(Playable)
 * s("hh").euclidrot(3, 8, 2).s()  // lowercase alias
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@KlangScript.Function
fun SprudelPattern.euclidrot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.euclidRot(pulses, steps, rotation, callInfo)

/**
 * Alias for [euclidRot] applied to the mini-notation string.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript(Playable)
 * "hh".euclidrot(3, 8, 2).s()  // lowercase alias
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@KlangScript.Function
fun String.euclidrot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).euclidrot(pulses, steps, rotation, callInfo)

/**
 * Returns a [PatternMapperFn] that is an alias for [euclidRot] — applies a rotated Euclidean rhythm.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A [PatternMapperFn] that restructures the source as a rotated Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(euclidrot(3, 8, 2))  // via mapper
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@KlangScript.Function
fun euclidrot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    euclidRot(pulses, steps, rotation, callInfo)

/** Chains a euclidrot onto this [PatternMapperFn]; alias for [PatternMapperFn.euclidRot]. */
@KlangScript.Function
fun PatternMapperFn.euclidrot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.euclidRot(pulses, steps, rotation, callInfo)

// -- bjork() ----------------------------------------------------------------------------------------------------------

private fun applyBjorkFromArgs(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val list = args.getOrNull(0)?.value as? List<*>
        ?: args.map { it.value }

    val pulsesVal = list.getOrNull(0)
    val stepsVal = list.getOrNull(1)
    val rotationVal = list.getOrNull(2)

    val pulsesPattern = when (pulsesVal) {
        is SprudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val stepsPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val rotationPattern = when (rotationVal) {
        is SprudelPattern -> rotationVal

        else -> parseMiniNotation(rotationVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(createSprudelVoiceData().voiceValueModifier(text))
        }
    }

    val staticPulses = pulsesVal?.asIntOrNull()
    val staticSteps = stepsVal?.asIntOrNull()
    val staticRotation = rotationVal?.asIntOrNull()

    return if (staticPulses != null && staticSteps != null && staticRotation != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation)
    } else {
        EuclideanPattern.control(
            inner = p,
            pulsesPattern = pulsesPattern,
            stepsPattern = stepsPattern,
            rotationPattern = rotationPattern,
            legato = false
        )
    }
}

/**
 * Applies a Euclidean rhythm specified as individual parameters (pulses, steps, rotation).
 *
 * Alternative to [euclidRot] that bundles parameters together. Named after Björk's use of
 * Euclidean rhythms in music.
 *
 * @param pulses   Number of onsets (beats).
 * @param steps    Total number of steps.
 * @param rotation Number of steps to rotate (default 0).
 * @return A pattern with the Euclidean rhythm applied.
 *
 * ```KlangScript(Playable)
 * s("hh").bjork(3, 8, 0)  // equivalent to euclid(3, 8)
 * ```
 *
 * ```KlangScript(Playable)
 * s("bd").bjork(5, 16, 2)  // 5-over-16 with rotation 2
 * ```
 * @category structural
 * @tags bjork, euclid, rhythm, rotation
 */
@KlangScript.Function
fun SprudelPattern.bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): SprudelPattern =
    applyBjorkFromArgs(this, listOf(listOf(pulses, steps, rotation)).asSprudelDslArgs(callInfo))

/** Like [bjork] applied to a mini-notation string. */
@KlangScript.Function
fun String.bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bjork(pulses, steps, rotation, callInfo)

/**
 * Returns a [PatternMapperFn] that applies a Euclidean rhythm specified as (pulses, steps, rotation).
 *
 * @param pulses   Number of onsets (beats).
 * @param steps    Total number of steps.
 * @param rotation Number of steps to rotate (default 0).
 * @return A [PatternMapperFn] that restructures the source as a bjork Euclidean rhythm.
 *
 * ```KlangScript(Playable)
 * s("hh").apply(bjork(3, 8, 0))  // via mapper
 * ```
 *
 * @category structural
 * @tags bjork, euclid, rhythm, rotation
 */
@KlangScript.Function
fun bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bjork(pulses, steps, rotation, callInfo) }

/** Chains a bjork onto this [PatternMapperFn]; applies a Euclidean rhythm (pulses, steps, rotation). */
@KlangScript.Function
fun PatternMapperFn.bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bjork(pulses, steps, rotation, callInfo) }

/** Like [bjork] as a top-level function taking an explicit pattern argument. */
fun bjork(pulses: Int, steps: Int, rotation: Int = 0, pattern: PatternLike): SprudelPattern =
    bjork(pulses, steps, rotation)(listOf(pattern).asSprudelDslArgs().toPattern())
