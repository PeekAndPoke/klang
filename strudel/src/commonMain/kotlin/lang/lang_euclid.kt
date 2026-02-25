package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData
import io.peekandpoke.klang.strudel.lang.StrudelDslArg.Companion.asStrudelDslArgs
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotation
import io.peekandpoke.klang.strudel.pattern.AtomicPattern
import io.peekandpoke.klang.strudel.pattern.EuclideanMorphPattern
import io.peekandpoke.klang.strudel.pattern.EuclideanPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangEuclidInit = false

// -- euclid() ---------------------------------------------------------------------------------------------------------

fun applyEuclid(source: StrudelPattern, pulses: Int, steps: Int, rotation: Int): StrudelPattern {
    return EuclideanPattern.create(
        inner = source,
        pulses = pulses,
        steps = steps,
        rotation = rotation,
    )
}

internal val _euclid by dslPatternMapper { args, callInfo -> { p -> p._euclid(args, callInfo) } }

internal val StrudelPattern._euclid by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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
 * ```KlangScript
 * s("hh").euclid(3, 8)  // classic 3-over-8 Euclidean rhythm
 * ```
 *
 * ```KlangScript
 * s("bd").euclid(5, 16)  // 5 beats distributed across 16 steps
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@StrudelDsl
fun euclid(pulses: Int, steps: Int, pattern: PatternLike): StrudelPattern =
    euclid(pulses, steps)(listOf(pattern).asStrudelDslArgs().toPattern())

/**
 * Applies a Euclidean rhythm structure to the pattern.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A pattern with the Euclidean rhythm applied as structure.
 *
 * ```KlangScript
 * s("hh").euclid(3, 8)  // classic 3-over-8 Euclidean rhythm
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@StrudelDsl
fun StrudelPattern.euclid(pulses: Int, steps: Int): StrudelPattern =
    this._euclid(listOf(pulses, steps).asStrudelDslArgs())

/**
 * Applies a Euclidean rhythm structure to the mini-notation string.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A pattern with the Euclidean rhythm applied as structure.
 *
 * ```KlangScript
 * "hh".euclid(3, 8).s()  // classic 3-over-8 Euclidean rhythm
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@StrudelDsl
fun String.euclid(pulses: Int, steps: Int): StrudelPattern =
    this._euclid(listOf(pulses, steps).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a Euclidean rhythm structure to the source pattern.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A [PatternMapperFn] that restructures the source as a Euclidean rhythm.
 *
 * ```KlangScript
 * s("hh").apply(euclid(3, 8))  // via mapper
 * ```
 *
 * @category structural
 * @tags euclid, rhythm, euclidean, structure, pattern
 */
@StrudelDsl
fun euclid(pulses: Int, steps: Int): PatternMapperFn =
    _euclid(listOf(pulses, steps).asStrudelDslArgs())

/** Chains a euclid onto this [PatternMapperFn]; applies Euclidean rhythm structure to the result. */
@StrudelDsl
fun PatternMapperFn.euclid(pulses: Int, steps: Int): PatternMapperFn =
    this._euclid(listOf(pulses, steps).asStrudelDslArgs())

// -- euclidRot() ------------------------------------------------------------------------------------------------------

internal val StrudelPattern._euclidRot by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val rotationArg = args.getOrNull(2)
    val rotationVal = rotationArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern: StrudelPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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

internal val StrudelPattern._euclidrot by dslPatternExtension { p, args, callInfo -> p._euclidRot(args, callInfo) }
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
 * ```KlangScript
 * s("hh").euclidRot(3, 8, 2)  // 3-over-8 rhythm, shifted by 2 steps
 * ```
 *
 * ```KlangScript
 * s("bd").euclidRot(5, 16, 1)  // 5-over-16 shifted by 1 step
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@StrudelDsl
fun euclidRot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): StrudelPattern =
    euclidRot(pulses, steps, rotation)(listOf(pattern).asStrudelDslArgs().toPattern())

/**
 * Applies a rotated Euclidean rhythm structure to the pattern.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript
 * s("hh").euclidRot(3, 8, 2)  // 3-over-8 rhythm, shifted by 2 steps
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@StrudelDsl
fun StrudelPattern.euclidRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/**
 * Applies a rotated Euclidean rhythm structure to the mini-notation string.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript
 * "hh".euclidRot(3, 8, 2).s()  // 3-over-8 rhythm, shifted by 2 steps
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@StrudelDsl
fun String.euclidRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a rotated Euclidean rhythm to the source pattern.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A [PatternMapperFn] that restructures the source as a rotated Euclidean rhythm.
 *
 * ```KlangScript
 * s("hh").apply(euclidRot(3, 8, 2))  // via mapper
 * ```
 *
 * @alias euclidrot
 * @category structural
 * @tags euclidRot, euclid, rhythm, rotation, structure
 */
@StrudelDsl
fun euclidRot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    _euclidRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/** Chains a euclidRot onto this [PatternMapperFn]; applies rotated Euclidean rhythm to the result. */
@StrudelDsl
fun PatternMapperFn.euclidRot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    this._euclidRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/**
 * Alias for [euclidRot] — Euclidean rhythm with rotation.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript
 * s("hh").euclidrot(3, 8, 2)  // lowercase alias
 * ```
 *
 * ```KlangScript
 * s("sd").euclidrot(5, 16, 3)  // 5-over-16, rotated by 3
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@StrudelDsl
fun euclidrot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): StrudelPattern =
    euclidrot(pulses, steps, rotation)(listOf(pattern).asStrudelDslArgs().toPattern())

/**
 * Alias for [euclidRot] applied to the pattern.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript
 * s("hh").euclidrot(3, 8, 2).s()  // lowercase alias
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@StrudelDsl
fun StrudelPattern.euclidrot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidrot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/**
 * Alias for [euclidRot] applied to the mini-notation string.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A rotated Euclidean rhythm pattern.
 *
 * ```KlangScript
 * "hh".euclidrot(3, 8, 2).s()  // lowercase alias
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@StrudelDsl
fun String.euclidrot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidrot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that is an alias for [euclidRot] — applies a rotated Euclidean rhythm.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A [PatternMapperFn] that restructures the source as a rotated Euclidean rhythm.
 *
 * ```KlangScript
 * s("hh").apply(euclidrot(3, 8, 2))  // via mapper
 * ```
 *
 * @alias euclidRot
 * @category structural
 * @tags euclidrot, euclidRot, euclid, rhythm, rotation
 */
@StrudelDsl
fun euclidrot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    _euclidrot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/** Chains a euclidrot onto this [PatternMapperFn]; alias for [PatternMapperFn.euclidRot]. */
@StrudelDsl
fun PatternMapperFn.euclidrot(pulses: Int, steps: Int, rotation: Int): PatternMapperFn =
    this._euclidrot(listOf(pulses, steps, rotation).asStrudelDslArgs())

// -- bjork() ----------------------------------------------------------------------------------------------------------

internal val _bjork by dslPatternMapper { args, callInfo -> { p -> p._bjork(args, callInfo) } }

internal val StrudelPattern._bjork by dslPatternExtension { p, args, /* callInfo */ _ ->
    val list = args.getOrNull(0)?.value as? List<*>
        ?: args.map { it.value }

    val pulsesVal = list.getOrNull(0)
    val stepsVal = list.getOrNull(1)
    val rotationVal = list.getOrNull(2)

    val pulsesPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationVal?.toString() ?: "0") { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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
 * ```KlangScript
 * s("hh").bjork(3, 8, 0)  // equivalent to euclid(3, 8)
 * ```
 *
 * ```KlangScript
 * s("bd").bjork(5, 16, 2)  // 5-over-16 with rotation 2
 * ```
 * @category structural
 * @tags bjork, euclid, rhythm, rotation
 */
@StrudelDsl
fun StrudelPattern.bjork(pulses: Int, steps: Int, rotation: Int = 0): StrudelPattern =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asStrudelDslArgs())

/** Like [bjork] applied to a mini-notation string. */
@StrudelDsl
fun String.bjork(pulses: Int, steps: Int, rotation: Int = 0): StrudelPattern =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asStrudelDslArgs())

/**
 * Returns a [PatternMapperFn] that applies a Euclidean rhythm specified as (pulses, steps, rotation).
 *
 * @param pulses   Number of onsets (beats).
 * @param steps    Total number of steps.
 * @param rotation Number of steps to rotate (default 0).
 * @return A [PatternMapperFn] that restructures the source as a bjork Euclidean rhythm.
 *
 * ```KlangScript
 * s("hh").apply(bjork(3, 8, 0))  // via mapper
 * ```
 *
 * @category structural
 * @tags bjork, euclid, rhythm, rotation
 */
@StrudelDsl
fun bjork(pulses: Int, steps: Int, rotation: Int = 0): PatternMapperFn =
    _bjork(listOf(listOf(pulses, steps, rotation)).asStrudelDslArgs())

/** Chains a bjork onto this [PatternMapperFn]; applies a Euclidean rhythm (pulses, steps, rotation). */
@StrudelDsl
fun PatternMapperFn.bjork(pulses: Int, steps: Int, rotation: Int = 0): PatternMapperFn =
    this._bjork(listOf(listOf(pulses, steps, rotation)).asStrudelDslArgs())

/** Like [bjork] as a top-level function taking an explicit pattern argument. */
@StrudelDsl
fun bjork(pulses: Int, steps: Int, rotation: Int = 0, pattern: PatternLike): StrudelPattern =
    bjork(pulses, steps, rotation)(listOf(pattern).asStrudelDslArgs().toPattern())

// -- euclidLegato() ---------------------------------------------------------------------------------------------------

internal val _euclidLegato by dslPatternFunction { args, callInfo ->
    val pattern = args.drop(2).toPattern()
    pattern._euclidLegato(args, callInfo)
}

internal val StrudelPattern._euclidLegato by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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

// ===== USER-FACING OVERLOADS =====

/**
 * Like [euclid], but each pulse is held until the next pulse, so there are no gaps between notes.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @return A legato Euclidean rhythm pattern (no rests between onsets).
 *
 * ```KlangScript
 * s("hh").euclidLegato(3, 8)  // 3-over-8 legato (held notes)
 * ```
 *
 * ```KlangScript
 * note("c").euclidLegato(5, 8)  // 5 legato notes across 8 steps
 * ```
 * @category structural
 * @tags euclidLegato, euclid, legato, rhythm, structure
 */
@StrudelDsl
fun euclidLegato(pulses: Int, steps: Int, pattern: PatternLike): StrudelPattern =
    _euclidLegato(listOf(pulses, steps, pattern).asStrudelDslArgs())

/** Applies legato Euclidean structure to the pattern. */
@StrudelDsl
fun StrudelPattern.euclidLegato(pulses: Int, steps: Int): StrudelPattern =
    this._euclidLegato(listOf(pulses, steps).asStrudelDslArgs())

/** Applies legato Euclidean structure to the mini-notation string. */
@StrudelDsl
fun String.euclidLegato(pulses: Int, steps: Int): StrudelPattern =
    this._euclidLegato(listOf(pulses, steps).asStrudelDslArgs())

// -- euclidLegatoRot() ------------------------------------------------------------------------------------------------

internal val _euclidLegatoRot by dslPatternFunction { args, callInfo ->
    val pattern = args.drop(3).toPattern()
    pattern._euclidLegatoRot(args, callInfo)
}

internal val StrudelPattern._euclidLegatoRot by dslPatternExtension { p, args, /* callInfo */ _ ->
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val rotationArg = args.getOrNull(2)
    val rotationVal = rotationArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val rotationPattern: StrudelPattern = when (rotationVal) {
        is StrudelPattern -> rotationVal

        else -> parseMiniNotation(rotationArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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

// ===== USER-FACING OVERLOADS =====

/**
 * Like [euclidLegato], but with rotation — each pulse is held until the next, with a step offset.
 *
 * @param pulses   Number of onsets (beats) to place.
 * @param steps    Total number of steps in the rhythm.
 * @param rotation Number of steps to rotate the pattern by.
 * @return A legato Euclidean rhythm with rotation applied.
 *
 * ```KlangScript
 * s("hh").euclidLegatoRot(3, 8, 2)  // legato 3-over-8, rotated by 2
 * ```
 *
 * ```KlangScript
 * note("c").euclidLegatoRot(5, 8, 1)  // legato 5-over-8, rotated by 1
 * ```
 * @category structural
 * @tags euclidLegatoRot, euclid, legato, rotation, rhythm
 */
@StrudelDsl
fun euclidLegatoRot(pulses: Int, steps: Int, rotation: Int, pattern: PatternLike): StrudelPattern =
    _euclidLegatoRot(listOf(pulses, steps, rotation, pattern).asStrudelDslArgs())

/** Applies legato Euclidean structure with rotation to the pattern. */
@StrudelDsl
fun StrudelPattern.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidLegatoRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

/** Applies legato Euclidean structure with rotation to the mini-notation string. */
@StrudelDsl
fun String.euclidLegatoRot(pulses: Int, steps: Int, rotation: Int): StrudelPattern =
    this._euclidLegatoRot(listOf(pulses, steps, rotation).asStrudelDslArgs())

// -- euclidish() ------------------------------------------------------------------------------------------------------

fun applyEuclidish(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val pulsesArg = args.getOrNull(0)
    val pulsesVal = pulsesArg?.value
    val stepsArg = args.getOrNull(1)
    val stepsVal = stepsArg?.value
    val grooveArg = args.getOrNull(2)
    val grooveVal = grooveArg?.value

    val pulsesPattern: StrudelPattern = when (pulsesVal) {
        is StrudelPattern -> pulsesVal

        else -> parseMiniNotation(pulsesArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    val stepsPattern: StrudelPattern = when (stepsVal) {
        is StrudelPattern -> stepsVal

        else -> parseMiniNotation(stepsArg ?: StrudelDslArg.of("0")) { text, _ ->
            AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
        }
    }

    // groove defaults to 0 (straight euclid)
    val groovePattern = when (grooveVal) {
        is StrudelPattern -> grooveVal
        else -> {
            parseMiniNotation(grooveArg ?: StrudelDslArg.of("0")) { text, _ ->
                AtomicPattern(StrudelVoiceData.empty.voiceValueModifier(text))
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

internal val _euclidish by dslPatternFunction { args, /* callInfo */ _ ->
    // euclidish(pulses, steps, groove, pat)
    val pattern = args.drop(3).toPattern()
    applyEuclidish(pattern, args.take(3))
}

internal val StrudelPattern._euclidish by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyEuclidish(p, args)
}

internal val String._euclidish by dslStringExtension { p, args, callInfo -> p._euclidish(args, callInfo) }

internal val _eish by dslPatternFunction { args, callInfo -> _euclidish(args, callInfo) }
internal val StrudelPattern._eish by dslPatternExtension { p, args, callInfo -> p._euclidish(args, callInfo) }
internal val String._eish by dslStringExtension { p, args, callInfo -> p._euclidish(args, callInfo) }

// ===== USER-FACING OVERLOADS =====

/**
 * A `euclid` variant with a `groove` parameter that morphs the rhythm from strict (0) to even (1).
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict Euclidean) to 1 (completely even spacing).
 * @return A pattern with the morphed Euclidean rhythm applied as structure.
 *
 * ```KlangScript
 * s("hh").euclidish(3, 8, 0.5)  // halfway between strict and even
 * ```
 *
 * ```KlangScript
 * s("bd").euclidish(5, 16, 0.0)  // same as euclid(5, 16)
 * ```
 * @alias eish
 * @category structural
 * @tags euclidish, euclid, groove, morph, rhythm
 */
@StrudelDsl
fun euclidish(pulses: Int, steps: Int, groove: PatternLike, pattern: PatternLike): StrudelPattern =
    _euclidish(listOf(pulses, steps, groove, pattern).asStrudelDslArgs())

/** Applies morphed Euclidean structure to the pattern. */
@StrudelDsl
fun StrudelPattern.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._euclidish(listOf(pulses, steps, groove).asStrudelDslArgs())

/** Applies morphed Euclidean structure to the mini-notation string. */
@StrudelDsl
fun String.euclidish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._euclidish(listOf(pulses, steps, groove).asStrudelDslArgs())

/**
 * Alias for [euclidish] — `euclid` variant with groove morphing.
 *
 * @param pulses Number of onsets (beats) to place.
 * @param steps  Total number of steps in the rhythm.
 * @param groove Morph factor from 0 (strict) to 1 (even).
 * @return A pattern with the morphed Euclidean rhythm applied.
 *
 * ```KlangScript
 * s("hh").eish(3, 8, 0.5)  // halfway between strict and even
 * ```
 *
 * ```KlangScript
 * s("bd").eish(5, 16, 1.0)  // completely even spacing
 * ```
 * @alias euclidish
 * @category structural
 * @tags eish, euclidish, euclid, groove, morph, rhythm
 */
@StrudelDsl
fun eish(pulses: Int, steps: Int, groove: PatternLike, pattern: PatternLike): StrudelPattern =
    _eish(listOf(pulses, steps, groove, pattern).asStrudelDslArgs())

/** Alias for [euclidish]. */
@StrudelDsl
fun StrudelPattern.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._eish(listOf(pulses, steps, groove).asStrudelDslArgs())

/** Alias for [euclidish]. */
@StrudelDsl
fun String.eish(pulses: Int, steps: Int, groove: PatternLike = 0.0): StrudelPattern =
    this._eish(listOf(pulses, steps, groove).asStrudelDslArgs())
