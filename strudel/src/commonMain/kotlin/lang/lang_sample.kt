package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangSampleInit = false

// -- begin() ----------------------------------------------------------------------------------------------------------

private val beginMutation = voiceModifier {
    copy(begin = it?.asDoubleOrNull())
}

private fun applyBegin(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = beginMutation,
        getValue = { begin },
        setValue = { v, c -> copy(begin = c.value?.asDouble ?: v) },
    )
}

/** Sets the sample start position (0..1) */
@StrudelDsl
val begin by dslFunction { args, callInfo -> args.toPattern(beginMutation) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val StrudelPattern.begin by dslPatternExtension { p, args, callInfo -> applyBegin(p, args) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val String.begin by dslStringExtension { p, args, callInfo -> applyBegin(p, args) }

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceModifier {
    copy(end = it?.asDoubleOrNull())
}

private fun applyEnd(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = endMutation,
        getValue = { end },
        setValue = { v, c -> copy(end = c.value?.asDouble ?: v) },
    )
}

/** Sets the sample end position (0..1) */
@StrudelDsl
val end by dslFunction { args, callInfo -> args.toPattern(endMutation) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val StrudelPattern.end by dslPatternExtension { p, args, callInfo -> applyEnd(p, args) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val String.end by dslStringExtension { p, args, callInfo -> applyEnd(p, args) }

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceModifier {
    copy(speed = it?.asDoubleOrNull())
}

private fun applySpeed(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = speedMutation,
        getValue = { speed },
        setValue = { v, c -> copy(speed = c.value?.asDouble ?: v) },
    )
}

/** Sets the sample playback speed */
@StrudelDsl
val speed by dslFunction { args, callInfo -> args.toPattern(speedMutation) }

/** Sets the sample playback speed */
@StrudelDsl
val StrudelPattern.speed by dslPatternExtension { p, args, callInfo -> applySpeed(p, args) }

/** Sets the sample playback speed */
@StrudelDsl
val String.speed by dslStringExtension { p, args, callInfo -> applySpeed(p, args) }

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceModifier {
    copy(loop = it?.asVoiceValue()?.asBoolean)
}

private fun applyLoop(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs: List<StrudelDslArg<Any?>> = when (args.isEmpty()) {
        true -> listOf(StrudelDslArg(1.0, null))
        else -> args
    }

    return source.applyNumericalParam(
        args = effectiveArgs,
        modify = loopMutation,
        getValue = { if (loop == true) 1.0 else 0.0 },
        setValue = { v, c -> copy(loop = c.value?.asBoolean ?: v.asVoiceValue().asBoolean) },
    )
}

/** Enables sample looping */
@StrudelDsl
val loop by dslFunction { args, callInfo -> args.toPattern(loopMutation) }

/** Enables sample looping */
@StrudelDsl
val StrudelPattern.loop by dslPatternExtension { p, args, callInfo -> applyLoop(p, args) }

/** Enables sample looping */
@StrudelDsl
val String.loop by dslStringExtension { p, args, callInfo -> applyLoop(p, args) }

// -- loopAt() ---------------------------------------------------------------------------------------------------------

private val loopAtMutation = voiceModifier {
    val value = it?.asDoubleOrNull()

    // 1.0 -> 0.5
    // 2.0 -> 0.25
    // 3.0 -> 0.16666666666666
    // 4.0 -> 0.125

    // What is the formula?


    if (value == null) {
        copy(speed = null)
    } else {
        copy(speed = 1.0 / (2.0 * value))
    }
}

private fun applyLoopAt(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = loopAtMutation,
        getValue = { speed },
        setValue = { v, c -> copy(speed = c.value?.asDouble ?: v) },
    )
}

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val loopAt by dslFunction { args, callInfo -> args.toPattern(loopAtMutation) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val StrudelPattern.loopAt by dslPatternExtension { p, args, callInfo -> applyLoopAt(p, args) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val String.loopAt by dslStringExtension { p, args, callInfo -> applyLoopAt(p, args) }

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceModifier {
    copy(cut = it?.asIntOrNull())
}

private fun applyCut(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val effectiveArgs: List<StrudelDslArg<Any?>> = when (args.isEmpty()) {
        true -> listOf(StrudelDslArg(1.0, null))
        else -> args
    }

    return source.applyNumericalParam(
        args = effectiveArgs,
        modify = cutMutation,
        getValue = { cut?.toDouble() },
        setValue = { v, c -> copy(cut = c.value?.asInt ?: v.toInt()) },
    )
}

/** Sets the cut group (choke group) */
@StrudelDsl
val cut by dslFunction { args, callInfo -> args.toPattern(cutMutation) }

/** Sets the cut group (choke group) */
@StrudelDsl
val StrudelPattern.cut by dslPatternExtension { p, args, callInfo -> applyCut(p, args) }

/** Sets the cut group (choke group) */
@StrudelDsl
val String.cut by dslStringExtension { p, args, callInfo -> applyCut(p, args) }

// -- slice() ----------------------------------------------------------------------------------------------------------

private fun applySlice(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    val n = args.getOrNull(0)?.asIntOrNull() ?: 1
    val indexArg = args.getOrNull(1)

    // TODO: support dynamic index pattern
    val index = indexArg?.asIntOrNull() ?: 0

    val start = index.toDouble() / n
    val end = (index + 1.0) / n

    return source.begin(start).end(end)
}

/**
 * Plays a slice of the sample.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val StrudelPattern.slice by dslPatternExtension { p, args, callInfo -> applySlice(p, args) }

/**
 * Plays a slice of the sample.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val String.slice by dslStringExtension { p, args, callInfo -> applySlice(p, args) }
