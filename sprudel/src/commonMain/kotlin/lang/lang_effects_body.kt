/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("DuplicatedCode", "ObjectPropertyName", "Detekt:TooManyFunctions", "ClassName")
@file:KlangScript.Library("sprudel")

package io.peekandpoke.klang.sprudel.lang

import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.script.annotations.KlangScript
import io.peekandpoke.klang.script.ast.CallInfo
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel._liftOrReinterpretNumericalField
import io.peekandpoke.klang.sprudel._liftStringField
import io.peekandpoke.klang.sprudel._mapNumericField
import io.peekandpoke.klang.sprudel.katalystParamsOrNew
import io.peekandpoke.klang.sprudel.lang.SprudelDslArg.Companion.asSprudelDslArgs
import io.peekandpoke.klang.sprudel.putKatalystParam

// -- body ------------------------------------------------------------------------------------------------------------

/**
 * Fills the body stage's companions, `body.wet` and `body.floor`, from
 * `constants/BusEffectDefaults.kt` (`/dsl-design` §4 is the rule; this is only what THIS door
 * does). Called from the MATERIAL setter alone, because the material is this stage's name knob.
 *
 * Fills the voice FIELDS with the same two constants. The wire still carries them (as a
 * `FilterDef.Body` in `filters`), but since step 5b-1 the orbit's resonator reads the SLOTS and
 * nothing else, and since step 5b-3 no backend code reads that filter at all.
 *
 * Byte-identical to what the engine did with an unset field: `toVoiceData` reads a null `bodyMix`
 * as [BODY_WET], and a null floor means [BODY_FLOOR] to `FilterDef.Body`; the chain takes the same
 * two through `KatalystSlots.bodyDef`. What the fill buys is that one place decides them instead
 * of two engine fallbacks that could drift.
 */
private fun SprudelVoiceData.fillBodyDefaults() {
    val slots = katalystParamsOrNew()

    slots.setOrDefault("body.wet", value = null, default = BODY_WET)
    slots.setOrDefault("body.floor", value = null, default = BODY_FLOOR)

    if (bodyMix == null) {
        bodyMix = BODY_WET
    }

    if (bodyFloor == null) {
        bodyFloor = BODY_FLOOR
    }
}

// The MATERIAL goes into the orbit chain's slot state as a number: `body.material` carries the
// material's INDEX in `BodyMaterials.names`, and `BodyMaterials.indexOf` is the one conversion both
// doors use (Katalyst step 5a-2, 2026-09-18). That is what keeps `body(material = "wood")` working
// on a DECLARED chain without a string ever reaching the wire as a slot. An unknown name is index
// 0, `none`, which is the same "off" the voice field's unknown material resolves to. A control
// value is always A STRING here (`asString` is total, so `body(material = 5)` names "5.0" and lands
// on index 0).
//
// Naming the material is what fills the rest of the stage. The material is not this door's head
// (the wet is, since step 3d(iii), 2026-09-24), so no bare call reaches this setter and nothing
// ever hands it a null: it has no clear arm and no reinterpret path. The off switch is
// `body(material = "none")`, index 0.
private fun applyBody(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    return source._liftStringField(args) { v ->
        val material = v?.lowercase() ?: return@_liftStringField this

        clone().also { data ->
            data.body = material
            data.putKatalystParam("body.material", BodyMaterials.indexOf(material))
            data.fillBodyDefaults()
        }
    }
}

// The two NUMBERS go into the slot state as well (`body.wet`, `body.floor`), which is what makes
// this door an alias of `katp` on a DECLARED chain (signal-flow plan §7, Katalyst step 5a). A
// wet-only or floor-only call writes only its own knob: the NAME KNOB of this stage is the
// MATERIAL, and a door never invents a name knob.
//
// No setter here has a CLEAR arm. `_liftNumericField` returns early on a control value that is not
// a number, `_mapNumericField` skips a null mapping (the 2026-09-16 rule), and a REST calls no
// setter at all. The only path that passes null is the bare-call reinterpret, which reaches the
// door's HEAD setter, the WET; it then writes nothing (`?: return@voiceSetter`), like the heads of
// `reverb` and `delay`.

private val bodyWetMutation = voiceSetter {
    val wet = it?.asDoubleOrNull() ?: return@voiceSetter

    bodyMix = wet
    putKatalystParam("body.wet", wet)
}

private fun applyBodyWet(source: SprudelPattern, args: List<SprudelDslArg<Any?>>): SprudelPattern {
    args.singleMapperOrNull()?.let { mapper ->
        return source._mapNumericField(mapper, read = { it.bodyMix }, update = bodyWetMutation)
    }

    return source._liftOrReinterpretNumericalField(args, bodyWetMutation)
}

private val bodyFloorMutation = voiceSetter {
    bodyFloor = it?.asDoubleOrNull()
    putKatalystParam("body.floor", bodyFloor)
}

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
 * `wet` here is a mix (and `floor` how much of the dry stays under it): the body processes the whole
 * orbit mix. The `wet` of `reverb` and `delay` reads differently: it is how much of the orbit goes
 * INTO them, a [send](/manuals/lexikon/send), and their sound is added on top of the dry. Give a
 * pattern its own body by giving it its own orbit.
 *
 * Materials: woods `wood`, `cedar`, `spruce`, `mahogany`, `rosewood`, `maple`, `oak`; bowed string
 * `violin`; voice `croon`; pipe and glass `tube`, `glass`; skin `membrane`; metals `brass`, `steel`,
 * `bell`. `none` removes the body. One name at a time: `body(material = "wood glass")` is mini-notation, so it
 * is wood for the first half of the cycle and glass for the second, not a blend of the two.
 *
 * Every slot is independent and patternable; an omitted slot keeps its value, a named slot takes
 * a mapper (`body(floor = mul(2))`), and the numeric slots read back as `body.wet`, `body.floor`. `material` is a name and has no reader.
 * With no argument at all, the pattern's own values are reinterpreted as `wet`
 * (`seq("<0.2 0.5>").body()`); a value that is not a number writes nothing.
 *
 * `wet` comes first, as on every door that has one. The material is the second parameter, so a
 * material alone is written by name: `body(material = "wood")`.
 *
 * Naming a material sets the whole stage: `wet` and `floor` take their shared defaults, 0.5 and 0.4,
 * unless an earlier call already set them, so `body(material = "wood")` is as audible as it always
 * was. Naming `wet` or `floor` alone does NOT invent a material, because the material is what
 * switches the body on. `body(material = "none")` switches it off again.
 *
 * Slots apply in order, `wet` first. A wet MAPPER in the same call as the material therefore maps a
 * wet that is not set yet, which does nothing: `body(mul(2), "wood")` on a fresh note is wet 0.5,
 * the material's default. To double the wet, map it in a later call, once it is set:
 * `body(material = "wood").body(wet = mul(2))` is wet 1.0.
 *
 * All three reach a DECLARED orbit chain as chain slots, `material` as the index of its name in the
 * material catalogue (`body.material`, 0 = none), so the same call works whether the orbit declares
 * a chain or runs the historical one, down to the sample. Guarded by
 * `KatalystDoorFillRenderSpec`, which renders the short and the long spelling and compares samples.
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").body(0.7, "wood")                                 // a wooden box around the tone
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").body(0.7, "wood glass").body(wet = mul("<1 0.5>"))   // wood then glass, half as boxy every other bar
 * ```
 *
 * ```KlangScript(Playable)
 * note("c3 e3").s("saw").body("<0.2 0.9>", "tube").reverb(wet = body.wet, size = 4)   // as much reverb as body
 * ```
 *
 * @param wet How much of the orbit runs through the body, 0 to 1.
 * @param material Material name. See the list above.
 * @param floor Minimum dry share kept in the mix, 0 to 1.
 * @param-tool material SprudelBodyEditor, SprudelBodySequenceEditor
 *
 * @scope orbit
 * @category effects
 * @tags body, wet, material, floor
 */
@KlangScript.Function
fun SprudelPattern.body(
    wet: PatternLike? = null,
    material: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern {
    // A call without a wet must not touch wet: reinterpret runs only on a fully bare call.
    var p = if (wet != null || !(material != null || floor != null)) {
        applyBodyWet(this, listOfNotNull(wet).asSprudelDslArgs(callInfo))
    } else {
        this
    }
    if (material != null) p = applyBody(p, listOf<Any?>(material).asSprudelDslArgs(callInfo?.forParam(1)))
    if (floor != null) p = applyBodyFloor(p, listOf<Any?>(floor).asSprudelDslArgs(callInfo?.forParam(2)))
    return p
}

/** Parses this string as a pattern, then applies [body]. */
@KlangScript.Function
fun String.body(
    wet: PatternLike? = null,
    material: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): SprudelPattern =
    this.toVoiceValuePattern(callInfo?.receiverLocation).body(wet, material, floor, callInfo)

/** Chains a [body] step onto this [PatternMapperFn]. */
@KlangScript.Function
fun PatternMapperFn.body(
    wet: PatternLike? = null,
    material: PatternLike? = null,
    floor: PatternLike? = null,
    callInfo: CallInfo? = null
): PatternMapperFn =
    this.chain { p -> p.body(wet, material, floor, callInfo) }

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
        wet: PatternLike? = null,
        material: PatternLike? = null,
        floor: PatternLike? = null,
        callInfo: CallInfo? = null
    ): PatternMapperFn =
        { p -> p.body(wet, material, floor, callInfo) }
}
