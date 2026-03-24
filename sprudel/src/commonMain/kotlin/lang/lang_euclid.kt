@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")

package io.peekandpoke.klang.sprudel.lang

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

fun applyEuclid(source: SprudelPattern, pulses: Int, steps: Int, rotation: Int): SprudelPattern {
    return EuclideanPattern.create(
        inner = source,
        pulses = pulses,
        steps = steps,
        rotation = rotation,
    )
}

internal val _euclid by dslPatternMapper { args, callInfo -> { p -> p._euclid(args, callInfo) } }

internal val SprudelPattern._euclid by dslPatternExtension { p, args, /* callInfo */ _ ->
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

    if (staticPulses != null && staticSteps != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = 0)
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = false)
    }
}

internal val String._euclid by dslStringExtension { p, args, callInfo -> p._euclid(args, callInfo) }
internal val PatternMapperFn._euclid by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_euclid(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.euclid(pulses: Int, steps: Int): SprudelPattern =
    this._euclid(listOf(pulses, steps).asSprudelDslArgs())

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
fun String.euclid(pulses: Int, steps: Int): SprudelPattern =
    this._euclid(listOf(pulses, steps).asSprudelDslArgs())

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
fun euclid(pulses: Int, steps: Int): PatternMapperFn =
    _euclid(listOf(pulses, steps).asSprudelDslArgs())

/** Chains a euclid onto this [PatternMapperFn]; applies Euclidean rhythm structure to the result. */
@SprudelDsl
fun PatternMapperFn.euclid(pulses: Int, steps: Int): PatternMapperFn =
    this._euclid(listOf(pulses, steps).asSprudelDslArgs())

// -- euclidRot() ------------------------------------------------------------------------------------------------------

internal val SprudelPattern._euclidRot by dslPatternExtension { p, args, /* callInfo */ _ ->
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

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
        applyEuclid(source = p, pulses = staticPulses, steps = staticSteps, rotation = staticRotation)
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern, legato = false)
    }
}

internal val String._euclidRot by dslStringExtension { p, args, callInfo -> p._euclidRot(args, callInfo) }
internal val _euclidRot by dslPatternMapper { args, callInfo -> { p -> p._euclidRot(args, callInfo) } }
internal val PatternMapperFn._euclidRot by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_euclidRot(args, callInfo))
}

internal val SprudelPattern._euclidrot by dslPatternExtension { p, args, callInfo -> p._euclidRot(args, callInfo) }
internal val String._euclidrot by dslStringExtension { p, args, callInfo -> p._euclidRot(args, callInfo) }
internal val _euclidrot by dslPatternMapper { args, callInfo -> { p -> p._euclidRot(args, callInfo) } }
internal val PatternMapperFn._euclidrot by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_euclidrot(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.euclidRot(pulses: Int, steps: Int, rotation: Int): SprudelPattern =
    this._euclidRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

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
fun String.euclidRot(pulses: Int, steps: Int, rotation: Int): SprudelPattern =
    this._euclidRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

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
fun euclidRot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    _euclidRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

/** Chains a euclidRot onto this [PatternMapperFn]; applies rotated Euclidean rhythm to the result. */
@SprudelDsl
fun PatternMapperFn.euclidRot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    this._euclidRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

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
fun SprudelPattern.euclidrot(pulses: Int, steps: Int, rotation: Int): SprudelPattern =
    this._euclidrot(listOf(pulses, steps, rotation).asSprudelDslArgs())

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
fun String.euclidrot(pulses: Int, steps: Int, rotation: Int): SprudelPattern =
    this._euclidrot(listOf(pulses, steps, rotation).asSprudelDslArgs())

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
fun euclidrot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    _euclidrot(listOf(pulses, steps, rotation).asSprudelDslArgs())

/** Chains a euclidrot onto this [PatternMapperFn]; alias for [PatternMapperFn.euclidRot]. */
@SprudelDsl
fun PatternMapperFn.euclidrot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    this._euclidrot(listOf(pulses, steps, rotation).asSprudelDslArgs())

// -- bjork() ----------------------------------------------------------------------------------------------------------

internal val _bjork by dslPatternMapper { args, callInfo -> { p -> p._bjork(args, callInfo) } }

internal val SprudelPattern._bjork by dslPatternExtension { p, args, /* callInfo */ _ ->
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

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
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

internal val String._bjork by dslStringExtension { p, args, callInfo -> p._bjork(args, callInfo) }
internal val PatternMapperFn._bjork by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_bjork(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.bjork(pulses: Int, steps: Int, rotation: Int = 0): SprudelPattern =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asSprudelDslArgs())

/** Like [bjork] applied to a mini-notation string. */
@SprudelDsl
fun String.bjork(pulses: Int, steps: Int, rotation: Int = 0): SprudelPattern =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asSprudelDslArgs())

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
fun bjork(pulses: Int, steps: Int, rotation: Int = 0): PatternMapperFn =
    _bjork(listOf(listOf(pulses, steps, rotation)).asSprudelDslArgs())

/** Chains a bjork onto this [PatternMapperFn]; applies a Euclidean rhythm (pulses, steps, rotation). */
@SprudelDsl
fun PatternMapperFn.bjork(pulses: Int, steps: Int, rotation: Int = 0): PatternMapperFn =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asSprudelDslArgs())

/** Like [bjork] as a top-level function taking an explicit pattern argument. */
@SprudelDsl
fun bjork(pulses: Int, steps: Int, rotation: Int = 0, pattern: PatternLike): SprudelPattern =
    bjork(pulses, steps, rotation)(listOf(pattern).asSprudelDslArgs().toPattern())

// -- euclidLegato() ---------------------------------------------------------------------------------------------------

internal val _euclidLegato by dslPatternMapper { args, callInfo -> { p -> p._euclidLegato(args, callInfo) } }

internal val SprudelPattern._euclidLegato by dslPatternExtension { p, args, /* callInfo */ _ ->
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

    if (staticPulses != null && staticSteps != null) {
        EuclideanPattern.createLegato(
            inner = p, pulses = staticPulses, steps = staticSteps, rotation = 0,
        )
    } else {
        EuclideanPattern.control(p, pulsesPattern, stepsPattern, rotationPattern = null, legato = true)
    }
}

internal val String._euclidLegato by dslStringExtension { p, args, callInfo -> p._euclidLegato(args, callInfo) }
internal val PatternMapperFn._euclidLegato by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_euclidLegato(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.euclidLegato(pulses: Int, steps: Int): SprudelPattern =
    this._euclidLegato(listOf(pulses, steps).asSprudelDslArgs())

/** Applies legato Euclidean structure to the mini-notation string. */
@SprudelDsl
fun String.euclidLegato(pulses: Int, steps: Int): SprudelPattern =
    this._euclidLegato(listOf(pulses, steps).asSprudelDslArgs())

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
fun euclidLegato(pulses: Int, steps: Int): PatternMapperFn =
    _euclidLegato(listOf(pulses, steps).asSprudelDslArgs())

/** Chains a euclidLegato onto this [PatternMapperFn]; applies legato Euclidean rhythm to the result. */
@SprudelDsl
fun PatternMapperFn.euclidLegato(pulses: Int, steps: Int): PatternMapperFn =
    this._euclidLegato(listOf(pulses, steps).asSprudelDslArgs())

/** Like [euclidLegato] as a top-level function taking an explicit pattern argument. */
@SprudelDsl
fun euclidLegato(pulses: Int, steps: Int, pattern: PatternLike): SprudelPattern =
    euclidLegato(pulses, steps)(listOf(pattern).asSprudelDslArgs().toPattern())

// -- euclidLegatoRot() ------------------------------------------------------------------------------------------------

internal val _euclidLegatoRot by dslPatternMapper { args, callInfo -> { p -> p._euclidLegatoRot(args, callInfo) } }

internal val SprudelPattern._euclidLegatoRot by dslPatternExtension { p, args, /* callInfo */ _ ->
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

    if (staticPulses != null && staticSteps != null && staticRotation != null) {
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

internal val String._euclidLegatoRot by dslStringExtension { p, args, callInfo ->
    p._euclidLegatoRot(args, callInfo)
}
internal val PatternMapperFn._euclidLegatoRot by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_euclidLegatoRot(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): SprudelPattern =
    this._euclidLegatoRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

/** Applies legato Euclidean structure with rotation to the mini-notation string. */
@SprudelDsl
fun String.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): SprudelPattern =
    this._euclidLegatoRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

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
fun euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    _euclidLegatoRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

/** Chains a euclidLegatoRot onto this [PatternMapperFn]; applies rotated legato Euclidean rhythm to the result. */
@SprudelDsl
fun PatternMapperFn.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    this._euclidLegatoRot(listOf(pulses, steps, rotation).asSprudelDslArgs())

/** Like [euclidLegatoRot] as a top-level function taking an explicit pattern argument. */
@SprudelDsl
fun euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): SprudelPattern =
    euclidLegatoRot(pulses, steps, rotation)(listOf(pattern).asSprudelDslArgs().toPattern())

// -- euclidish() ------------------------------------------------------------------------------------------------------

fun applyEuclidish(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
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

internal val _euclidish by dslPatternMapper { args, callInfo -> { p -> p._euclidish(args, callInfo) } }

internal val SprudelPattern._euclidish by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyEuclidish(p, args)
}

internal val String._euclidish by dslStringExtension { p, args, callInfo -> p._euclidish(args, callInfo) }
internal val PatternMapperFn._euclidish by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_euclidish(args, callInfo))
}

internal val _eish by dslPatternMapper { args, callInfo -> { p -> p._euclidish(args, callInfo) } }
internal val SprudelPattern._eish by dslPatternExtension { p, args, callInfo -> p._euclidish(args, callInfo) }
internal val String._eish by dslStringExtension { p, args, callInfo -> p._euclidish(args, callInfo) }
internal val PatternMapperFn._eish by dslPatternMapperExtension { m, args, callInfo ->
    m.chain(_eish(args, callInfo))
}

// ===== USER-FACING OVERLOADS =====

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
fun SprudelPattern.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): SprudelPattern =
    this._euclidish(listOf(pulses, steps, groove).asSprudelDslArgs())

/** Applies morphed Euclidean structure to the mini-notation string. */
@SprudelDsl
fun String.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): SprudelPattern =
    this._euclidish(listOf(pulses, steps, groove).asSprudelDslArgs())

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
fun euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): PatternMapperFn =
    _euclidish(listOf(pulses, steps, groove).asSprudelDslArgs())

/** Chains a euclidish onto this [PatternMapperFn]; applies morphed Euclidean rhythm to the result. */
@SprudelDsl
fun PatternMapperFn.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): PatternMapperFn =
    this._euclidish(listOf(pulses, steps, groove).asSprudelDslArgs())

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
fun SprudelPattern.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): SprudelPattern =
    this._eish(listOf(pulses, steps, groove).asSprudelDslArgs())

/** Alias for [euclidish]. */
@SprudelDsl
fun String.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): SprudelPattern =
    this._eish(listOf(pulses, steps, groove).asSprudelDslArgs())

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
fun eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): PatternMapperFn =
    _eish(listOf(pulses, steps, groove).asSprudelDslArgs())

/** Chains an eish onto this [PatternMapperFn]; alias for [PatternMapperFn.euclidish]. */
@SprudelDsl
fun PatternMapperFn.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): PatternMapperFn =
    this._eish(listOf(pulses, steps, groove).asSprudelDslArgs())
