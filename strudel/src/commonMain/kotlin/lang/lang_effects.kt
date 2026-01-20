@file:Suppress("DuplicatedCode")

package io.peekandpoke.klang.strudel.lang

import io.peekandpoke.klang.strudel.StrudelPattern

/**
 * Accessing this property forces the initialization of this file's class,
 * ensuring all 'by dsl...' delegates are registered in StrudelRegistry.
 */
var strudelLangEffectsInit = false

// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Effects
// ///

// -- distort() --------------------------------------------------------------------------------------------------------

private val distortMutation = voiceModifier {
    copy(distort = it?.asDoubleOrNull())
}

private fun applyDistort(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = distortMutation,
        getValue = { distort },
        setValue = { v, _ -> copy(distort = v) },
    )
}

@StrudelDsl
val StrudelPattern.distort by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDistort(p, args)
}

@StrudelDsl
val distort by dslFunction { args, /* callInfo */ _ -> args.toPattern(distortMutation) }

@StrudelDsl
val String.distort by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDistort(p, args)
}

// -- crush() ----------------------------------------------------------------------------------------------------------

private val crushMutation = voiceModifier { copy(crush = it?.asDoubleOrNull()) }

private fun applyCrush(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = crushMutation,
        getValue = { crush },
        setValue = { v, _ -> copy(crush = v) },
    )
}

@StrudelDsl
val StrudelPattern.crush by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCrush(p, args)
}

@StrudelDsl
val crush by dslFunction { args, /* callInfo */ _ -> args.toPattern(crushMutation) }

@StrudelDsl
val String.crush by dslStringExtension { p, args, /* callInfo */ _ ->
    applyCrush(p, args)
}

// -- coarse() ---------------------------------------------------------------------------------------------------------

private val coarseMutation = voiceModifier {
    copy(coarse = it?.asDoubleOrNull())
}

private fun applyCoarse(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = coarseMutation,
        getValue = { coarse },
        setValue = { v, _ -> copy(coarse = v) },
    )
}

@StrudelDsl
val StrudelPattern.coarse by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyCoarse(p, args)
}

@StrudelDsl
val coarse by dslFunction { args, /* callInfo */ _ -> args.toPattern(coarseMutation) }

@StrudelDsl
val String.coarse by dslStringExtension { p, args, /* callInfo */ _ ->
    applyCoarse(p, args)
}

// -- room() -----------------------------------------------------------------------------------------------------------

private val roomMutation = voiceModifier {
    copy(room = it?.asDoubleOrNull())
}

private fun applyRoom(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = roomMutation,
        getValue = { room },
        setValue = { v, _ -> copy(room = v) },
    )
}

@StrudelDsl
val StrudelPattern.room by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRoom(p, args)
}

@StrudelDsl
val room by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomMutation) }

@StrudelDsl
val String.room by dslStringExtension { p, args, /* callInfo */ _ ->
    applyRoom(p, args)
}

// -- roomsize() / rsize() ---------------------------------------------------------------------------------------------

private val roomSizeMutation = voiceModifier {
    copy(roomSize = it?.asDoubleOrNull())
}

private fun applyRoomSize(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = roomSizeMutation,
        getValue = { roomSize },
        setValue = { v, _ -> copy(roomSize = v) },
    )
}

@StrudelDsl
val StrudelPattern.roomsize by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyRoomSize(p, args)
}

@StrudelDsl
val roomsize by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }

@StrudelDsl
val String.roomsize by dslStringExtension { p, args, /* callInfo */ _ ->
    applyRoomSize(p, args)
}

/** Alias for [roomsize] */
@StrudelDsl
val StrudelPattern.rsize by dslPatternExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

/** Alias for [roomsize] */
@StrudelDsl
val rsize by dslFunction { args, /* callInfo */ _ -> args.toPattern(roomSizeMutation) }

/** Alias for [roomsize] on a string */
@StrudelDsl
val String.rsize by dslStringExtension { p, args, /* callInfo */ _ -> applyRoomSize(p, args) }

// -- delay() ----------------------------------------------------------------------------------------------------------

private val delayMutation = voiceModifier {
    copy(delay = it?.asDoubleOrNull())
}

private fun applyDelay(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = delayMutation,
        getValue = { delay },
        setValue = { v, _ -> copy(delay = v) },
    )
}

@StrudelDsl
val StrudelPattern.delay by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelay(p, args)
}

@StrudelDsl
val delay by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayMutation) }

@StrudelDsl
val String.delay by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDelay(p, args)
}

// -- delaytime() ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceModifier {
    copy(delayTime = it?.asDoubleOrNull())
}

private fun applyDelayTime(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = delayTimeMutation,
        getValue = { delayTime },
        setValue = { v, _ -> copy(delayTime = v) },
    )
}

@StrudelDsl
val StrudelPattern.delaytime by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayTime(p, args)
}

@StrudelDsl
val delaytime by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayTimeMutation) }

@StrudelDsl
val String.delaytime by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDelayTime(p, args)
}

// -- delayfeedback() --------------------------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceModifier {
    copy(delayFeedback = it?.asDoubleOrNull())
}

private fun applyDelayFeedback(source: StrudelPattern, args: List<StrudelDslArg<Any?>>): StrudelPattern {
    return source.applyNumericalParam(
        args = args,
        modify = delayFeedbackMutation,
        getValue = { delayFeedback },
        setValue = { v, _ -> copy(delayFeedback = v) },
    )
}

@StrudelDsl
val StrudelPattern.delayfeedback by dslPatternExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}

@StrudelDsl
val delayfeedback by dslFunction { args, /* callInfo */ _ -> args.toPattern(delayFeedbackMutation) }

@StrudelDsl
val String.delayfeedback by dslStringExtension { p, args, /* callInfo */ _ ->
    applyDelayFeedback(p, args)
}
