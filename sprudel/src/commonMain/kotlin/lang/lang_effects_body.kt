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
 * The resonant body: the material, its send and its dry floor.
 *
 * Runs the sound through a resonating body; the material is a name, `wet` how much goes through it.
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
 * note("c3 e3").s("saw").body("wood glass", 0.7).body(wet = mul("<1 0.5>"))   // half as boxy every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").body("tube", "0.2 0.9").room(wet = body.wet)      // as much reverb as body
 * ```
 *
 * @param material Material name. Woods: `wood`, `cedar`, `spruce`, `mahogany`, `rosewood`, `maple`, `oak`; bowed string:
 *   `violin`; voice: `croon`; pipe and glass: `tube`, `glass`; skin: `membrane`; metals: `brass`, `steel`, `bell`. `none` removes the body.
 * @param wet Send, 0 to 1.
 * @param floor Minimum dry share of the wet/dry law, 0 to 1.
 * @param-tool material SprudelBodyEditor, SprudelBodySequenceEditor
 *
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
