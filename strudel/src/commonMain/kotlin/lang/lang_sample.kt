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
val begin by dslFunction { args, /* callInfo */ _ -> args.toPattern(beginMutation) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val StrudelPattern.begin by dslPatternExtension { p, args, /* callInfo */ _ -> applyBegin(p, args) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val String.begin by dslStringExtension { p, args, callInfo -> p.begin(args, callInfo) }

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
val end by dslFunction { args, /* callInfo */ _ -> args.toPattern(endMutation) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val StrudelPattern.end by dslPatternExtension { p, args, /* callInfo */ _ -> applyEnd(p, args) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val String.end by dslStringExtension { p, args, callInfo -> p.end(args, callInfo) }

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
val speed by dslFunction { args, /* callInfo */ _ -> args.toPattern(speedMutation) }

/** Sets the sample playback speed */
@StrudelDsl
val StrudelPattern.speed by dslPatternExtension { p, args, /* callInfo */ _ -> applySpeed(p, args) }

/** Sets the sample playback speed */
@StrudelDsl
val String.speed by dslStringExtension { p, args, callInfo -> p.speed(args, callInfo) }

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
val loop by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopMutation) }

/** Enables sample looping */
@StrudelDsl
val StrudelPattern.loop by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoop(p, args) }

/** Enables sample looping */
@StrudelDsl
val String.loop by dslStringExtension { p, args, callInfo -> p.loop(args, callInfo) }

// -- loopAt() ---------------------------------------------------------------------------------------------------------

private val loopAtMutation = voiceModifier {
    val value = it?.asDoubleOrNull()

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
val loopAt by dslFunction { args, /* callInfo */ _ -> args.toPattern(loopAtMutation) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val StrudelPattern.loopAt by dslPatternExtension { p, args, /* callInfo */ _ -> applyLoopAt(p, args) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val String.loopAt by dslStringExtension { p, args, callInfo -> p.loopAt(args, callInfo) }

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
