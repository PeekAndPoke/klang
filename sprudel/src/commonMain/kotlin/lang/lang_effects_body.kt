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
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- body ------------------------------------------------------------------------------------------------------------

private fun applyBody(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { v -> clone().also { it.body = v?.lowercase() } }
}

private val bodyWetMutation = voiceSetter { bodyMix = it?.asDoubleOrNull() }

private fun applyBodyWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bodyMix }, update = bodyWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bodyWetMutation)
}

private val bodyFloorMutation = voiceSetter { bodyFloor = it?.asDoubleOrNull() }

private fun applyBodyFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bodyFloor }, update = bodyFloorMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bodyFloorMutation)
}

/**
 * The resonant body: the material, its mix and its dry floor.
 *
 * Runs the orbit through a resonating body, the way a guitar top or a drum shell colours what passes
 * through it. One body per [orbit bus](/manuals/lexikon/orbit-bus), so ALL of it, `material`
 * included, is set once for everyone by the orbit's owning voice.
 *
 * `wet` here is a mix, not a [send](/manuals/lexikon/send): the body processes the whole orbit mix,
 * so unlike `room` and `delay` there is no per-voice amount. Give a pattern its own body by giving
 * it its own orbit.
 *
 * Materials: woods `wood`, `cedar`, `spruce`, `mahogany`, `rosewood`, `maple`, `oak`; bowed string
 * `violin`; voice `croon`; pipe and glass `tube`, `glass`; skin `membrane`; metals `brass`, `steel`,
 * `bell`. `none` removes the body. One name at a time: `body("wood glass")` is mini-notation, so it
 * is wood for the first half of the cycle and glass for the second, not a blend of the two.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`body(floor = mul(2))`), and the numeric slots read back as `body.wet`, `body.floor`. `material` is a name and has no reader.
 * With no argument at all, the pattern's own values are reinterpreted as `material`.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").body("wood", 0.7)                                 // a wooden box around the tone
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").body("wood glass", 0.7).body(wet = mul("<1 0.5>"))   // wood then glass, half as boxy every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").body("tube", "0.2 0.9").room(wet = body.wet)      // as much reverb as body
 * ```
 *
 * @param material Material name. See the list above.
 * @param wet How much of the orbit runs through the body, 0 to 1.
 * @param floor Minimum dry share kept in the mix, 0 to 1.
 * @param-tool material SprudelBodyEditor, SprudelBodySequenceEditor
 *
 * @scope orbit
 * @category effects
 * @tags body, material, wet, floor
 */
@KlangScript.Function
fun SprudelPattern.body(
    material: PatternLike? = null,
    wet: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A tail-only call must not touch material: reinterpret runs only on a fully bare call.
    var p = if (material != null || !(wet != null || floor != null)) {
        applyBody(this, listOfNotNull(material).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (wet != null) p = applyBodyWet(p, listOf<Any?>(wet).asSprudelDslArgs(callInfo?.forParam(1)))
    if (floor != null) p = applyBodyFloor(p, listOf<Any?>(floor).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [body]. */
@KlangScript.Function
fun String.body(
    material: PatternLike? = null,
    wet: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).body(material, wet, floor, callInfo)

/** Chains a [body] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.body(
    material: PatternLike? = null,
    wet: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.body(material, wet, floor, callInfo) }

/**
 * The `body` object: `body(...)` sets the slots, and each numeric slot reads back as a child,
 * `body.wet`, `body.floor`.
 *
 * @scope orbit
 * @category effects
 * @tags body, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("body")
object body {

    /** The wet slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val wet: FieldAccessor = FieldAccessor { it.bodyMix }

    /** The floor slot of each event, as a value other setters can read. */
    @KlangScript.Property
    val floor: FieldAccessor = FieldAccessor { it.bodyFloor }

    /** The setter, see [SprudelPattern.body]. */
    @KlangScript.Invoke
    operator fun invoke(
        material: PatternLike? = null,
        wet: PatternLike? = null,
        floor: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.body(material, wet, floor, callInfo) }
}
