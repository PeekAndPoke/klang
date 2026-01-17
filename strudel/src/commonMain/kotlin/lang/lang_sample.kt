package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.pattern.ReinterpretPattern.Companion.reinterpretVoice

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangSampleInit = false

// -- begin() ----------------------------------------------------------------------------------------------------------

private val beginMutation = voiceModifier {
    copy(begin = it?.asDoubleOrNull())
}

private fun applyBegin(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = beginMutation,
        getValue = { begin },
        setValue = { v, _ -> copy(begin = v) },
    )
}

/** Sets the sample start position (0..1) */
@StrudelDsl
val StrudelPattern.begin by dslPatternExtension { p, args -> applyBegin(p, args) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val begin by dslFunction { args -> args.toPattern(beginMutation) }

/** Sets the sample start position (0..1) */
@StrudelDsl
val String.begin by dslStringExtension { p, args -> applyBegin(p, args) }

// -- end() ------------------------------------------------------------------------------------------------------------

private val endMutation = voiceModifier {
    copy(end = it?.asDoubleOrNull())
}

private fun applyEnd(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = endMutation,
        getValue = { end },
        setValue = { v, _ -> copy(end = v) },
    )
}

/** Sets the sample end position (0..1) */
@StrudelDsl
val StrudelPattern.end by dslPatternExtension { p, args -> applyEnd(p, args) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val end by dslFunction { args -> args.toPattern(endMutation) }

/** Sets the sample end position (0..1) */
@StrudelDsl
val String.end by dslStringExtension { p, args -> applyEnd(p, args) }

// -- speed() ----------------------------------------------------------------------------------------------------------

private val speedMutation = voiceModifier {
    copy(speed = it?.asDoubleOrNull())
}

private fun applySpeed(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = speedMutation,
        getValue = { speed },
        setValue = { v, _ -> copy(speed = v) },
    )
}

/** Sets the sample playback speed */
@StrudelDsl
val StrudelPattern.speed by dslPatternExtension { p, args -> applySpeed(p, args) }

/** Sets the sample playback speed */
@StrudelDsl
val speed by dslFunction { args -> args.toPattern(speedMutation) }

/** Sets the sample playback speed */
@StrudelDsl
val String.speed by dslStringExtension { p, args -> applySpeed(p, args) }

// -- loop() -----------------------------------------------------------------------------------------------------------

private val loopMutation = voiceModifier {
    copy(loop = true)
}

private fun applyLoop(source: StrudelPattern): StrudelPattern {
    return source.reinterpretVoice { it.copy(loop = true) }
}

/** Enables sample looping */
@StrudelDsl
val StrudelPattern.loop by dslPatternExtension { p, _ -> applyLoop(p) }

/** Enables sample looping */
@StrudelDsl
val loop by dslFunction { args -> args.toPattern(loopMutation) }

/** Enables sample looping */
@StrudelDsl
val String.loop by dslStringExtension { p, _ -> applyLoop(p) }

// -- loopAt() ---------------------------------------------------------------------------------------------------------

private val loopAtMutation = voiceModifier {
    copy(loopAt = it?.asDoubleOrNull())
}

private fun applyLoopAt(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = loopAtMutation,
        getValue = { loopAt },
        setValue = { v, _ -> copy(loopAt = v) },
    )
}

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val StrudelPattern.loopAt by dslPatternExtension { p, args -> applyLoopAt(p, args) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val loopAt by dslFunction { args -> args.toPattern(loopAtMutation) }

/** Fits the sample to the specified number of cycles */
@StrudelDsl
val String.loopAt by dslStringExtension { p, args -> applyLoopAt(p, args) }

// -- cut() ------------------------------------------------------------------------------------------------------------

private val cutMutation = voiceModifier {
    copy(cut = it?.asIntOrNull())
}

private fun applyCut(source: StrudelPattern, args: List<Any?>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = cutMutation,
        getValue = { cut?.toDouble() },
        setValue = { v, _ -> copy(cut = v.toInt()) },
    )
}

/** Sets the cut group (choke group) */
@StrudelDsl
val StrudelPattern.cut by dslPatternExtension { p, args -> applyCut(p, args) }

/** Sets the cut group (choke group) */
@StrudelDsl
val cut by dslFunction { args -> args.toPattern(cutMutation) }

/** Sets the cut group (choke group) */
@StrudelDsl
val String.cut by dslStringExtension { p, args -> applyCut(p, args) }

// -- slice() ----------------------------------------------------------------------------------------------------------

/**
 * Plays a slice of the sample.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val StrudelPattern.slice by dslPatternExtension { p, args ->
    val n = args.getOrNull(0)?.asIntOrNull() ?: 1
    val indexArg = args.getOrNull(1)

    // TODO: support dynamic index pattern
    val index = indexArg?.asIntOrNull() ?: 0

    val start = index.toDouble() / n
    val end = (index + 1.0) / n

    p.begin(start).end(end)
}

/**
 * Plays a slice of the sample.
 * @param {n} Total number of slices
 * @param {index} Index of the slice to play (0 to n-1)
 */
@StrudelDsl
val String.slice by dslStringExtension { p, args ->
    p.slice(args)
}
