/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftOrReinterpretStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs

// -- body() -----------------------------------------------------------------------------------------------------------

private fun applyBody(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretStringField(args) { v -> clone().also { it.body = v?.lowercase() } }
}

/**
 * Adds a resonating body to the voice — a bank of fixed resonances mixed on top of the dry
 * source so it sounds like a physical instrument instead of a synthetic/plastic tube.
 *
 * The body's resonances are at *fixed* frequencies that do **not** move with the played note,
 * so different notes get colored differently — the cue your ear reads as a real object. The
 * resonances blend on top of a dry that never drops below its physical floor (the shared C4
 * wet/dry law with `BODY_FLOOR` — see [bodyFloor]), so no highs/lows are lost. Pair with
 * [bodyWet] to set the balance; the default is a moderate amount.
 *
 * When called with no argument, reinterprets the current event value as the material name.
 *
 * **Materials:** woods — `wood`, `cedar`, `spruce`, `mahogany`, `rosewood`, `maple`, `oak`;
 * strings — `violin`; voice — `croon`; pipe/glass — `tube`, `glass`; skin — `membrane`; metals —
 * `brass`, `steel`, `bell`. Use `none` to reset (removes the body).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").body("wood")              // warm wooden body
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 c3 c4").body("cedar").bodyWet(0.4) // warm cedar guitar top, played across octaves
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").body("brass").bodyWet(0.4) // metallic horn colour
 * ```
 *
 * @param material The body material — one of `wood`, `cedar`, `tube`, `glass`, `membrane`, `brass`.
 * @param-tool material SprudelBodyEditor, SprudelBodySequenceEditor
 * @category effects
 * @tags body, resonator, modal, formant, material, wood, cedar, spruce, mahogany, rosewood, maple, oak, violin, croon, voice, tube, glass, brass, steel, bell, metal, none
 */
@KlangScript.Function
fun SprudelPattern.body(material: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBody(this, listOfNotNull(material).asSprudelDslArgs(callInfo))

/** Sets the body resonator on a string pattern. */
@KlangScript.Function
fun String.body(material: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).body(material, callInfo)

/** Returns a [PatternMapperFn] that adds a body resonator. */
@KlangScript.Function
fun body(material: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.body(material, callInfo) }

/** Chains a body step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.body(material: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.body(material, callInfo) }

// -- bodyWet() --------------------------------------------------------------------------------------------------------

private val bodyWetMutation = voiceSetter { bodyMix = it?.asDoubleOrNull() }

private fun applyBodyWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bodyMix }, update = bodyWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bodyWetMutation)
}

/**
 * Sets the body resonator's wet/dry balance — the shared wet knob (C4), on the body it is
 * prefixed because sprudel sets fields on one unordered voice. `0.0` = no body, `1.0` = the
 * resonances at full level. The dry never drops below its physical floor ([bodyFloor], the
 * correlated branch of the shared wet/dry law), so broadband content is never lost.
 *
 * Use with [body]. Start around 0.3–0.5. The knob lives on `[0, 1]`: values above 1 behave
 * as 1 (the old raw extension is a deleted capability). When omitted, the pattern's own
 * numeric values are reinterpreted as the amount.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").body("glass").bodyWet(0.4)   // glassy body blended in at 0.4
 * ```
 *
 * @category effects
 * @tags body, resonator, mix, dry, wet
 */
@KlangScript.Function
fun SprudelPattern.bodyWet(wet: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBodyWet(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))

/** Sets the body resonator wet balance on a string pattern. */
@KlangScript.Function
fun String.bodyWet(wet: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bodyWet(wet, callInfo)

/**
 * The body resonator mix of each event, as a value other setters can read.
 *
 * Bare `bodyWet` reads what the chain has set so far, so it comes after whatever set the field
 * (`bodyWet(...)` or an alias). Call it, `bodyWet(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").body("cedar").bodyWet(0.4).bodyWet(mul("1 0.5"))          // the second note less body
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").body("cedar").bodyWet("0.2 0.6").bodyFloor(bodyWet.mul(0.5))   // floor follows mix
 * ```
 *
 * @category effects
 * @tags bodyWet, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("bodyWet")
object BodyWet : FieldAccessor({ it.bodyMix }) {

    /** Returns a [PatternMapperFn] that sets the body resonator wet balance. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(wet: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.bodyWet(wet, callInfo) }
}

/** The [BodyWet] accessor as a value, so the Kotlin door reads like the script. */
val bodyWet: BodyWet = BodyWet

/** Chains a bodyWet step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bodyWet(wet: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bodyWet(wet, callInfo) }

// -- bodyFloor() ------------------------------------------------------------------------------------------------------

private val bodyFloorMutation = voiceSetter { bodyFloor = it?.asDoubleOrNull() }

private fun applyBodyFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bodyFloor }, update = bodyFloorMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bodyFloorMutation)
}

/**
 * Sets the body resonator's broadband dry floor — how much of the untouched dry source stays under
 * the resonances. Lower makes the body more audible (the resonances sit over less dry); higher is a
 * subtler colour. When omitted, the engine default is used. Independent of [bodyWet] (which
 * lives on `[0, 1]`) — the floor sets the *dry* level, the wet drives the *resonances*.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").body("brass").bodyFloor(0.2)  // brass forward over a thin dry floor
 * ```
 *
 * @category effects
 * @tags body, resonator, floor, dry, mix
 */
@KlangScript.Function
fun SprudelPattern.bodyFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBodyFloor(this, listOfNotNull(floor).asSprudelDslArgs(callInfo))

/** Sets the body resonator floor on a string pattern. */
@KlangScript.Function
fun String.bodyFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bodyFloor(floor, callInfo)

/**
 * The body resonator floor of each event, as a value other setters can read.
 *
 * Bare `bodyFloor` reads what the chain has set so far, so it comes after whatever set the field
 * (`bodyFloor(...)` or an alias). Call it, `bodyFloor(...)`, to set the field; a mapper argument applies
 * to the field.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").body("brass").bodyFloor(0.2).bodyFloor(add("0 0.4"))       // more dry on the second note
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").body("brass").bodyFloor("0.1 0.5").bodyWet(bodyFloor.add(0.3))   // mix follows floor
 * ```
 *
 * @category effects
 * @tags bodyFloor, accessor
 */
@KlangScript.Library("sprudel")
@KlangScript.Object("bodyFloor")
object BodyFloor : FieldAccessor({ it.bodyFloor }) {

    /** Returns a [PatternMapperFn] that sets the body resonator floor. */
    @KlangScript.Method(name = "invoke")
    operator fun invoke(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
        { p -> p.bodyFloor(floor, callInfo) }
}

/** The [BodyFloor] accessor as a value, so the Kotlin door reads like the script. */
val bodyFloor: BodyFloor = BodyFloor

/** Chains a bodyFloor step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bodyFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bodyFloor(floor, callInfo) }
