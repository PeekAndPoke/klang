package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel._liftData

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangSampleInit = false

// -- begin() ----------------------------------------------------------------------------------------------------------

private val beginMutation = voiceModifier { copy(begin = it?.asDoubleOrNull()) }

private fun applyBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    // 1. Create a control pattern where 'begin' is already set correctly
    val control = args.toPattern(beginMutation)
    // 2. Use liftData to MERGE the control data into the source
    return source._liftData(control)
}

/** Sets the sample start position (0..1) */
@StrudelDsl
val begin by dslFunction { args, /* callInfo */ _ -> args.toPattern(beginMutation) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val StrudelPattern.begin by dslPatternExtension { p, args, /* callInfo */ _ -> applyBegin(p, args) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val String.begin by dslStringExtension { p, args, callInfo -> p.begin(args, callInfo) }

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceModifier { copy(end = it?.asDoubleOrNull()) }

private fun applyEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(endMutation)
    return source._liftData(control)
}

/** Sets the sample end position (0..1) */
@StrudelDsl
val end by dslFunction { args, /* callInfo */ _ -> args.toPattern(endMutation) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val StrudelPattern.end by dslPatternExtension { p, args, /* callInfo */ _ -> applyEnd(p, args) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val String.end by dslStringExtension { p, args, callInfo -> p.end(args, callInfo) }

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceModifier { copy(speed = it?.asDoubleOrNull()) }

private fun applySpeed(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(speedMutation)
    return source._liftData(control)
}

/** Sets the sample playback speed */
@StrudelDsl
val speed by dslFunction { args, /* callInfo */ _ -> args.toPattern(speedMutation) }

/** Sets the sample playback speed */
@StrudelDsl
val StrudelPattern.speed by dslPatternExtension { p, args, /* callInfo */ _ -> applySpeed(p, args) }

/** Sets the sample playback speed */
@StrudelDsl
val String.speed by dslStringExtension { p, args, callInfo -> p.speed(args, callInfo) }

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceModifier { copy(loop = it?.asVoiceValue()?.asBoolean) }

private fun applyLoop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(loopMutation)
    return source._liftData(control)
}

/** Enables sample looping */
@StrudelDsl
val loop by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopMutation) }

/** Enables sample looping */
@StrudelDsl
val StrudelPattern.loop by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoop(p, args) }

/** Enables sample looping */
@StrudelDsl
val String.loop by dslStringExtension { p, args, callInfo -> p.loop(args, callInfo) }

// -- loopBegin() / loopb() --------------------------------------------------------------------------------------------

private val loopBeginMutation = voiceModifier { copy(loopBegin = it?.asDoubleOrNull()) }

private fun applyLoopBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(loopBeginMutation)
    return source._liftData(control)
}

/** Sets the loop start position (0..1) */
@StrudelDsl
val loopBegin by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopBeginMutation) }

/** Sets the loop start position (0..1) */
@StrudelDsl
val StrudelPattern.loopBegin by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopBegin(p, args) }

/** Sets the loop start position (0..1) */
@StrudelDsl
val String.loopBegin by dslStringExtension { p, args, _ -> applyLoopBegin(p, args) }

/** Sets the loop start position (0..1) - alias for loopBegin */
@StrudelDsl
val StrudelPattern.loopb by dslPatternExtension { p, args, callInfo -> p.loopBegin(args, callInfo) }

/** Sets the loop start position (0..1) - alias for loopBegin */
@StrudelDsl
val loopb by dslFunction { args, callInfo -> loopBegin(args, callInfo) }

/** Sets the loop start position (0..1) - alias for loopBegin */
@StrudelDsl
val String.loopb by dslStringExtension { p, args, callInfo -> p.loopBegin(args, callInfo) }

// -- loopEnd() / loope() ----------------------------------------------------------------------------------------------

private val loopEndMutation = voiceModifier { copy(loopEnd = it?.asDoubleOrNull()) }

private fun applyLoopEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source
    val control = args.toPattern(loopEndMutation)
    return source._liftData(control)
}

/** Sets the loop end position (0..1) */
@StrudelDsl
val loopEnd by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopEndMutation) }

/** Sets the loop end position (0..1) */
@StrudelDsl
val StrudelPattern.loopEnd by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopEnd(p, args) }

/** Sets the loop end position (0..1) */
@StrudelDsl
val String.loopEnd by dslStringExtension { p, args, _ -> applyLoopEnd(p, args) }

/** Sets the loop end position (0..1) - alias for loopEnd */
@StrudelDsl
val StrudelPattern.loope by dslPatternExtension { p, args, callInfo -> p.loopEnd(args, callInfo) }

/** Sets the loop end position (0..1) - alias for loopEnd */
@StrudelDsl
val loope by dslFunction { args, callInfo -> loopEnd(args, callInfo) }

/** Sets the loop end position (0..1) - alias for loopEnd */
@StrudelDsl
val String.loope by dslStringExtension { p, args, callInfo -> p.loopEnd(args, callInfo) }

// -- loopAt() ---------------------------------------------------------------------------------------------------------

private val loopAtMutation = voiceModifier {
    val value = it?.asDoubleOrNull()
    if (value == null) copy(speed = null) else copy(speed = 1.0 / (2.0 * value))
}

private fun applyLoopAt(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    if (args.isEmpty()) return source

    // Get the loopAt factor
    val factor = args[0].value?.asDoubleOrNull() ?: return source

    // Apply slow() to stretch the events to the desired duration
    // loopAt(2) stretches events to 2 cycles, loopAt(0.5) compresses to 0.5 cycles
    val slowed = source.slow(factor)

    // Then set the speed parameter to compensate for sample playback
    val control = args.toPattern(loopAtMutation)
    return slowed._liftData(control)
}

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val loopAt by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopAtMutation) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val StrudelPattern.loopAt by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAt(p, args) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val String.loopAt by dslStringExtension { p, args, callInfo -> p.loopAt(args, callInfo) }

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceModifier { copy(cut = it?.asIntOrNull()) }

private fun applyCut(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs = args.ifEmpty { listOf(StrudelDslArg.of(1.0)) }
    val control = effectiveArgs.toPattern(cutMutation)
    return source._liftData(control)
}

/** Sets the cut group (choke group) */
@StrudelDsl
val cut by dslFunction { args, /* callInfo */ _ -> args.toPattern(cutMutation) }

/** Sets the cut group (choke group) */
@StrudelDsl
val StrudelPattern.cut by dslPatternExtension { p, args, /* callInfo */ _ -> applyCut(p, args) }

/** Sets the cut group (choke group) */
@StrudelDsl
val String.cut by dslStringExtension { p, args, callInfo -> p.cut(args, callInfo) }

// -- slice() ----------------------------------------------------------------------------------------------------------

private fun applySlice(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // TODO: support dynamic index pattern

    val indexArg = args.getOrNull(1)
    val indexVal = indexArg?.value?.asIntOrNull() ?: 0

    val start = indexVal.toDouble() / nVal
    val end = (indexVal + 1.0) / nVal

    return source.begin(start).end(end)
}

/**
 * Plays a slice of the sample.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val StrudelPattern.slice by dslPatternExtension { p, args, /* callInfo */ _ -> applySlice(p, args) }

/**
 * Plays a slice of the sample.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val String.slice by dslStringExtension { p, args, callInfo -> p.slice(args, callInfo) }

// -- splice() ---------------------------------------------------------------------------------------------------------

private fun applySplice(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    // Get number of slices (also used as speed multiplier)
    val nArg = args.getOrNull(0)
    val nVal = maxOf(1, nArg?.value?.asIntOrNull() ?: 1)

    // Apply slice, then multiply speed by n to maintain timing
    return source.slice(args).speed(nVal.toDouble())
}

/**
 * Plays a slice of the sample at the original tempo.
 * Combines slice() with speed() to maintain timing.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val StrudelPattern.splice by dslPatternExtension { p, args, /* callInfo */ _ -> applySplice(p, args) }

/**
 * Plays a slice of the sample at the original tempo.
 * Combines slice() with speed() to maintain timing.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val String.splice by dslStringExtension { p, args, callInfo -> p.splice(args, callInfo) }
