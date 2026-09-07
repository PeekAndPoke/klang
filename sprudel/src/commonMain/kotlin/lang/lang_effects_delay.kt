/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- delay, the wet slot ---------------------------------------------------------------------------------------------

private val delayMutation = voiceSetter {
    val str = it?.toString() ?: return@voiceSetter
    delay = str.toDoubleOrNull() ?: delay
}

private fun applyDelay(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delay }, update = delayMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayMutation)
}


/**
 * The orbit delay: send, time, feedback and the feedback cap.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`delay(time = mul(2))`), and the numeric slots read back as `delay.wet`, `delay.time`, `delay.feedback`, `delay.cap`.
 * With no argument at all, the pattern's own values are reinterpreted as `wet`.
 *
 * ```KlangScript(Playable)
 * s("hh*4").delay(0.3, 0.25, 0.4)                                        // send, time, feedback
 * ```
 *
 * ```KlangScript(Playable)
 * s("hh*4").delay(0.3, 0.25).delay(wet = mul("1 0 1 0"))                  // delay on every other hat only
 * ```
 *
 * ```KlangScript(Playable)
 * s("sd sd").delay("0.1 0.4", 0.25).room(wet = delay.wet)                  // as much reverb as delay
 * ```
 *
 * @param wet Delay send, 0 to 1.
 * @param time Delay time in seconds (`pure(1/8).div(cps)` for tempo sync).
 * @param feedback Feedback, 0 to 1; above 1 builds up.
 * @param cap Feedback cap, the ceiling the repeats may not exceed.
 * @param-tool wet SprudelDelayEditor, SprudelDelaySequenceEditor
 * @param-tool time SprudelDelayTimeEditor, SprudelDelayTimeSequenceEditor
 * @param-tool feedback SprudelDelayFeedbackEditor, SprudelDelayFeedbackSequenceEditor
 *
 * @category effects
 * @tags delay, wet, time, feedback, cap
 */
@KlangScript.Function
fun SprudelPattern.delay(
    wet: PatternLike? = null,
    time: PatternLike? = null,
    feedback: PatternLike? = null,
    cap: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(time != null || feedback != null || cap != null)) {
        applyDelay(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (time != null) p = applyDelayTime(p, listOf<Any?>(time).asSprudelDslArgs(callInfo?.forParam(1)))
    if (feedback != null) p = applyDelayFeedback(p, listOf<Any?>(feedback).asSprudelDslArgs(callInfo?.forParam(2)))
    if (cap != null) p = applyDelayCap(p, listOf<Any?>(cap).asSprudelDslArgs(callInfo?.forParam(3)))
    return p
}

/** Parses this string as a pattern, then applies [delay]. */
@KlangScript.Function
fun String.delay(
    wet: PatternLike? = null,
    time: PatternLike? = null,
    feedback: PatternLike? = null,
    cap: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).delay(wet, time, feedback, cap, callInfo)

/** Chains a [delay] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.delay(
    wet: PatternLike? = null,
    time: PatternLike? = null,
    feedback: PatternLike? = null,
    cap: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.delay(wet, time, feedback, cap, callInfo) }

/**
 * The `delay` object: `delay(...)` sets the slots, and each numeric slot reads back as a child,
 * `delay.wet`, `delay.time`, `delay.feedback`, `delay.cap`.
 *
 * @category effects
 * @tags delay, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("delay")
object delay {

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.delay }

    /** The time slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val time: FieldAccessor = FieldAccessor { it.delayTime }

    /** The feedback slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val feedback: FieldAccessor = FieldAccessor { it.delayFeedback }

    /** The cap slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val cap: FieldAccessor = FieldAccessor { it.delayCap }

    /** The setter, see [SprudelPattern.delay]. */
    @KlangScript.Invoke
    operator fun invoke(
        wet: PatternLike? = null,
        time: PatternLike? = null,
        feedback: PatternLike? = null,
        cap: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.delay(wet, time, feedback, cap, callInfo) }
}

// -- delay.time ------------------------------------------------------------------------------------------------------

private val delayTimeMutation = voiceSetter { delayTime = it?.asDoubleOrNull() }

private fun applyDelayTime(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayTime }, update = delayTimeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayTimeMutation)
}

// -- delay.feedback --------------------------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceSetter { delayFeedback = it?.asDoubleOrNull() }

private fun applyDelayFeedback(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayFeedback }, update = delayFeedbackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayFeedbackMutation)
}

// -- delay.cap -------------------------------------------------------------------------------------------------------

private val delayCapMutation = voiceSetter { delayCap = it?.asDoubleOrNull() }

private fun applyDelayCap(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayCap }, update = delayCapMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayCapMutation)
}
