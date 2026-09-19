/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.katalystParamsOrNew
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- the call sets every slot ----------------------------------------------------------------------------------------

/**
 * Fills the delay stage's companions from `constants/SendEffectDefaults.kt`, the same constants the
 * master delay uses (`/dsl-design` §4 is the rule; this is only what THIS door does). Called from
 * every delay setter, because the delay has no name knob: `delay(time = 0.5)` fills `wet` and the
 * echo is ON.
 *
 * Fills the voice FIELDS with the same four constants, so the two sources agree. Since step 5b-1
 * the orbit's line reads the SLOTS alone; the `delay` field is the per-voice send AMOUNT until step
 * 5b-2, and the rest leave the wire in 5b-3.
 *
 * Byte-identical to what the engine did with an unset field: `VoiceFactory` substituted exactly
 * these constants.
 *
 * The HEAD setter, the send, is the one setter here a bare call can reach with a null; it then
 * writes nothing at all (`if (wet != null)`). Only the reverb door clears on that path, in
 * `applyReverb`'s no-args block.
 */
private fun SprudelVoiceData.fillDelayDefaults() {
    val slots = katalystParamsOrNew()

    slots.setOrDefault("delay.wet", value = null, default = DELAY_WET)
    slots.setOrDefault("delay.time", value = null, default = DELAY_TIME_SECONDS)
    slots.setOrDefault("delay.feedback", value = null, default = DELAY_FEEDBACK)
    slots.setOrDefault("delay.cap", value = null, default = DELAY_CAP)

    if (delay == null) {
        delay = DELAY_WET
    }

    if (delayTime == null) {
        delayTime = DELAY_TIME_SECONDS
    }

    if (delayFeedback == null) {
        delayFeedback = DELAY_FEEDBACK
    }

    if (delayCap == null) {
        delayCap = DELAY_CAP
    }
}

// -- delay, the wet slot ---------------------------------------------------------------------------------------------

private val delayMutation = voiceSetter {
    val wet = it?.toString()?.toDoubleOrNull()

    if (wet != null) {
        delay = wet
        katalystParamsOrNew().set("delay.wet", wet)
        fillDelayDefaults()
    }
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
 * One delay line per orbit, so `time`, `feedback` and `cap` are set once for everyone by the
 * orbit's owning voice. `wet` is the exception: it is a per-voice
 * [send](/manuals/lexikon/send), so a dry voice on a wet orbit stays dry. Give a pattern its
 * own delay by giving it its own [orbit bus](/manuals/lexikon/orbit-bus).
 *
 * The call sets every slot: the ones you leave out take the same default as on the master delay,
 * wet 0.25, time 0.25 s, feedback 0.3 and cap 1, unless an earlier call already set them. So a bare
 * `delay(0.4)` already echoes a quarter second later, and `delay.time` reads 0.25 after it. Slots
 * apply in order, wet first, so a mapper on a later slot sees a default an earlier slot of the same
 * call filled in. A call whose only slot rests in its control pattern writes nothing on that event,
 * and fills nothing.
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
 * s("sd sd").delay("0.1 0.4", 0.25).reverb(wet = delay.wet, size = 4)       // as much reverb as delay
 * ```
 *
 * @param wet Send into the orbit delay, 0 to 1, default 0.25. Per voice.
 * @param time Delay time in seconds, default 0.25. Orbit-wide.
 * @param feedback Feedback, 0 to 1, default 0.3. Above 1 builds up. Orbit-wide.
 * @param cap Ceiling the repeats may not exceed, default 1. Orbit-wide.
 * @param-tool wet SprudelDelayEditor, SprudelDelaySequenceEditor
 * @param-tool time SprudelDelayTimeEditor, SprudelDelayTimeSequenceEditor
 * @param-tool feedback SprudelDelayFeedbackEditor, SprudelDelayFeedbackSequenceEditor
 *
 * @scope orbit-send
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
 * @scope orbit-send
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

private val delayTimeMutation = voiceSetter {
    delayTime = it?.asDoubleOrNull()

    val value = delayTime

    if (value != null) {
        katalystParamsOrNew().set("delay.time", value)
        fillDelayDefaults()
    }
}

private fun applyDelayTime(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayTime }, update = delayTimeMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayTimeMutation)
}

// -- delay.feedback --------------------------------------------------------------------------------------------------

private val delayFeedbackMutation = voiceSetter {
    delayFeedback = it?.asDoubleOrNull()

    val value = delayFeedback

    if (value != null) {
        katalystParamsOrNew().set("delay.feedback", value)
        fillDelayDefaults()
    }
}

private fun applyDelayFeedback(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayFeedback }, update = delayFeedbackMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayFeedbackMutation)
}

// -- delay.cap -------------------------------------------------------------------------------------------------------

private val delayCapMutation = voiceSetter {
    delayCap = it?.asDoubleOrNull()

    val value = delayCap

    if (value != null) {
        katalystParamsOrNew().set("delay.cap", value)
        fillDelayDefaults()
    }
}

private fun applyDelayCap(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.delayCap }, update = delayCapMutation)
    }

    return source._liftOrReinterpretNumericalField(args, delayCapMutation)
}
