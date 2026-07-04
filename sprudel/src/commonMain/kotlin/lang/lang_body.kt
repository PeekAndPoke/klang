/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
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
 * resonances are *added on top of* the dry source (never crossfaded away, so no highs/lows are
 * lost). Pair with [bodyMix] to set how much body is added; the default is a moderate amount.
 *
 * When called with no argument, reinterprets the current event value as the material name.
 *
 * **Materials:** woods — `wood`, `cedar`, `spruce`, `mahogany`, `rosewood`, `maple`, `oak`;
 * strings — `violin`; pipe/glass — `tube`, `glass`; skin — `membrane`; metals — `brass`, `steel`,
 * `bell`. Use `none` to reset (removes the body).
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").body("wood")              // warm wooden body
 * ```
 *
 * ```KlangScript(Playable)
 * note("c2 c3 c4").body("cedar").bodyMix(0.4) // warm cedar guitar top, played across octaves
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").body("brass").bodyMix(0.4) // metallic horn colour
 * ```
 *
 * @param material The body material — one of `wood`, `cedar`, `tube`, `glass`, `membrane`, `brass`.
 * @param-tool material SprudelBodySequenceEditor
 * @category effects
 * @tags body, resonator, modal, formant, material, wood, cedar, spruce, mahogany, rosewood, maple, oak, violin, tube, glass, brass, steel, bell, metal, none
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

// -- bodyMix() --------------------------------------------------------------------------------------------------------

private val bodyMixMutation = voiceSetter { bodyMix = it?.asDoubleOrNull() }

private fun applyBodyMix(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bodyMixMutation)
}

/**
 * Sets how much body resonance is added on top of the dry source (0.0 = none). The dry signal
 * always passes through in full — the body is *added*, never crossfaded away — so no highs or
 * lows are lost.
 *
 * Use with [body]. Higher values lay more resonance on top — start around 0.3–0.5; values above
 * 1 drive it harder. When omitted, the pattern's own numeric values are reinterpreted as the
 * amount.
 *
 * ```KlangScript(Playable)
 * note("c3 e3 g3").body("glass").bodyMix(0.4)   // glassy body added at 0.4
 * ```
 *
 * @category effects
 * @tags body, resonator, mix, dry, wet
 */
@KlangScript.Function
fun SprudelPattern.bodyMix(mix: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    applyBodyMix(this, listOfNotNull(mix).asSprudelDslArgs(callInfo))

/** Sets the body resonator mix on a string pattern. */
@KlangScript.Function
fun String.bodyMix(mix: PatternLike? = null, callInfo: CallInfo? = null): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).bodyMix(mix, callInfo)

/** Returns a [PatternMapperFn] that sets the body resonator mix. */
@KlangScript.Function
fun bodyMix(mix: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bodyMix(mix, callInfo) }

/** Chains a bodyMix step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bodyMix(mix: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bodyMix(mix, callInfo) }

// -- bodyFloor() ------------------------------------------------------------------------------------------------------

private val bodyFloorMutation = voiceSetter { bodyFloor = it?.asDoubleOrNull() }

private fun applyBodyFloor(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftOrReinterpretNumericalField(args, bodyFloorMutation)
}

/**
 * Sets the body resonator's broadband dry floor — how much of the untouched dry source stays under
 * the resonances. Lower makes the body more audible (the resonances sit over less dry); higher is a
 * subtler colour. When omitted, the engine default is used. Independent of [bodyMix] (which is
 * uncapped above 1) — the floor sets the *dry* level, the mix drives the *resonances*.
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

/** Returns a [PatternMapperFn] that sets the body resonator floor. */
@KlangScript.Function
fun bodyFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    { p -> p.bodyFloor(floor, callInfo) }

/** Chains a bodyFloor step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.bodyFloor(floor: PatternLike? = null, callInfo: CallInfo? = null): PatternMapperFn =
    this.chain { p -> p.bodyFloor(floor, callInfo) }
