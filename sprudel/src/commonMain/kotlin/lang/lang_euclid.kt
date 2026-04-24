@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.sprudel.pattern.AtomicPattern
import io.peekandpoke.klang.sprudel.pattern.EuclideanMorphPattern
import io.peekandpoke.klang.sprudel.pattern.EuclideanPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in SprudelRegistry.
 */
var sprudelLangEuclidInit = false

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
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun String.euclid(pulses: Int, steps: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().euclid(pulses, steps, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun euclid(pulses: Int, steps: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclid(pulses, steps, callInfo) }

/** Chains a euclid onto this [PatternMapperFn]; applies Euclidean rhythm structure to the result. */
@SprudelDsl
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
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern: SprudelPattern = when (rotationVal) {
        is SprudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun String.euclidRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().euclidRot(pulses, steps, rotation, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun euclidRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidRot(pulses, steps, rotation, callInfo) }

/** Chains a euclidRot onto this [PatternMapperFn]; applies rotated Euclidean rhythm to the result. */
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun String.euclidrot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().euclidrot(pulses, steps, rotation, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun euclidrot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    euclidRot(pulses, steps, rotation, callInfo)

/** Chains a euclidrot onto this [PatternMapperFn]; alias for [PatternMapperFn.euclidRot]. */
@SprudelDsl
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
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern = when (rotationVal) {
        is SprudelPattern -> rotationVal

        else -> parseMiniNotation(rotationVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): SprudelPattern =
    applyBjorkFromArgs(this, listOf(listOf(pulses, steps, rotation)).asSprudelDslArgs(callInfo))

/** Like [bjork] applied to a mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().bjork(pulses, steps, rotation, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bjork(pulses, steps, rotation, callInfo) }

/** Chains a bjork onto this [PatternMapperFn]; applies a Euclidean rhythm (pulses, steps, rotation). */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.bjork(pulses: Int, steps: Int, rotation: Int = 0, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bjork(pulses, steps, rotation, callInfo) }

/** Like [bjork] as a top-level function taking an explicit pattern argument. */
@SprudelDsl
fun bjork(pulses: Int, steps: Int, rotation: Int = 0, pattern: PatternLike): SprudelPattern =
    bjork(pulses, steps, rotation)(listOf(pattern).asSprudelDslArgs().toPattern())

// -- euclidLegato() ---------------------------------------------------------------------------------------------------

private fun applyEuclidLegatoFromArgs(p: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value

    val pulsesPattern: SprudelPattern = when (pulsesVal) {
        is SprudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidLegatoFromArgs(this, listOf(pulses, steps).asSprudelDslArgs(callInfo))

/** Applies legato Euclidean structure to the mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().euclidLegato(pulses, steps, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidLegato(pulses, steps, callInfo) }

/** Chains a euclidLegato onto this [PatternMapperFn]; applies legato Euclidean rhythm to the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.euclidLegato(pulses: Int, steps: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.euclidLegato(pulses, steps, callInfo) }

/** Like [euclidLegato] as a top-level function taking an explicit pattern argument. */
@SprudelDsl
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
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern: SprudelPattern = when (rotationVal) {
        is SprudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidLegatoRotFromArgs(this, listOf(pulses, steps, rotation).asSprudelDslArgs(callInfo))

/** Applies legato Euclidean structure with rotation to the mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().euclidLegatoRot(pulses, steps, rotation, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidLegatoRot(pulses, steps, rotation, callInfo) }

/** Chains a euclidLegatoRot onto this [PatternMapperFn]; applies rotated legato Euclidean rhythm to the result. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.euclidLegatoRot(pulses, steps, rotation, callInfo) }

/** Like [euclidLegatoRot] as a top-level function taking an explicit pattern argument. */
@SprudelDsl
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
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: SprudelPattern = when (stepsVal) {
        is SprudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: SprudelDslArg.of("0")) { text, _ ->
            AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    // groove defaults to 0 (straight euclid)
    val groovePattern = when (grooveVal) {
        is SprudelPattern -> grooveVal
        else -> {
            parseMiniNotation(grooveArg ?: SprudelDslArg.of("0")) { text, _ ->
                AtomicPattern(SprudelVoiceData.empty.voiceValueModifier(text))
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    applyEuclidishFromArgs(this, listOf(pulses, steps, groove).asSprudelDslArgs(callInfo))

/** Applies morphed Euclidean structure to the mini-notation string. */
@SprudelDsl
@KlangScript.Function
fun String.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().euclidish(pulses, steps, groove, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.euclidish(pulses, steps, groove, callInfo) }

/** Chains a euclidish onto this [PatternMapperFn]; applies morphed Euclidean rhythm to the result. */
@SprudelDsl
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
@SprudelDsl
@KlangScript.Function
fun SprudelPattern.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    this.euclidish(pulses, steps, groove, callInfo)

/** Alias for [euclidish]. */
@SprudelDsl
@KlangScript.Function
fun String.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern().eish(pulses, steps, groove, callInfo)

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
@SprudelDsl
@KlangScript.Function
fun eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): PatternMapperFn =
    euclidish(pulses, steps, groove, callInfo)

/** Chains an eish onto this [PatternMapperFn]; alias for [PatternMapperFn.euclidish]. */
@SprudelDsl
@KlangScript.Function
fun PatternMapperFn.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0, callInfo: CallInfo? = null): PatternMapperFn =
    this.euclidish(pulses, steps, groove, callInfo)
